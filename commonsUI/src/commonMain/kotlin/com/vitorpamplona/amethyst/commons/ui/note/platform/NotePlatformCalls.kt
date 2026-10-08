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
import androidx.compose.runtime.remember
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
import kotlinx.collections.immutable.persistentListOf
import okio.Path

// Call-site shims for [NotePlatform]: the same names and defaults the app's own composables have,
// so shared note code reads as it did and only its imports point here.

@Composable
fun ZoomableContentView(
    content: BaseMediaContent,
    images: ImmutableList<BaseMediaContent> = remember(content) { persistentListOf(content) },
    roundedCorner: Boolean,
    contentScale: ContentScale,
    accountViewModel: AccountViewModel,
) = LocalNotePlatform.current.ZoomableContentView(
    content = content,
    images = images,
    roundedCorner = roundedCorner,
    contentScale = contentScale,
    accountViewModel = accountViewModel,
)

@Composable
fun ZoomableImageDialog(
    imageUrl: BaseMediaContent,
    allImages: ImmutableList<BaseMediaContent> = remember(imageUrl) { persistentListOf(imageUrl) },
    sourceBounds: Rect? = null,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
) = LocalNotePlatform.current.ZoomableImageDialog(
    imageUrl = imageUrl,
    allImages = allImages,
    sourceBounds = sourceBounds,
    onDismiss = onDismiss,
    accountViewModel = accountViewModel,
)

