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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
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
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.ui.note.types.EditState
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.podcasts.PodcastAudio
import kotlinx.collections.immutable.ImmutableList

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

    /** The link card for [url]: OpenGraph preview, inline media, or a plain link. */
    @Composable
    fun UrlPreview(
        url: String,
        urlText: String,
        callbackUri: String?,
        accountViewModel: AccountViewModel,
        nav: INav?,
    ) {}

    /** The OpenGraph preview of [url], starting from the platform's cache. */
    @Composable
    fun rememberUrlPreviewState(
        url: String,
        accountViewModel: AccountViewModel,
    ): UrlPreviewState = UrlPreviewState.Loading

    // Places

    @Composable
    fun LocationPreviewMap(
        latitude: Double,
        longitude: Double,
        aspectRatio: Float,
        pinColor: Color?,
        pinEmoji: String?,
        pinAlpha: Float,
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

    @Composable
    fun ReactionsRow(
        baseNote: Note,
        showReactionDetail: Boolean,
        addPadding: Boolean,
        editState: State<GenericLoadable<EditState>>?,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    @Composable
    fun LikeReaction(
        baseNote: Note,
        grayTint: Color,
        accountViewModel: AccountViewModel,
        nav: INav,
        iconSize: Dp,
        heartSizeModifier: Modifier,
        iconFontSize: TextUnit,
        showCounter: Boolean,
    ) {}

    @Composable
    fun ZapReaction(
        baseNote: Note,
        grayTint: Color,
        accountViewModel: AccountViewModel,
        iconSize: Dp,
        iconSizeModifier: Modifier,
        animationModifier: Modifier,
        showCounter: Boolean,
        nav: INav,
    ) {}

    /** The editor for a new version of [edit], shown while [versionLookingAt] is on screen. */
    @Composable
    fun EditPostView(
        onClose: () -> Unit,
        edit: Note,
        versionLookingAt: Note?,
        accountViewModel: AccountViewModel,
        nav: INav,
    ) {}

    /** Draws nothing: previews, and front ends still wiring their pieces. */
    object None : NotePlatform
}

/** The front end's [NotePlatform]. Static: it is set once at the root and never changes. */
val LocalNotePlatform = staticCompositionLocalOf<NotePlatform> { NotePlatform.None }
