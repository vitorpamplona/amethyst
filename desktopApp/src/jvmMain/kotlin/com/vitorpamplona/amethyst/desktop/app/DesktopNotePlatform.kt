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
package com.vitorpamplona.amethyst.desktop.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaLocalImage
import com.vitorpamplona.amethyst.commons.richtext.MediaLocalVideo
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.isAnimatedGifUrl
import com.vitorpamplona.amethyst.commons.ui.components.UrlCachedPreviewer
import com.vitorpamplona.amethyst.commons.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.components.pdf.PdfPreviewCard
import com.vitorpamplona.amethyst.commons.ui.components.pdf.PdfViewerContent
import com.vitorpamplona.amethyst.commons.ui.components.rememberZoomTransition
import com.vitorpamplona.amethyst.commons.ui.components.urlPreview
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.platform.NotePlatform
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.util.extractFilename
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.desktop.service.media.GlobalMediaPlayer
import com.vitorpamplona.amethyst.desktop.ui.media.AnimatedGifImage
import com.vitorpamplona.amethyst.desktop.ui.media.AudioPlayer
import com.vitorpamplona.amethyst.desktop.ui.media.DesktopVideoPlayer
import com.vitorpamplona.amethyst.desktop.ui.media.LightboxOverlay
import com.vitorpamplona.amethyst.desktop.ui.media.SaveMediaAction
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import com.vitorpamplona.amethyst.commons.ui.components.LoadUrlPreview as SharedLoadUrlPreview
import com.vitorpamplona.amethyst.commons.ui.components.rememberUrlPreviewState as sharedRememberUrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGitIssueEvent as SharedRenderGitIssueEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGitPatchEvent as SharedRenderGitPatchEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGitPullRequestEvent as SharedRenderGitPullRequestEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGitPullRequestUpdateEvent as SharedRenderGitPullRequestUpdateEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderGitRepositoryEvent as SharedRenderGitRepositoryEvent

/**
 * The desktop halves of the shared note renderer: images and GIFs through Coil, video and audio
 * through the desktop media player, the lightbox for the full-size viewer, and the shared link
 * previews. Note types that need a platform engine Android has (maps, chess boards, git views)
 * keep [NotePlatform]'s defaults until they are ported.
 */
object DesktopNotePlatform : NotePlatform {
    private val MaxInlineMediaHeight = 600.dp

    private fun BaseMediaContent.mediaUrl(): String? =
        when (this) {
            is MediaUrlContent -> url
            is MediaLocalImage -> localFile?.toString() ?: uri
            is MediaLocalVideo -> localFile?.toString() ?: uri
            else -> null
        }

    /**
     * Shows [url] in the window's OS full-screen overlay, starting it at [position] (0..1) unless
     * it is already the playing video: that one carries on where it is, with no seek to stutter it.
     * The overlay grows out of [sourceBounds], where the video sat in the window.
     */
    private fun playFullscreen(
        url: String,
        position: Float,
        sourceBounds: Rect?,
        cornerRadius: Dp,
    ) {
        val current = GlobalMediaPlayer.videoState.value
        if (current.url != url || current.errorReason != null) GlobalMediaPlayer.playVideo(url, position)
        if (!GlobalMediaPlayer.isFullscreen.value) GlobalMediaPlayer.enterFullscreen(sourceBounds, cornerRadius)
    }

