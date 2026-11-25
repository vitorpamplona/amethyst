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
package com.vitorpamplona.amethyst.service.playback.player

import android.app.AppOpsManager
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Process
import android.provider.Settings
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntRect
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.commons.audio.Spectrum
import com.vitorpamplona.amethyst.commons.audio.delayedByFrames
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cast_stop_casting
import com.vitorpamplona.amethyst.commons.resources.cast_to_device
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.ui.components.getDialogWindow
import com.vitorpamplona.amethyst.commons.ui.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.commons.ui.playback.mediaitem.MediaItemData
import com.vitorpamplona.amethyst.commons.ui.playback.player.AmethystPlayer
import com.vitorpamplona.amethyst.commons.ui.playback.player.DeviceLevels
import com.vitorpamplona.amethyst.commons.ui.playback.player.ExternalPlayback
import com.vitorpamplona.amethyst.commons.ui.playback.player.PlaybackPlatform
import com.vitorpamplona.amethyst.commons.ui.playback.player.VideoSurfaceStyle
import com.vitorpamplona.amethyst.commons.ui.playback.player.VideoTrack
import com.vitorpamplona.amethyst.commons.ui.playback.player.VisibleRootBounds
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.cast.CastRequest
import com.vitorpamplona.amethyst.service.cast.CastSessionState
import com.vitorpamplona.amethyst.service.cast.resolveCastLiveness
import com.vitorpamplona.amethyst.service.cast.summarizeCastFormat
import com.vitorpamplona.amethyst.service.playback.composable.ConnectVideoController
import com.vitorpamplona.amethyst.service.playback.diskCache.HlsLivenessCache
import com.vitorpamplona.amethyst.service.playback.pip.PipVideoActivity
import com.vitorpamplona.amethyst.service.playback.playerPool.PcmTapRegistry
import com.vitorpamplona.amethyst.ui.cast.CastDevicePickerDialog
import com.vitorpamplona.amethyst.ui.cast.rememberCastWithLocalNetworkPermission
import com.vitorpamplona.amethyst.ui.components.RequestHdrFor
import com.vitorpamplona.amethyst.ui.components.getActivity
import com.vitorpamplona.amethyst.ui.screen.loggedIn.saveMediaToGallery
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import okio.Path
import java.lang.ref.WeakReference
import com.vitorpamplona.amethyst.service.playback.composable.controls.rememberSaveMediaAction as rememberAndroidSaveMediaAction
import com.vitorpamplona.amethyst.ui.components.ShareMediaAction as AppShareMediaAction

/**
 * Android's [PlaybackPlatform]: pooled media3 controllers from the PlaybackService, media3's
 * ContentFrame for the picture, PiP through [PipVideoActivity], Cast, the gallery save, the
 * fullscreen volume/brightness swipe and the PCM tap for the visualizer.
 */
