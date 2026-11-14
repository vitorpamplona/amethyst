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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devtools

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.devtools.EventInspectResult
import com.vitorpamplona.amethyst.commons.devtools.EventInspector
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.clear
import com.vitorpamplona.amethyst.commons.resources.copied_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.copy_to_clipboard
import com.vitorpamplona.amethyst.commons.resources.devtools_paste
import com.vitorpamplona.amethyst.commons.ui.components.rememberShortNotice
import com.vitorpamplona.amethyst.commons.ui.components.util.getText
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The input every dev tool opens with: monospace, multi-line, with paste and clear. */
@Composable
fun DevToolsInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minLines: Int = 2,
    maxLines: Int = 8,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            minLines = minLines,
            maxLines = maxLines,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { scope.launch { clipboard.getText()?.let(onValueChange) } }) {
                Icon(MaterialSymbols.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(stringRes(Res.string.devtools_paste), modifier = Modifier.padding(start = 4.dp))
            }
            if (value.isNotEmpty()) {
                TextButton(onClick = { onValueChange("") }) {
                    Icon(MaterialSymbols.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(stringRes(Res.string.clear), modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

/** A labelled value in monospace that the reader can select or copy in one tap. */
@Composable
fun DevToolsValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = valueColor)
            }
        }
        trailing()
        CopyIconButton(value)
    }
}

@Composable
fun CopyIconButton(value: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val notice = rememberShortNotice()
    IconButton(
        onClick = {
            scope.launch {
                clipboard.setText(value)
                notice.show(loadStringRes(Res.string.copied_to_clipboard))
            }
        },
    ) {
        Icon(
            MaterialSymbols.ContentCopy,
            contentDescription = stringRes(Res.string.copy_to_clipboard),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A block of pretty-printed JSON, scrollable both ways and selectable. */
@Composable
fun DevToolsCodeBlock(
    code: String,
    modifier: Modifier = Modifier,
    maxHeight: Int = 360,
) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
            .padding(10.dp),
    ) {
        SelectionContainer {
            Text(code, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

/** Colours for each JSON token; [jsonColors] picks the set for the current theme. */
@Immutable
data class JsonColors(
    val key: Color,
    val string: Color,
    val number: Color,
    val literal: Color,
    val punctuation: Color,
)

private val LightJsonColors =
    JsonColors(
        key = Color(0xFF7A3E9D),
        string = Color(0xFF2E7D32),
        number = Color(0xFFB35C00),
        literal = Color(0xFF1565C0),
        punctuation = Color(0xFF6B6B6B),
    )

private val DarkJsonColors =
    JsonColors(
        key = Color(0xFFD7A6F2),
        string = Color(0xFF9CCC65),
        number = Color(0xFFFFB74D),
        literal = Color(0xFF64B5F6),
        punctuation = Color(0xFF9E9E9E),
    )

@Composable
fun jsonColors(): JsonColors = if (MaterialTheme.colorScheme.isLight) LightJsonColors else DarkJsonColors

/**
 * Colours [json] token by token: object keys, string values, numbers, `true`/`false`/`null` and
 * punctuation. A lenient single pass: text that is not valid JSON keeps the default colour
 * instead of failing, and whitespace is copied as is, so the layout of a pretty print survives.
 */
fun highlightJson(
    json: String,
    colors: JsonColors,
): AnnotatedString =
    buildAnnotatedString {
        append(json)
        val n = json.length
        var i = 0
        while (i < n) {
            val c = json[i]
            when {
                c == '"' -> {
                    val start = i
                    i++
                    while (i < n && json[i] != '"') {
                        i += if (json[i] == '\\') 2 else 1
                    }
                    i = minOf(i + 1, n)
                    // A string followed by a colon is a key.
                    var next = i
                    while (next < n && json[next].isWhitespace()) next++
                    val color = if (next < n && json[next] == ':') colors.key else colors.string
                    addStyle(SpanStyle(color = color), start, i)
                }

                c == '-' || c.isDigit() -> {
                    val start = i
                    i++
                    while (i < n && (json[i].isDigit() || json[i] in NUMBER_CHARS)) i++
                    addStyle(SpanStyle(color = colors.number), start, i)
                }

                c.isLetter() -> {
                    val start = i
                    while (i < n && json[i].isLetter()) i++
                    addStyle(SpanStyle(color = colors.literal), start, i)
                }

                c in PUNCTUATION -> {
                    addStyle(SpanStyle(color = colors.punctuation), i, i + 1)
                    i++
                }

                else -> {
                    i++
                }
            }
        }
    }

private const val NUMBER_CHARS = ".eE+-"
private const val PUNCTUATION = "{}[]:,"

/** Read-only JSON in monospace with syntax colours, selectable, wrapping long values instead of scrolling sideways. */
@Composable
fun DevToolsJsonBlock(
    json: String,
    modifier: Modifier = Modifier,
) {
    val colors = jsonColors()
    val highlighted = remember(json, colors) { highlightJson(json, colors) }
    Box(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(10.dp),
    ) {
        SelectionContainer {
            Text(
                highlighted,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** A one-line verdict with an icon: the inspector's checks and the converter's warnings. */
@Composable
fun DevToolsCheckRow(
    symbol: MaterialSymbol,
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(symbol, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun DevToolsSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** Past this, pasted text is not written into saved state: Android caps the whole bundle near 1 MB. */
private const val MAX_SAVED_CHARS = 32_000

private val BoundedTextSaver =
    Saver<MutableState<String>, String>(
        save = { state -> state.value.takeIf { it.length <= MAX_SAVED_CHARS } ?: "" },
        restore = { mutableStateOf(it) },
    )

/**
 * [rememberSaveable] for a text field that may hold a pasted event: survives rotation and process
 * death while small, and is dropped rather than risking a TransactionTooLargeException when not.
 */
@Composable
fun rememberSaveableText(initial: () -> String = { "" }): MutableState<String> = rememberSaveable(saver = BoundedTextSaver) { mutableStateOf(initial()) }

private const val INSPECT_DEBOUNCE_MS = 150L

/**
 * Parses, hashes, verifies and pretty-prints [input] off the main thread, after typing pauses: a
 * Schnorr verify plus a pretty print of a long article per keystroke is too much for a frame.
 */
@Composable
fun rememberEventInspection(input: String): State<EventInspectResult> =
    produceState<EventInspectResult>(EventInspectResult.Empty, input) {
        if (value != EventInspectResult.Empty) delay(INSPECT_DEBOUNCE_MS)
        value = withContext(Dispatchers.Default) { EventInspector.inspect(input) }
    }