    @Composable
    override fun ZoomableContentView(
        content: BaseMediaContent,
        images: ImmutableList<BaseMediaContent>,
        roundedCorner: Boolean,
        contentScale: ContentScale,
        accountViewModel: AccountViewModel,
    ) {
        var showViewer by remember { mutableStateOf(false) }
        var sourceBounds by remember { mutableStateOf<Rect?>(null) }
        val cornerRadius = if (roundedCorner) 12.dp else 0.dp
        val shape = RoundedCornerShape(cornerRadius)
        val url = content.mediaUrl() ?: return

        when (content) {
            is MediaUrlVideo, is MediaLocalVideo -> {
                DesktopVideoPlayer(
                    url = url,
                    modifier = Modifier.fillMaxWidth().heightIn(max = MaxInlineMediaHeight),
                    shape = shape,
                    onPictureBounds = { sourceBounds = it },
                    isLive = (content as? MediaUrlVideo)?.isLiveStream == true,
                    onFullscreen = { position -> playFullscreen(url, position, sourceBounds, cornerRadius) },
                    autoPlayWhenVisible = accountViewModel.settings.autoPlayVideos(),
                    pauseWhenHidden = true,
                    loadOnDemand = !accountViewModel.settings.startVideoPlayback(),
                )
            }

            is MediaUrlPdf -> {
                PdfPreviewCard(content = content, accountViewModel = accountViewModel, onOpen = { showViewer = true })
                if (showViewer) {
                    DesktopPdfViewer(content, accountViewModel) { showViewer = false }
                }
                return
            }

            else -> {
                // The declared size, else the one this image had last time (or that the feed's
                // prefetch read), so the card keeps its shape while it loads instead of jumping.
                val ratio = content.dim.ratioOrNull() ?: MediaAspectRatioCache.get(url)
                val modifier =
                    Modifier
                        .inlineImageSize(ratio, MaxInlineMediaHeight)
                        // Where the viewer grows the image out of, and shrinks it back into.
                        .onGloballyPositioned { sourceBounds = it.boundsInWindow() }
                        .clip(shape)
                        .clickable { showViewer = true }
                if (isAnimatedGifUrl(url)) {
                    AnimatedGifImage(url = url, contentDescription = content.description, modifier = modifier, contentScale = contentScale)
                } else {
                    AsyncImage(
                        model = url,
                        contentDescription = content.description,
                        modifier = modifier,
                        contentScale = contentScale,
                        onSuccess = { state ->
                            val image = state.result.image
                            MediaAspectRatioCache.add(url, image.width, image.height)
                        },
                    )
                }
            }
        }

        if (showViewer) {
            MediaViewerDialog(content, images, sourceBounds, cornerRadius) { showViewer = false }
        }
    }

    @Composable
    override fun ZoomableImageDialog(
        imageUrl: BaseMediaContent,
        allImages: ImmutableList<BaseMediaContent>,
        sourceBounds: Rect?,
        onDismiss: () -> Unit,
        accountViewModel: AccountViewModel,
    ) = MediaViewerDialog(imageUrl, allImages, sourceBounds, 0.dp, onDismiss)

    /**
     * The full-size viewer over the window. The media grows out of [sourceBounds] (window
     * coordinates, with [sourceCornerRadius]) and shrinks back into it on close, as on the phone.
     * The dialog's own scrim, open animation and insets are off: the viewer fades its backdrop with
     * the transition, and must sit exactly over the window for the bounds to line up.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    private fun MediaViewerDialog(
        imageUrl: BaseMediaContent,
        allImages: ImmutableList<BaseMediaContent>,
        sourceBounds: Rect?,
        sourceCornerRadius: Dp,
        onDismiss: () -> Unit,
    ) {
        val urls = remember(allImages) { allImages.mapNotNull { it.mediaUrl() } }
        val start = remember(urls, imageUrl) { urls.indexOf(imageUrl.mediaUrl()).coerceAtLeast(0) }
        if (urls.isEmpty()) return

        val transition = rememberZoomTransition(sourceBounds, sourceCornerRadius, onDismiss)

        Dialog(
            onDismissRequest = transition::dismiss,
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                    usePlatformInsets = false,
                    scrimColor = Color.Transparent,
                    animateTransition = false,
                ),
        ) {
            Box(Modifier.fillMaxSize()) {
                LightboxOverlay(urls = urls, initialIndex = start, onDismiss = transition::dismiss, transition = transition)
            }
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
    ) {
        val shape = if (roundedCorner) RoundedCornerShape(12.dp) else RoundedCornerShape(0.dp)
        DesktopVideoPlayer(
            url = videoUri,
            modifier = Modifier.fillMaxWidth().heightIn(max = MaxInlineMediaHeight),
            shape = shape,
            onFullscreen = onDialog?.let { open -> { _ -> open() } },
            autoPlayWhenVisible = accountViewModel.settings.autoPlayVideos(),
            pauseWhenHidden = true,
            loadOnDemand = !accountViewModel.settings.startVideoPlayback(),
        )
    }

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
    ) {
        val cornerRadius = if (roundedCorner) 12.dp else 0.dp
        val shape = RoundedCornerShape(cornerRadius)
        var sourceBounds by remember { mutableStateOf<Rect?>(null) }
        DesktopVideoPlayer(
            url = videoUri,
            modifier = Modifier.fillMaxWidth().heightIn(max = MaxInlineMediaHeight),
            shape = shape,
            onPictureBounds = { sourceBounds = it },
            isLive = isLiveStream,
            onFullscreen = { position -> playFullscreen(videoUri, position, sourceBounds, cornerRadius) },
            autoPlayWhenVisible = accountViewModel.settings.autoPlayVideos(),
            pauseWhenHidden = true,
            loadOnDemand = !accountViewModel.settings.startVideoPlayback(),
        )
    }

    @Composable
    override fun FullscreenVideoView(
        videoUri: String,
        mimeType: String?,
        contentScale: ContentScale,
        modifier: Modifier,
        controllerVisible: MutableState<Boolean>,
        accountViewModel: AccountViewModel,
    ) {
        DesktopVideoPlayer(url = videoUri, modifier = modifier, autoPlay = true)
    }

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

    /**
     * The desktop plays every video on one engine, which opens a video only once its card is the
     * one in the middle of the window, so each started downloading only then. These open ahead,
     * paused, unless videos load only on a click.
     */
    override fun warmVideos(
        owner: Any,
        urls: List<String>,
        accountViewModel: AccountViewModel,
    ) {
        GlobalMediaPlayer.warmVideos(owner, if (accountViewModel.settings.startVideoPlayback()) urls else emptyList())
    }

