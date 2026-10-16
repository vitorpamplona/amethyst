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
package com.vitorpamplona.amethyst.commons.ui.spellcheck

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.service.spellcheck.Misspelling
import com.vitorpamplona.amethyst.commons.service.spellcheck.SpellCheckService
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** The misspellings found in [text], the stored (not drawn) text of the field. */
@Immutable
internal data class CheckedText(
    val text: String,
    val misspellings: List<Misspelling>,
) {
    /**
     * Carries the misspellings across an edit until the next check: those before the edit stay,
     * those after it move with the text, and those it touched are dropped. Keeps the underlines
     * from flickering away while the user types somewhere else in the post.
     */
    fun followEdit(newText: String): CheckedText {
        if (newText == text) return this
        val max = minOf(text.length, newText.length)
        var prefix = 0
        while (prefix < max && text[prefix] == newText[prefix]) prefix++
        var suffix = 0
        while (suffix < max - prefix && text[text.length - 1 - suffix] == newText[newText.length - 1 - suffix]) suffix++
        val oldEditEnd = text.length - suffix
        val delta = newText.length - text.length

        val kept =
            misspellings.mapNotNull { m ->
                when {
                    m.end < prefix -> m
                    m.start > oldEditEnd -> m.copy(start = m.start + delta, end = m.end + delta)
                    else -> null
                }
            }
        return CheckedText(newText, kept)
    }

    companion object {
        val Empty = CheckedText("", emptyList())
    }
}

/**
 * Spell checking for one text field: checks the text after each pause in typing, draws the wavy
 * underlines, and remembers which misspelled word was right-clicked so the context menu can offer
 * its suggestions.
 */
@Stable
class SpellCheckFieldState internal constructor(
    val service: SpellCheckService,
    private val field: TextFieldState,
    private val scope: CoroutineScope,
) {
    internal var checked by mutableStateOf(CheckedText.Empty)

    /** Reads the field's current layout. The getter reads snapshot state, so a draw that calls it redraws on relayout. */
    internal var layout: (() -> TextLayoutResult?)? = null

    internal var mapping: VisualOffsetMapping? = null

    /** The misspelled word under the last right-click, or null when the click missed every one. */
    var menuTarget by mutableStateOf<Misspelling?>(null)
        private set

    /** Suggestions for [menuTarget]; null while they are still being looked up. */
    var menuSuggestions by mutableStateOf<List<String>?>(null)
        private set

    private var replacementsFor: String? = null
    private var replacementsCache: List<TextReplacement> = emptyList()

    internal val onTextLayout: Density.(getResult: () -> TextLayoutResult?) -> Unit = { getResult -> layout = getResult }

    internal suspend fun checkContinuously() {
        combine(snapshotFlow { field.text.toString() }, service.revision) { text, revision -> text to revision }
            .collectLatest { (text, _) ->
                checked = checked.followEdit(text)
                // Wait for a pause in typing; the next keystroke cancels this check.
                delay(TYPING_PAUSE_MS)
                checked = CheckedText(text, service.findMisspellings(text))
            }
    }

    /** The misspellings to draw now, with where each one is drawn. */
    internal fun visibleMisspellings(layout: TextLayoutResult): List<Pair<Misspelling, TextRange>> {
        val current = checked
        val text = field.text
        if (current.text.length != text.length) return emptyList()

        val drawn = layout.layoutInput.text.text
        val replacements = replacements(current.text)
        val selection = field.selection

        return current.misspellings.mapNotNull { m ->
            // Leave the word being typed alone until the cursor moves on.
            if (selection.collapsed && selection.start in m.start..m.end) return@mapNotNull null
            val start = replacements.toVisual(m.start) ?: return@mapNotNull null
            val end = start + (m.end - m.start)
            if (end > drawn.length || !drawn.regionMatches(start, m.word, 0, m.word.length)) return@mapNotNull null
            m to TextRange(start, end)
        }
    }

    internal fun onSecondaryPress(positionInText: Offset) {
        val layout = layout?.invoke()
        val offset = layout?.getOffsetForPosition(positionInText)
        val target =
            if (layout == null || offset == null) {
                null
            } else {
                visibleMisspellings(layout).firstOrNull { (_, range) -> offset in range.start..range.end }?.first
            }

        menuTarget = target
        menuSuggestions = null
        if (target != null) {
            scope.launch {
                val suggestions = service.suggestions(target.word)
                if (menuTarget == target) menuSuggestions = suggestions
            }
        }
    }

    fun replace(
        misspelling: Misspelling,
        replacement: String,
    ) {
        val text = field.text
        if (misspelling.end > text.length || text.substring(misspelling.start, misspelling.end) != misspelling.word) return
        field.edit {
            replace(misspelling.start, misspelling.end, replacement)
            selection = TextRange(misspelling.start + replacement.length)
        }
        menuTarget = null
    }

    fun addToDictionary(misspelling: Misspelling) {
        menuTarget = null
        scope.launch { service.addToDictionary(misspelling.word) }
    }

    private fun replacements(text: String): List<TextReplacement> {
        val mapping = mapping ?: return emptyList()
        if (replacementsFor != text) {
            replacementsCache = mapping.replacements(text)
            replacementsFor = text
        }
        return replacementsCache
    }

    companion object {
        private const val TYPING_PAUSE_MS = 250L
    }
}

