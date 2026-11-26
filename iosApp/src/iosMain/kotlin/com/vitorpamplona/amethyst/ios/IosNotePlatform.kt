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
package com.vitorpamplona.amethyst.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.audio.VoiceRecorder
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.commons.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.commons.ui.components.ImageViewerDialog
import com.vitorpamplona.amethyst.commons.ui.components.UrlCachedPreviewer
import com.vitorpamplona.amethyst.commons.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.components.ZoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.components.rememberZoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.components.urlPreview
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.platform.EpisodeControl
import com.vitorpamplona.amethyst.commons.ui.note.platform.EpisodePlayhead
import com.vitorpamplona.amethyst.commons.ui.note.platform.NotePlatform
import com.vitorpamplona.amethyst.commons.ui.note.types.EditState
import com.vitorpamplona.amethyst.commons.ui.playback.audio.RenderAudioWaveformPlayer
import com.vitorpamplona.amethyst.commons.ui.playback.composable.VideoViewInner
import com.vitorpamplona.amethyst.commons.ui.playback.composable.VideoViewerDialog
import com.vitorpamplona.amethyst.commons.ui.playback.composable.ZoomableVideoContent
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.ios.playback.IosVoiceRecorder
import com.vitorpamplona.amethyst.ios.playback.cache.IosVideoCache
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.podcasts.PodcastAudio
import kotlinx.collections.immutable.ImmutableList
import com.vitorpamplona.amethyst.commons.ui.components.LoadUrlPreview as SharedLoadUrlPreview
import com.vitorpamplona.amethyst.commons.ui.components.rememberUrlPreviewState as sharedRememberUrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGitIssueEvent as SharedRenderGitIssueEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGitPatchEvent as SharedRenderGitPatchEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGitPullRequestEvent as SharedRenderGitPullRequestEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGitPullRequestUpdateEvent as SharedRenderGitPullRequestUpdateEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGitRepositoryEvent as SharedRenderGitRepositoryEvent
import com.vitorpamplona.amethyst.commons.ui.playback.audio.EpisodeAudioPlayer as SharedEpisodeAudioPlayer
import com.vitorpamplona.amethyst.commons.ui.playback.audio.MusicTrackAudioPlayer as SharedMusicTrackAudioPlayer
import com.vitorpamplona.amethyst.commons.ui.playback.audio.NoteMediaView as SharedNoteMediaView
import com.vitorpamplona.amethyst.commons.ui.playback.audio.PodcastEpisodeAudioPlayer as SharedPodcastEpisodeAudioPlayer
import com.vitorpamplona.amethyst.commons.ui.playback.audio.RenderAudioWithWaveform as SharedRenderAudioWithWaveform
import com.vitorpamplona.amethyst.commons.ui.playback.composable.VideoView as SharedVideoView

/**
 * iOS's note media: images draw inline through Coil (GIFs and animated WebPs through the shared
 * Skia player, [IosGifVideoView]) and open the shared [ImageViewerDialog]; videos
 * and audio run the shared player UI (`commons.ui.playback`) on AVPlayer through
 * [IosPlaybackPlatform], and voice messages record through [IosVoiceRecorder]. PDFs and link
 * previews are still links the user can open (B7).
 */
object IosNotePlatform : NotePlatform {
    override val maxInlineMediaHeight: Dp get() = MAX_INLINE_MEDIA_HEIGHT

    // Voice messages: the shared recording UI over AVAudioRecorder.

    override val canRecordAudio: Boolean get() = true

    @Composable
    override fun rememberVoiceRecorder(): VoiceRecorder = remember { IosVoiceRecorder() }

    /**
     * As on Android: a video card's player opens its video as soon as the card is composed, so the
     * videos further down get their first bytes cached ahead ([IosVideoCache]), unless videos load
     * only on a tap.
     */
    override fun prefetchVideos(
        owner: Any,
        urls: List<String>,
        accountViewModel: AccountViewModel,
    ) {
        IosVideoCache.prefetch(owner, if (accountViewModel.settings.startVideoPlayback()) urls else emptyList())
    }