@Stable
class AndroidPlaybackPlatform(
    private val appContext: Context,
) : PlaybackPlatform {
    @Composable
    override fun GetVideoController(
        mediaItem: MediaItemData,
        muted: Boolean,
        play: Boolean,
        releaseOnBackgroundTimeout: Boolean,
        inner: @Composable (MediaControllerState) -> Unit,
    ) = ConnectVideoController(mediaItem, muted, play, releaseOnBackgroundTimeout, inner)

    override fun proxyPortForVideo(
        videoUri: String,
        accountViewModel: AccountViewModel,
    ): Int? = accountViewModel.httpClientBuilder.proxyPortForVideo(videoUri)

    /**
     * A plain ExoPlayer of its own, outside the PlaybackService pool: a recording that is not sent
     * yet has no place in the media session, the lock screen or the video cache, and the pool's
     * data source reads only through OkHttp, which cannot open a file. The default one here reads
     * the file and nothing else.
     */
    @Composable
    override fun rememberLocalAudioPlayer(file: Path?): AmethystPlayer? {
        val player =
            remember(file) {
                file?.toFile()?.takeIf { it.exists() }?.let { local ->
                    val exo = ExoPlayer.Builder(appContext).build()
                    exo.setMediaItem(MediaItem.fromUri(local.toUri()))
                    exo.prepare()
                    Media3Player(exo)
                }
            }
        DisposableEffect(player) {
            onDispose { player?.player?.release() }
        }
        return player
    }

    @OptIn(UnstableApi::class)
    @Composable
    override fun VideoSurface(
        controllerState: MediaControllerState,
        style: VideoSurfaceStyle,
        contentScale: ContentScale,
        modifier: Modifier,
    ) {
        val player = controllerState.media3
        when (style) {
            VideoSurfaceStyle.AUDIO_STRIP -> {
                ContentFrame(
                    modifier = modifier,
                    player = player,
                    keepContentOnReset = true,
                    surfaceType = SURFACE_TYPE_TEXTURE_VIEW, // texture view is better inside lazy layouts.
                )
            }

            else -> {
                // Fullscreen plays into a SurfaceView so the decoder hands HDR frames straight to the
                // compositor. The feed keeps its TextureView, which clips, scales and sits in a lazy
                // list the way a SurfaceView can't; HDR is only requested where the surface can show it.
                val surfaceType = if (style == VideoSurfaceStyle.FULLSCREEN) SURFACE_TYPE_SURFACE_VIEW else SURFACE_TYPE_TEXTURE_VIEW
                if (surfaceType == SURFACE_TYPE_SURFACE_VIEW) RequestHdrFor(player)

                ContentFrame(
                    player = player,
                    modifier = modifier,
                    surfaceType = surfaceType,
                    contentScale = contentScale,
                    // Transparent shutter — media3's default is an opaque black Box that flashes
                    // until EVENT_RENDERED_FIRST_FRAME fires. Letting the layer beneath show
                    // through (blurhash backdrop in the with-blurhash branch, or the post
                    // background otherwise) avoids the black blink when re-entering the feed.
                    shutter = {},
                )
            }
        }
    }

    @Composable
    override fun KeepScreenOnWhilePlaying(player: AmethystPlayer) {
        val view = LocalView.current

        // Keeps the screen on while playing and viewing videos.
        DisposableEffect(key1 = player, key2 = view) {
            val listener = PlayerEventListener(view)

            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                listener.destroy()
            }
        }
    }

    @Composable
    override fun rememberVisibleRootBounds(): VisibleRootBounds {
        val view = LocalView.current
        return remember(view) { VisibleRootBounds { cachedGlobalVisibleRect(view) } }
    }

    // Picture in picture

    override val canPictureInPicture: Boolean by lazy {
        appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    override val isPictureInPictureAllowed: Boolean
        get() {
            val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode =
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                    Process.myUid(),
                    appContext.packageName,
                )
            return mode == AppOpsManager.MODE_ALLOWED
        }

    @Composable
    override fun rememberEnterPictureInPicture(): (MediaItemData, IntRect?) -> Unit {
        val context = LocalContext.current
        return remember(context) {
            { mediaData, bounds ->
                PipVideoActivity.callIn(mediaData, bounds?.let { Rect(it.left, it.top, it.right, it.bottom) }, context.getActivity())
            }
        }
    }

    // Cast

    override val canPlayExternally: Boolean get() = BuildConfig.IS_CASTING_AVAILABLE

    @Composable
    override fun isPlayingExternally(videoUri: String): Boolean {
        val castSessionState by Amethyst.instance.castRegistry.sessionState
            .collectAsStateWithLifecycle()
        return (castSessionState as? CastSessionState.Casting)?.request?.url == videoUri
    }

    @Composable
    override fun rememberExternalPlayback(
        mediaData: MediaItemData,
        bestVideoTrack: VideoTrack?,
    ): ExternalPlayback? {
        val context = LocalContext.current
        val castDialogVisible = remember { mutableStateOf(false) }
        val isThisVideoCasting = isPlayingExternally(mediaData.videoUri)
        val castIcon = if (isThisVideoCasting) MaterialSymbols.CastConnected else MaterialSymbols.Cast
        val castContentDescription =
            stringRes(if (isThisVideoCasting) Res.string.cast_stop_casting else Res.string.cast_to_device)
        // Android 17 Local Network Protection blocks Cast device discovery until the
        // user grants ACCESS_LOCAL_NETWORK, so gate the picker behind the request.
        val showCastPicker = remember { { castDialogVisible.value = true } }
        val openCastPicker = rememberCastWithLocalNetworkPermission(context, showCastPicker)

        // What the local player decoded this as. Only the Cast failure messages use it: a receiver that
        // refuses a video usually will not say why, so this is the only description of it available.
        val castFormatSummary = bestVideoTrack?.let { summarizeCastFormat(it.mimeType, it.codecs, it.width, it.height) }

        if (!BuildConfig.IS_CASTING_AVAILABLE) return null

        return remember(isThisVideoCasting, openCastPicker, castIcon, castContentDescription, mediaData, castFormatSummary) {
            CastButton(
                isActive = isThisVideoCasting,
                symbol = castIcon,
                contentDescription = castContentDescription,
                mediaData = mediaData,
                castFormatSummary = castFormatSummary,
                dialogVisible = castDialogVisible,
                openCastPicker = openCastPicker,
            )
        }
    }

    // Saving and sharing

    override val canSaveMedia: Boolean get() = true

    @Composable
    override fun rememberSaveMediaAction(
        videoUri: String,
        mimeType: String?,
        accountViewModel: AccountViewModel,
    ): () -> Unit =
        rememberAndroidSaveMediaAction { context ->
            accountViewModel.saveMediaToGallery(videoUri, mimeType, context)
        }

    override val canShareMedia: Boolean get() = true

    @Composable
    override fun ShareMediaAction(
        popupExpanded: MutableState<Boolean>,
        videoUri: String,
        postNostrUri: String?,
        blurhash: String?,
        dim: DimensionTag?,
        hash: String?,
        mimeType: String?,
        onDismiss: () -> Unit,
        content: BaseMediaContent,
        accountViewModel: AccountViewModel,
    ) = AppShareMediaAction(
        popupExpanded = popupExpanded,
        videoUri = videoUri,
        postNostrUri = postNostrUri,
        blurhash = blurhash,
        dim = dim,
        hash = hash,
        mimeType = mimeType,
        onDismiss = onDismiss,
        content = content,
        accountViewModel = accountViewModel,
    )

    // Device levels

    @Composable
    override fun rememberDeviceLevels(): DeviceLevels {
        val context = LocalContext.current
        val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
        // Brightness is applied to the fullscreen dialog window so it auto-reverts on dismiss.
        // Returns null for the inline feed player (not inside a dialog).
        val dialogWindow = getDialogWindow()
        val resolver = context.contentResolver
        return remember(audioManager, dialogWindow, resolver) { AndroidDeviceLevels(audioManager, dialogWindow, resolver) }
    }

    // Audio visualizer

    @Composable
    override fun rememberAudioSpectrum(mediaId: String): Flow<Spectrum> {
        val bluetooth by rememberIsBluetoothOutput()
        val delayFrames = if (bluetooth) BT_VISUALIZER_DELAY_FRAMES else FEED_VISUALIZER_DELAY_FRAMES
        return remember(mediaId, delayFrames) { PcmTapRegistry.spectrumFor(mediaId).delayedByFrames(delayFrames) }
    }
}

