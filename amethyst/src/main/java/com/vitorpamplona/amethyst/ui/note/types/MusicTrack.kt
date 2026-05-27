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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.playback.composable.GetVideoController
import com.vitorpamplona.amethyst.service.playback.composable.LoadThumbAndThenVideoView
import com.vitorpamplona.amethyst.service.playback.composable.PauseControllerWhenInBackground
import com.vitorpamplona.amethyst.service.playback.composable.VideoView
import com.vitorpamplona.amethyst.service.playback.composable.mediaitem.GetMediaItem
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeaderBackground
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Font10SP
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.experimental.music.track.MusicTrackEvent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private val COVER_ASPECT_RATIO = 1f

// Content text prefixes that some music-publishing tools auto-fill — purely restate the
// title/artist that the card already shows above. Suppress the description block in that case
// so the card doesn't waste a paragraph echoing its own header.
private val MUSIC_TRACK_BOILERPLATE_PREFIXES =
    listOf(
        "Listen to my song",
    )

@Composable
fun RenderMusicTrack(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? MusicTrackEvent ?: return

    MusicTrackHeader(
        noteEvent = noteEvent,
        note = note,
        makeItShort = makeItShort,
        canPreview = canPreview,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusicTrackHeader(
    noteEvent: MusicTrackEvent,
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val title = remember(noteEvent) { noteEvent.title() }
    val artist = remember(noteEvent) { noteEvent.artist() }
    val url = remember(noteEvent) { noteEvent.url() }
    val videoUrl = remember(noteEvent) { noteEvent.videoUrl() }
    val image = remember(noteEvent) { noteEvent.image() }
    val album = remember(noteEvent) { noteEvent.album() }
    val trackNumber = remember(noteEvent) { noteEvent.trackNumber() }
    val released = remember(noteEvent) { noteEvent.released() }
    val duration = remember(noteEvent) { noteEvent.duration() }
    val format = remember(noteEvent) { noteEvent.format() }
    val isExplicit = remember(noteEvent) { noteEvent.isExplicit() }
    // Suppress publishing-tool boilerplate that just restates the title/artist already shown
    // above. Today the only known offender is "Listen to my song ..." (emitted by some Blossom
    // uploaders); extend MUSIC_TRACK_BOILERPLATE_PREFIXES if more turn up.
    val description =
        remember(noteEvent) {
            noteEvent.content
                .takeUnless { content ->
                    MUSIC_TRACK_BOILERPLATE_PREFIXES.any { content.trim().startsWith(it, ignoreCase = true) }
                }?.ifBlank { null }
        }
    val topics =
        remember(noteEvent) {
            noteEvent.tags
                .mapNotNull { if (it.size > 1 && it[0] == "t" && it[1] != MusicTrackEvent.GENRE_TAG) it[1] else null }
                .distinct()
                .take(4)
                .toImmutableList()
        }
    // Per spec, `format` is the AUDIO format (mp3/flac/m4a/ogg) — only meaningful on the
    // audio path. The video path lets ExoPlayer sniff the container.
    val audioMimeType = remember(format) { format?.let { "audio/$it" } }

    Column(MaterialTheme.colorScheme.replyModifier) {
        if (videoUrl != null) {
            // A track with a `video` tag is an actual music-video file — show it through the
            // full video player (with the album cover as thumb when we have one).
            if (image != null) {
                LoadThumbAndThenVideoView(
                    videoUri = videoUrl,
                    mimeType = null,
                    title = title,
                    thumbUri = image,
                    authorName = note.author?.toBestDisplayName(),
                    roundedCorner = true,
                    contentScale = ContentScale.FillWidth,
                    nostrUriCallback = "nostr:${note.toNEvent()}",
                    accountViewModel = accountViewModel,
                )
            } else {
                VideoView(
                    videoUri = videoUrl,
                    mimeType = null,
                    title = title,
                    authorName = note.author?.toBestDisplayName(),
                    roundedCorner = true,
                    contentScale = ContentScale.FillWidth,
                    nostrUriCallback = "nostr:${note.toNEvent()}",
                    accountViewModel = accountViewModel,
                )
            }
        } else {
            // Audio-only path. We *don't* call RenderAudioWithWaveform here for two reasons:
            //   1. It wraps the player in `MaxWidthPaddingTop5dp` and renders its own
            //      DisplayUncitedHashtags row. We need the player to butt directly against
            //      the cover (no gap) and we already render hashtags as TopicChips below,
            //      so the inner ones would duplicate.
            //   2. It hard-codes the all-rounded `imageModifier` as the player border. The
            //      music card wants the cover's top corners + the player's bottom corners
            //      to read as one continuous unit.
            // So we inline the GetMediaItem → GetVideoController → RenderVoicePlayer chain
            // and supply a bottom-rounded border. ExoPlayer logic, controls, and PiP all
            // come from the shared composables — only the chrome differs.
            MusicTrackCover(image, note, accountViewModel)
            if (url != null) {
                // Spec doesn't carry a waveform for kind 36787, so we synthesize one keyed
                // off the track address. The bars are purely decorative — ExoPlayer owns
                // actual playback progress.
                val syntheticWaveform = remember(note.idHex) { syntheticWaveformFor(note.idHex) }
                val callbackUri = remember(note) { note.toNostrUri() }
                val playerBorder =
                    remember {
                        Modifier.clip(RoundedCornerShape(bottomStart = 15.dp, bottomEnd = 15.dp))
                    }

                Row(
                    Modifier.fillMaxWidth().height(100.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GetMediaItem(
                        videoUri = url,
                        title = title,
                        artworkUri = null,
                        authorName = note.author?.toBestDisplayName(),
                        callbackUri = callbackUri,
                        mimeType = audioMimeType,
                        aspectRatio = null,
                        proxyPort = accountViewModel.httpClientBuilder.proxyPortForVideo(url),
                        keepPlaying = false,
                        waveformData = syntheticWaveform,
                    ) { mediaItem ->
                        GetVideoController(
                            mediaItem = mediaItem,
                            muted = false,
                        ) { controller ->
                            PauseControllerWhenInBackground(controller)
                            RenderVoicePlayer(
                                mediaItem = mediaItem,
                                controllerState = controller,
                                waveform = syntheticWaveform,
                                borderModifier = playerBorder,
                                accountViewModel = accountViewModel,
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Size5dp),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            artist?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        symbol = MaterialSymbols.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.grayText,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.padding(start = 6.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (album != null || released != null || duration != null || isExplicit) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    album?.let {
                        val trackPart = trackNumber?.let { n -> " · #$n" } ?: ""
                        MetaText(text = it + trackPart)
                    }
                    if (album != null && (released != null || duration != null || isExplicit)) MetaSeparator()
                    released?.let { MetaText(text = it) }
                    if (released != null && (duration != null || isExplicit)) MetaSeparator()
                    duration?.let { MetaText(text = formatDuration(it)) }
                    if (duration != null && isExplicit) MetaSeparator()
                    if (isExplicit) ExplicitBadge()
                }
            }

            description?.takeIf { !makeItShort }?.let {
                Spacer(Modifier.padding(top = 4.dp))
                val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }
                val callbackUri = remember(note) { note.toNostrUri() }

                TranslatableRichTextViewer(
                    content = it,
                    canPreview = canPreview,
                    quotesLeft = 1,
                    modifier = Modifier.fillMaxWidth(),
                    tags = tags,
                    backgroundColor = backgroundColor,
                    id = note.idHex,
                    callbackUri = callbackUri,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
                // No DisplayUncitedHashtags here — the TopicChip FlowRow below already shows
                // every non-genre-marker hashtag, and stacking both produces visually identical
                // duplicate chip rows.
            }

            if (topics.isNotEmpty()) {
                Spacer(Modifier.padding(top = 4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Size5dp),
                    verticalArrangement = Arrangement.spacedBy(Size5dp),
                ) {
                    topics.forEach { topic ->
                        TopicChip(topic) { nav.nav(Route.Hashtag(topic.lowercase())) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicTrackCover(
    image: String?,
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val imageShape = RoundedCornerShape(topStart = 15.dp, topEnd = 15.dp)
    val imageModifier =
        Modifier
            .fillMaxWidth()
            .aspectRatio(COVER_ASPECT_RATIO)
            .clip(imageShape)

    Box(imageModifier) {
        if (image != null) {
            MyAsyncImage(
                imageUrl = image,
                contentDescription = stringRes(R.string.preview_card_image_for, image),
                contentScale = ContentScale.Crop,
                mainImageModifier = Modifier.fillMaxSize(),
                loadedImageModifier = imageModifier,
                accountViewModel = accountViewModel,
                onLoadingBackground = { DefaultImageHeaderBackground(note, accountViewModel, imageModifier) },
                onError = { DefaultImageHeader(note, accountViewModel, imageModifier) },
            )
        } else {
            DefaultImageHeader(note, accountViewModel, imageModifier)
        }
    }
}

@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.grayText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MetaSeparator() {
    Text(
        text = " · ",
        color = MaterialTheme.colorScheme.grayText,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ExplicitBadge() {
    Text(
        text = "E",
        style = MaterialTheme.typography.labelSmall,
        fontSize = Font10SP,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
private fun TopicChip(
    topic: String,
    onClick: (() -> Unit)? = null,
) {
    // clip→clickable→background→padding so the tap target is the same as the visible chip
    // and the Material ripple stays inside the rounded shape.
    val base =
        Modifier
            .clip(CircleShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    Text(
        text = "#$topic",
        style = MaterialTheme.typography.labelSmall,
        fontSize = Font10SP,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.grayText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = base,
    )
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(minutes, secs)
}

private const val SYNTHETIC_WAVEFORM_SAMPLES = 96
private const val TWO_PI = (Math.PI * 2).toFloat()

/**
 * Builds a stable, decorative waveform from the track id. ExoPlayer drives actual playback
 * progress — these bars only exist so the audio player has the familiar "music silhouette"
 * shape rather than a flat strip when the spec has no `waveform` tag.
 *
 * Every shape parameter (phase offset, carrier frequency, baseline, envelope skew, jitter
 * envelope) is drawn from the seeded RNG before the per-bar loop, so two different track ids
 * produce visibly different waveforms — earlier versions used a fixed envelope + carrier that
 * only varied by per-bar noise, which made every track look like the same slow-fade sine with
 * minor wiggle.
 */
private fun syntheticWaveformFor(seed: String): com.vitorpamplona.amethyst.service.playback.composable.WaveformData {
    // Fold the seed's 32-bit hash into a Long so two ids whose hashCode happens to collide
    // (rare for hex addresses but cheap to defend against) still differ via the bit shuffle.
    val rng = kotlin.random.Random((seed.hashCode().toLong() * 0x9E3779B97F4A7C15uL.toLong()) xor seed.length.toLong())

    // Shape parameters drawn once per track — these are what makes track A look different
    // from track B at a glance.
    val phaseOffset = rng.nextFloat() * TWO_PI
    val carrierCycles = 2.5f + rng.nextFloat() * 5.5f // 2.5–8 full cycles across the strip
    val carrierWeight = 0.18f + rng.nextFloat() * 0.18f // 0.18–0.36
    val baseline = 0.28f + rng.nextFloat() * 0.22f // 0.28–0.50
    val envelopeStrength = rng.nextFloat() * 0.45f // 0.0–0.45; some tracks fade in/out, others don't
    val noiseStrength = 0.18f + rng.nextFloat() * 0.22f // 0.18–0.40

    val bars =
        List(SYNTHETIC_WAVEFORM_SAMPLES) { index ->
            val phase = index.toFloat() / SYNTHETIC_WAVEFORM_SAMPLES
            // Optional gentle fade so some tracks taper at the ends, others stay even.
            val envelope = 1f - envelopeStrength * (1f - kotlin.math.sin(phase * Math.PI).toFloat())
            val carrier = kotlin.math.sin(phase * TWO_PI * carrierCycles + phaseOffset) * carrierWeight
            val noise = (rng.nextFloat() - 0.5f) * 2f * noiseStrength
            ((baseline + carrier + noise) * envelope).coerceIn(0.05f, 1.0f)
        }
    return com.vitorpamplona.amethyst.service.playback.composable
        .WaveformData(bars)
}

// ---------------------------------------------------------------------------
// @Preview composables. These wire a constructed MusicTrackEvent through
// LocalCache so the renderer pulls the real Note path it would in production.
// The audio/image URLs are intentionally unreachable — VideoView falls back to
// its tap-to-load thumbnail state, and MyAsyncImage falls back to the
// DefaultImageHeader robohash. That's the same first-paint state real users
// see for a track they haven't tapped to download yet, which is what the
// preview is meant to show.
// ---------------------------------------------------------------------------

private const val PREVIEW_TRACK_PUBKEY = "989c3734c46abac7ce3ce229971581a5a6ee39cdd6aa7261a55823fa7f8c4799"
private val PREVIEW_TRACK_SIG = "0".repeat(128)

// Deterministic 64-char hex from any string — Event ids must be hex even for previews so
// downstream consumers that validate the format don't blow up. Not cryptographic; just
// reproducible per (preview, dTag, title) so the LocalCache slot stays stable across recomposes.
private fun previewHexId(prefix: String): String {
    val mixed = (prefix.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
    return mixed.padStart(64, 'a').take(64)
}

private fun previewMusicTrackEvent(
    dTag: String,
    title: String,
    artist: String,
    album: String? = null,
    duration: Int? = null,
    released: String? = null,
    trackNumber: Int? = null,
    explicit: Boolean = false,
    image: String? = "https://example.invalid/cover.jpg",
    url: String = "https://example.invalid/track.mp3",
    content: String = "",
): MusicTrackEvent {
    val tags =
        buildList {
            add(arrayOf("d", dTag))
            add(arrayOf("title", title))
            add(arrayOf("artist", artist))
            add(arrayOf("url", url))
            add(arrayOf("t", "music"))
            image?.let { add(arrayOf("image", it)) }
            album?.let { add(arrayOf("album", it)) }
            trackNumber?.let { add(arrayOf("track_number", it.toString())) }
            released?.let { add(arrayOf("released", it)) }
            duration?.let { add(arrayOf("duration", it.toString())) }
            if (explicit) add(arrayOf("explicit", "true"))
        }.toTypedArray()

    return MusicTrackEvent(
        id = previewHexId("track-$dTag-$title"),
        pubKey = PREVIEW_TRACK_PUBKEY,
        createdAt = 1_730_000_000L,
        tags = tags,
        content = content,
        sig = PREVIEW_TRACK_SIG,
    )
}

@Preview
@Composable
private fun RenderMusicTrackPreview() {
    val event =
        previewMusicTrackEvent(
            dTag = "summer-nights-2024",
            title = "Summer Nights",
            artist = "The Midnight Collective",
            album = "Endless Summer",
            duration = 245,
            released = "2024-06-15",
            trackNumber = 3,
            explicit = false,
            content = "Lyrics:\n[Verse 1]\nWalking through the city lights...\n\nProducer: John Doe",
        )

    remember(event) { runBlocking { withContext(Dispatchers.IO) { LocalCache.justConsume(event, null, true) } } }

    ThemeComparisonColumn {
        LoadNote(baseNoteHex = event.address().toValue(), accountViewModel = mockAccountViewModel()) { note ->
            note?.let {
                RenderMusicTrack(
                    note = it,
                    makeItShort = false,
                    canPreview = true,
                    backgroundColor = remember { mutableStateOf(Color.Transparent) },
                    accountViewModel = mockAccountViewModel(),
                    nav = EmptyNav(),
                )
            }
        }
    }
}

@Preview
@Composable
private fun RenderMusicTrackExplicitPreview() {
    val event =
        previewMusicTrackEvent(
            dTag = "loud-and-clear",
            title = "Loud and Clear",
            artist = "Static Riot",
            album = "Distortion",
            duration = 187,
            explicit = true,
            content = "",
        )

    remember(event) { runBlocking { withContext(Dispatchers.IO) { LocalCache.justConsume(event, null, true) } } }

    ThemeComparisonColumn {
        LoadNote(baseNoteHex = event.address().toValue(), accountViewModel = mockAccountViewModel()) { note ->
            note?.let {
                RenderMusicTrack(
                    note = it,
                    makeItShort = false,
                    canPreview = true,
                    backgroundColor = remember { mutableStateOf(Color.Transparent) },
                    accountViewModel = mockAccountViewModel(),
                    nav = EmptyNav(),
                )
            }
        }
    }
}

@Preview
@Composable
private fun MusicTrackCoverPreview() {
    val event = previewMusicTrackEvent(dTag = "cover-only", title = "Cover Only", artist = "Anon")
    remember(event) { runBlocking { withContext(Dispatchers.IO) { LocalCache.justConsume(event, null, true) } } }

    ThemeComparisonColumn {
        LoadNote(baseNoteHex = event.address().toValue(), accountViewModel = mockAccountViewModel()) { note ->
            note?.let { MusicTrackCover(image = null, note = it, accountViewModel = mockAccountViewModel()) }
        }
    }
}

@Preview
@Composable
private fun ExplicitBadgePreview() {
    ThemeComparisonColumn {
        Row(modifier = Modifier.padding(8.dp)) {
            ExplicitBadge()
        }
    }
}

@Preview
@Composable
private fun TopicChipPreview() {
    ThemeComparisonColumn {
        Row(modifier = Modifier.padding(8.dp)) {
            TopicChip("electronic")
            Spacer(Modifier.padding(start = 4.dp))
            TopicChip("synthwave")
            Spacer(Modifier.padding(start = 4.dp))
            TopicChip("80s")
        }
    }
}
