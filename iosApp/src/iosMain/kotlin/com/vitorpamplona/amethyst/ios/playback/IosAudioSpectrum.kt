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
package com.vitorpamplona.amethyst.ios.playback

import com.vitorpamplona.amethyst.commons.audio.MonoPcmRing
import com.vitorpamplona.amethyst.commons.audio.PcmDownmix
import com.vitorpamplona.amethyst.commons.audio.PcmSpectrumAnalyzer
import com.vitorpamplona.amethyst.commons.audio.Spectrum
import com.vitorpamplona.amethyst.commons.audio.SpectrumTrail
import com.vitorpamplona.amethyst.commons.audio.delayedByLatency
import com.vitorpamplona.quartz.utils.Log
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.ShortVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.IOBufferDuration
import platform.AVFAudio.outputLatency
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVKeyValueStatusLoaded
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMutableAudioMix
import platform.AVFoundation.AVMutableAudioMixInputParameters
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.asset
import platform.AVFoundation.audioMix
import platform.AVFoundation.currentItem
import platform.AVFoundation.tracksWithMediaType
import platform.CoreAudioTypes.AudioBufferList
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreAudioTypes.kAudioFormatFlagIsFloat
import platform.CoreAudioTypes.kAudioFormatFlagIsSignedInteger
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.MediaToolbox.MTAudioProcessingTapCallbacks
import platform.MediaToolbox.MTAudioProcessingTapCreate
import platform.MediaToolbox.MTAudioProcessingTapGetSourceAudio
import platform.MediaToolbox.MTAudioProcessingTapGetStorage
import platform.MediaToolbox.MTAudioProcessingTapRef
import platform.MediaToolbox.MTAudioProcessingTapRefVar
import platform.MediaToolbox.kMTAudioProcessingTapCallbacksVersion_0
import platform.MediaToolbox.kMTAudioProcessingTapCreationFlag_PreEffects
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The real spectrum of what an [AvAmethystPlayer] plays, for the shared audio visualizer: the iOS
 * side of `PlaybackPlatform.rememberAudioSpectrum`. Android taps media3's decoded PCM; here an
 * `MTAudioProcessingTap` rides on the player item's audio track through an `AVMutableAudioMix`.
 *
 * The tap exists only while someone collects [spectrumFor] for that URL: the first collector
 * attaches it, the last one leaving takes the audio mix off the item, so feed videos nobody
 * visualizes pay nothing. A new item on the player (a re-prepare) is noticed and tapped again.
 *
 * The real-time side ([TapState.capture], called from the tap's process callback) only downmixes
 * into preallocated scratch and a lock-free [MonoPcmRing]; a collector on [Dispatchers.Default]
 * drains the ring and runs the shared [PcmSpectrumAnalyzer], one [Spectrum] per 1024 samples as
 * Android does.
 *
 * Streams AVFoundation plays without exposing asset tracks (HLS) get no tap and the flow stays
 * empty: `MTAudioProcessingTap` does not run on HLS, so the visualizer idles there, as Android's
 * does on offloaded audio.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class, ExperimentalCoroutinesApi::class)
object IosAudioSpectrum {
    private const val TAG = "IosAudioSpectrum"

    // How often the main-thread watcher looks for the player and its current item.
    private const val WATCH_MS = 250L

    // How often the collector drains the ring: about half of one 1024-sample frame at 44.1 kHz.
    private const val DRAIN_MS = 12L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Touched on the main thread only.
    private val sessions = HashMap<String, Session>()