private class CastButton(
    override val isActive: Boolean,
    override val symbol: MaterialSymbol,
    override val contentDescription: String,
    private val mediaData: MediaItemData,
    private val castFormatSummary: String?,
    private val dialogVisible: MutableState<Boolean>,
    private val openCastPicker: () -> Unit,
) : ExternalPlayback {
    override fun onClick() {
        if (isActive) {
            Amethyst.instance.applicationIOScope.launch {
                Amethyst.instance.castRegistry.stopCasting()
            }
        } else {
            openCastPicker()
        }
    }

    @Composable
    override fun Content() {
        if (dialogVisible.value) {
            CastDevicePickerDialog(
                request =
                    CastRequest(
                        url = mediaData.videoUri,
                        mimeType = mediaData.mimeType,
                        title = mediaData.title,
                        artworkUri = mediaData.artworkUri,
                        // By the time the cast button is reachable the player has already parsed the
                        // playlist, so the learned verdict is normally available here.
                        isLive =
                            resolveCastLiveness(
                                learned = HlsLivenessCache.verdict(mediaData.videoUri),
                                metadataFlag = mediaData.isLiveStream,
                            ),
                        // The local player has already decoded this, so it knows what the receiver
                        // is about to be handed — the only description of the media available when
                        // the receiver refuses it without saying why.
                        formatSummary = castFormatSummary,
                    ),
                onDismiss = { dialogVisible.value = false },
            )
        }
    }
}

/** Keeps the screen on while the player plays: doesn't set it up front, only follows changes. */
private class PlayerEventListener(
    val view: View,
) : AmethystPlayer.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        // doesn't consider the mutex because the screen can turn off if the video
        // being played in the mutex is not visible.
        if (view.keepScreenOn != isPlaying) {
            view.keepScreenOn = isPlaying
        }
    }

    fun destroy() {
        if (view.keepScreenOn) {
            view.keepScreenOn = false
        }
    }
}

// Cached result of view.getGlobalVisibleRect. The compose root rarely changes
// across a scroll burst; caching avoids N native calls per frame.
// The view is held weakly: it is only used for an identity check, never
// dereferenced, and a strong static reference would leak the Activity.
private val cachedRootRect = Rect()
private var cachedRootRectView: WeakReference<View>? = null
private var cachedRootRectVisible: Boolean = false
private var cachedRootIntRect: IntRect? = null
private var cachedRootRectTimeNs: Long = 0L
private const val ROOT_RECT_CACHE_TTL_NS = 8_000_000L // ~half a frame at 60fps

