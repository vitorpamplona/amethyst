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
package com.vitorpamplona.amethyst.ui.note.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.vitorpamplona.amethyst.commons.audio.WaveformData
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.commons.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.platform.NotePlatform
import com.vitorpamplona.amethyst.commons.ui.note.types.EditState
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.model.UrlCachedPreviewer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.urlPreview
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.podcasts.PodcastAudio
import kotlinx.collections.immutable.ImmutableList
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderMeetingRoomEvent as AppRenderMeetingRoomEvent
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderMeetingRoomPresence as AppRenderMeetingRoomPresence
import com.vitorpamplona.amethyst.commons.ui.note.types.RenderMeetingSpaceEvent as AppRenderMeetingSpaceEvent
import com.vitorpamplona.amethyst.service.playback.composable.VideoView as AppVideoView
import com.vitorpamplona.amethyst.ui.actions.EditPostView as AppEditPostView
import com.vitorpamplona.amethyst.ui.components.GifVideoView as AppGifVideoView
import com.vitorpamplona.amethyst.ui.components.LoadUrlPreview as AppLoadUrlPreview
import com.vitorpamplona.amethyst.ui.components.ZoomableContentView as AppZoomableContentView
import com.vitorpamplona.amethyst.ui.components.ZoomableImageDialog as AppZoomableImageDialog
import com.vitorpamplona.amethyst.ui.components.rememberUrlPreviewState as AppRememberUrlPreviewState
import com.vitorpamplona.amethyst.ui.note.LikeReaction as AppLikeReaction
import com.vitorpamplona.amethyst.ui.note.ReactionsRow as AppReactionsRow
import com.vitorpamplona.amethyst.ui.note.ZapReaction as AppZapReaction
import com.vitorpamplona.amethyst.ui.note.creators.location.LoadCityName as AppLoadCityName
import com.vitorpamplona.amethyst.ui.note.creators.location.LocationPreviewMap as AppLocationPreviewMap
import com.vitorpamplona.amethyst.ui.note.types.PodcastEpisodeAudioPlayer as AppPodcastEpisodeAudioPlayer
import com.vitorpamplona.amethyst.ui.note.types.RenderAudioFromIMeta as AppRenderAudioFromIMeta
import com.vitorpamplona.amethyst.ui.note.types.RenderAudioHeader as AppRenderAudioHeader
import com.vitorpamplona.amethyst.ui.note.types.RenderAudioTrack as AppRenderAudioTrack
import com.vitorpamplona.amethyst.ui.note.types.RenderAudioWithWaveform as AppRenderAudioWithWaveform
import com.vitorpamplona.amethyst.ui.note.types.RenderChessGame as AppRenderChessGame
import com.vitorpamplona.amethyst.ui.note.types.RenderGitIssueEvent as AppRenderGitIssueEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderGitPatchEvent as AppRenderGitPatchEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderGitPullRequestEvent as AppRenderGitPullRequestEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderGitPullRequestUpdateEvent as AppRenderGitPullRequestUpdateEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderGitRepositoryEvent as AppRenderGitRepositoryEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderLiveChessChallenge as AppRenderLiveChessChallenge
import com.vitorpamplona.amethyst.ui.note.types.RenderLiveChessGameEnd as AppRenderLiveChessGameEnd
import com.vitorpamplona.amethyst.ui.note.types.RenderMusicTrack as AppRenderMusicTrack
import com.vitorpamplona.amethyst.ui.note.types.RenderNamedNappletEvent as AppRenderNamedNappletEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderNamedSiteEvent as AppRenderNamedSiteEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderRootNappletEvent as AppRenderRootNappletEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderRootSiteEvent as AppRenderRootSiteEvent
import com.vitorpamplona.amethyst.ui.note.types.RenderVoiceTrack as AppRenderVoiceTrack

/** Android's [NotePlatform]: the app's own media, map, audio, chess, git, napplet and zap composables. */
object AndroidNotePlatform : NotePlatform {
    override fun warmUrlPreview(
        url: String,
        accountViewModel: AccountViewModel,
    ) {
        if (UrlCachedPreviewer.cache.get(url) == null) accountViewModel.urlPreview(url) {}
    }