    /**
     * The spectrum of [mediaId]'s player, delayed by the audio session's output latency so the
     * bars match the speaker. Empty until a player for that URL plays tappable audio.
     */
    fun spectrumFor(mediaId: String): Flow<Spectrum> =
        flow {
            val session = withContext(Dispatchers.Main) { acquire(mediaId) }
            try {
                session.frames.collect { emit(it) }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) { release(session) }
            }
        }.delayedByLatency(::outputLatencyNanos)

    private fun outputLatencyNanos(): Long {
        val session = AVAudioSession.sharedInstance()
        return ((session.outputLatency + session.IOBufferDuration) * 1_000_000_000.0).toLong()
    }

    private fun acquire(mediaId: String): Session {
        val session = sessions.getOrPut(mediaId) { Session(mediaId) }
        session.users++
        if (session.users == 1) session.start()
        return session
    }

    private fun release(session: Session) {
        session.users--
        if (session.users <= 0) {
            session.stop()
            sessions.remove(session.mediaId)
        }
    }

    private class Session(
        val mediaId: String,
    ) {
        var users = 0

        // As Android's per-media flow: the last frame for a late subscriber, and room for a
        // cluster of frames the visualizer paces out itself.
        val frames =
            MutableSharedFlow<Spectrum>(
                replay = 1,
                extraBufferCapacity = SpectrumTrail.MAX_BACKLOG_FRAMES,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )

        // Main thread only.
        private var watcher: Job? = null
        private var drainer: Job? = null
        private var tappedItem: AVPlayerItem? = null
        private var mix: AVMutableAudioMix? = null

        // Written on main, read by the drainer.
        private val live = AtomicReference<TapState?>(null)

        fun start() {
            watcher =
                scope.launch {
                    while (isActive) {
                        follow(IosPlayerPool.find(mediaId)?.avPlayer?.currentItem)
                        delay(WATCH_MS)
                    }
                }
            drainer =
                scope.launch(Dispatchers.Default) {
                    val analyzer = PcmSpectrumAnalyzer()
                    val mono = FloatArray(analyzer.fftSize)
                    while (isActive) {
                        val state = live.load()
                        if (state != null) {
                            val duration = analyzer.frameDurationNanos(state.sampleRate)
                            while (state.ring.read(mono)) frames.tryEmit(analyzer.analyze(mono, duration))
                        }
                        delay(DRAIN_MS)
                    }
                }
        }

        fun stop() {
            watcher?.cancel()
            drainer?.cancel()
            watcher = null
            drainer = null
            follow(null)
            frames.resetReplayCache()
        }

        /** Moves the tap to [item], the player's current item (null: take it off). */
        private fun follow(item: AVPlayerItem?) {
            if (item === tappedItem) return
            tappedItem?.let { old -> if (old.audioMix === mix) old.audioMix = null }
            mix = null
            live.store(null)
            tappedItem = item
            if (item != null) attach(item)
        }

        /** Loads the item's tracks off the main thread, then puts a tap on its first audio track. */
        private fun attach(item: AVPlayerItem) {
            val asset = item.asset
            asset.loadValuesAsynchronouslyForKeys(listOf(TRACKS_KEY)) {
                dispatch_async(dispatch_get_main_queue()) {
                    if (tappedItem !== item || users <= 0) return@dispatch_async
                    // Loaded, so reading the tracks no longer blocks. HLS lists none: no tap.
                    if (asset.statusOfValueForKey(TRACKS_KEY, error = null) != AVKeyValueStatusLoaded) return@dispatch_async
                    val track = asset.tracksWithMediaType(AVMediaTypeAudio).firstOrNull() as? AVAssetTrack
                    if (track == null) {
                        Log.d(TAG) { "No tappable audio track for $mediaId" }
                        return@dispatch_async
                    }
                    val state = TapState()
                    val tap = createTap(state) ?: return@dispatch_async
                    val params = AVMutableAudioMixInputParameters.audioMixInputParametersWithTrack(track)
                    params.setAudioTapProcessor(tap)
                    // The input parameters retain the tap; ours from Create is no longer needed.
                    CFRelease(tap)
                    val newMix = AVMutableAudioMix.audioMix()
                    newMix.setInputParameters(listOf(params))
                    item.audioMix = newMix
                    mix = newMix
                    live.store(state)
                }
            }
        }
    }

    private const val TRACKS_KEY = "tracks"

    /**
     * Creates a tap whose storage is a [StableRef] to [state]. The ref is released by the tap's
     * finalize callback, when AVFoundation lets go of the tap (the audio mix is dropped).
     */
    private fun createTap(state: TapState): MTAudioProcessingTapRef? {
        val ref = StableRef.create(state)
        return memScoped {
            val callbacks = alloc<MTAudioProcessingTapCallbacks>()
            callbacks.version = kMTAudioProcessingTapCallbacksVersion_0
            callbacks.clientInfo = ref.asCPointer()
            callbacks.init = staticCFunction(::tapInit)
            callbacks.finalize = staticCFunction(::tapFinalize)
            callbacks.prepare = staticCFunction(::tapPrepare)
            callbacks.unprepare = staticCFunction(::tapUnprepare)
            callbacks.process = staticCFunction(::tapProcess)
            val out = alloc<MTAudioProcessingTapRefVar>()
            val status = MTAudioProcessingTapCreate(kCFAllocatorDefault, callbacks.ptr, kMTAudioProcessingTapCreationFlag_PreEffects, out.ptr)
            val tap = out.value
            if (status != 0 || tap == null) {
                Log.w(TAG) { "MTAudioProcessingTapCreate failed: $status" }
                ref.dispose()
                null
            } else {
                tap
            }
        }
    }
}

