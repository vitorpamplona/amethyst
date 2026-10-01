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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.robohash.CachedRobohash
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.commons.ui.theme.isLight

/** The post that prompted the redesign (an MP3 with `m audio/mpeg`, no ID3 tags), plus richer variants. */
object AudioPlayerSamples {
    const val AUTHOR_HEX = "f728d9e6e7048358e70930f5ca64b097770d989ccd86854fe618eda9c8a38106"
    const val AUTHOR_NAME = "npub17u5d…t4tp"
    const val POST_TEXT =
        "There aren't many nerdcore hip hop artists making music but I just realized that's no longer a problem: " +
            "I can simply generate the esoteric beats I want to hear!"
    private const val HASH = "b21c6e2a4d38f2abac617ac6643aba919f271b37c1c539b5643c17753716c506"

    /** Straight from the imeta, before ExoPlayer has probed anything: no title, no duration. */
    val notLoaded =
        AudioCardUi(
            title = "MP3 audio",
            artist = AUTHOR_NAME,
            format = "MP3",
            sizeBytes = 4_992_768,
            durationSeconds = null,
            positionSeconds = 0,
            isPlaying = false,
            seed = HASH,
        )

    /** Same file, playing: the player now knows it runs 3:28. */
    val playing =
        AudioCardUi(
            title = "MP3 audio",
            artist = AUTHOR_NAME,
            format = "MP3",
            sizeBytes = 4_992_768,
            durationSeconds = 208,
            positionSeconds = 72,
            isPlaying = true,
            seed = HASH,
        )

    /** A file whose ID3 tags (or imeta `alt`/title) name the track. */
    val tagged =
        AudioCardUi(
            title = "Segfault in the Cipher",
            artist = "Esoteric Beats Vol. 1",
            format = "MP3",
            sizeBytes = 4_992_768,
            durationSeconds = 208,
            positionSeconds = 72,
            isPlaying = true,
            seed = "3fa1c27be0d94e1a6c55b8f02d7e4a9c1b6f8e03d2a7c5b941e60f8d3c2b1a07",
        )
}

/** Dark and light side by side, each filling its half, so screenshots have no transparent gaps. */
@Composable
private fun AudioPreviewFrame(content: @Composable () -> Unit) {
    ThemeComparisonRow {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
    }
}

/** A stripped-down note: avatar, name, the post text, then the audio card where the media goes. */
@Composable
fun MockAudioPost(
    text: String = AudioPlayerSamples.POST_TEXT,
    media: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(12.dp)) {
        Image(
            imageVector = CachedRobohash.get(AudioPlayerSamples.AUTHOR_HEX, MaterialTheme.colorScheme.isLight),
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(AudioPlayerSamples.AUTHOR_NAME, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(" · 2h", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(2.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            media()
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 14.dp),
    )
}

/** Roughly what ships today: the video player's black square, controls and all, until tracks resolve. */
@Composable
private fun TodaysPlayerSketch() {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Icon(MaterialSymbols.PlayArrow, null, Modifier.size(64.dp), tint = Color.White, filled = true)
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("0:00", style = MaterialTheme.typography.labelSmall, color = Color.White)
            Box(Modifier.weight(1f).height(3.dp).background(Color.White.copy(alpha = 0.4f)))
            Text("--:--", style = MaterialTheme.typography.labelSmall, color = Color.White)
            Icon(MaterialSymbols.Fullscreen, null, Modifier.size(20.dp), tint = Color.White)
        }
    }
}

@Preview(widthDp = 820, heightDp = 700)
@Composable
fun AudioTodayPreview() =
    AudioPreviewFrame {
        Column {
            Caption("Today · the video player's black square, until ExoPlayer reports tracks")
            MockAudioPost { TodaysPlayerSketch() }
        }
    }

@Preview(widthDp = 820, heightDp = 560)
@Composable
fun AudioTrackCardPreview() =
    AudioPreviewFrame {
        Column {
            Caption("A · Track card — before the player loads (imeta only)")
            MockAudioPost { AudioTrackCardPrototype(AudioPlayerSamples.notLoaded) }
            Caption("Playing")
            MockAudioPost { AudioTrackCardPrototype(AudioPlayerSamples.playing) }
        }
    }

@Preview(widthDp = 820, heightDp = 540)
@Composable
fun AudioWaveformCardPreview() =
    AudioPreviewFrame {
        Column {
            Caption("B · Waveform scrubber — before the player loads")
            MockAudioPost { AudioWaveformCardPrototype(AudioPlayerSamples.notLoaded) }
            Caption("Playing")
            MockAudioPost { AudioWaveformCardPrototype(AudioPlayerSamples.playing) }
        }
    }

@Preview(widthDp = 820, heightDp = 620)
@Composable
fun AudioCoverCardPreview() =
    AudioPreviewFrame {
        Column {
            Caption("C · Cover — when the file has artwork")
            MockAudioPost { AudioCoverCardPrototype(AudioPlayerSamples.tagged) }
        }
    }

@Preview(widthDp = 820, heightDp = 680)
@Composable
fun AudioVisualizerCardPreview() =
    AudioPreviewFrame {
        Column {
            Caption("D · Visualizer card — idle")
            MockAudioPost { AudioVisualizerCardPrototype(AudioPlayerSamples.notLoaded) }
            Caption("Playing")
            MockAudioPost { AudioVisualizerCardPrototype(AudioPlayerSamples.playing) }
        }
    }

@Preview(widthDp = 820, heightDp = 520)
@Composable
fun AudioMiniPlayerPreview() =
    AudioPreviewFrame {
        Column(Modifier.fillMaxSize()) {
            Caption("E · Mini player — after the post scrolls away")
            // Ghost feed rows standing in for the notes that scrolled in.
            repeat(3) {
                Row(Modifier.fillMaxWidth().padding(12.dp)) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier
                                .width(120.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth(0.7f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            AudioMiniPlayerPrototype(AudioPlayerSamples.tagged, Modifier.padding(horizontal = 8.dp))
            Spacer(Modifier.height(8.dp))
            // Bottom navigation bar placeholder.
            Row(
                Modifier.fillMaxWidth().height(56.dp).background(MaterialTheme.colorScheme.surfaceContainer),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(MaterialSymbols.Home, MaterialSymbols.Search, MaterialSymbols.Notifications).forEach {
                    Icon(it, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

@Preview(widthDp = 820, heightDp = 560)
@Composable
fun AudioTaggedVariantsPreview() =
    AudioPreviewFrame {
        Column {
            Caption("A + B with ID3 tags — a named track")
            MockAudioPost { AudioTrackCardPrototype(AudioPlayerSamples.tagged) }
            MockAudioPost { AudioWaveformCardPrototype(AudioPlayerSamples.tagged) }
        }
    }