/**
 * Returns the cached visible-rect of the root view if it was computed within
 * the last frame, otherwise refreshes the cache.
 */
private fun cachedGlobalVisibleRect(view: View): IntRect? {
    val now = System.nanoTime()
    if (cachedRootRectView?.get() === view && (now - cachedRootRectTimeNs) < ROOT_RECT_CACHE_TTL_NS) {
        return if (cachedRootRectVisible) cachedRootIntRect else null
    }
    cachedRootRectVisible = view.getGlobalVisibleRect(cachedRootRect)
    cachedRootIntRect =
        cachedRootIntRect?.takeIf {
            it.left == cachedRootRect.left && it.top == cachedRootRect.top && it.right == cachedRootRect.right && it.bottom == cachedRootRect.bottom
        } ?: IntRect(cachedRootRect.left, cachedRootRect.top, cachedRootRect.right, cachedRootRect.bottom)
    cachedRootRectView = WeakReference(view)
    cachedRootRectTimeNs = now
    return if (cachedRootRectVisible) cachedRootIntRect else null
}

private class AndroidDeviceLevels(
    private val audioManager: AudioManager?,
    private val window: Window?,
    private val resolver: ContentResolver,
) : DeviceLevels {
    override val maxMediaVolume: Int get() = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0

    override fun mediaVolume(): Int? = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)

    override fun setMediaVolume(index: Int) {
        // Flag 0 = no system volume UI; the player draws its own ring.
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
    }

    override fun brightness(): Float {
        val override = window?.attributes?.screenBrightness ?: -1f
        if (override in 0f..1f) return override
        val system =
            try {
                Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS)
            } catch (e: Settings.SettingNotFoundException) {
                128
            }
        return (system / 255f).coerceIn(0f, 1f)
    }

    override val canSetBrightness: Boolean get() = window != null

    override fun setBrightness(level: Float) {
        val window = window ?: return
        val params = window.attributes
        params.screenBrightness = level
        window.attributes = params
    }

    override fun releaseBrightness() {
        val window = window ?: return
        val params = window.attributes
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = params
    }
}

// Output-latency compensation. The spectrum is tapped upstream of the AudioTrack output buffer (and,
// over Bluetooth, the codec/transmission lag downstream of it), so the visual leads the speaker and
// must be delayed to match. Each hop is one 1024-sample FFT frame (~23 ms at 44.1 kHz). Tuned on a
// Pixel 9a against the 120-BPM beat of the synthetic demo clip (1 beat = 500 ms): wired/speaker wants
// ~20 hops, Bluetooth ~27 hops (the extra ~7 hops / ~160 ms is the BT codec latency).
//
// DEVICE/CODEC-SPECIFIC tuned constants. Android exposes no reliable output latency, and runtime
// auto-detection from player.currentPosition was tried and rejected (~3.4x off — it measures decode
// buffer depth, not the perceptual sync point). [rememberIsBluetoothOutput] only switches between
// these two presets by route; the BT figure is a per-codec average, so it may be off on other gear.
private const val FEED_VISUALIZER_DELAY_FRAMES = 20
private const val BT_VISUALIZER_DELAY_FRAMES = 27

/**
 * Tracks whether audio is currently routed to a Bluetooth output, updating live as devices connect or
 * disconnect. Bluetooth adds codec/transmission latency downstream of AudioTrack, so the visualizer
 * needs a larger delay there. Keys off CONNECTED A2DP/LE output devices — a good proxy since A2DP
 * captures media playback, and Android exposes no reliable "active media route" before API 31.
 */
@Composable
private fun rememberIsBluetoothOutput(): State<Boolean> {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val isBluetooth = remember { mutableStateOf(audioManager.isBluetoothOutput()) }
    DisposableEffect(audioManager) {
        val callback =
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    isBluetooth.value = audioManager.isBluetoothOutput()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    isBluetooth.value = audioManager.isBluetoothOutput()
                }
            }
        audioManager?.registerAudioDeviceCallback(callback, null)
        onDispose { audioManager?.unregisterAudioDeviceCallback(callback) }
    }
    return isBluetooth
}

// The BLE device types are API 31 constants; an older device simply never reports them.
@Suppress("InlinedApi")
private fun AudioManager?.isBluetoothOutput(): Boolean {
    if (this == null) return false
    return getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
        device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
    }
}
