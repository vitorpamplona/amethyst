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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip71Video.CaptionTrack
import com.vitorpamplona.amethyst.commons.playback.CaptionFileParser
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.failed_to_save_the_video
import com.vitorpamplona.amethyst.commons.resources.play_on_another_device
import com.vitorpamplona.amethyst.commons.resources.play_on_another_device_description
import com.vitorpamplona.amethyst.commons.resources.video_saved_to_the_gallery
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.amethyst.commons.ui.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.commons.ui.playback.mediaitem.MediaItemData
import com.vitorpamplona.amethyst.commons.ui.playback.pip.BackgroundMedia
import com.vitorpamplona.amethyst.commons.ui.playback.player.AmethystPlayer
import com.vitorpamplona.amethyst.commons.ui.playback.player.DeviceLevels
import com.vitorpamplona.amethyst.commons.ui.playback.player.ExternalPlayback
import com.vitorpamplona.amethyst.commons.ui.playback.player.PlaybackPlatform
import com.vitorpamplona.amethyst.commons.ui.playback.player.VideoSize
import com.vitorpamplona.amethyst.commons.ui.playback.player.VideoSurfaceStyle
import com.vitorpamplona.amethyst.commons.ui.playback.player.VideoTrack
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.ios.toNSData
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.externalPlaybackActive
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIScreen
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import kotlin.coroutines.resume

/**
 * iOS's half of the shared player ([PlaybackPlatform]): [AvAmethystPlayer]s from [IosPlayerPool],
 * drawn by [AvPlayerSurface], picture in picture, AirPlay as the "play on another device" button,
 * saving videos to Photos, the share sheet and the screen brightness swipe. The media volume
 * can't be set by an app on iOS, so the fullscreen volume swipe stays off.
 *
 * [download] fetches a file's bytes (captions, saved videos) over the app's HTTP client.
 */
