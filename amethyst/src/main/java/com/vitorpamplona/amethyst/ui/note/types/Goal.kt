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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.util.showAmount
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteZaps
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.ZapraiserStatus
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeaderBackground
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.fundraiserProgressColor
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.nip75ZapGoals.GoalEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.math.BigDecimal
import kotlin.math.roundToInt

@Composable
fun RenderGoal(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? GoalEvent ?: return

    GoalHeader(noteEvent, note, accountViewModel, nav)
}

@Composable
fun GoalHeader(
    noteEvent: GoalEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val image = noteEvent.image()
    val summary =
        remember(noteEvent) {
            noteEvent.summary()?.ifBlank { null } ?: noteEvent.content.take(200).ifBlank { null }
        }
    val goalAmountMillisats = noteEvent.amount() ?: 0L
    val goalAmountSats = goalAmountMillisats / 1000
    val closedAt = noteEvent.closedAt()
    val isClosed = closedAt != null && closedAt < TimeUtils.now()

    Column(MaterialTheme.colorScheme.replyModifier) {
        image?.let {
            Box {
                MyAsyncImage(
                    imageUrl = it,
                    contentDescription = stringRes(R.string.preview_card_image_for, it),
                    contentScale = ContentScale.FillWidth,
                    mainImageModifier = Modifier.fillMaxWidth(),
                    loadedImageModifier = Modifier,
                    accountViewModel = accountViewModel,
                    onLoadingBackground = { DefaultImageHeaderBackground(note, accountViewModel) },
                    onError = { DefaultImageHeader(note, accountViewModel) },
                )
            }
        }

        Column(Modifier.padding(10.dp)) {
            summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (goalAmountSats > 0) {
                GoalProgressBar(
                    note = note,
                    goalAmountSats = goalAmountSats,
                    accountViewModel = accountViewModel,
                )
            }

            if (isClosed) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringRes(R.string.goal_closed),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        }
    }
}

@Composable
fun GoalProgressBar(
    note: Note,
    goalAmountSats: Long,
    accountViewModel: AccountViewModel,
) {
    val zapsState by observeNoteZaps(note, accountViewModel)

    var zapraiserStatus by
        remember { mutableStateOf(ZapraiserStatus(0F, showAmount(goalAmountSats.toBigDecimal()))) }

    LaunchedEffect(key1 = zapsState) {
        zapsState?.note?.let {
            val newZapAmount = accountViewModel.account.calculateZappedAmount(note)
            var percentage = newZapAmount.div(goalAmountSats.toBigDecimal()).toFloat()
            if (percentage > 1) percentage = 1f

            val left =
                if (percentage > 0.99) {
                    "0"
                } else {
                    showAmount(
                        goalAmountSats.toBigDecimal() * BigDecimal(1.0 - percentage),
                    )
                }
            zapraiserStatus = ZapraiserStatus(percentage, left)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(24.dp),
            color = MaterialTheme.colorScheme.fundraiserProgressColor,
            progress = { zapraiserStatus.progress },
            gapSize = 0.dp,
            strokeCap = StrokeCap.Square,
            drawStopIndicator = {},
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            val totalPercentage by
                remember(zapraiserStatus) {
                    derivedStateOf { "${(zapraiserStatus.progress * 100).roundToInt()}%" }
                }

            Text(
                text =
                    stringRes(
                        R.string.goal_progress,
                        totalPercentage,
                        showAmount(goalAmountSats.toBigDecimal()),
                    ),
                fontSize = Font14SP,
                maxLines = 1,
            )
        }
    }
}