    @Composable
    override fun RenderAudioPlayer(
        mediaUrl: String,
        title: String?,
        mimeType: String?,
        waveform: WaveformData?,
        accountViewModel: AccountViewModel,
    ) = AudioPlayer(url = mediaUrl, modifier = Modifier.fillMaxWidth())

    @Composable
    override fun RenderAudioWithWaveform(
        mediaUrl: String,
        title: String?,
        mimeType: String?,
        waveform: WaveformData?,
        note: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AudioPlayer(url = mediaUrl, modifier = Modifier.fillMaxWidth())

    // Git events: the same renderers Android uses (shared, JVM-only for the repository browser).

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
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = SharedRenderGitPatchEvent(baseNote, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)

    @Composable
    override fun RenderGitIssueEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = SharedRenderGitIssueEvent(baseNote, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)

    @Composable
    override fun RenderGitPullRequestEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = SharedRenderGitPullRequestEvent(baseNote, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)

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
}

/**
 * The shared PDF reader in a full-window dialog, with page buttons for the mouse (it can't drag the
 * pager) and a button that saves the file. Escape closes it; the arrow and page keys turn pages.
 */
@Composable
private fun DesktopPdfViewer(
    content: MediaUrlPdf,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            PdfViewerContent(content = content, accountViewModel = accountViewModel, onDismiss = onDismiss, showPageButtons = true) {
                OutlinedButton(
                    // The view model's scope, not the dialog's: closing the viewer must not cancel a
                    // download the user started.
                    onClick = {
                        accountViewModel.viewModelScope.launch {
                            SaveMediaAction.saveMedia(content.url, extractFilename(content.url))
                        }
                    },
                    contentPadding = PaddingValues(horizontal = Size5dp),
                    colors = ButtonDefaults.outlinedButtonColors().copy(containerColor = MaterialTheme.colorScheme.background),
                ) {
                    Icon(
                        symbol = MaterialSymbols.Download,
                        modifier = Size20Modifier,
                        contentDescription = stringRes(Res.string.save),
                    )
                }
            }
        }
    }
}

/**
 * The size of an image shown in a note: as wide as the column and at most [maxHeight] tall, at the
 * ratio [dim] declares. A photo too tall for the column's width at that height narrows instead,
 * centered: forcing the full width left aspectRatio no size that fit, so it measured past the height
 * cap and the slot centered the overflow over the text above and the reactions below.
 */
internal fun Modifier.inlineImageSize(
    dim: DimensionTag?,
    maxHeight: Dp,
): Modifier = inlineImageSize(dim.ratioOrNull(), maxHeight)

/** [inlineImageSize] for a width-to-height [ratio], or null when the shape is unknown. */
internal fun Modifier.inlineImageSize(
    ratio: Float?,
    maxHeight: Dp,
): Modifier {
    val known = ratio?.takeIf { it > 0f && it.isFinite() }
    return if (known != null) {
        fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .heightIn(max = maxHeight)
            .aspectRatio(known, matchHeightConstraintsFirst = false)
    } else {
        fillMaxWidth().heightIn(max = maxHeight)
    }
}

private fun DimensionTag?.ratioOrNull(): Float? = this?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width.toFloat() / it.height }
