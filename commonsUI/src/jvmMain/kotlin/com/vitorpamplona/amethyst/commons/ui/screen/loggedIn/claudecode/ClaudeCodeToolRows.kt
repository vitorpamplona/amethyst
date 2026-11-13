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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.claudecode.ChatItem
import com.vitorpamplona.amethyst.commons.claudecode.ToolKind
import com.vitorpamplona.amethyst.commons.claudecode.ToolView
import com.vitorpamplona.amethyst.commons.claudecode.ToolViews
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_from_line
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_input
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_lines
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_output
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_search_in
import com.vitorpamplona.amethyst.commons.ui.note.GitDiffView
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip34Git.patch.ParsedPatch
import com.vitorpamplona.quartz.nip34Git.patch.UnifiedDiffParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * A call's [ToolView], worked out off the main thread: placing an edit reads its file. Null
 * until it is ready.
 */
@Composable
internal fun rememberToolView(
    name: String,
    input: String,
    cwd: String,
    pending: Boolean = false,
): ToolView? {
    val view by produceState<ToolView?>(null, name, input, cwd, pending) {
        value = withContext(Dispatchers.IO) { ToolViews.of(name, input, File(cwd), pending) }
    }
    return view
}

@Composable
internal fun rememberToolView(
    name: String,
    input: JsonObject,
    cwd: String,
    pending: Boolean,
): ToolView? {
    val view by produceState<ToolView?>(null, name, input, cwd, pending) {
        value = withContext(Dispatchers.IO) { ToolViews.of(name, input, File(cwd), pending) }
    }
    return view
}

/** A file change drawn the way the Changes screen draws it: the file's card, with its diff. */
@Composable
internal fun FileChangeDiff(
    change: ToolView.FileChange,
    modifier: Modifier = Modifier,
) {
    val parsed: ParsedPatch? by produceState<ParsedPatch?>(null, change.diff) {
        value = withContext(Dispatchers.Default) { UnifiedDiffParser.parse(change.diff) }
    }
    parsed?.let { GitDiffView(it, modifier, showSummary = false) }
}

/** What a call acts on, in one line, with paths from the session's folder. */
@Composable
internal fun toolLabel(
    view: ToolView?,
    call: ChatItem.ToolCall,
): String =
    when (view) {
        is ToolView.Command -> view.command.lineSequence().first()
        is ToolView.FileRead ->
            when {
                view.fromLine != null && view.toLine != null -> view.path + "  " + stringRes(Res.string.claude_code_tool_lines, view.fromLine!!, view.toLine!!)
                view.fromLine != null -> view.path + "  " + stringRes(Res.string.claude_code_tool_from_line, view.fromLine!!)
                else -> view.path
            }
        is ToolView.Search -> view.scope?.let { stringRes(Res.string.claude_code_tool_search_in, view.pattern, it) } ?: view.pattern
        is ToolView.Web -> view.target
        is ToolView.FileChange -> view.path
        is ToolView.Raw, null -> call.summary ?: ""
    }

/**
 * An edit or a write, on its own row: the file's diff, as it landed. A failed one says why
 * instead, since nothing changed.
 */
@Composable
internal fun FileChangeRow(
    call: ChatItem.ToolCall,
    cwd: String,
    turnRunning: Boolean,
) {
    // Until it runs, the change is the approval card's to show; here it is one line. Once it
    // ran, the view is worked out again, placed by its new text in the file as it is now.
    val view = rememberToolView(call.name, call.input, cwd, pending = !call.isDone)
    val change = view as? ToolView.FileChange
    if (change != null && call.isDone && !call.isError) {
        FileChangeDiff(change)
    } else {
        ToolCallRow(call, cwd, turnRunning)
    }
}

/** One tool call: what it did in a line; opened, what came of it. */
@Composable
internal fun ToolCallRow(
    call: ChatItem.ToolCall,
    cwd: String,
    turnRunning: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    val view = rememberToolView(call.name, call.input, cwd, pending = !call.isDone)
    Column {
        ToolLine(
            symbol = ToolKind.of(call.name).symbol(),
            text = listOf(call.name, toolLabel(view, call)).filter { it.isNotEmpty() }.joinToString("  "),
            running = turnRunning && !call.isDone,
            error = call.isError,
            expanded = expanded,
            monospace = true,
            onClick = { expanded = !expanded },
        )
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 26.dp, top = 2.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ToolBody(view, call)
            }
        }
    }
}

/** A call opened: a command and its output, a file's lines, or (for a tool the app doesn't know) its input. */
@Composable
private fun ToolBody(
    view: ToolView?,
    call: ChatItem.ToolCall,
) {
    val resultColor = if (call.isError) MaterialTheme.colorScheme.error else Color.Unspecified
    when (view) {
        is ToolView.Command -> {
            view.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText) }
            CodeBlock("$ " + view.command)
            call.result?.takeIf { it.isNotBlank() }?.let { CodeBlock(it.take(RESULT_SHOWN), resultColor) }
        }
        is ToolView.FileChange -> {
            if (!call.isError) FileChangeDiff(view)
            call.result?.takeIf { call.isError }?.let { CodeBlock(it.take(RESULT_SHOWN), resultColor) }
        }
        is ToolView.FileRead, is ToolView.Search, is ToolView.Web ->
            call.result?.takeIf { it.isNotBlank() }?.let { CodeBlock(it.take(RESULT_SHOWN), resultColor) }
        is ToolView.Raw, null -> {
            ToolSectionLabel(stringRes(Res.string.claude_code_tool_input))
            CodeBlock(call.input)
            call.result?.let {
                ToolSectionLabel(stringRes(Res.string.claude_code_tool_output))
                CodeBlock(it.take(RESULT_SHOWN), resultColor)
            }
        }
    }
}

@Composable
private fun ToolSectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.grayText)
}

internal fun ToolKind.symbol(): MaterialSymbol =
    when (this) {
        ToolKind.COMMAND -> MaterialSymbols.Code
        ToolKind.READ -> MaterialSymbols.Visibility
        ToolKind.EDIT -> MaterialSymbols.Edit
        ToolKind.SEARCH -> MaterialSymbols.Search
        ToolKind.WEB -> MaterialSymbols.Language
        ToolKind.AGENT -> MaterialSymbols.Assistant
        ToolKind.OTHER -> MaterialSymbols.Apps
    }

@Composable
internal fun ToolLine(
    symbol: MaterialSymbol,
    text: String,
    running: Boolean,
    error: Boolean,
    expanded: Boolean,
    monospace: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.grayText
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (running) {
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.primary)
            } else {
                Icon(if (error) MaterialSymbols.Error else symbol, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.placeholderText,
            modifier = Modifier.size(16.dp),
        )
    }
}

private const val RESULT_SHOWN = 20_000