/**
 * Spell checking for [field] when the platform offers it, the user has not turned it off, and
 * [enabled] (false for read-only and password-like fields). Null otherwise.
 */
@Composable
fun rememberSpellCheckFieldState(
    field: TextFieldState,
    enabled: Boolean,
    mapping: VisualOffsetMapping?,
): SpellCheckFieldState? {
    val service = LocalAppPlatform.current.spellCheck ?: return null
    val engine by service.activeEngine.collectAsState()
    if (!enabled || engine == null) return null

    val scope = rememberCoroutineScope()
    val state = remember(service, field, scope) { SpellCheckFieldState(service, field, scope) }
    state.mapping = mapping
    LaunchedEffect(state) { state.checkContinuously() }
    return state
}

/**
 * Wraps a text field's inner text (the `innerTextField` a decorator receives) to draw the
 * underlines over it and to notice right-clicks. [scrollState] must be the one given to the
 * `BasicTextField`, because the text inside scrolls under this box.
 */
@Composable
fun SpellCheckedInnerTextField(
    spell: SpellCheckFieldState,
    scrollState: ScrollState,
    singleLine: Boolean,
    innerTextField: @Composable () -> Unit,
) {
    val color = MaterialTheme.colorScheme.error
    Box(
        propagateMinConstraints = true,
        modifier =
            Modifier
                .pointerInput(spell, singleLine) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                val position = event.changes.firstOrNull()?.position ?: continue
                                spell.onSecondaryPress(position + scrollOffset(scrollState, singleLine))
                            }
                        }
                    }
                }.drawWithContent {
                    drawContent()
                    val layout = spell.layout?.invoke() ?: return@drawWithContent
                    val misspellings = spell.visibleMisspellings(layout)
                    if (misspellings.isEmpty()) return@drawWithContent

                    val shift = scrollOffset(scrollState, singleLine)
                    val amplitude = 1.2.dp.toPx()
                    val halfWave = 2.dp.toPx()
                    val gap = 1.5.dp.toPx()
                    val stroke = Stroke(width = 1.dp.toPx())
                    clipRect {
                        translate(-shift.x, -shift.y) {
                            misspellings.forEach { (_, range) ->
                                layout.forEachLineSegment(range) { left, right, baseline ->
                                    drawPath(squiggle(left, right, baseline + gap + amplitude, amplitude, halfWave), color, style = stroke)
                                }
                            }
                        }
                    }
                },
    ) {
        innerTextField()
    }
}

private fun scrollOffset(
    scrollState: ScrollState,
    singleLine: Boolean,
): Offset = if (singleLine) Offset(scrollState.value.toFloat(), 0f) else Offset(0f, scrollState.value.toFloat())

/** Calls [block] with the horizontal extent and baseline of [range] on each line it spans. */
private inline fun TextLayoutResult.forEachLineSegment(
    range: TextRange,
    block: (left: Float, right: Float, baseline: Float) -> Unit,
) {
    if (range.collapsed) return
    val firstLine = getLineForOffset(range.start)
    val lastLine = getLineForOffset(range.end - 1)
    for (line in firstLine..lastLine) {
        val start = maxOf(range.start, getLineStart(line))
        val end = minOf(range.end, getLineEnd(line, visibleEnd = true))
        if (end <= start) continue
        val first = getBoundingBox(start)
        val last = getBoundingBox(end - 1)
        block(minOf(first.left, last.left), maxOf(first.right, last.right), getLineBaseline(line))
    }
}

private fun squiggle(
    left: Float,
    right: Float,
    centerY: Float,
    amplitude: Float,
    halfWave: Float,
): Path =
    Path().apply {
        moveTo(left, centerY)
        var x = left
        var up = true
        while (x < right) {
            val next = minOf(x + halfWave, right)
            quadraticTo((x + next) / 2, if (up) centerY - 2 * amplitude else centerY + 2 * amplitude, next, centerY)
            x = next
            up = !up
        }
    }

/** Context-menu entries for the right-clicked misspelling, ahead of Cut/Copy/Paste. */
@Immutable
data class SpellCheckMenuEntry(
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/**
 * The menu entries for [spell]'s right-clicked word: its suggestions (or [noSuggestionsLabel]),
 * then [addToDictionaryLabel]. Empty when the click was not on a misspelling. Reads snapshot
 * state, so a menu that calls it while open fills in as suggestions arrive.
 */
fun spellCheckMenuEntries(
    spell: SpellCheckFieldState,
    noSuggestionsLabel: String,
    addToDictionaryLabel: String,
): List<SpellCheckMenuEntry> {
    val target = spell.menuTarget ?: return emptyList()
    val suggestions = spell.menuSuggestions
    return buildList {
        when {
            suggestions == null -> {}
            suggestions.isEmpty() -> add(SpellCheckMenuEntry(noSuggestionsLabel, enabled = false) {})
            else -> suggestions.forEach { add(SpellCheckMenuEntry(it, enabled = true) { spell.replace(target, it) }) }
        }
        add(SpellCheckMenuEntry(addToDictionaryLabel, enabled = true) { spell.addToDictionary(target) })
    }
}

/**
 * Puts [spell]'s suggestions in the text field's context menu, where the platform has a menu that
 * takes extra entries (desktop). Elsewhere it only draws [content].
 */
@Composable
expect fun SpellCheckContextMenu(
    spell: SpellCheckFieldState?,
    content: @Composable () -> Unit,
)
