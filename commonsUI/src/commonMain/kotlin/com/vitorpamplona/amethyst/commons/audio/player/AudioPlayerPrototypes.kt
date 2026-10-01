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
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.pause
import com.vitorpamplona.amethyst.commons.resources.play
import com.vitorpamplona.amethyst.commons.resources.skip_back
import com.vitorpamplona.amethyst.commons.resources.skip_forward
import com.vitorpamplona.amethyst.commons.ui.stringRes
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * PROTOTYPES — design exploration for how an audio file inside a post should render, in place of
 * today's video player with a square box. Stateless and fed by [AudioCardUi]; nothing here is wired
 * to a player yet. Rendered offscreen by `AudioPlayerPrototypesRenderTest` into
 * `commonsUI/build/audio-player/`.
 *
 * Every layout is decided from what the imeta declares (`m`, `size`, `x`, optional `waveform`,
 * `image`, `alt`), so the card has its final shape before ExoPlayer has loaded a byte.
 */

/** Everything an audio card shows. [durationSeconds] is null until the player has probed the file. */
@Immutable
class AudioCardUi(
    val title: String,
    val artist: String,
    val format: String?,
    val sizeBytes: Long?,
    val durationSeconds: Int?,
    val positionSeconds: Int,
    val isPlaying: Boolean,
    /** The blob's sha256 (`x`): seeds the cover colours and the placeholder bars, so both are stable per file. */
    val seed: String,
    /** A NIP-A0 or decoded amplitude envelope, 0..1. Null falls back to bars generated from [seed]. */
    val waveform: List<Float>? = null,
) {
    val progress: Float
        get() = durationSeconds?.takeIf { it > 0 }?.let { (positionSeconds.toFloat() / it).coerceIn(0f, 1f) } ?: 0f

    /** "MP3 · 4.8 MB" — known from the imeta alone. */
    val fileFacts: String
        get() = listOfNotNull(format, sizeBytes?.let(::formatFileSize)).joinToString(" · ")

    /** "1:12 / 3:28" while there is a duration; the file facts before that. */
    val timeLabel: String
        get() =
            durationSeconds?.let { "${formatClock(positionSeconds)} / ${formatClock(it)}" } ?: fileFacts
}

// ---------------------------------------------------------------------------------------------
// A. Track card — one compact row, like a music-service embed. The recommended default.
// ---------------------------------------------------------------------------------------------

@Composable
fun AudioTrackCardPrototype(
    audio: AudioCardUi,
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
                GeneratedCover(audio.seed, Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)), iconSize = 28.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        audio.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        audio.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (audio.durationSeconds != null) "${audio.timeLabel} · ${audio.fileFacts}" else audio.fileFacts,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                PlayPauseButton(audio.isPlaying, size = 48.dp)
            }
            ThinProgress(audio.progress, Modifier.fillMaxWidth().height(3.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// B. Waveform scrubber — the voice-note look, generalised: the waveform IS the seek bar.
// ---------------------------------------------------------------------------------------------

@Composable
fun AudioWaveformCardPrototype(
    audio: AudioCardUi,
    modifier: Modifier = Modifier,
) {
    val bars = remember(audio.seed, audio.waveform) { audio.waveform ?: placeholderBars(audio.seed, 64) }
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
// C. Cover — for files that carry artwork (imeta `image` or an embedded cover). Here the cover is
// generated from the hash; a real one would replace the gradient and the record.
// ---------------------------------------------------------------------------------------------

@Composable
fun AudioCoverCardPrototype(
    audio: AudioCardUi,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp)),
    ) {
        GeneratedCover(audio.seed, Modifier.fillMaxSize(), iconSize = 0.dp, record = true)

        // Bottom scrim so the title and controls read on any cover.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))),
        )

        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                        "${audio.artist} · ${audio.timeLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(12.dp))
                PlayPauseButton(audio.isPlaying, size = 56.dp, container = Color.White, content = Color.Black)
            }
            Spacer(Modifier.height(12.dp))
            ThinProgress(
                audio.progress,
                Modifier.fillMaxWidth().height(4.dp),
                track = Color.White.copy(alpha = 0.25f),
                fill = Color.White,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// D. Visualizer card — keeps the spectrum visualizer, but on a coloured 2:1 card with a static
// frame before playback, so it reads as audio from the first frame instead of a black box.
// ---------------------------------------------------------------------------------------------

@Composable
fun AudioVisualizerCardPrototype(
    audio: AudioCardUi,
    modifier: Modifier = Modifier,
) {
    val (top, bottom) = remember(audio.seed) { seedColors(audio.seed) }
    // Stand-in for a live spectrum frame (the real one comes from PcmTapRegistry).
    val spectrum = remember(audio.seed) { placeholderBars(audio.seed.reversed(), 40) }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(2f)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(top, bottom))),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Text(
                audio.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(audio.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f), maxLines = 1)

            WaveformBars(
                bars = spectrum,
                progress = 0f,
                played = Color.White,
                unplayed = Color.White.copy(alpha = if (audio.isPlaying) 0.85f else 0.4f),
                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 10.dp),
                fromBottom = true,
                barWidth = 5.dp,
                gap = 3.dp,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(MaterialSymbols.Replay10, stringRes(Res.string.skip_back, 10), Modifier.size(26.dp), tint = Color.White)
                Spacer(Modifier.width(10.dp))
                PlayPauseButton(audio.isPlaying, size = 40.dp, container = Color.White, content = Color.Black)
                Spacer(Modifier.width(10.dp))
                Icon(MaterialSymbols.Forward10, stringRes(Res.string.skip_forward, 10), Modifier.size(26.dp), tint = Color.White)
                Spacer(Modifier.width(12.dp))
                ThinProgress(
                    audio.progress,
                    Modifier.weight(1f).height(4.dp).clip(CircleShape),
                    track = Color.White.copy(alpha = 0.3f),
                    fill = Color.White,
                )
                Spacer(Modifier.width(10.dp))
                Text(audio.timeLabel, style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// E. Mini player — where a playing track goes once its post scrolls away. Complements A–D.
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
                GeneratedCover(audio.seed, Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)), iconSize = 20.dp)
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