/**
 * What one tap shares between its real-time callbacks and the collector. The format fields are
 * written by the prepare callback and read by process, both on the audio thread.
 */
@OptIn(ExperimentalForeignApi::class)
internal class TapState {
    val ring = MonoPcmRing()
    private val downmix = PcmDownmix()

    @Volatile
    var sampleRate = 0.0
        private set

    private var isFloat32 = false
    private var isInt16 = false

    fun prepare(
        maxFrames: Int,
        format: AudioStreamBasicDescription,
    ) {
        sampleRate = format.mSampleRate
        val linear = format.mFormatID == kAudioFormatLinearPCM
        isFloat32 = linear && (format.mFormatFlags and kAudioFormatFlagIsFloat) != 0u && format.mBitsPerChannel == 32u
        isInt16 = linear && (format.mFormatFlags and kAudioFormatFlagIsSignedInteger) != 0u && format.mBitsPerChannel == 16u
        downmix.ensureCapacity(maxFrames)
    }

    /**
     * Downmixes one callback's audio into the ring. Real-time: no locks, no coroutines, no
     * allocation beyond the cinterop struct views. Each buffer carries its own channel count, so
     * interleaved (one buffer) and non-interleaved (one per channel) layouts take the same path.
     */
    fun capture(
        list: AudioBufferList,
        frames: Int,
    ) {
        if (!isFloat32 && !isInt16) return
        if (!downmix.begin(frames)) return
        val buffers = list.mBuffers
        for (b in 0 until list.mNumberBuffers.toInt()) {
            val buffer = buffers[b]
            val channels = buffer.mNumberChannels.toInt()
            val data = buffer.mData ?: continue
            val bytesPerSample = if (isFloat32) 4 else 2
            if (buffer.mDataByteSize.toLong() < frames.toLong() * channels * bytesPerSample) continue
            if (isFloat32) {
                val samples = data.reinterpret<FloatVar>()
                downmix.addBuffer(channels) { samples[it] }
            } else {
                val samples = data.reinterpret<ShortVar>()
                downmix.addBuffer(channels) { PcmDownmix.int16ToFloat(samples[it]) }
            }
        }
        downmix.finishInto(ring)
    }
}

// The tap's C callbacks. They capture nothing: each finds its TapState through the tap's storage.

@OptIn(ExperimentalForeignApi::class)
private fun stateOf(tap: MTAudioProcessingTapRef?): TapState? = MTAudioProcessingTapGetStorage(tap)?.asStableRef<TapState>()?.get()

@OptIn(ExperimentalForeignApi::class)
private fun tapInit(
    tap: MTAudioProcessingTapRef?,
    clientInfo: COpaquePointer?,
    tapStorageOut: CPointer<COpaquePointerVar>?,
) {
    tapStorageOut?.pointed?.value = clientInfo
}

@OptIn(ExperimentalForeignApi::class)
private fun tapFinalize(tap: MTAudioProcessingTapRef?) {
    MTAudioProcessingTapGetStorage(tap)?.asStableRef<TapState>()?.dispose()
}

@OptIn(ExperimentalForeignApi::class)
private fun tapPrepare(
    tap: MTAudioProcessingTapRef?,
    maxFrames: Long,
    processingFormat: CPointer<AudioStreamBasicDescription>?,
) {
    val format = processingFormat?.pointed ?: return
    stateOf(tap)?.prepare(maxFrames.toInt(), format)
}

@OptIn(ExperimentalForeignApi::class)
private fun tapUnprepare(tap: MTAudioProcessingTapRef?) {}

@OptIn(ExperimentalForeignApi::class)
private fun tapProcess(
    tap: MTAudioProcessingTapRef?,
    numberFrames: Long,
    flags: UInt,
    bufferListInOut: CPointer<AudioBufferList>?,
    numberFramesOut: CPointer<LongVar>?,
    flagsOut: CPointer<UIntVar>?,
) {
    // The source audio lands in bufferListInOut, which is also what plays: leaving it untouched
    // passes the sound through unchanged.
    val status = MTAudioProcessingTapGetSourceAudio(tap, numberFrames, bufferListInOut, flagsOut, null, numberFramesOut)
    if (status != 0 || bufferListInOut == null) return
    val frames = numberFramesOut?.pointed?.value ?: return
    stateOf(tap)?.capture(bufferListInOut.pointed, frames.toInt())
}
