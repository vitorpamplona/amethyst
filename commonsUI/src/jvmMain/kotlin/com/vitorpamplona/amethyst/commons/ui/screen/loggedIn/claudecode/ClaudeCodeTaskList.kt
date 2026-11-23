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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.claudecode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.claudecode.AgentTask
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.claude_code_tasks
import com.vitorpamplona.amethyst.commons.resources.claude_code_tasks_collapse
import com.vitorpamplona.amethyst.commons.resources.claude_code_tasks_expand
import com.vitorpamplona.amethyst.commons.resources.claude_code_tasks_progress
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText

/**
 * The plan the agent keeps with its task tools, above the composer: what is done, what it is on
 * now, what is left. Folds to its header line so it doesn't crowd a short window.
 */
@Composable
internal fun AgentTaskList(
    tasks: List<AgentTask>,
    /** Whether a turn runs: the task in progress spins only while the agent works on it. */
    working: Boolean,
    chatKey: String,
) {
    var expanded by rememberSaveable(chatKey) { mutableStateOf(true) }
    val done = tasks.count { it.status == AgentTask.Status.COMPLETED }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = CardPadding, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(MaterialSymbols.Checklist, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(InlineGap))
                Text(stringRes(Res.string.claude_code_tasks), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(InlineGap))
                Text(
                    stringRes(Res.string.claude_code_tasks_progress, done, tasks.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (expanded) MaterialSymbols.ExpandMore else MaterialSymbols.ExpandLess,
                    contentDescription = stringRes(if (expanded) Res.string.claude_code_tasks_collapse else Res.string.claude_code_tasks_expand),
                    tint = MaterialTheme.colorScheme.grayText,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (expanded) {
                Column(
                    Modifier
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(start = CardPadding, end = CardPadding, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(InlineGap),
                ) {
                    tasks.forEach { TaskRow(it, working) }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: AgentTask,
    working: Boolean,
) {
    val completed = task.status == AgentTask.Status.COMPLETED
    val inProgress = task.status == AgentTask.Status.IN_PROGRESS
    Row(verticalAlignment = Alignment.Top) {
        // The icon sits on the first line of the subject, which may wrap.
        Row(Modifier.padding(top = 2.dp).size(18.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                completed -> Icon(MaterialSymbols.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, filled = true, modifier = Modifier.size(18.dp))
                inProgress && working -> CircularProgressIndicator(Modifier.padding(2.dp).size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                inProgress -> Icon(MaterialSymbols.RadioButtonChecked, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                else -> Icon(MaterialSymbols.RadioButtonUnchecked, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.subject,
                style = MaterialTheme.typography.bodyMedium,
                color = if (completed) MaterialTheme.colorScheme.grayText else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // What it is doing right now, when that says more than the subject.
            task.activeForm?.takeIf { inProgress && it != task.subject }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
