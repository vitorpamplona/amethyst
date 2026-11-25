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
package com.vitorpamplona.amethyst.commons.ui.playback.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntRect
import com.vitorpamplona.amethyst.commons.audio.Spectrum
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.ui.playback.composable.MediaControllerState
import com.vitorpamplona.amethyst.commons.ui.playback.mediaitem.MediaItemData
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The platform halves of the shared video and audio player UI: what only the front end can do.
 * Getting a player for an item, drawing its picture, holding the screen on, picture-in-picture,
 * playing on another device (Cast, AirPlay), saving and sharing the file, the fullscreen
 * brightness/volume swipe, and the audio visualizer's spectrum.
 *
 * The shared player calls these through [LocalPlaybackPlatform]; a front end installs its own at
 * the composition root next to its NotePlatform. Every member degrades gracefully: with [None] no
 * player is ever handed out, so nothing plays, and every platform button stays hidden.
 */
@Stable
interface PlaybackPlatform {
    // The player

    /**
     * Hands [inner] a player for [mediaItem] once one is ready, and keeps it while this is
     * composed. [muted] is the volume it starts at, [play] starts it at once. When
     * [releaseOnBackgroundTimeout], a player left in the background for a while is released and
     * gotten again when the app comes back (see `ReleaseControllerWhenBackgroundedFor`); callers
     * that are themselves the background surface (picture-in-picture) opt out.
     */
    @Composable
    fun GetVideoController(
        mediaItem: MediaItemData,
        muted: Boolean,
        play: Boolean,
        releaseOnBackgroundTimeout: Boolean,
        inner: @Composable (MediaControllerState) -> Unit,
    ) {}

    /**
     * The local proxy port the player should fetch [videoUri] through (Tor), or null to fetch it
     * directly. Decided once per video and carried in its [MediaItemData].
     */
    fun proxyPortForVideo(
        videoUri: String,
        accountViewModel: AccountViewModel,
    ): Int? = null

    /** Draws the picture of [controllerState]'s player into [modifier], in the way [style] asks. */
    @Composable
    fun VideoSurface(
        controllerState: MediaControllerState,
        style: VideoSurfaceStyle,
        contentScale: ContentScale,
        modifier: Modifier,
    ) {}

    /** Keeps the display on while [player] plays, and lets it sleep again when it stops or this leaves. */
    @Composable
    fun KeepScreenOnWhilePlaying(player: AmethystPlayer) {}

    /**
     * Where the visible part of the app's window is, in the window coordinates `boundsInWindow`
     * uses. The video nearest its centre is the one that plays. Read once per layout pass of every
     * video, so a platform should make it cheap. The default is the whole window.
     */
    @Composable
    fun rememberVisibleRootBounds(): VisibleRootBounds {
        val size = LocalWindowInfo.current.containerSize
        return remember(size) { VisibleRootBounds { if (size.width > 0 && size.height > 0) IntRect(0, 0, size.width, size.height) else null } }
    }

    // Picture in picture

    /** The device can float a video in a picture-in-picture window. */
    val canPictureInPicture: Boolean get() = false

    /**
     * The user lets this app use picture-in-picture (Android's per-app switch). Read when a button
     * is composed, so a change applies to the next one.
     */
    val isPictureInPictureAllowed: Boolean get() = canPictureInPicture

    /**
     * What moves [MediaItemData] into a picture-in-picture window, growing out of the on-screen
     * bounds it gets (null when unknown). Only called where [canPictureInPicture].
     */
    @Composable
    fun rememberEnterPictureInPicture(): (MediaItemData, IntRect?) -> Unit = remember { { _, _ -> } }

    // Playing on another device

    /** The app can send a video to another device: Cast on Android, AirPlay on iOS. */
    val canPlayExternally: Boolean get() = false

    /** Whether [videoUri] is playing on another device now; the local player is then held paused. */
    @Composable
    fun isPlayingExternally(videoUri: String): Boolean = false