    @Composable
    override fun ZoomableContentView(
        content: BaseMediaContent,
        images: ImmutableList<BaseMediaContent>,
        roundedCorner: Boolean,
        contentScale: ContentScale,
        accountViewModel: AccountViewModel,
    ) {
        val url = (content as? MediaUrlContent)?.url ?: return
        if (content is MediaUrlImage) {
            var showViewer by remember { mutableStateOf(false) }
            // Keyed by the image, so a viewer paged to a sibling can close into that one's thumbnail.
            val source = rememberZoomSourceAnchor(content)
            val cornerRadius = if (roundedCorner) 12.dp else 0.dp
            // Coil here decodes a GIF's first frame only: animated images play through the GIF player.
            IosInlineImage(content, contentScale, roundedCorner, cornerRadius, MAX_INLINE_MEDIA_HEIGHT, source, { showViewer = true }, accountViewModel)
            if (showViewer) {
                ImageViewerDialog(content, images, source, cornerRadius) { showViewer = false }
            }
        } else if (content is MediaUrlVideo) {
            var showViewer by remember { mutableStateOf(false) }
            ZoomableVideoContent(
                content = content,
                roundedCorner = roundedCorner,
                contentScale = contentScale,
                boundsTrackingModifier = Modifier,
                onDialog = { showViewer = true },
                accountViewModel = accountViewModel,
            )
            if (showViewer) {
                VideoViewerDialog(content, accountViewModel) { showViewer = false }
            }
        } else {
            ClickableUrl(url)
        }
    }

    @Composable
    override fun GifVideoView(
        videoUri: String,
        contentDescription: String?,
        dimensions: DimensionTag?,
        blurhash: String?,
        roundedCorner: Boolean,
        contentScale: ContentScale,
        onDialog: (() -> Unit)?,
        accountViewModel: AccountViewModel,
        thumbhash: String?,
    ) = IosGifVideoView(videoUri, contentDescription, dimensions, blurhash, roundedCorner, contentScale, onDialog, accountViewModel, thumbhash)

    @Composable
    override fun AnimatedImage(
        url: String,
        contentDescription: String?,
        contentScale: ContentScale,
        modifier: Modifier,
        onError: () -> Unit,
        local: Boolean,
    ) = IosAnimatedImage(url, contentDescription, contentScale, modifier, onError, local)

    @Composable
    override fun VideoView(
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
    ) = SharedVideoView(
        videoUri = videoUri,
        mimeType = mimeType,
        title = title,
        artworkUri = artworkUri,
        authorName = authorName,
        roundedCorner = roundedCorner,
        contentScale = contentScale,
        nostrUriCallback = nostrUriCallback,
        isLiveStream = isLiveStream,
        accountViewModel = accountViewModel,
    )

    @Composable
    override fun FullscreenVideoView(
        videoUri: String,
        mimeType: String?,
        contentScale: ContentScale,
        modifier: Modifier,
        controllerVisible: MutableState<Boolean>,
        accountViewModel: AccountViewModel,
    ) = VideoViewInner(
        videoUri = videoUri,
        mimeType = mimeType,
        contentScale = contentScale,
        borderModifier = modifier,
        automaticallyStartPlayback = true,
        controllerVisible = controllerVisible,
        isFullscreen = true,
        accountViewModel = accountViewModel,
    )

    @Composable
    override fun NoteMediaView(
        mediaUri: String,
        isAudio: Boolean?,
        title: String?,
        coverUri: String?,
        waveform: WaveformData?,
        contentScale: ContentScale,
        note: Note,
        accountViewModel: AccountViewModel,
    ) = SharedNoteMediaView(
        mediaUri = mediaUri,
        title = title,
        coverUri = coverUri,
        waveform = waveform,
        contentScale = contentScale,
        note = note,
        accountViewModel = accountViewModel,
    )

    @Composable
    override fun MusicTrackAudioPlayer(
        audioUrl: String,
        mimeType: String?,
        title: String?,
        note: Note,
        borderModifier: Modifier,
        accountViewModel: AccountViewModel,
    ) = SharedMusicTrackAudioPlayer(audioUrl, mimeType, title, note, borderModifier, accountViewModel)