class IosPlaybackPlatform(
    private val download: suspend (String) -> ByteArray?,
    private val keyCache: EncryptionKeyCache,
) : PlaybackPlatform {
    private val decrypted = DecryptedMediaFiles(download)

    // The state handed out for each live player, so picture in picture can mark it as the one
    // that keeps playing in the background.
    private val states = mutableMapOf<AvAmethystPlayer, MediaControllerState>()

    init {
        // The floating window closed: its player is no longer the one that keeps playing.
        PictureInPicture.onStopped = { player ->
            if (BackgroundMedia.bgInstance?.controller == player) BackgroundMedia.bgInstance = null
        }
    }

    @Composable
    override fun GetVideoController(
        mediaItem: MediaItemData,
        muted: Boolean,
        play: Boolean,
        releaseOnBackgroundTimeout: Boolean,
        inner: @Composable (MediaControllerState) -> Unit,
    ) {
        // An encrypted file (a NIP-17, Marmot or Concord attachment) plays from a decrypted copy:
        // AES-GCM opens only the whole blob, and AVPlayer fetches ranges. The placeholder stays
        // until it is ready.
        val keys = remember(mediaItem.videoUri) { keyCache.get(mediaItem.videoUri) }
        val playUrl by produceState(initialValue = if (keys == null) mediaItem.videoUri else null, mediaItem.videoUri) {
            if (keys != null) value = decrypted.fileUrlFor(mediaItem.videoUri, keys)
        }
        val url = playUrl ?: return
        PlayerFor(url, mediaItem, muted, play, inner)
    }

    @Composable
    private fun PlayerFor(
        url: String,
        mediaItem: MediaItemData,
        muted: Boolean,
        play: Boolean,
        inner: @Composable (MediaControllerState) -> Unit,
    ) {
        val metadata = NowPlayingMetadata(mediaItem.title, mediaItem.authorName, mediaItem.artworkUri)
        val player = remember(url) { IosPlayerPool.acquire(url, metadata) }
        val state = remember(player) { states[player] ?: MediaControllerState(controller = player).also { states[player] = it } }

        DisposableEffect(player) {
            onDispose {
                IosPlayerPool.release(player)
                if (IosPlayerPool.find(player.url) == null) states.remove(player)
            }
        }

        LaunchedEffect(player) {
            // Feed videos loop (with the replay cap); a live stream has nothing to loop.
            player.repeatOne = mediaItem.keepPlaying && !mediaItem.isLiveStream
            // As on Android: something already playing in the background keeps the new one quiet.
            val target =
                if (BackgroundMedia.isPlaying() && !BackgroundMedia.isMutex(state)) {
                    0f
                } else if (muted) {
                    0f
                } else {
                    1f
                }
            if (player.volume != target) player.volume = target
            if (play) player.playWhenReady = true
        }

        LaunchedEffect(player, mediaItem.captions) {
            if (mediaItem.captions.isNotEmpty()) player.setSideLoadedCaptions(loadCaptions(mediaItem.captions))
        }

        inner(state)
    }

    private suspend fun loadCaptions(tracks: List<CaptionTrack>): SideLoadedCaptions {
        val files =
            tracks.mapNotNull { track ->
                try {
                    val text = download(track.url)?.decodeToString() ?: return@mapNotNull null
                    SideLoadedCaptions.File(track.language, track.type, CaptionFileParser.parse(text))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not load captions ${track.url}", e)
                    null
                }
            }
        return SideLoadedCaptions(files)
    }

    /**
     * What media3's `ContentFrame` does on Android: the picture takes the space it is given, in
     * the video's own shape under [contentScale], centered. The native view must never be larger
     * than the picture: a see-through part of it would show the white window behind Compose, not
     * the blurhash or the post. Until the first frame says the size, nothing is drawn, so the
     * placeholder underneath stays visible.
     */
    @Composable
    override fun VideoSurface(
        controllerState: MediaControllerState,
        style: VideoSurfaceStyle,
        contentScale: ContentScale,
        modifier: Modifier,
    ) {
        val player = controllerState.controller as? AvAmethystPlayer ?: return
        val size by rememberVideoSize(player)
        BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val video = size
            if (video.width <= 0 || video.height <= 0) return@BoxWithConstraints
            val ratio = video.width * video.pixelWidthHeightRatio / video.height
            val surfaceModifier =
                if (contentScale == ContentScale.Crop || contentScale == ContentScale.FillBounds || !constraints.hasBoundedHeight) {
                    // Crop fills the box (the layer crops the overflow); an unbounded height takes the video's.
                    if (constraints.hasBoundedHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(ratio)
                } else {
                    Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = maxWidth / maxHeight > ratio)
                }
            AvPlayerSurface(
                player = player.avPlayer,
                contentScale = contentScale,
                modifier = surfaceModifier,
                onView = { view -> if (view != null) PictureInPicture.register(player, view) },
            )
        }
    }

    /** The player's picture size, following it as it changes (an HLS rendition switch, a rotation). */
    @Composable
    private fun rememberVideoSize(player: AvAmethystPlayer): State<VideoSize> {
        val state = remember(player) { mutableStateOf(player.videoSize) }
        DisposableEffect(player) {
            val listener =
                object : AmethystPlayer.Listener {
                    override fun onVideoSizeChanged(size: VideoSize) {
                        state.value = size
                    }
                }
            player.addListener(listener)
            state.value = player.videoSize
            onDispose { player.removeListener(listener) }
        }
        return state
    }

    // Keep the screen on

    private var screenHolders = 0

    @Composable
    override fun KeepScreenOnWhilePlaying(player: AmethystPlayer) {
        DisposableEffect(player) {
            var holding = false

            fun hold(on: Boolean) {
                if (on == holding) return
                holding = on
                screenHolders += if (on) 1 else -1
                UIApplication.sharedApplication.idleTimerDisabled = screenHolders > 0
            }
            val listener =
                object : AmethystPlayer.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) = hold(isPlaying)
                }
            player.addListener(listener)
            hold(player.isPlaying)
            onDispose {
                player.removeListener(listener)
                hold(false)
            }
        }
    }

    // Picture in picture

    override val canPictureInPicture: Boolean get() = PictureInPicture.isSupported

    @Composable
    override fun rememberEnterPictureInPicture(): (MediaItemData, IntRect?) -> Unit =
        remember {
            { item, _ ->
                val player = IosPlayerPool.find(decrypted.playingUrl(item.videoUri))
                if (player != null && PictureInPicture.start(player)) {
                    states[player]?.let { BackgroundMedia.bgInstance = it }
                }
            }
        }

    // AirPlay

    override val canPlayExternally: Boolean get() = true

    @Composable
    override fun rememberExternalPlayback(
        mediaData: MediaItemData,
        bestVideoTrack: VideoTrack?,
    ): ExternalPlayback {
        val label = stringRes(Res.string.play_on_another_device)
        return remember(mediaData.videoUri, label) { AirPlay(mediaData.videoUri, label) }
    }

    private class AirPlay(
        private val videoUri: String,
        override val contentDescription: String,
    ) : ExternalPlayback {
        private var pickerOpen by mutableStateOf(false)

        // AirPlay moves the AVPlayer itself to the TV, so the local player keeps "playing".
        override val isActive: Boolean get() = IosPlayerPool.find(videoUri)?.avPlayer?.externalPlaybackActive == true

        override val symbol: MaterialSymbol get() = MaterialSymbols.Cast

        override fun onClick() {
            pickerOpen = true
        }

        override val hasNativeButton: Boolean get() = true

        @Composable
        override fun NativeButton(
            tint: Color,
            modifier: Modifier,
        ) = AirPlayButton(tint, modifier)

        @Composable
        override fun Content() {
            if (!pickerOpen) return
            AlertDialog(
                onDismissRequest = { pickerOpen = false },
                title = { Text(contentDescription) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringRes(Res.string.play_on_another_device_description))
                        AirPlayButton(tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp).padding(4.dp))
                    }
                },
                confirmButton = { TextButton(onClick = { pickerOpen = false }) { Text(stringRes(Res.string.close)) } },
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
    ): () -> Unit {
        val scope = rememberCoroutineScope()
        val saved = stringRes(Res.string.video_saved_to_the_gallery)
        val failed = stringRes(Res.string.failed_to_save_the_video)
        val currentDownload by rememberUpdatedState(download)
        return remember(videoUri) {
            {
                scope.launch {
                    val ok = saveVideoToPhotos(videoUri, mimeType, currentDownload)
                    accountViewModel.toastManager.toast(if (ok) saved else failed, videoUri.substringAfterLast('/'))
                }
            }
        }
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
    ) {
        if (!popupExpanded.value) return
        LaunchedEffect(videoUri) {
            val items = listOfNotNull(NSURL.URLWithString(videoUri), postNostrUri?.let { "https://njump.me/" + it.removePrefix("nostr:") })
            presentModally(UIActivityViewController(activityItems = items, applicationActivities = null))
            popupExpanded.value = false
            onDismiss()
        }
    }

    // Brightness (iOS gives apps no way to set the media volume)

    @Composable
    override fun rememberDeviceLevels(): DeviceLevels = remember { ScreenLevels() }

    private class ScreenLevels : DeviceLevels {
        private var original: Double? = null

        override val maxMediaVolume: Int get() = 0

        override fun mediaVolume(): Int? = null

        override fun setMediaVolume(index: Int) = Unit

        override fun brightness(): Float = UIScreen.mainScreen.brightness.toFloat()

        override val canSetBrightness: Boolean get() = true

        override fun setBrightness(level: Float) {
            if (original == null) original = UIScreen.mainScreen.brightness
            UIScreen.mainScreen.brightness = level.coerceIn(0f, 1f).toDouble()
        }

        override fun releaseBrightness() {
            original?.let { UIScreen.mainScreen.brightness = it }
            original = null
        }
    }

    companion object {
        private const val TAG = "IosPlaybackPlatform"
    }
}

