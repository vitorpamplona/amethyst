/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.service.uploads

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import androidx.core.net.toUri
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.UUID

object GifToMp4Converter {
    private const val LOG_TAG = "GifToMp4Converter"
    private const val I_FRAME_INTERVAL = 1
    private const val TIMEOUT_US = 10_000L
    private const val DEFAULT_BITRATE_BPS = 2_000_000
    private const val DEFAULT_FRAME_DELAY_MS = 100
    private const val US_PER_MS = 1000L
    private const val NS_PER_US = 1000L

    // Fullscreen quad: 4 vertices x (2 position + 2 texcoord) floats
    // Texcoord Y is flipped because bitmap origin is top-left, GL is bottom-left
    private val QUAD_COORDS =
        floatArrayOf(
            -1f, -1f, 0f, 1f,
            1f, -1f, 1f, 1f,
            -1f, 1f, 0f, 0f,
            1f, 1f, 1f, 0f,
        )

    private const val VERTEX_SHADER =
        "attribute vec4 aPosition;\n" +
            "attribute vec2 aTexCoord;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "  gl_Position = aPosition;\n" +
            "  vTexCoord = aTexCoord;\n" +
            "}\n"

    private const val FRAGMENT_SHADER =
        "precision mediump float;\n" +
            "varying vec2 vTexCoord;\n" +
            "uniform sampler2D uTexture;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(uTexture, vTexCoord);\n" +
            "}\n"

    suspend fun convert(
        uri: Uri,
        context: Context,
    ): MediaCompressorResult? =
        withContext(Dispatchers.IO) {
            try {
                convertInternal(uri, context)
            } catch (e: Exception) {
                Log.e(LOG_TAG, "GIF to MP4 conversion failed", e)
                null
            }
        }

