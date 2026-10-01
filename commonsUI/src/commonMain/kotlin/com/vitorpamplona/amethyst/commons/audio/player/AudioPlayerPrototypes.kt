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
package com.vitorpamplona.amethyst.commons.audio.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.audio.PlayableLayout
import com.vitorpamplona.amethyst.commons.audio.playableLayout
import com.vitorpamplona.amethyst.commons.audio.syntheticWaveformFor
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.pause
import com.vitorpamplona.amethyst.commons.resources.play
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * PROTOTYPES — how a playable file inside a post should render, in place of today's video player
 * with a square box. Stateless and fed by [AudioCardUi]; nothing here is wired to a player yet.
 * Rendered offscreen by `AudioPlayerPrototypesRenderTest` into `commonsUI/build/audio-player/`.
 *
 * Which card is shown is [playableLayout]'s call, made from the imeta before the player loads a byte:
 *  - known audio, no artwork  -> B, the waveform scrubber (the file's waveform, else a synthetic one);
 *  - known audio with artwork -> C, the cover with the same scrubber over its foot;
 *  - audio or video, can't tell (an HLS playlist, say) -> A, the neutral track card, until the
 *    player's probe settles it;
 *  - known video -> the video player.
 */

/** Everything a card shows. [durationSeconds] is null until the player has probed the file. */
@Immutable
class AudioCardUi(
    val url: String,
    val mimeType: String?,
    val title: String,
    val artist: String,
    val format: String?,
    val sizeBytes: Long?,
    val durationSeconds: Int?,
    val positionSeconds: Int,
    val isPlaying: Boolean,
    /** The blob's sha256 (`x`): seeds the synthetic waveform and the generated cover, so both are stable per file. */
    val seed: String,
    /** The file's own amplitude envelope (a `waveform` tag, or one decoded from the audio), 0..1. */
    val waveform: List<Float>? = null,
    /** True when the imeta `image` or an embedded cover gives the file artwork. */
    val hasArtwork: Boolean = false,
) {
    val progress: Float
        get() = durationSeconds?.takeIf { it > 0 }?.let { (positionSeconds.toFloat() / it).coerceIn(0f, 1f) } ?: 0f

    /** "MP3 · 4.8 MB" — known from the imeta alone. */
    val fileFacts: String
        get() = listOfNotNull(format, sizeBytes?.let(::formatFileSize)).joinToString(" · ")

    /** "1:12 / 3:28" while there is a duration; the file facts before that. */
    val timeLabel: String
        get() = durationSeconds?.let { "${formatClock(positionSeconds)} / ${formatClock(it)}" } ?: fileFacts

    val layout: PlayableLayout
        get() = playableLayout(mimeType, url, hasArtwork)
}

/** The one entry point a post would call: picks the card from [AudioCardUi.layout]. */
@Composable
fun PlayablePostMediaPrototype(
    media: AudioCardUi,
    modifier: Modifier = Modifier,
) {
    when (media.layout) {
        PlayableLayout.AUDIO_WAVEFORM -> AudioWaveformCardPrototype(media, modifier)
        PlayableLayout.AUDIO_COVER -> AudioCoverCardPrototype(media, modifier)
        PlayableLayout.UNDECIDED -> UndecidedMediaCardPrototype(media, modifier)
        // Not prototyped: known video keeps the existing video player.
        PlayableLayout.VIDEO ->
            Box(
                modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
            )
    }
}

// ---------------------------------------------------------------------------------------------
// B. Waveform scrubber — known audio. The waveform IS the seek bar.
// ---------------------------------------------------------------------------------------------

@Composable
fun AudioWaveformCardPrototype(
    audio: AudioCardUi,
    modifier: Modifier = Modifier,
) {
    val bars = rememberBars(audio)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayPauseButton(audio.isPlaying, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                WaveformBars(
                    bars = bars,
                    progress = audio.progress,
                    played = MaterialTheme.colorScheme.primary,
                    unplayed = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                )
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        audio.durationSeconds?.let { formatClock(audio.positionSeconds) } ?: audio.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        listOfNotNull(audio.fileFacts, audio.durationSeconds?.let(::formatClock)).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// C. Cover — known audio with artwork: the cover, with B's scrubber over its foot. The cover here is
// generated from the hash; real artwork would replace the gradient and the record.
// ---------------------------------------------------------------------------------------------

@Composable
fun AudioCoverCardPrototype(
    audio: AudioCardUi,
    modifier: Modifier = Modifier,
) {
    val bars = rememberBars(audio)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp)),
    ) {
        GeneratedCover(audio.seed, Modifier.fillMaxSize(), icon = null, record = true)

        // Bottom scrim so the title and the waveform read on any cover.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))),
        )

        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        audio.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        audio.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(12.dp))
                PlayPauseButton(audio.isPlaying, size = 52.dp, container = Color.White, content = Color.Black)
            }
            Spacer(Modifier.height(12.dp))
            WaveformBars(
                bars = bars,
                progress = audio.progress,
                played = Color.White,
                unplayed = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth().height(36.dp),
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    audio.durationSeconds?.let { formatClock(audio.positionSeconds) } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Text(
                    listOfNotNull(audio.fileFacts, audio.durationSeconds?.let(::formatClock)).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// A. Neutral track card — only while we can't tell audio from video. It commits to neither shape
// (no waveform, no picture box) and is replaced by B, C or the video player once the probe answers.
// ---------------------------------------------------------------------------------------------

@Composable
fun UndecidedMediaCardPrototype(
    media: AudioCardUi,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MaterialSymbols.Podcasts, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        media.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        media.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (media.durationSeconds != null) "${media.timeLabel} · ${media.fileFacts}" else media.fileFacts,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                PlayPauseButton(media.isPlaying, size = 48.dp)
            }
            ThinProgress(media.progress, Modifier.fillMaxWidth().height(3.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// E. Mini player — where a playing track goes once its post scrolls away. Complements B and C.
// ---------------------------------------------------------------------------------------------

@Composable
fun AudioMiniPlayerPrototype(
    audio: AudioCardUi,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 6.dp,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GeneratedCover(audio.seed, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)), icon = MaterialSymbols.MusicNote, iconSize = 20.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(audio.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(audio.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PlayPauseButton(audio.isPlaying, size = 40.dp, container = Color.Transparent, content = MaterialTheme.colorScheme.onSurface)
                Icon(MaterialSymbols.Close, stringRes(Res.string.close), Modifier.padding(horizontal = 8.dp).size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ThinProgress(audio.progress, Modifier.fillMaxWidth().height(2.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------------------------

/** The file's own waveform when it has one, else the synthetic one every other audio renderer already uses. */
@Composable
private fun rememberBars(audio: AudioCardUi): List<Float> = remember(audio.seed, audio.waveform) { audio.waveform ?: syntheticWaveformFor(audio.seed).wave }

@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = if (isPlaying) MaterialSymbols.Pause else MaterialSymbols.PlayArrow,
            contentDescription = stringRes(if (isPlaying) Res.string.pause else Res.string.play),
            modifier = Modifier.size(size * 0.6f),
            tint = content,
            filled = true,
        )
    }
}

/** A cover generated from the file hash: a two-tone gradient, optionally a record, optionally a glyph. */
@Composable
fun GeneratedCover(
    seed: String,
    modifier: Modifier = Modifier,
    icon: MaterialSymbol?,
    iconSize: Dp = 28.dp,
    record: Boolean = false,
) {
    val (top, bottom) = remember(seed) { seedColors(seed) }
    Box(
        modifier = modifier.background(Brush.linearGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center,
    ) {
        if (record) RecordArt()
        if (icon != null) {
            Icon(icon, null, Modifier.size(iconSize), tint = Color.White.copy(alpha = 0.9f), filled = true)
        }
    }
}

@Composable
private fun BoxScope.RecordArt() {
    Canvas(Modifier.matchParentSize()) {
        val center = Offset(size.width * 0.62f, size.height * 0.34f)
        val radius = size.minDimension * 0.4f
        drawCircle(Color.Black.copy(alpha = 0.55f), radius, center)
        var r = radius * 0.95f
        while (r > radius * 0.4f) {
            drawCircle(Color.White.copy(alpha = 0.06f), r, center, style = Stroke(width = 1.5f))
            r -= radius * 0.06f
        }
        drawCircle(Color.White.copy(alpha = 0.85f), radius * 0.3f, center)
        drawCircle(Color.Black.copy(alpha = 0.8f), radius * 0.04f, center)
    }
}

@Composable
private fun ThinProgress(
    progress: Float,
    modifier: Modifier,
    track: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    fill: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier) {
        drawRect(track)
        drawRect(fill, size = Size(size.width * progress, size.height))
    }
}

/** Centred bars, coloured [played] up to [progress] and [unplayed] after it. */
@Composable
fun WaveformBars(
    bars: List<Float>,
    progress: Float,
    played: Color,
    unplayed: Color,
    modifier: Modifier = Modifier,
    barWidth: Dp = 3.dp,
    gap: Dp = 2.dp,
) {
    Canvas(modifier) {
        val step = (barWidth + gap).toPx()
        val count = (size.width / step).toInt().coerceAtLeast(1)
        val w = barWidth.toPx()
        for (i in 0 until count) {
            val amp = bars[(i * bars.size) / count].coerceIn(0.08f, 1f)
            val h = amp * size.height
            val x = i * step
            drawRoundRect(
                color = if (x / size.width < progress) played else unplayed,
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f, w / 2f),
            )
        }
    }
}

/** Two colours derived from the hash, so every file gets its own stable cover. */
fun seedColors(seed: String): Pair<Color, Color> {
    val n = seed.take(6).toIntOrNull(16) ?: seed.hashCode()
    val hue = (abs(n) % 360).toFloat()
    return Color.hsv(hue, 0.6f, 0.62f) to Color.hsv((hue + 48f) % 360f, 0.75f, 0.32f)
}

fun formatClock(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

fun formatFileSize(bytes: Long): String {
    val tenthsOfMb = (bytes * 10 / 1_048_576.0).roundToInt()
    return if (tenthsOfMb >= 10) "${tenthsOfMb / 10}.${tenthsOfMb % 10} MB" else "${(bytes / 1024.0).roundToInt()} KB"
}
