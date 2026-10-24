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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.marmotGroup.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotPolls
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_add_option
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_allow_multiple
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_create
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_deadline_days
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_deadline_hours
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_has_deadline
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_invalid
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_no_deadline
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_not_anonymous
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_option
import com.vitorpamplona.amethyst.commons.resources.marmot_poll_question
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.launch

/** Deadlines offered by the dialog, in seconds; MDK caps a poll at 30 days. */
private val DEADLINES = listOf(null, 3_600L, 24 * 3_600L, 7 * 24 * 3_600L, MarmotPolls.MAX_LIFETIME_SECONDS)

/**
 * Create an encrypted NIP-88 poll in a Marmot group: a question, 2-10 options,
 * single or multiple choice, and an optional deadline of at most 30 days. Text
 * is trimmed before it is checked, since MDK refuses padded labels outright.
 */
@Composable
fun CreateMarmotPollDialog(
    nostrGroupId: HexKey,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var multiple by remember { mutableStateOf(false) }
    var deadline by remember { mutableStateOf<Long?>(null) }
    var invalid by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.marmot_poll_create)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text(stringRes(Res.string.marmot_poll_question)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                options.forEachIndexed { index, value ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { options[index] = it },
                        label = { Text(stringRes(Res.string.marmot_poll_option, index + 1)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (options.size < MarmotPolls.MAX_OPTIONS) {
                    TextButton(onClick = { options.add("") }) { Text(stringRes(Res.string.marmot_poll_add_option)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringRes(Res.string.marmot_poll_allow_multiple), modifier = Modifier.weight(1f))
                    Switch(checked = multiple, onCheckedChange = { multiple = it })
                }
                Text(stringRes(Res.string.marmot_poll_has_deadline), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DEADLINES.forEach { seconds ->
                        FilterChip(
                            selected = deadline == seconds,
                            onClick = { deadline = seconds },
                            label = {
                                Text(
                                    when {
                                        seconds == null -> stringRes(Res.string.marmot_poll_no_deadline)
                                        seconds < 24 * 3_600L -> stringRes(Res.string.marmot_poll_deadline_hours, (seconds / 3_600L).toInt())
                                        else -> stringRes(Res.string.marmot_poll_deadline_days, (seconds / (24 * 3_600L)).toInt())
                                    },
                                )
                            },
                        )
                    }
                }
                Text(
                    stringRes(Res.string.marmot_poll_not_anonymous),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (invalid) {
                    Text(stringRes(Res.string.marmot_poll_invalid), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val labels = options.map { it.trim() }.filter { it.isNotEmpty() }
                    val type = if (multiple) MarmotPolls.PollType.MULTIPLE_CHOICE else MarmotPolls.PollType.SINGLE_CHOICE
                    val endsAt = deadline?.let { TimeUtils.now() + it }
                    scope.launch {
                        if (accountViewModel.createMarmotPoll(nostrGroupId, question.trim(), labels, type, endsAt)) {
                            onDismiss()
                        } else {
                            invalid = true
                        }
                    }
                },
            ) { Text(stringRes(Res.string.marmot_poll_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.cancel)) } },
    )
}