    @Suppress("deprecation")
    private fun convertInternal(
        uri: Uri,
        context: Context,
    ): MediaCompressorResult? {
        val gifBytes =
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: run {
                    Log.w(LOG_TAG) { "Failed to read GIF bytes" }
                    return null
                }

        val movie =
            Movie.decodeByteArray(gifBytes, 0, gifBytes.size)
                ?: run {
                    Log.w(LOG_TAG) { "Failed to decode GIF" }
                    return null
                }

        val gifWidth = movie.width()
        val gifHeight = movie.height()
        val durationMs = movie.duration()

        if (gifWidth <= 0 || gifHeight <= 0 || durationMs <= 0) {
            Log.w(LOG_TAG) { "Invalid GIF dimensions ($gifWidth x $gifHeight) or duration ($durationMs ms)" }
            return null
        }

        val frameDelays = parseGifFrameDelays(gifBytes)
        if (frameDelays.isEmpty()) {
            Log.w(LOG_TAG) { "No frames found in GIF" }
            return null
        }

        val totalDelayMs = frameDelays.sum()
        val avgFps =
            if (totalDelayMs > 0) {
                (frameDelays.size * 1000.0 / totalDelayMs).toInt().coerceIn(1, 50)
            } else {
                10
            }

        val width = gifWidth.roundToEven()
        val height = gifHeight.roundToEven()

        Log.d(LOG_TAG) {
            "Converting GIF: ${gifWidth}x$gifHeight, duration=${durationMs}ms, " +
                "frames=${frameDelays.size}, avgFps=$avgFps -> MP4 ${width}x$height"
        }

        val outputFile = File(context.cacheDir, "${UUID.randomUUID()}.mp4")
        var codec: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var egl: EglHelper? = null
        var codecSurface: Surface? = null

        try {
            val mimeType = MediaFormat.MIMETYPE_VIDEO_AVC
            val format =
                MediaFormat.createVideoFormat(mimeType, width, height).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, calculateBitrate(width, height))
                    setInteger(MediaFormat.KEY_FRAME_RATE, avgFps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
                }

            codec = MediaCodec.createEncoderByType(mimeType)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codecSurface = codec.createInputSurface()
            codec.start()

            // Wrap the codec surface with EGL for presentation timestamp control
            egl = EglHelper(codecSurface)
            egl.makeCurrent()

            // Set up GL program and texture for drawing bitmaps
            val program = createGlProgram()
            val textureId = createGlTexture()
            val vertexBuffer = createVertexBuffer()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false
            val bufferInfo = MediaCodec.BufferInfo()

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val bitmapCanvas = Canvas(bitmap)

            var presentationTimeUs = 0L
            var gifTimeMs = 0

            for (i in frameDelays.indices) {
                movie.setTime(gifTimeMs)

                bitmapCanvas.drawColor(Color.WHITE)
                movie.draw(bitmapCanvas, 0f, 0f)

                // Draw bitmap to EGL surface via GL texture
                drawBitmapFrame(bitmap, textureId, program, vertexBuffer, width, height)

                // Set the precise presentation timestamp and submit
                egl.setPresentationTime(presentationTimeUs * NS_PER_US)
                egl.swapBuffers()

                // Drain encoder output
                val drainResult = drainEncoder(codec, muxer, bufferInfo, trackIndex, muxerStarted, false)
                trackIndex = drainResult.first
                muxerStarted = drainResult.second

                presentationTimeUs += frameDelays[i] * US_PER_MS
                gifTimeMs += frameDelays[i]
            }

            // Signal end of stream and drain
            codec.signalEndOfInputStream()
            drainEncoder(codec, muxer, bufferInfo, trackIndex, muxerStarted, true)

            // Cleanup GL resources
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            GLES20.glDeleteProgram(program)
            bitmap.recycle()

            Log.d(LOG_TAG) { "GIF to MP4 conversion complete: ${outputFile.length()} bytes" }

            return MediaCompressorResult(outputFile.toUri(), "video/mp4", outputFile.length())
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Encoding failed", e)
            if (outputFile.exists()) outputFile.delete()
            return null
        } finally {
            try { egl?.release() } catch (_: Exception) { }
            try { codec?.stop() } catch (_: Exception) { }
            try { codec?.release() } catch (_: Exception) { }
            try { codecSurface?.release() } catch (_: Exception) { }
            try { muxer?.stop() } catch (_: Exception) { }
            try { muxer?.release() } catch (_: Exception) { }
        }
    }

    // region EGL

    private class EglHelper(surface: Surface) {
        val display: EGLDisplay
        val context: EGLContext
        val eglSurface: EGLSurface

        init {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            check(display != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }

            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }

            val configAttribs =
                intArrayOf(
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                    EGL14.EGL_NONE,
                )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            check(EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0)) {
                "eglChooseConfig failed"
            }

            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            context = EGL14.eglCreateContext(display, configs[0]!!, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(display, configs[0]!!, surface, surfaceAttribs, 0)
            check(eglSurface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface failed" }
        }

        fun makeCurrent() {
            check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "eglMakeCurrent failed" }
        }

        fun setPresentationTime(nsecs: Long) {
            EGLExt.eglPresentationTimeANDROID(display, eglSurface, nsecs)
        }

        fun swapBuffers() {
            EGL14.eglSwapBuffers(display, eglSurface)
        }

        fun release() {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, eglSurface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
    }

    // endregion

    // region GL helpers

    private fun createGlProgram(): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return program
    }

    private fun compileShader(
        type: Int,
        source: String,
    ): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun createGlTexture(): Int {
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texIds[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return texIds[0]
    }

    private fun createVertexBuffer(): FloatBuffer =
        ByteBuffer
            .allocateDirect(QUAD_COORDS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(QUAD_COORDS)
                position(0)
            }

    private fun drawBitmapFrame(
        bitmap: Bitmap,
        textureId: Int,
        program: Int,
        vertexBuffer: FloatBuffer,
        width: Int,
        height: Int,
    ) {
        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(1f, 1f, 1f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        val posHandle = GLES20.glGetAttribLocation(program, "aPosition")
        val texHandle = GLES20.glGetAttribLocation(program, "aTexCoord")

        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)

        // stride = 4 floats per vertex (2 pos + 2 tex) * 4 bytes
        val stride = 4 * 4
        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, stride, vertexBuffer)

        vertexBuffer.position(2)
        GLES20.glEnableVertexAttribArray(texHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, stride, vertexBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(texHandle)
    }

    // endregion

    // region GIF parsing

    /**
     * Parses per-frame delays from the GIF binary by reading Graphic Control Extension blocks.
     * Returns a list of delays in milliseconds, one per frame.
     *
     * GIF delay values are in centiseconds (1/100s). Per browser convention,
     * delays of 0 or 1 centisecond are treated as 100ms (10fps).
     */
    private fun parseGifFrameDelays(bytes: ByteArray): List<Int> {
        if (bytes.size < 13) return emptyList()

        val delays = mutableListOf<Int>()

        var pos = 13

        // Skip Global Color Table if present
        val packed = bytes[10].toInt() and 0xFF
        if (packed and 0x80 != 0) {
            pos += 3 * (1 shl ((packed and 0x07) + 1))
        }

        while (pos < bytes.size) {
            when (bytes[pos].toInt() and 0xFF) {
                0x21 -> {
                    pos++
                    if (pos >= bytes.size) break
                    val label = bytes[pos].toInt() and 0xFF
                    pos++

                    if (label == 0xF9 && pos + 5 <= bytes.size) {
                        val blockSize = bytes[pos].toInt() and 0xFF
                        if (blockSize == 4) {
                            val delayLow = bytes[pos + 2].toInt() and 0xFF
                            val delayHigh = bytes[pos + 3].toInt() and 0xFF
                            val delayCentiseconds = delayLow or (delayHigh shl 8)
                            val delayMs =
                                if (delayCentiseconds <= 1) DEFAULT_FRAME_DELAY_MS else delayCentiseconds * 10
                            delays.add(delayMs)
                        }
                        pos = skipSubBlocks(bytes, pos)
                    } else {
                        pos = skipSubBlocks(bytes, pos)
                    }
                }
                0x2C -> {
                    pos += 10
                    if (pos > bytes.size) break
                    val imgPacked = bytes[pos - 1].toInt() and 0xFF
                    if (imgPacked and 0x80 != 0) {
                        pos += 3 * (1 shl ((imgPacked and 0x07) + 1))
                    }
                    pos++
                    pos = skipSubBlocks(bytes, pos)
                }
                0x3B -> break
                else -> pos++
            }
        }

        return delays
    }

    private fun skipSubBlocks(
        bytes: ByteArray,
        startPos: Int,
    ): Int {
        var pos = startPos
        while (pos < bytes.size) {
            val blockSize = bytes[pos].toInt() and 0xFF
            pos++
            if (blockSize == 0) break
            pos += blockSize
        }
        return pos
    }

    // endregion

    // region Encoder helpers

    private fun drainEncoder(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        trackIndex: Int,
        muxerStarted: Boolean,
        endOfStream: Boolean,
    ): Pair<Int, Boolean> {
        var currentTrackIndex = trackIndex
        var currentMuxerStarted = muxerStarted

        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return Pair(currentTrackIndex, currentMuxerStarted)
                }

                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    currentTrackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    currentMuxerStarted = true
                }

                outputIndex >= 0 -> {
                    val outputBuffer =
                        codec.getOutputBuffer(outputIndex)
                            ?: throw RuntimeException("Encoder output buffer $outputIndex was null")

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size > 0 && currentMuxerStarted) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(currentTrackIndex, outputBuffer, bufferInfo)
                    }

                    codec.releaseOutputBuffer(outputIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        return Pair(currentTrackIndex, currentMuxerStarted)
                    }
                }
            }
        }
    }

    private fun calculateBitrate(
        width: Int,
        height: Int,
    ): Int {
        val pixels = width * height
        return when {
            pixels >= 1920 * 1080 -> 4_000_000
            pixels >= 1280 * 720 -> DEFAULT_BITRATE_BPS
            pixels >= 640 * 480 -> 1_000_000
            else -> 500_000
        }
    }

    private fun Int.roundToEven(): Int = if (this % 2 != 0) this + 1 else this

    // endregion
}