/** The view controller on top, to present a sheet over. */
internal fun topViewController(): UIViewController? {
    @Suppress("DEPRECATION")
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

private fun presentModally(controller: UIViewController) {
    val top = topViewController() ?: return
    // On iPad a sheet must point at something.
    controller.popoverPresentationController?.sourceView = top.view
    top.presentViewController(controller, animated = true, completion = null)
}

/** Downloads [videoUri] and adds it to the photo library. */
@OptIn(ExperimentalForeignApi::class)
private suspend fun saveVideoToPhotos(
    videoUri: String,
    mimeType: String?,
    download: suspend (String) -> ByteArray?,
): Boolean {
    val bytes =
        try {
            download(videoUri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("IosPlaybackPlatform", "Could not download $videoUri", e)
            null
        } ?: return false
    val extension =
        videoUri.substringBefore('?').substringAfterLast('.', "").takeIf { it.length in 2..4 }
            ?: if (mimeType?.contains("quicktime") == true) "mov" else "mp4"
    val path = NSTemporaryDirectory() + NSUUID().UUIDString + "." + extension
    if (!bytes.toNSData().writeToFile(path, atomically = true)) return false
    val url = NSURL.fileURLWithPath(path)
    val ok =
        suspendCancellableCoroutine { cont ->
            PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                { PHAssetChangeRequest.creationRequestForAssetFromVideoAtFileURL(url) },
                completionHandler = { success, error ->
                    if (!success) Log.w("IosPlaybackPlatform") { "Photos refused $videoUri: ${error?.localizedDescription}" }
                    cont.resume(success)
                },
            )
        }
    NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    return ok
}
