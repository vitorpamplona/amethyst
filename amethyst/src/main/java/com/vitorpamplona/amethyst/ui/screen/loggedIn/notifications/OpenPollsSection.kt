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
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.zapPolls.ZapPollEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun openPollsState(accountViewModel: AccountViewModel) =
    produceState(initialValue = emptyList<Note>(), accountViewModel) {
        value =
            withContext(Dispatchers.IO) {
                findOpenPolls(accountViewModel)
            }
    }

private fun findOpenPolls(accountViewModel: AccountViewModel): List<Note> {
    val userPubkeyHex = accountViewModel.account.userProfile().pubkeyHex
    val now = TimeUtils.now()
    val oneDayInSeconds = TimeUtils.ONE_DAY

    val openPolls = mutableListOf<Note>()

    LocalCache.notes.forEach { _, note ->
        val event = note.event ?: return@forEach

        when (event) {
            is PollEvent -> {
                if (event.pubKey == userPubkeyHex) {
                    val endsAt = event.endsAt()
                    val isOpen =
                        if (endsAt != null) {
                            endsAt > now
                        } else {
                            event.createdAt + oneDayInSeconds > now
                        }
                    if (isOpen) {
                        openPolls.add(note)
                    }
                }
            }

            is ZapPollEvent -> {
                if (event.pubKey == userPubkeyHex) {
                    val closedAt = event.closedAt()
                    val isOpen =
                        if (closedAt != null) {
                            closedAt > now
                        } else {
                            event.createdAt + oneDayInSeconds > now
                        }
                    if (isOpen) {
                        openPolls.add(note)
                    }
                }
            }
        }
    }

    return openPolls.sortedByDescending { it.createdAt() }
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
