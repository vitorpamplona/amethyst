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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotPolls
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_change_vote
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_closed
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_closes_at
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_multiple_choice_hint
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_no_votes
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_not_anonymous
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_participants
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_single_choice_hint
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_unsupported
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_view_votes
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_vote
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_votes_title
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** True for a kind-1068 poll that lives in a Marmot group. */
fun isMarmotPoll(
    note: Note,
    accountViewModel: AccountViewModel,
): Boolean = note.event?.kind == MarmotPolls.KIND_POLL && accountViewModel.account.marmotGroupList.groupIdForNote(note.idHex) != null

/**
 * An encrypted NIP-88 poll in a Marmot group: question, options with live
 * counts, the reader's own vote, and the deadline.
 *
 * The tally is MDK's ([MarmotPolls.project]), recomputed from every kind-1018
 * the cache has linked to this poll, so this client and White Noise show the
 * same numbers for the same group. A poll that breaks the profile shows the
 * unsupported state, never its question as if it could be answered.
 */
@Composable
fun RenderMarmotPoll(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event ?: return
    val definition = remember(event) { MarmotPolls.parsePoll(event) }
    if (definition == null) {
        Text(
            text = stringRes(Res.string.marmot_poll_unsupported),
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val groupId = remember(note) { accountViewModel.account.marmotGroupList.groupIdForNote(note.idHex) }
    val tally by note.pollState().responses.collectAsStateWithLifecycle()
    val myPubKey = accountViewModel.account.signer.pubKey
    val projection =
        remember(tally, definition) {
            MarmotPolls.project(event, definition, tally.allResponses.mapNotNull { it.event }, myPubKey)
        }

    // `open` is a function of the clock, so a poll left on screen closes itself
    // at its deadline instead of waiting for another message to recompose it.
    val now by
        produceState(TimeUtils.now(), definition.endsAt) {
            val endsAt = definition.endsAt ?: return@produceState
            while (value <= endsAt) {
                delay(((endsAt - value + 1) * 1000).coerceIn(1_000, 60_000))
                value = TimeUtils.now()
            }
        }
    val isOpen = definition.isOpen(now)

    var selection by remember(projection.localSelection) { mutableStateOf(projection.localSelection.toSet()) }
    var showVotes by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Text(definition.question, fontWeight = FontWeight.Bold)
        Text(
            text =
                stringRes(
                    if (definition.type == MarmotPolls.PollType.SINGLE_CHOICE) {
                        Res.string.marmot_poll_single_choice_hint
                    } else {
                        Res.string.marmot_poll_multiple_choice_hint
                    },
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val total = projection.participants.coerceAtLeast(1)
        projection.options.forEach { option ->
            val chosen = option.id in selection
            Column(
                modifier =
                    Modifier.fillMaxWidth().clickable(enabled = isOpen) {
                        selection =
                            if (definition.type == MarmotPolls.PollType.SINGLE_CHOICE) {
                                setOf(option.id)
                            } else if (chosen) {
                                selection - option.id
                            } else {
                                selection + option.id
                            }
                    },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (definition.type == MarmotPolls.PollType.SINGLE_CHOICE) {
                        RadioButton(selected = chosen, onClick = null, enabled = isOpen)
                    } else {
                        Checkbox(checked = chosen, onCheckedChange = null, enabled = isOpen)
                    }
                    Text(option.label, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    Text(option.votes.toString(), style = MaterialTheme.typography.labelMedium)
                }
                LinearProgressIndicator(
                    progress = { option.votes.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                pluralStringRes(Res.plurals.marmot_poll_participants, projection.participants, projection.participants),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            val endsAt = definition.endsAt
            Text(
                text =
                    when {
                        !isOpen -> stringRes(Res.string.marmot_poll_closed)
                        endsAt != null -> stringRes(Res.string.marmot_poll_closes_at, formatDateTime(endsAt * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.SHORT))
                        else -> ""
                    },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text(
            stringRes(Res.string.marmot_poll_not_anonymous),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Sent in the poll's own option order, so the same choice is always the same tags.
            val optionIds = definition.options.map { it.id }
            val selectionList = optionIds.filter { it in selection }
            val canVote =
                isOpen &&
                    groupId != null &&
                    selection != projection.localSelection.toSet() &&
                    MarmotPolls.isValidSelection(definition.type, optionIds, selectionList)
            if (isOpen) {
                FilledTonalButton(
                    enabled = canVote,
                    onClick = {
                        val id = groupId ?: return@FilledTonalButton
                        scope.launch { accountViewModel.castMarmotPollVote(id, event, selectionList) }
                    },
                ) {
                    Text(stringRes(if (projection.localSelection.isEmpty()) Res.string.marmot_poll_vote else Res.string.marmot_poll_change_vote))
                }
            }
            TextButton(onClick = { showVotes = true }) { Text(stringRes(Res.string.marmot_poll_view_votes)) }
        }
    }

    if (showVotes) {
        MarmotPollVotesDialog(projection, accountViewModel) { showVotes = false }
    }
}

/** Who chose what: every member's effective vote, the same set the counts above are summed from. */
@Composable
private fun MarmotPollVotesDialog(
    projection: MarmotPolls.Projection,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    val labels = remember(projection) { projection.options.associate { it.id to it.label } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.marmot_poll_votes_title)) },
        text = {
            if (projection.votes.isEmpty()) {
                Text(stringRes(Res.string.marmot_poll_no_votes))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(projection.votes, key = { it.voter }) { vote ->
                        Column {
                            Text(observeUserNameByHex(vote.voter, accountViewModel), fontWeight = FontWeight.Medium)
                            Text(
                                vote.optionIds.mapNotNull { labels[it] }.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.close)) } },
    )
}