    /**
     * The button that sends [mediaData] to another device or stops it there. [bestVideoTrack] is
     * the highest rendition the local player found, the only description of the media there is
     * when a receiver refuses it without saying why. Null where [canPlayExternally] is false.
     */
    @Composable
    fun rememberExternalPlayback(
        mediaData: MediaItemData,
        bestVideoTrack: VideoTrack?,
    ): ExternalPlayback? = null

    // Saving and sharing

    /** The file can be saved to the device's gallery or downloads. */
    val canSaveMedia: Boolean get() = false

    /** What saves [videoUri] (asking for any permission it needs and telling the user it started). */
    @Composable
    fun rememberSaveMediaAction(
        videoUri: String,
        mimeType: String?,
        accountViewModel: AccountViewModel,
    ): () -> Unit = remember { {} }

    /** The share sheet can be opened for a playing file. */
    val canShareMedia: Boolean get() = false

    /**
     * The share dialog for a playing file, shown while [popupExpanded]: the link, the post, the
     * file itself, the profile gallery. [blurhash], [dim] and [hash] enrich a gallery entry.
     */
    @Composable
    fun ShareMediaAction(
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
    ) {}

    // Device levels

    /**
     * The device's media volume and the screen brightness of the window this is composed in, for
     * the fullscreen swipe and for telling whether anything is audible. Null where there are none.
     */
    @Composable
    fun rememberDeviceLevels(): DeviceLevels? = null

    // Audio visualizer

    /**
     * The spectrum of what the player of [mediaId] is playing now, delayed to match what the
     * speaker is playing. Empty where the platform can't tap the sound.
     */
    @Composable
    fun rememberAudioSpectrum(mediaId: String): Flow<Spectrum> = remember { emptyFlow() }

    /** Nothing plays and no platform button shows: previews, and front ends without a player yet. */
    object None : PlaybackPlatform
}

/** How [PlaybackPlatform.VideoSurface] draws the picture. */
enum class VideoSurfaceStyle {
    /** A card in a feed or list: clipped and scaled with the layout, no black flash before the first frame. */
    INLINE,

    /** The fullscreen viewer: the platform's fastest surface, HDR where the video and display have it. */
    FULLSCREEN,

    /**
     * The strip behind a voice or audio player: keeps its last frame when the player is reset, and
     * draws the default backdrop until the first frame, as Android's voice strip always did.
     */
    AUDIO_STRIP,
}

/** The visible part of the app's window, or null when none of it is visible. */
fun interface VisibleRootBounds {
    fun current(): IntRect?
}

/**
 * A "play on another device" button as the platform draws it. [Content] holds whatever it opens
 * (a device picker), and must be composed while the button is.
 */
@Stable
interface ExternalPlayback {
    /** This video is the one playing on the other device. */
    val isActive: Boolean

    val symbol: MaterialSymbol

    val contentDescription: String

    fun onClick()

    @Composable
    fun Content()
}

/**
 * The device's media volume and the window's brightness, for the fullscreen swipe. Volume is in
 * the device's own steps, 0 to [maxMediaVolume]; brightness is 0f to 1f.
 */
interface DeviceLevels {
    /** The highest media volume step, or 0 when the volume can't be read. */
    val maxMediaVolume: Int

    /** The media volume now, or null when it can't be read. */
    fun mediaVolume(): Int?

    fun setMediaVolume(index: Int)

    /** The brightness this window draws at: its own override when it has one, else the system's. */
    fun brightness(): Float

    /** Whether [setBrightness] can change this window's brightness (it is a window of its own). */
    val canSetBrightness: Boolean

    fun setBrightness(level: Float)

    /** Drops any brightness override this applied, giving the window back the system brightness. */
    fun releaseBrightness()
}

val LocalPlaybackPlatform = staticCompositionLocalOf<PlaybackPlatform> { PlaybackPlatform.None }