    @Composable
    override fun PodcastEpisodeAudioPlayer(
        audio: PodcastAudio,
        note: Note,
        title: String?,
        image: String?,
        borderModifier: Modifier,
        accountViewModel: AccountViewModel,
    ) = SharedPodcastEpisodeAudioPlayer(audio, note, title, image, borderModifier, accountViewModel)

    @Composable
    override fun EpisodeAudioPlayer(
        audioUrl: String,
        mimeType: String?,
        title: String?,
        image: String?,
        note: Note,
        startAtSeconds: Long?,
        speed: Float,
        control: EpisodeControl,
        onPlayhead: (EpisodePlayhead) -> Unit,
        accountViewModel: AccountViewModel,
    ) = SharedEpisodeAudioPlayer(audioUrl, mimeType, title, image, note, startAtSeconds, speed, control, onPlayhead, accountViewModel)

    @Composable
    override fun RenderAudioPlayer(
        mediaUrl: String,
        title: String?,
        mimeType: String?,
        waveform: WaveformData?,
        accountViewModel: AccountViewModel,
    ) = RenderAudioWaveformPlayer(
        mediaUrl = mediaUrl,
        title = title,
        mimeType = mimeType,
        waveform = waveform,
        authorName = null,
        callbackUri = null,
        accountViewModel = accountViewModel,
    )

    @Composable
    override fun RenderAudioWithWaveform(
        mediaUrl: String,
        title: String?,
        mimeType: String?,
        waveform: WaveformData?,
        note: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = SharedRenderAudioWithWaveform(
        mediaUrl = mediaUrl,
        title = title,
        mimeType = mimeType,
        waveform = waveform,
        note = note,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun ZoomableImageDialog(
        imageUrl: BaseMediaContent,
        allImages: ImmutableList<BaseMediaContent>,
        source: ZoomSourceAnchor?,
        onDismiss: () -> Unit,
        accountViewModel: AccountViewModel,
    ) = ImageViewerDialog(imageUrl, allImages, source, 0.dp, onDismiss)

    // Link previews: the shared OpenGraph card, cache and fetch, on the app's Ktor client.

    @Composable
    override fun UrlPreview(
        url: String,
        urlText: String,
        callbackUri: String?,
        accountViewModel: AccountViewModel,
        nav: INav?,
    ) = SharedLoadUrlPreview(url, urlText, callbackUri, accountViewModel, nav)

    override fun loadUrlPreview(
        url: String,
        accountViewModel: AccountViewModel,
        onResult: suspend (UrlPreviewState) -> Unit,
    ) = accountViewModel.urlPreview(url, onResult)

    @Composable
    override fun rememberUrlPreviewState(
        url: String,
        accountViewModel: AccountViewModel,
    ): UrlPreviewState = sharedRememberUrlPreviewState(url, accountViewModel)

    override fun warmUrlPreview(
        url: String,
        accountViewModel: AccountViewModel,
    ) {
        if (UrlCachedPreviewer.cache[url] == null) accountViewModel.urlPreview(url) {}
    }

    // Git: the shared NIP-34 cards with GitCardSlots.Portable, since there is no git client here to
    // fetch a repository's snapshot, a pull request's diff or merge it.

    @Composable
    override fun RenderGitRepositoryEvent(
        baseNote: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = SharedRenderGitRepositoryEvent(baseNote, accountViewModel, nav)

    @Composable
    override fun RenderGitPatchEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        editState: State<GenericLoadable<EditState>>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = SharedRenderGitPatchEvent(baseNote, makeItShort, canPreview, quotesLeft, backgroundColor, editState, accountViewModel, nav)

    @Composable
    override fun RenderGitIssueEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        editState: State<GenericLoadable<EditState>>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = SharedRenderGitIssueEvent(baseNote, makeItShort, canPreview, quotesLeft, backgroundColor, editState, accountViewModel, nav)

    @Composable
    override fun RenderGitPullRequestEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        editState: State<GenericLoadable<EditState>>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = SharedRenderGitPullRequestEvent(baseNote, makeItShort, canPreview, quotesLeft, backgroundColor, editState, accountViewModel, nav)

    @Composable
    override fun RenderGitPullRequestUpdateEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = SharedRenderGitPullRequestUpdateEvent(baseNote, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)

    private val MAX_INLINE_MEDIA_HEIGHT = 600.dp
}
