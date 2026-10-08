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
package com.vitorpamplona.amethyst.commons.ui.note.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.vitorpamplona.amethyst.commons.audio.RecordingResult
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.nipA0VoiceMessages.AudioMeta
import com.vitorpamplona.quartz.podcasts.PodcastAudio
import kotlinx.collections.immutable.ImmutableList
import okio.Path

/**
 * The platform halves of the shared note renderer: what a note card needs that only the front end
 * can draw or do. Media players and the zoomable viewer, maps and reverse geocoding, the note types
 * built on a platform engine (audio players, chess, the git browser, meeting rooms, napplets), the
 * reactions/zap row with its wallet hand-offs, and the post editor.
 *
 * The shared note code calls these through [LocalNotePlatform]; a front end installs its own at the
 * composition root (Android does in its theme). Every member defaults to drawing nothing, so a
 * preview, or a front end still wiring a piece, gets an empty slot rather than a crash.
 */
@Stable
interface NotePlatform {
    // Media

    @Composable
    fun ZoomableContentView(
        content: BaseMediaContent,
        images: ImmutableList<BaseMediaContent>,
        roundedCorner: Boolean,
        contentScale: ContentScale,
        accountViewModel: AccountViewModel,
    ) {}

    @Composable
    fun ZoomableImageDialog(
        imageUrl: BaseMediaContent,
        allImages: ImmutableList<BaseMediaContent>,
        sourceBounds: Rect?,
        onDismiss: () -> Unit,
        accountViewModel: AccountViewModel,
    ) {}

    @Composable
    fun GifVideoView(
        videoUri: String,
        contentDescription: String?,
        dimensions: DimensionTag?,
        blurhash: String?,
        roundedCorner: Boolean,
        contentScale: ContentScale,
        onDialog: (() -> Unit)?,
        accountViewModel: AccountViewModel,
        thumbhash: String?,
    ) {}

    @Composable
    fun VideoView(
        videoUri: String,
        mimeType: String?,
        title: String?,
        artworkUri: String?,
        authorName: String?,
        roundedCorner: Boolean,
        contentScale: ContentScale,
        nostrUriCallback: String?,
        isLiveStream: Boolean,
        accountViewModel: AccountViewModel,
    ) {}

    /**
     * A video filling a full-screen viewer, playing at once. [controllerVisible] shows and hides
     * its playback controls, so the viewer can toggle them with its own chrome.
     */
    @Composable
    fun FullscreenVideoView(
        videoUri: String,
        mimeType: String?,
        contentScale: ContentScale,
        modifier: Modifier,
        controllerVisible: MutableState<Boolean>,
        accountViewModel: AccountViewModel,
    ) {}

    /** The link card for [url]: OpenGraph preview, inline media, or a plain link. */
    @Composable
    fun UrlPreview(
        url: String,
        urlText: String,
        callbackUri: String?,
        accountViewModel: AccountViewModel,
        nav: INav?,
    ) {}

    /**
     * Fetches the OpenGraph preview of [url] off the main thread and hands it to [onResult]; for
     * forms that pre-fill from a link rather than render a card. Nothing where the platform has no fetcher.
     */
    fun loadUrlPreview(
        url: String,
        accountViewModel: AccountViewModel,
        onResult: suspend (UrlPreviewState) -> Unit,
    ) {}

    /** The OpenGraph preview of [url], starting from the platform's cache. */
    @Composable
    fun rememberUrlPreviewState(
        url: String,
        accountViewModel: AccountViewModel,
    ): UrlPreviewState = UrlPreviewState.Loading

    /**
     * Starts loading the OpenGraph preview of [url] into the platform's cache ahead of display,
     * unless it is already there. Not composable: feed prefetchers call it from a coroutine.
     */
    fun warmUrlPreview(
        url: String,
        accountViewModel: AccountViewModel,
    ) {}

    // Places

    @Composable
    fun LocationPreviewMap(
        latitude: Double,
        longitude: Double,
        aspectRatio: Float,
        pinColor: Color?,
        pinEmoji: String?,
        pinAlpha: Float,
        modifier: Modifier,
        zoom: Double?,
    ) {}

    /** Resolves [geohashStr] to a place name for [content], showing [onLoading] meanwhile. */
    @Composable
    fun LoadCityName(
        geohashStr: String,
        onLoading: (@Composable () -> Unit)?,
        content: @Composable (String) -> Unit,
    ) {}

