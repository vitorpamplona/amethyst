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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.privateDM.header

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.nip56Reports.UserReportWarningState
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.ui.note.types.reportTypeLabels
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey

/**
 * Warns that the person on the other end of a 1:1 DM has been reported by someone the user follows.
 *
 * Collapsed it states the count and, when the reports agree on one reason, that reason. The reporters
 * themselves stay behind a tap so an accusation is visible only when the user asks for it. Group rooms
 * render nothing — the warning names one person.
 */
@Composable
fun DmReportWarningCard(
    room: ChatroomKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val counterpartHex = room.users.singleOrNull() ?: return

    LoadUser(baseUserHex = counterpartHex, accountViewModel = accountViewModel) { user ->
        if (user != null) {
            val flow = remember(user) { accountViewModel.createUserReportWarningFlow(user) }
            val state by flow.collectAsStateWithLifecycle()

            key(counterpartHex) {
                // Hoisted above the `shouldWarn` check so it survives the transient SILENT state the
                // flow emits on ~20s of backgrounding (`WhileSubscribed(10000, 10000)` +
                // lifecycle-driven unsubscribe at ON_STOP) rather than being lost when this
                // `remember` group briefly leaves composition.
                var dismissed by remember { mutableStateOf(false) }

                if (state.shouldWarn && !dismissed) {
                    ReportWarningBody(state, accountViewModel, nav) { dismissed = true }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportWarningBody(
    state: UserReportWarningState,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDismiss: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val typeLabels = reportTypeLabels(state.types)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = reportWarningHeadline(state),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )

            // The reason gets its own line rather than being spliced into the sentence above, so no
            // translated noun has to agree with a surrounding grammar. Shown collapsed only when the
            // reports agree; when they disagree the full list appears on expand.
            if (typeLabels.size == 1) {
                Text(
                    text = typeLabels.first(),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (expanded) {
                FlowRow(modifier = Modifier.padding(top = 10.dp)) {
                    state.reports.forEach { report ->
                        NoteAuthorPicture(
                            baseNote = report,
                            size = Size35dp,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }

                if (typeLabels.size > 1) {
                    Text(
                        text = typeLabels.joinToString(", "),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(top = 5.dp),
                        textAlign = TextAlign.Start,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { expanded = !expanded },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                ) {
                    Text(text = stringRes(R.string.dm_sender_reported_who))
                }
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                ) {
                    Text(text = stringRes(R.string.dm_sender_reported_dismiss))
                }
            }
        }
    }
}

/** "Reported by 2 people you follow". The reason, if any, is rendered separately. */
@Composable
private fun reportWarningHeadline(state: UserReportWarningState): String =
    pluralStringResource(
        R.plurals.dm_sender_reported,
        state.reporterCount,
        state.reporterCount,
    )

/**
 * The headline followed by the reasons as separate sentences, for the chat-list row's screen reader
 * label — where there is no second line to put them on. Sentence-separated rather than spliced, for
 * the same translation reason the headline avoids interpolation.
 */
@Composable
fun reportWarningContentDescription(state: UserReportWarningState): String {
    val headline = reportWarningHeadline(state)
    val typeLabels = reportTypeLabels(state.types)

    return remember(headline, typeLabels) {
        if (typeLabels.isEmpty()) headline else headline + ". " + typeLabels.joinToString(", ")
    }
}