/** A cover generated from the file hash: a two-tone gradient, optionally a record, optionally a note glyph. */
@Composable
fun GeneratedCover(
    seed: String,
    modifier: Modifier = Modifier,
    iconSize: Dp,
    record: Boolean = false,
) {
    val (top, bottom) = remember(seed) { seedColors(seed) }
    Box(
        modifier = modifier.background(Brush.linearGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center,
    ) {
        if (record) RecordArt()
        if (iconSize > 0.dp) {
            Icon(MaterialSymbols.MusicNote, null, Modifier.size(iconSize), tint = Color.White.copy(alpha = 0.9f), filled = true)
        }
    }
}

@Composable
private fun BoxScope.RecordArt() {
    Canvas(Modifier.matchParentSize()) {
        val center = Offset(size.width * 0.62f, size.height * 0.38f)
        val radius = size.minDimension * 0.42f
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

/** Bars coloured [played] up to [progress] and [unplayed] after it; centred, or grown up [fromBottom]. */
@Composable
fun WaveformBars(
    bars: List<Float>,
    progress: Float,
    played: Color,
    unplayed: Color,
    modifier: Modifier = Modifier,
    fromBottom: Boolean = false,
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
            val y = if (fromBottom) size.height - h else (size.height - h) / 2f
            drawRoundRect(
                color = if (x / size.width < progress) played else unplayed,
                topLeft = Offset(x, y),
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

/** A music-looking envelope generated from the hash: the stand-in until a real waveform exists. */
fun placeholderBars(
    seed: String,
    count: Int,
): List<Float> {
    val hex = seed.ifEmpty { "0" }
    val raw =
        List(count) { i ->
            val nibble = hex[i % hex.length].digitToIntOrNull(16) ?: 8
            0.3f + 0.45f * (nibble / 15f) + 0.25f * abs(sin(i * 0.45f))
        }
    // Light smoothing so neighbouring bars relate, as in real audio.
    return List(count) { i ->
        val prev = raw[(i - 1).coerceAtLeast(0)]
        val next = raw[(i + 1).coerceAtMost(count - 1)]
        ((prev + 2 * raw[i] + next) / 4f).coerceIn(0f, 1f)
    }
}

fun formatClock(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

fun formatFileSize(bytes: Long): String {
    val tenthsOfMb = (bytes * 10 / 1_048_576.0).roundToInt()
    return if (tenthsOfMb >= 10) "${tenthsOfMb / 10}.${tenthsOfMb % 10} MB" else "${(bytes / 1024.0).roundToInt()} KB"
}