    // Audio

    @Composable
    fun RenderAudioHeader(
        note: Note,
        contentScale: ContentScale,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    @Composable
    fun RenderAudioTrack(
        note: Note,
        contentScale: ContentScale,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    @Composable
    fun RenderMusicTrack(
        note: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    @Composable
    fun PodcastEpisodeAudioPlayer(
        audio: PodcastAudio,
        note: Note,
        title: String?,
        image: String?,
        borderModifier: Modifier,
        accountViewModel: AccountViewModel,
    ) {}

    @Composable
    fun RenderVoiceTrack(
        note: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    @Composable
    fun RenderAudioFromIMeta(
        note: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    @Composable
    fun RenderAudioWithWaveform(
        mediaUrl: String,
        title: String?,
        mimeType: String?,
        waveform: WaveformData?,
        note: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    /**
     * A playable audio clip that belongs to no [Note], such as a voice message in an MLS group
     * whose messages never enter the cache: [mediaUrl] plays with [waveform]'s bars when given.
     */
    @Composable
    fun RenderAudioPlayer(
        mediaUrl: String,
        title: String?,
        mimeType: String?,
        waveform: WaveformData?,
        accountViewModel: AccountViewModel,
    ) {}

    // Chess

    @Composable
    fun RenderChessGame(
        note: Note,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    @Composable
    fun RenderLiveChessChallenge(
        note: Note,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    @Composable
    fun RenderLiveChessGameEnd(
        note: Note,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    // Git

    @Composable
    fun RenderGitRepositoryEvent(
        baseNote: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    @Composable
    fun RenderGitPatchEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    @Composable
    fun RenderGitIssueEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    @Composable
    fun RenderGitPullRequestEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    @Composable
    fun RenderGitPullRequestUpdateEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    // Meeting rooms (audio spaces)

    @Composable
    fun RenderMeetingSpaceEvent(
        baseNote: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    @Composable
    fun RenderMeetingRoomEvent(
        baseNote: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    @Composable
    fun RenderMeetingRoomPresence(
        baseNote: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    // Napplets and nsites

    @Composable
    fun RenderRootNappletEvent(
        baseNote: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    @Composable
    fun RenderNamedNappletEvent(
        baseNote: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    @Composable
    fun RenderRootSiteEvent(
        baseNote: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    @Composable
    fun RenderNamedSiteEvent(
        baseNote: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    // Reactions, zaps and editing

    /**
     * The microphone button: records a voice message (up to [maxDurationSeconds]) and hands the
     * finished recording to [onVoiceTaken].
     */
    @Composable
    fun RecordVoiceButton(
        onVoiceTaken: (RecordingResult) -> Unit,
        maxDurationSeconds: Int?,
    ) {}

    /** Whether [RecordAudioBox] can record here; callers offer a text alternative when it cannot. */
    val canRecordAudio: Boolean get() = false

    /**
     * Records a voice message behind a caller-drawn trigger: [content] gets whether it is
     * recording, the elapsed seconds and a stop action, and the finished recording goes to
     * [onRecordTaken]. Platforms without a recorder draw nothing, so no dead button shows.
     */
    @Composable
    fun RecordAudioBox(
        modifier: Modifier,
        onRecordTaken: (RecordingResult) -> Unit,
        maxDurationSeconds: Int?,
        content: @Composable (isRecording: Boolean, elapsedSeconds: Int, onStop: () -> Unit) -> Unit,
    ) {}

    /**
     * Plays back a voice message before it is sent: [localFile] while it is only on the device,
     * else [voiceMetadata]'s URL. [onReRecord] replaces it with a new recording.
     */
    @Composable
    fun VoiceMessagePreview(
        voiceMetadata: AudioMeta,
        localFile: Path?,
        onRemove: () -> Unit,
        onReRecord: ((RecordingResult) -> Unit)?,
        isUploading: Boolean,
        modifier: Modifier,
    ) {}

    /** Draws nothing: previews, and front ends still wiring their pieces. */
    object None : NotePlatform
}

/** The front end's [NotePlatform]. Static: it is set once at the root and never changes. */
val LocalNotePlatform = staticCompositionLocalOf<NotePlatform> { NotePlatform.None }
