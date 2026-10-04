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
package com.vitorpamplona.amethyst.commons.observer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.observer.ObserverPress
import com.vitorpamplona.amethyst.commons.observer.ObserverReadiness
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.observer_cancel
import com.vitorpamplona.amethyst.commons.resources.observer_dismiss
import com.vitorpamplona.amethyst.commons.resources.observer_failed
import com.vitorpamplona.amethyst.commons.resources.observer_no_rank_service
import com.vitorpamplona.amethyst.commons.resources.observer_no_score_list
import com.vitorpamplona.amethyst.commons.resources.observer_not_projected
import com.vitorpamplona.amethyst.commons.resources.observer_printing
import com.vitorpamplona.amethyst.commons.resources.observer_read
import com.vitorpamplona.amethyst.commons.resources.observer_ready
import com.vitorpamplona.amethyst.commons.resources.observer_relay_silent
import com.vitorpamplona.amethyst.commons.resources.observer_step_bylines
import com.vitorpamplona.amethyst.commons.resources.observer_step_checking_lens
import com.vitorpamplona.amethyst.commons.resources.observer_step_desks
import com.vitorpamplona.amethyst.commons.resources.observer_step_layout
import com.vitorpamplona.amethyst.commons.resources.observer_step_signals
import com.vitorpamplona.amethyst.commons.ui.stringRes
import org.jetbrains.compose.resources.StringResource

/**
 * The floating "printing your paper" popup — the Observer's counterpart to the
 * proof-of-work mining banner. It follows the reader around the app while the
 * press runs and turns into a "your paper is ready" card with a Read button
 * when it is done, so a long print never holds a screen hostage.
 *
 * Hidden while the reader is on the paper itself ([ObserverPress.readerIsLooking]),
 * which shows the same progress inline.
 */
@Composable
fun ObserverPressBanner(
    state: ObserverPress.State,
    readerIsLooking: Boolean,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible =
        !readerIsLooking &&
            when (state) {
                is ObserverPress.State.Printing -> true
                is ObserverPress.State.Ready -> !state.seen
                is ObserverPress.State.Failed, is ObserverPress.State.NoLens -> true
                ObserverPress.State.Idle -> false
            }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(200)),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(150)),
        modifier = modifier,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).animateContentSize()) {
                when (state) {
                    is ObserverPress.State.Printing -> {
                        BannerRow(
                            text = stringRes(Res.string.observer_printing),
                            pulsing = true,
                            trailing = { CloseButton(stringRes(Res.string.observer_cancel), onCancel) },
                        )
                        ObserverPrintingProgress(state, Modifier.padding(start = 26.dp, bottom = 4.dp))
                    }

                    is ObserverPress.State.Ready -> {
                        BannerRow(
                            text = stringRes(Res.string.observer_ready),
                            pulsing = false,
                            trailing = {
                                TextButton(onClick = onOpen) { Text(stringRes(Res.string.observer_read)) }
                                CloseButton(stringRes(Res.string.observer_dismiss), onDismiss)
                            },
                        )
                    }

                    is ObserverPress.State.NoLens -> {
                        BannerRow(
                            text = stringRes(noLensMessage(state.reason)),
                            pulsing = false,
                            maxLines = 3,
                            trailing = { CloseButton(stringRes(Res.string.observer_dismiss), onDismiss) },
                        )
                    }

                    is ObserverPress.State.Failed -> {
                        BannerRow(
                            text = stringRes(Res.string.observer_failed),
                            pulsing = false,
                            maxLines = 2,
                            trailing = { CloseButton(stringRes(Res.string.observer_dismiss), onDismiss) },
                        )
                    }

                    ObserverPress.State.Idle -> {}
                }
            }
        }
    }
}

@Composable
private fun BannerRow(
    text: String,
    pulsing: Boolean,
    maxLines: Int = 1,
    trailing: @Composable () -> Unit,
) {
    // Printing has a predictable bar, but the pulse is what says "the app is working right now".
    val alpha =
        if (pulsing) {
            val pulse = rememberInfiniteTransition(label = "observerPulse")
            val value by pulse.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
                label = "observerAlpha",
            )
            value
        } else {
            1f
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            symbol = MaterialSymbols.News,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
private fun CloseButton(
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(22.dp)) {
        Icon(
            symbol = MaterialSymbols.Close,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
}

/** The step the press is on, and a bar that fills over the desks — they are most of the wait. */
@Composable
fun ObserverPrintingProgress(
    state: ObserverPress.State.Printing,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text =
                when (state.step) {
                    ObserverPress.Step.CHECKING_LENS -> stringRes(Res.string.observer_step_checking_lens)
                    ObserverPress.Step.READING_DESKS -> stringRes(Res.string.observer_step_desks, state.desksDone, state.desksTotal)
                    ObserverPress.Step.READING_SIGNALS -> stringRes(Res.string.observer_step_signals)
                    ObserverPress.Step.READING_BYLINES -> stringRes(Res.string.observer_step_bylines)
                    ObserverPress.Step.LAYING_OUT -> stringRes(Res.string.observer_step_layout)
                },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { state.fraction },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

fun noLensMessage(reason: ObserverReadiness.State): StringResource =
    when (reason) {
        ObserverReadiness.State.NO_SCORE_LIST -> Res.string.observer_no_score_list
        ObserverReadiness.State.NO_RANK_SERVICE -> Res.string.observer_no_rank_service
        ObserverReadiness.State.NOT_PROJECTED -> Res.string.observer_not_projected
        ObserverReadiness.State.RELAY_SILENT -> Res.string.observer_relay_silent
        // READY never reaches NoLens; listed so the mapping stays exhaustive.
        ObserverReadiness.State.READY -> Res.string.observer_relay_silent
    }
