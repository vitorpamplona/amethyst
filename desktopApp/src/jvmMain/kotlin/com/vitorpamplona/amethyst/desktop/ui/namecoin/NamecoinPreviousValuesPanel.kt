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
package com.vitorpamplona.amethyst.desktop.ui.namecoin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinHistoryEntry
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinNameHistory
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Desktop counterpart of the Android `PreviousValuesPanel`.
 *
 * Stays visually consistent with the [com.vitorpamplona.amethyst.desktop.ui.SearchScreen]
 * card stack — same rounded corners, same surface-variant fill — but
 * routes taps through the desktop `onNavigateToProfile(pubkeyHex)`
 * callback rather than the Android `User`-object-based callback.
 *
 * See the Android implementation in
 * `amethyst/ui/components/namecoin/NamecoinResolutionRow.kt` for the
 * rendering rationale (header, expiry-gap dividers, ordinal pills).
 */
@Composable
fun NamecoinPreviousValuesPanel(
    history: NamecoinNameHistory,
    onNavigateToProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!history.hasEntries) return
    var expanded by remember(history.namecoinName) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = headerText(history, expanded),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    symbol = if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                    contentDescription = if (expanded) "Hide previous values" else "Show previous values",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                ) {
                    history.entries.forEachIndexed { idx, entry ->
                        if (entry.precededByExpiryGap) {
                            ExpiryGapDivider(entry)
                        }
                        PreviousValueRow(
                            entry = entry,
                            ordinal = idx + 1,
                            onNavigateToProfile = onNavigateToProfile,
                        )
                    }
                }
            }
        }
    }
}

private fun headerText(
    history: NamecoinNameHistory,
    expanded: Boolean,
): String {
    if (expanded) return "Hide previous values"
    val n = history.entries.size
    val gaps = history.expiryGapCount
    val suffix =
        when {
            gaps == 0 -> ""
            gaps == 1 -> " · 1 prior owner"
            else -> " · $gaps prior owners"
        }
    return "Show $n previous value${if (n == 1) "" else "s"}$suffix"
}

@Composable
private fun ExpiryGapDivider(entry: NamecoinHistoryEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier.width(16.dp),
            color = MaterialTheme.colorScheme.error,
        )
        Icon(
            symbol = MaterialSymbols.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "Name expired — registered again ${formatDate(entry.timestampSec)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun PreviousValueRow(
    entry: NamecoinHistoryEntry,
    ordinal: Int,
    onNavigateToProfile: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onNavigateToProfile(entry.pubkeyHex) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.size(20.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "$ordinal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            // Coloured identicon-style dot so it's visually obvious
            // each entry is a *different* pubkey even without metadata.
            ColorDot(entry.pubkeyHex)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shortPubkey(entry.pubkeyHex),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "set ${formatDate(entry.timestampSec)} · block ${entry.blockHeight}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ColorDot(pubkeyHex: String) {
    // Two-tone identicon-ish swatch derived from the pubkey bytes.
    // Anchors on a non-greyscale hue so it's distinguishable from the
    // surrounding chrome regardless of light/dark theme.
    val seed =
        pubkeyHex.take(8).toLongOrNull(16) ?: abs(pubkeyHex.hashCode()).toLong()
    val hue = (seed % 360).toInt().let { if (it < 0) it + 360 else it }
    val color = colorFromHue(hue)
    Surface(
        color = color,
        shape = RoundedCornerShape(50),
        modifier = Modifier.size(20.dp),
    ) {}
}

private fun colorFromHue(hue: Int): Color {
    // Fixed saturation/lightness so the swatches stay legible against
    // both light and dark surface-variants.
    val h = (hue % 360).toFloat()
    val s = 0.55f
    val v = 0.85f
    val c = v * s
    val x = c * (1 - kotlin.math.abs(((h / 60f) % 2) - 1))
    val m = v - c
    val (rp, gp, bp) =
        when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
    return Color(rp + m, gp + m, bp + m, 1f)
}

private fun shortPubkey(hex: String): String = if (hex.length <= 16) hex else "${hex.take(8)}…${hex.takeLast(8)}"

private fun formatDate(epochSeconds: Long?): String {
    if (epochSeconds == null) return "unknown date"
    val date = Instant.ofEpochSecond(epochSeconds).atOffset(ZoneOffset.UTC).toLocalDate()
    return date.format(DateTimeFormatter.ISO_LOCAL_DATE)
}