    @Composable
    override fun rememberUrlPreviewState(
        url: String,
        accountViewModel: AccountViewModel,
    ): UrlPreviewState = AppRememberUrlPreviewState(url, accountViewModel)

    @Composable
    override fun RenderAudioHeader(
        note: Note,
        contentScale: ContentScale,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderAudioHeader(note, contentScale, accountViewModel, nav)

    @Composable
    override fun ZoomableContentView(
        content: BaseMediaContent,
        images: ImmutableList<BaseMediaContent>,
        roundedCorner: Boolean,
        contentScale: ContentScale,
        accountViewModel: AccountViewModel,
    ) = AppZoomableContentView(
        content = content,
        images = images,
        roundedCorner = roundedCorner,
        contentScale = contentScale,
        accountViewModel = accountViewModel,
    )

    @Composable
    override fun ZoomableImageDialog(
        imageUrl: BaseMediaContent,
        allImages: ImmutableList<BaseMediaContent>,
        sourceBounds: Rect?,
        onDismiss: () -> Unit,
        accountViewModel: AccountViewModel,
    ) = AppZoomableImageDialog(
        imageUrl = imageUrl,
        allImages = allImages,
        sourceBounds = sourceBounds,
        onDismiss = onDismiss,
        accountViewModel = accountViewModel,
    )

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
    ) = AppGifVideoView(
        videoUri = videoUri,
        contentDescription = contentDescription,
        dimensions = dimensions,
        blurhash = blurhash,
        roundedCorner = roundedCorner,
        contentScale = contentScale,
        onDialog = onDialog,
        accountViewModel = accountViewModel,
        thumbhash = thumbhash,
    )

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
    ) = AppVideoView(
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
    override fun UrlPreview(
        url: String,
        urlText: String,
        callbackUri: String?,
        accountViewModel: AccountViewModel,
        nav: INav?,
    ) = AppLoadUrlPreview(
        url = url,
        urlText = urlText,
        callbackUri = callbackUri,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun LocationPreviewMap(
        latitude: Double,
        longitude: Double,
        aspectRatio: Float,
        pinColor: Color?,
        pinEmoji: String?,
        pinAlpha: Float,
    ) = AppLocationPreviewMap(
        latitude = latitude,
        longitude = longitude,
        aspectRatio = aspectRatio,
        pinColor = pinColor,
        pinEmoji = pinEmoji,
        pinAlpha = pinAlpha,
    )

    @Composable
    override fun LoadCityName(
        geohashStr: String,
        onLoading: (@Composable () -> Unit)?,
        content: @Composable (String) -> Unit,
    ) = AppLoadCityName(
        geohashStr = geohashStr,
        onLoading = onLoading,
        content = content,
    )

    @Composable
    override fun RenderAudioTrack(
        note: Note,
        contentScale: ContentScale,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderAudioTrack(
        note = note,
        contentScale = contentScale,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderMusicTrack(
        note: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderMusicTrack(
        note = note,
        makeItShort = makeItShort,
        canPreview = canPreview,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun PodcastEpisodeAudioPlayer(
        audio: PodcastAudio,
        note: Note,
        title: String?,
        image: String?,
        borderModifier: Modifier,
        accountViewModel: AccountViewModel,
    ) = AppPodcastEpisodeAudioPlayer(
        audio = audio,
        note = note,
        title = title,
        image = image,
        borderModifier = borderModifier,
        accountViewModel = accountViewModel,
    )

    @Composable
    override fun RenderVoiceTrack(
        note: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderVoiceTrack(
        note = note,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderAudioFromIMeta(
        note: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderAudioFromIMeta(
        note = note,
        accountViewModel = accountViewModel,
        nav = nav,
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
    ) = AppRenderAudioWithWaveform(
        mediaUrl = mediaUrl,
        title = title,
        mimeType = mimeType,
        waveform = waveform,
        note = note,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderChessGame(
        note: Note,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderChessGame(
        note = note,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderLiveChessChallenge(
        note: Note,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderLiveChessChallenge(
        note = note,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderLiveChessGameEnd(
        note: Note,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderLiveChessGameEnd(
        note = note,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderGitRepositoryEvent(
        baseNote: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderGitRepositoryEvent(
        baseNote = baseNote,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderGitPatchEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderGitPatchEvent(
        baseNote = baseNote,
        makeItShort = makeItShort,
        canPreview = canPreview,
        quotesLeft = quotesLeft,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderGitIssueEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderGitIssueEvent(
        baseNote = baseNote,
        makeItShort = makeItShort,
        canPreview = canPreview,
        quotesLeft = quotesLeft,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderGitPullRequestEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderGitPullRequestEvent(
        baseNote = baseNote,
        makeItShort = makeItShort,
        canPreview = canPreview,
        quotesLeft = quotesLeft,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderGitPullRequestUpdateEvent(
        baseNote: Note,
        makeItShort: Boolean,
        canPreview: Boolean,
        quotesLeft: Int,
        backgroundColor: MutableState<Color>,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderGitPullRequestUpdateEvent(
        baseNote = baseNote,
        makeItShort = makeItShort,
        canPreview = canPreview,
        quotesLeft = quotesLeft,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderMeetingSpaceEvent(
        baseNote: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderMeetingSpaceEvent(
        baseNote = baseNote,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderMeetingRoomEvent(
        baseNote: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderMeetingRoomEvent(
        baseNote = baseNote,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderMeetingRoomPresence(
        baseNote: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderMeetingRoomPresence(
        baseNote = baseNote,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderRootNappletEvent(
        baseNote: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderRootNappletEvent(
        baseNote = baseNote,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderNamedNappletEvent(
        baseNote: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderNamedNappletEvent(
        baseNote = baseNote,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderRootSiteEvent(
        baseNote: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderRootSiteEvent(
        baseNote = baseNote,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun RenderNamedSiteEvent(
        baseNote: Note,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppRenderNamedSiteEvent(
        baseNote = baseNote,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun ReactionsRow(
        baseNote: Note,
        showReactionDetail: Boolean,
        addPadding: Boolean,
        editState: State<GenericLoadable<EditState>>?,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppReactionsRow(
        baseNote = baseNote,
        showReactionDetail = showReactionDetail,
        addPadding = addPadding,
        editState = editState,
        accountViewModel = accountViewModel,
        nav = nav,
    )

    @Composable
    override fun LikeReaction(
        baseNote: Note,
        grayTint: Color,
        accountViewModel: AccountViewModel,
        nav: INav,
        iconSize: Dp,
        heartSizeModifier: Modifier,
        iconFontSize: TextUnit,
        showCounter: Boolean,
    ) = AppLikeReaction(
        baseNote = baseNote,
        grayTint = grayTint,
        accountViewModel = accountViewModel,
        nav = nav,
        iconSize = iconSize,
        heartSizeModifier = heartSizeModifier,
        iconFontSize = iconFontSize,
        showCounter = showCounter,
    )

    @Composable
    override fun ZapReaction(
        baseNote: Note,
        grayTint: Color,
        accountViewModel: AccountViewModel,
        iconSize: Dp,
        iconSizeModifier: Modifier,
        animationModifier: Modifier,
        showCounter: Boolean,
        nav: INav,
    ) = AppZapReaction(
        baseNote = baseNote,
        grayTint = grayTint,
        accountViewModel = accountViewModel,
        iconSize = iconSize,
        iconSizeModifier = iconSizeModifier,
        animationModifier = animationModifier,
        showCounter = showCounter,
        nav = nav,
    )

    @Composable
    override fun EditPostView(
        onClose: () -> Unit,
        edit: Note,
        versionLookingAt: Note?,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) = AppEditPostView(
        onClose = onClose,
        edit = edit,
        versionLookingAt = versionLookingAt,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}
