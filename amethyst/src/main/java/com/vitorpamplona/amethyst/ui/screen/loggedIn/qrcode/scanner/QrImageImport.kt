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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.scanner

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Decoding a QR code out of an image the user already has.
 *
 * This is the single biggest gap the camera-only scanner left: most QR codes people need to scan
 * in a Nostr client arrive as a screenshot, a photo in a chat, or an image saved from a website.
 * Before this, the only way to use one was to display it on a second screen and photograph it.
 */
object QrImageImport {
    /**
     * Longest edge we downscale a picked image to for the first attempt.
     *
     * Detection does not improve above this, and full-resolution phone photos are 50+ megapixels
     * of mostly-wall that make a thorough pass take seconds.
     */
    private const val FIRST_PASS_MAX_EDGE = 2_000

    /** Below this, a code is likely too few pixels per module; the upscale pass is worth trying. */
    private const val SMALL_IMAGE_EDGE = 600

    /** Decodes every QR code in the image at [uri], hardest-effort, with a retry ladder. */
    suspend fun decode(
        context: Context,
        uri: Uri,
        decoder: BarcodeDecoder,
    ): List<ScanResult> =
        withContext(Dispatchers.IO) {
            val bounds = readBounds(context, uri) ?: return@withContext emptyList()
            val longestEdge = max(bounds.outWidth, bounds.outHeight)
            if (longestEdge <= 0) return@withContext emptyList()

            // Three passes, cheapest first. A code that a downscaled pass misses because its
            // modules blurred together often survives at full resolution, and a code in a small
            // thumbnail often needs *more* pixels per module than it shipped with.
            val sampleSizes =
                buildList {
                    add(sampleSizeFor(longestEdge, FIRST_PASS_MAX_EDGE))
                    if (sampleSizeFor(longestEdge, FIRST_PASS_MAX_EDGE) != 1) add(1)
                }

            for (sampleSize in sampleSizes) {
                val found = decodeAt(context, uri, sampleSize, upscale = false, decoder)
                if (found.isNotEmpty()) return@withContext found
            }

            if (longestEdge <= SMALL_IMAGE_EDGE) {
                val found = decodeAt(context, uri, sampleSize = 1, upscale = true, decoder)
                if (found.isNotEmpty()) return@withContext found
            }

            emptyList()
        }

    /** Text sitting on the clipboard, or null when there is none. */
    fun clipboardText(context: Context): String? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip
            .getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /** An image sitting on the clipboard, or null when there is none. */
    fun clipboardImage(context: Context): Uri? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0)?.uri
    }

    private fun decodeAt(
        context: Context,
        uri: Uri,
        sampleSize: Int,
        upscale: Boolean,
        decoder: BarcodeDecoder,
    ): List<ScanResult> {
        val original = loadBitmap(context, uri, sampleSize) ?: return emptyList()
        var upscaled: Bitmap? = null
        return try {
            if (upscale) upscaled = original.scale(original.width * 2, original.height * 2)
            decoder.decode(upscaled ?: original, DecodeEffort.Thorough)
        } catch (e: Exception) {
            Log.w("QrScanner", "Could not decode picked image", e)
            emptyList()
        } finally {
            upscaled?.recycle()
            original.recycle()
        }
    }

    private fun readBounds(
        context: Context,
        uri: Uri,
    ): BitmapFactory.Options? =
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            options
        } catch (e: Exception) {
            Log.w("QrScanner", "Could not read image bounds", e)
            null
        }

    /**
     * Always an ARGB_8888 software bitmap: zxing-cpp reads the pixels over JNI, and a
     * hardware-backed bitmap has no pixels to read.
     */
    private fun loadBitmap(
        context: Context,
        uri: Uri,
        sampleSize: Int,
    ): Bitmap? =
        try {
            val options =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: Exception) {
            Log.w("QrScanner", "Could not load picked image", e)
            null
        }

    /** The power-of-two `inSampleSize` that brings [longestEdge] to at most [target]. */
    private fun sampleSizeFor(
        longestEdge: Int,
        target: Int,
    ): Int {
        var sample = 1
        while (longestEdge / sample > target) sample *= 2
        return sample
    }
}
