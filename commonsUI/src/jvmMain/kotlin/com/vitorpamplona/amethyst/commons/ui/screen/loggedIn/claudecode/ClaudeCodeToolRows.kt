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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.claudecode.ChatItem
import com.vitorpamplona.amethyst.commons.claudecode.PublishedArtifacts
import com.vitorpamplona.amethyst.commons.claudecode.ToolKind
import com.vitorpamplona.amethyst.commons.claudecode.ToolView
import com.vitorpamplona.amethyst.commons.claudecode.ToolViews
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.claude_code_subagent_running
import com.vitorpamplona.amethyst.commons.resources.claude_code_subagent_steps
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_from_line
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_input
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_lines
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_output
import com.vitorpamplona.amethyst.commons.resources.claude_code_tool_search_in
import com.vitorpamplona.amethyst.commons.ui.markdown.RenderMarkdown
import com.vitorpamplona.amethyst.commons.ui.note.GitDiffView
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.onSurface65
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
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
    // A row scrolled back into view starts from the view already made, not from nothing.
    val view by produceState(ToolViews.peek(name, input, File(cwd), pending), name, input, cwd, pending) {
        value = withContext(Dispatchers.IO) { ToolViews.cached(name, input, File(cwd), pending) }
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
        value = withContext(Dispatchers.IO) { ToolViews.cached(name, input, File(cwd), pending) }
    }
    return view
}

/** A file change drawn the way the Changes screen draws it: the file's card, with its diff. */
@Composable
internal fun FileChangeDiff(
    change: ToolView.FileChange,
    modifier: Modifier = Modifier,
    startFolded: Boolean = false,
) {
    // Parsed when the view was made, off the main thread (ToolViews.cached).
    GitDiffView(change.patch, modifier, showSummary = false, startFolded = startFolded)
}

/** A tool's name for the chat: an in-app tool's own name (`search settings`), not `mcp__server__tool`. */
internal fun displayName(toolName: String): String = if (toolName.startsWith("mcp__")) toolName.substringAfterLast("__").replace('_', ' ') else toolName

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
        // An in-app tool's arguments, plainly: `theme DARK`.
        is ToolView.Raw, null ->
            if (call.name.startsWith("mcp__")) {
                ToolViews.argumentsLine(call.input)
            } else {
                call.summary ?: ""
            }
    }

/**
 * An edit or a write, on its own row: the file's card, folded, with its diff as it landed. A failed one says why
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
        // A page Claude wrote (HTML, SVG) is looked at in the browser, not only read as a diff.
        val page = rememberLocalPage(change.path, cwd)
        Column {
            // Folded to the file's card: a turn of edits reads as a list of files, each opened on a click.
            FileChangeDiff(change, startFolded = true)
            page?.let { OpenPageButton(it) }
        }
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
    // A subagent: how far it got, and whether it still runs after its call returned.
    val steps = call.subagent.count { it is ChatItem.ToolCall }
    val stepsLabel = if (steps > 0) pluralStringRes(Res.plurals.claude_code_subagent_steps, steps, steps) else ""
    val background = if (call.subagentRunning) stringRes(Res.string.claude_code_subagent_running) else ""
    Column {
        ToolLine(
            symbol = ToolKind.of(call.name).symbol(),
            text = listOf(displayName(call.name), toolLabel(view, call), stepsLabel, background).filter { it.isNotEmpty() }.joinToString("  "),
            running = (turnRunning && !call.isDone) || call.subagentRunning,
            error = call.isError,
            expanded = expanded,
            onClick = { expanded = !expanded },
        )
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 26.dp, top = 2.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (call.subagent.isEmpty()) ToolBody(view, call) else SubagentBody(call, cwd, turnRunning || call.subagentRunning)
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

/** What a subagent did, in order: what it said, and each of its calls, opened like the chat's own. */
@Composable
private fun SubagentBody(
    call: ChatItem.ToolCall,
    cwd: String,
    running: Boolean,
) {
    // No navigation down here: a PR number still opens on GitHub, a word hashtag does nothing.
    val onLink = rememberChatLinks(nav = null)
    val style = rememberChatMarkdownStyle()
    val codeChips = rememberChatCodeChips()
    call.subagent.forEach { item ->
        when (item) {
            is ChatItem.ToolCall -> {
                // A page a subagent published gets its card too: it is as much the session's as the chat's own.
                val published = remember(item) { PublishedArtifacts.of(item)?.takeIf { !item.isError } }
                if (published != null) {
                    ArtifactCard(item, rememberTitled(published, cwd), cwd, running)
                } else {
                    ToolCallRow(item, cwd, running)
                }
            }
            is ChatItem.AssistantText -> RenderMarkdown(item.text, onLink, Modifier.fillMaxWidth().padding(vertical = 2.dp), style = style, textStyle = MaterialTheme.typography.bodySmall, blockComposer = codeChips)
            else -> {}
        }
    }
    // Its final answer is the call's result, once the subagent is done.
    call.result?.takeIf { !call.subagentRunning && call.subagent.none { it is ChatItem.AssistantText } }?.let { CodeBlock(it.take(RESULT_SHOWN)) }
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
        ToolKind.SETTINGS -> MaterialSymbols.Settings
        ToolKind.PLAN -> MaterialSymbols.Checklist
        ToolKind.QUESTION -> MaterialSymbols.Forum
        ToolKind.OTHER -> MaterialSymbols.Apps
    }

@Composable
internal fun ToolLine(
    symbol: MaterialSymbol,
    text: String,
    running: Boolean,
    error: Boolean,
    expanded: Boolean,
    /** Said after [text] in the error's color: how many of a group's calls failed. */
    errorSuffix: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            // Less below than above: the reply's first line brings its own ascent.
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Dark enough to read as text (about 5:1 on the surface), still a step under the reply.
        val tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface65
        val style = MaterialTheme.typography.bodyMedium
        // The icons' middles sit half an x-height over the label's baseline, level with its
        // lowercase. Centred on the line box instead, they line up with the capitals and look high.
        val halfXHeight = with(LocalDensity.current) { (style.fontSize * HALF_X_HEIGHT).roundToPx() }
        val onLowercase = Modifier.alignBy { it.measuredHeight / 2 + halfXHeight }
        Box(onLowercase.size(16.dp), contentAlignment = Alignment.Center) {
            if (running) {
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = MaterialTheme.colorScheme.primary)
            } else {
                Icon(if (error) MaterialSymbols.Error else symbol, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = style,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Not filling: the chevron follows the label instead of sitting at the far edge.
            modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
        )
        errorSuffix?.let {
            Text(" · $it", style = style, color = MaterialTheme.colorScheme.error, maxLines = 1, modifier = Modifier.alignByBaseline())
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.placeholderText,
            modifier = onLowercase.size(16.dp),
        )
    }
}

/** Half a sans-serif face's x-height, as a share of its size (about 0.53 em in the app's faces). */
private const val HALF_X_HEIGHT = 0.27f

internal const val RESULT_SHOWN = 20_000
