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
package com.vitorpamplona.amethyst.commons.ui.playback.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.vitorpamplona.amethyst.commons.audio.MediaTracks
import com.vitorpamplona.amethyst.commons.audio.PlayableLayout
import com.vitorpamplona.amethyst.commons.audio.playableLayout
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.ui.components.BlurhashBackdrop
import com.vitorpamplona.amethyst.commons.ui.components.ContentWarningGate
import com.vitorpamplona.amethyst.commons.ui.components.mediaSizingModifier
import com.vitorpamplona.amethyst.commons.ui.note.types.rememberMediaTracks
import com.vitorpamplona.amethyst.commons.ui.playback.audio.PlayableAudioView
import com.vitorpamplona.amethyst.commons.ui.playback.audio.PlayableMediaProbeCache
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

// Assumed shape of a video whose dimensions nobody has reported yet — no imeta `dim` and nothing
// cached, which is the norm for a NIP-53 live stream on its first play. Without a ratio the sizing
// modifier leaves height unconstrained, so the player stretches to whatever ceiling encloses it
// (300.dp on the live-stream screen) and letterboxes the real frame inside, leaving black bars top
// and bottom. Guessing the overwhelmingly common video shape puts the first layout in the right
// place; [MediaAspectRatioCache] then corrects anything unusual once the decoder reports its size.
private const val DEFAULT_VIDEO_ASPECT_RATIO = 16f / 9f

/**
 * Shape to assume for a [MediaUrlVideo] nobody has reported dimensions for — no imeta `dim` and
 * nothing in [MediaAspectRatioCache].
 *
 * Video gets [DEFAULT_VIDEO_ASPECT_RATIO]. Audio gets null, meaning "no ratio, wrap whatever the
 * player asks for": audio has no picture, and the inline player sizes itself as a square (see
 * `AudioPlayerSquare.audioSquare`) so the visualizer and controls get room. A square is taller than
 * 16:9, so caging it in a 16:9 box — which is centre-aligned and unclipped all the way up through
 * NoteComposeLayout — makes the player paint over the note header above it and the reactions row
 * below. Audio also never reaches the cache, since it has no video track to report a size, so the
 * miss is permanent rather than first-play-only.
 */
fun unknownMediaAspectRatio(
    mimeType: String?,
    url: String,
): Float? = if (RichTextParser.isAudioContent(mimeType, url)) null else DEFAULT_VIDEO_ASPECT_RATIO

/**
 * A [MediaUrlVideo] inside a post, as the zoomable media view routes it: audio (or media that may
 * be audio) to its audio card, everything else to the video player sized by its known shape.
 * [boundsTrackingModifier] marks where the fullscreen viewer grows from; [onDialog] opens it.
 */
@Composable
fun ZoomableVideoContent(
    content: MediaUrlVideo,
    roundedCorner: Boolean,
    contentScale: ContentScale,
    boundsTrackingModifier: Modifier,
    onDialog: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    // Audio, or media that may be audio, gets an audio card: B (waveform) or C (cover) when the
    // declaration says audio, A while it can't tell. The player's probe, once in, overrides the
    // declaration — undecided files find their real card, mislabelled videos come back here.
    var probedAudioOnly by remember(content.url) { mutableStateOf(PlayableMediaProbeCache.get(content.url)) }
    // An `.mp4` / `video/mp4` that doesn't say whether it holds video has its track list read
    // before playback: Blossom serves AAC uploads that way, and they belong in the audio card.
    val tracks = if (content.isLiveStream) MediaTracks.UNKNOWN else rememberMediaTracks(content.url, content.mimeType, accountViewModel)
    val audioOnly =
        probedAudioOnly ?: when (tracks) {
            MediaTracks.AUDIO_ONLY -> true
            MediaTracks.HAS_VIDEO -> false
            else -> null
        }
    val layout =
        remember(content.url, content.mimeType, content.artworkUri, content.isLiveStream, audioOnly) {
            // A live stream is the one stream we know is video-first; it keeps the video player.
            if (content.isLiveStream) {
                PlayableLayout.VIDEO
            } else {
                playableLayout(content.mimeType, content.url, hasArtwork = content.artworkUri != null, probedAudioOnly = audioOnly)
            }
        }

    if (layout != PlayableLayout.VIDEO) {
        ContentWarningGate(
            isSensitive = content.contentWarning != null,
            reasons = setOfNotNull(content.contentWarning),
            preloadUrls = emptyList(),
            accountViewModel = accountViewModel,
        ) {
            PlayableAudioView(
                content = content,
                layout = layout,
                onProbed = { audioOnly ->
                    PlayableMediaProbeCache.put(content.url, audioOnly)
                    probedAudioOnly = audioOnly
                },
                accountViewModel = accountViewModel,
            )
        }
    } else {
        // The fallback classifies the URL string, so compute it once per content — for audio
        // the cache miss is permanent and this branch re-runs on every recomposition.
        val fallbackRatio =
            remember(content.url, content.mimeType) {
                unknownMediaAspectRatio(content.mimeType, content.url)
            }
        // What the player measured beats the event's `dim`, which can be the stored size of
        // an anamorphic video or ignore rotation.
        val ratio =
            MediaAspectRatioCache.videoRatio(content.url, content.dim?.aspectRatioOrNull())
                ?: fallbackRatio
        ContentWarningGate(
            isSensitive = content.contentWarning != null,
            reasons = setOfNotNull(content.contentWarning),
            preloadUrls = emptyList(),
            accountViewModel = accountViewModel,
            modifier = mediaSizingModifier(ratio, contentScale),
            backdrop = (content.thumbhash ?: content.blurhash)?.let { { BlurhashBackdrop(content.blurhash, content.description, content.thumbhash) } },
        ) {
            Box(
                // The sizing modifier is repeated here because ContentWarningGate only applies
                // the one it is handed when the content is actually sensitive — the common
                // non-sensitive path emits content() bare. Without a height constraint of its
                // own this box stretches to whatever ceiling encloses it and the player
                // letterboxes the frame inside, which is what put black bars above and below
                // live streams (their enclosure is StreamingHeaderModifier's 300.dp cap).
                modifier = mediaSizingModifier(ratio, contentScale).then(boundsTrackingModifier),
                contentAlignment = Alignment.Center,
            ) {
                VideoView(
                    videoUri = content.url,
                    mimeType = content.mimeType,
                    title = content.description,
                    artworkUri = content.artworkUri,
                    authorName = content.authorName,
                    dimensions = content.dim,
                    blurhash = content.blurhash,
                    roundedCorner = roundedCorner,
                    contentScale = contentScale,
                    nostrUriCallback = content.uri,
                    onDialog = onDialog,
                    accountViewModel = accountViewModel,
                    thumbhash = content.thumbhash,
                    isLiveStream = content.isLiveStream,
                    hash = content.hash,
                    captions = content.captions,
                    sizeBytes = content.sizeBytes,
                )
            }
        }
    }
}