@Composable
fun GifVideoView(
    videoUri: String,
    contentDescription: String?,
    dimensions: DimensionTag?,
    blurhash: String?,
    roundedCorner: Boolean,
    contentScale: ContentScale,
    onDialog: (() -> Unit)? = null,
    accountViewModel: AccountViewModel,
    thumbhash: String? = null,
) = LocalNotePlatform.current.GifVideoView(
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
fun FullscreenVideoView(
    videoUri: String,
    mimeType: String?,
    contentScale: ContentScale,
    controllerVisible: MutableState<Boolean>,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) = LocalNotePlatform.current.FullscreenVideoView(videoUri, mimeType, contentScale, modifier, controllerVisible, accountViewModel)

@Composable
fun VideoView(
    videoUri: String,
    mimeType: String?,
    title: String? = null,
    artworkUri: String? = null,
    authorName: String? = null,
    roundedCorner: Boolean,
    contentScale: ContentScale,
    nostrUriCallback: String? = null,
    isLiveStream: Boolean = false,
    accountViewModel: AccountViewModel,
) = LocalNotePlatform.current.VideoView(
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
fun LoadUrlPreview(
    url: String,
    urlText: String,
    callbackUri: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav? = null,
) = LocalNotePlatform.current.UrlPreview(
    url = url,
    urlText = urlText,
    callbackUri = callbackUri,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun LocationPreviewMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    zoom: Double? = null,
    aspectRatio: Float = 1f,
    pinColor: Color? = null,
    pinEmoji: String? = null,
    pinAlpha: Float = 1f,
) = LocalNotePlatform.current.LocationPreviewMap(
    latitude = latitude,
    longitude = longitude,
    aspectRatio = aspectRatio,
    pinColor = pinColor,
    pinEmoji = pinEmoji,
    pinAlpha = pinAlpha,
    modifier = modifier,
    zoom = zoom,
)

@Composable
fun LoadCityName(
    geohashStr: String,
    onLoading: (@Composable () -> Unit)? = null,
    content: @Composable (String) -> Unit,
) = LocalNotePlatform.current.LoadCityName(
    geohashStr = geohashStr,
    onLoading = onLoading,
    content = content,
)

@Composable
fun RenderAudioTrack(
    note: Note,
    contentScale: ContentScale,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderAudioTrack(
    note = note,
    contentScale = contentScale,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun RenderMusicTrack(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderMusicTrack(
    note = note,
    makeItShort = makeItShort,
    canPreview = canPreview,
    backgroundColor = backgroundColor,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun PodcastEpisodeAudioPlayer(
    audio: PodcastAudio,
    note: Note,
    title: String?,
    image: String?,
    borderModifier: Modifier,
    accountViewModel: AccountViewModel,
) = LocalNotePlatform.current.PodcastEpisodeAudioPlayer(
    audio = audio,
    note = note,
    title = title,
    image = image,
    borderModifier = borderModifier,
    accountViewModel = accountViewModel,
)

@Composable
fun RenderVoiceTrack(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderVoiceTrack(
    note = note,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun RenderAudioFromIMeta(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderAudioFromIMeta(
    note = note,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun RenderAudioPlayer(
    mediaUrl: String,
    title: String?,
    mimeType: String?,
    waveform: WaveformData?,
    accountViewModel: AccountViewModel,
) = LocalNotePlatform.current.RenderAudioPlayer(mediaUrl, title, mimeType, waveform, accountViewModel)

@Composable
fun RenderAudioWithWaveform(
    mediaUrl: String,
    title: String?,
    mimeType: String?,
    waveform: WaveformData?,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderAudioWithWaveform(
    mediaUrl = mediaUrl,
    title = title,
    mimeType = mimeType,
    waveform = waveform,
    note = note,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun RenderChessGame(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderChessGame(
    note = note,
    backgroundColor = backgroundColor,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun RenderLiveChessChallenge(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderLiveChessChallenge(
    note = note,
    backgroundColor = backgroundColor,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun RenderLiveChessGameEnd(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderLiveChessGameEnd(
    note = note,
    backgroundColor = backgroundColor,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun RenderGitRepositoryEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderGitRepositoryEvent(
    baseNote = baseNote,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun RenderGitPatchEvent(
    baseNote: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderGitPatchEvent(
    baseNote = baseNote,
    makeItShort = makeItShort,
    canPreview = canPreview,
    quotesLeft = quotesLeft,
    backgroundColor = backgroundColor,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun RenderGitIssueEvent(
    baseNote: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderGitIssueEvent(
    baseNote = baseNote,
    makeItShort = makeItShort,
    canPreview = canPreview,
    quotesLeft = quotesLeft,
    backgroundColor = backgroundColor,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun RenderGitPullRequestEvent(
    baseNote: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderGitPullRequestEvent(
    baseNote = baseNote,
    makeItShort = makeItShort,
    canPreview = canPreview,
    quotesLeft = quotesLeft,
    backgroundColor = backgroundColor,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun RenderGitPullRequestUpdateEvent(
    baseNote: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderGitPullRequestUpdateEvent(
    baseNote = baseNote,
    makeItShort = makeItShort,
    canPreview = canPreview,
    quotesLeft = quotesLeft,
    backgroundColor = backgroundColor,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun RenderCalendarRSVPEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderCalendarRSVPEvent(
    baseNote = baseNote,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun RenderMeetingSpaceEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderMeetingSpaceEvent(
    baseNote = baseNote,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun RenderMeetingRoomEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderMeetingRoomEvent(
    baseNote = baseNote,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun RenderMeetingRoomPresence(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderMeetingRoomPresence(
    baseNote = baseNote,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun RenderRootNappletEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderRootNappletEvent(
    baseNote = baseNote,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun RenderNamedNappletEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderNamedNappletEvent(
    baseNote = baseNote,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun RenderRootSiteEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderRootSiteEvent(
    baseNote = baseNote,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun RenderNamedSiteEvent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderNamedSiteEvent(
    baseNote = baseNote,
    accountViewModel = accountViewModel,
    nav = nav,
)

/** The longest voice message the recorder allows, in seconds. */
const val MAX_VOICE_RECORD_SECONDS = 600

@Composable
fun RecordVoiceButton(
    onVoiceTaken: (RecordingResult) -> Unit,
    maxDurationSeconds: Int? = null,
) = LocalNotePlatform.current.RecordVoiceButton(onVoiceTaken, maxDurationSeconds)

@Composable
fun RecordAudioBox(
    modifier: Modifier = Modifier,
    onRecordTaken: (RecordingResult) -> Unit,
    maxDurationSeconds: Int? = null,
    content: @Composable (isRecording: Boolean, elapsedSeconds: Int, onStop: () -> Unit) -> Unit,
) = LocalNotePlatform.current.RecordAudioBox(modifier, onRecordTaken, maxDurationSeconds, content)

@Composable
fun VoiceMessagePreview(
    voiceMetadata: AudioMeta,
    localFile: Path? = null,
    onRemove: () -> Unit,
    onReRecord: ((RecordingResult) -> Unit)? = null,
    isUploading: Boolean = false,
    modifier: Modifier = Modifier,
) = LocalNotePlatform.current.VoiceMessagePreview(voiceMetadata, localFile, onRemove, onReRecord, isUploading, modifier)

@Composable
fun EditPostView(
    onClose: () -> Unit,
    edit: Note,
    versionLookingAt: Note?,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.EditPostView(
    onClose = onClose,
    edit = edit,
    versionLookingAt = versionLookingAt,
    accountViewModel = accountViewModel,
    nav = nav,
)

@Composable
fun rememberUrlPreviewState(
    url: String,
    accountViewModel: AccountViewModel,
): UrlPreviewState = LocalNotePlatform.current.rememberUrlPreviewState(url, accountViewModel)

@Composable
fun RenderAudioHeader(
    note: Note,
    contentScale: ContentScale,
    accountViewModel: AccountViewModel,
    nav: INav,
) = LocalNotePlatform.current.RenderAudioHeader(note, contentScale, accountViewModel, nav)
