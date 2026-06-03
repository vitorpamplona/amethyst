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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// How long the "All caught up" state lingers before the card collapses away.
private const val ALL_DONE_VISIBLE_MS = 2200L

/**
 * The DM "older history" status card, shown at one protocol's oldest-loaded boundary (rooms list and
 * conversation). It tells the user exactly what the app is reaching for: which protocol, how many
 * relays it is asking, and how far back it has paged. When that protocol runs dry it does NOT just
 * vanish — it crossfades to an "All caught up" state, holds for a beat, then collapses away.
 *
 * @param protocolName human label woven into sentences, e.g. "encrypted" / "legacy".
 * @param protocolTag  short technical tag for the subtitle, e.g. "NIP-17" / "NIP-04".
 * @param reachedBack  epoch seconds of the oldest point reached so far (the deepest `until` cursor).
 */
@Composable
fun DmHistoryLoadingCard(
    protocolName: String,
    protocolTag: String,
    loading: Boolean,
    exhausted: Boolean,
    relayCount: Int,
    reachedBack: Long?,
    modifier: Modifier = Modifier,
) {
    // Once exhausted, show "All caught up" for a beat, then collapse. Reset if it un-exhausts.
    var collapsed by remember { mutableStateOf(false) }
    LaunchedEffect(exhausted) {
        collapsed =
            if (exhausted) {
                delay(ALL_DONE_VISIBLE_MS)
                true
            } else {
                false
            }
    }

    AnimatedVisibility(
        visible = !collapsed,
        modifier = modifier,
        enter = fadeIn(),
        exit = shrinkVertically(tween(400)) + fadeOut(tween(250)),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            tonalElevation = 2.dp,
        ) {
            Crossfade(targetState = exhausted, animationSpec = tween(500), label = "dmHistoryState") { done ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                        if (done) {
                            Text(
                                "✓",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else if (loading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text =
                                if (done) {
                                    stringResource(R.string.chats_history_all_caught_up)
                                } else {
                                    stringResource(R.string.chats_history_older, protocolName)
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text =
                                if (done) {
                                    stringResource(R.string.chats_history_reached_start, protocolName)
                                } else {
                                    historySubtitle(protocolTag, relayCount, reachedBack)
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun historySubtitle(
    protocolTag: String,
    relayCount: Int,
    reachedBack: Long?,
): String {
    // A transient frame can carry loading=true with relayCount=0 (the count updates a beat after the
    // spinner flips, and again as the last relay settles); don't render a nonsensical "0 relays".
    if (relayCount <= 0) return protocolTag
    val backLabel =
        remember(reachedBack) {
            reachedBack?.let { SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(it * 1000)) }
        }
    val relays = pluralStringResource(R.plurals.chats_history_relays, relayCount, relayCount)
    return if (backLabel != null) {
        stringResource(R.string.chats_history_subtitle, protocolTag, relays, backLabel)
    } else {
        stringResource(R.string.chats_history_subtitle_no_date, protocolTag, relays)
    }
}
