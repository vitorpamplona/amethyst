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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Composable
fun openPollsState(accountViewModel: AccountViewModel) =
    remember(accountViewModel) {
        val userPubkeyHex = accountViewModel.account.userProfile().pubkeyHex

        val filter =
            Filter(
                kinds = listOf(PollEvent.KIND, ZapPollEvent.KIND),
                authors = listOf(userPubkeyHex),
            )

        accountViewModel.account.cache
            .observeNotes(filter)
            .map { notes -> filterOpenPolls(notes) }
            .flowOn(Dispatchers.IO)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

private fun filterOpenPolls(notes: List<Note>): List<Note> {
    val now = TimeUtils.now()
    val oneDayInSeconds = TimeUtils.ONE_DAY

    return notes
        .filter { note ->
            val event = note.event ?: return@filter false

            when (event) {
                is PollEvent -> {
                    val endsAt = event.endsAt()
                    if (endsAt != null) endsAt > now else event.createdAt + oneDayInSeconds > now
                }

                is ZapPollEvent -> {
                    val closedAt = event.closedAt()
                    if (closedAt != null) closedAt > now else event.createdAt + oneDayInSeconds > now
                }

                else -> {
                    false
                }
            }
        }.sortedByDescending { it.createdAt() }
}

@Composable
fun OpenPollsSectionHeader() {
    Text(
        text = stringRes(R.string.open_polls),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
