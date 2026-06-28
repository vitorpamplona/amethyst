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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.code.CodeHighlighter
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip34Git.patch.CharSpan
import com.vitorpamplona.quartz.nip34Git.patch.GitDiffFile
import com.vitorpamplona.quartz.nip34Git.patch.GitDiffLine
import com.vitorpamplona.quartz.nip34Git.patch.GitDiffLineType
import com.vitorpamplona.quartz.nip34Git.patch.GitFileChange
import com.vitorpamplona.quartz.nip34Git.patch.IntralineDiff
import com.vitorpamplona.quartz.nip34Git.patch.ParsedPatch
import dev.snipme.highlights.model.SyntaxLanguage

private val AddColor = Color(0xFF1F883D)
private val DeleteColor = Color(0xFFCF222E)
private val DiffFontSize = 12.sp
private val DiffLineHeight = 17.sp

// Above this many diff lines we skip per-line syntax highlighting to stay snappy.
private const val HIGHLIGHT_LINE_BUDGET = 600

/**
 * Renders a parsed [ParsedPatch] as a GitHub-style file-by-file diff: a stat
 * summary, then one collapsible card per file with +/- line coloring, old/new
 * line numbers, and (for reasonably sized diffs) syntax highlighting reused from
 * the repository code browser.
 */
@Composable
fun GitDiffView(
    parsed: ParsedPatch,
    modifier: Modifier = Modifier,
) {
    if (!parsed.hasDiff) return
    val highlightEnabled =
        remember(parsed) { parsed.files.sumOf { f -> f.hunks.sumOf { it.lines.size } } <= HIGHLIGHT_LINE_BUDGET }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DiffStatSummary(parsed)
        parsed.files.forEach { file ->
            DiffFileCard(file, highlightEnabled)
        }
    }
}

@Composable
private fun DiffStatSummary(parsed: ParsedPatch) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val fileCount = parsed.files.size
        Text(
            text = pluralStringResource(R.plurals.git_diff_files_changed, fileCount, fileCount),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text("+${parsed.totalAdditions}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = AddColor)
        Text("-${parsed.totalDeletions}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = DeleteColor)
    }
}

@Composable
private fun DiffFileCard(
    file: GitDiffFile,
    highlightEnabled: Boolean,
) {
    var expanded by rememberSaveable(file.displayPath) { mutableStateOf(true) }
    val language = remember(file.displayPath) { CodeHighlighter.languageForFile(file.displayPath) }
    val darkMode = isSystemInDarkTheme()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface),
    ) {
        // File header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val (symbol, tint) = changeBadge(file.change)
            Icon(symbol = symbol, contentDescription = null, modifier = Modifier.size(16.dp), tint = tint)
            Text(
                text = file.displayPath,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (file.additions > 0) Text("+${file.additions}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = AddColor)
            if (file.deletions > 0) Text("-${file.deletions}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = DeleteColor)
            Icon(
                symbol = if (expanded) MaterialSymbols.KeyboardArrowUp else MaterialSymbols.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (expanded) {
            if (file.isBinary) {
                Text(
                    text = stringRes(R.string.git_diff_binary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            } else {
                val gutterWidth = remember(file) { gutterWidthFor(file) }
                Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    file.hunks.forEach { hunk ->
                        HunkHeaderRow(hunk.header)
                        val emphasis = remember(hunk) { IntralineDiff.emphasis(hunk.lines) }
                        hunk.lines.forEachIndexed { index, line ->
                            DiffLineRow(line, gutterWidth, language.takeIf { highlightEnabled }, darkMode, emphasis[index])
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HunkHeaderRow(header: String) {
    Text(
        text = header,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        fontSize = DiffFontSize,
        lineHeight = DiffLineHeight,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        maxLines = 1,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun DiffLineRow(
    line: GitDiffLine,
    gutterWidth: Dp,
    language: SyntaxLanguage?,
    darkMode: Boolean,
    emphasis: CharSpan?,
) {
    val background =
        when (line.type) {
            GitDiffLineType.ADD -> AddColor.copy(alpha = 0.12f)
            GitDiffLineType.DELETE -> DeleteColor.copy(alpha = 0.12f)
            GitDiffLineType.CONTEXT -> Color.Transparent
        }
    val emphasisColor =
        when (line.type) {
            GitDiffLineType.ADD -> AddColor.copy(alpha = 0.30f)
            GitDiffLineType.DELETE -> DeleteColor.copy(alpha = 0.30f)
            GitDiffLineType.CONTEXT -> Color.Transparent
        }
    val marker =
        when (line.type) {
            GitDiffLineType.ADD -> "+"
            GitDiffLineType.DELETE -> "-"
            GitDiffLineType.CONTEXT -> " "
        }
    val markerColor =
        when (line.type) {
            GitDiffLineType.ADD -> AddColor
            GitDiffLineType.DELETE -> DeleteColor
            GitDiffLineType.CONTEXT -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        }
    val numberColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

    val content =
        remember(line.content, language, darkMode, emphasis) {
            val base =
                if (language != null && line.content.isNotEmpty()) {
                    CodeHighlighter.highlight(line.content, language, darkMode)
                } else {
                    AnnotatedString(line.content)
                }
            if (emphasis != null && !emphasis.isEmpty) {
                buildAnnotatedString {
                    append(base)
                    addStyle(
                        SpanStyle(background = emphasisColor),
                        emphasis.start.coerceIn(0, line.content.length),
                        emphasis.end.coerceIn(0, line.content.length),
                    )
                }
            } else {
                base
            }
        }

    Row(
        modifier = Modifier.background(background),
        verticalAlignment = Alignment.Top,
    ) {
        LineNumber(line.oldNumber, gutterWidth, numberColor)
        LineNumber(line.newNumber, gutterWidth, numberColor)
        Text(
            text = marker,
            fontFamily = FontFamily.Monospace,
            fontSize = DiffFontSize,
            lineHeight = DiffLineHeight,
            color = markerColor,
            modifier = Modifier.padding(start = 4.dp),
        )
        Text(
            text = content,
            fontFamily = FontFamily.Monospace,
            fontSize = DiffFontSize,
            lineHeight = DiffLineHeight,
            softWrap = false,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, end = 12.dp),
        )
    }
}

@Composable
private fun LineNumber(
    number: Int?,
    width: Dp,
    color: Color,
) {
    Text(
        text = number?.toString() ?: "",
        fontFamily = FontFamily.Monospace,
        fontSize = DiffFontSize,
        lineHeight = DiffLineHeight,
        color = color,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.width(width).padding(horizontal = 4.dp),
    )
}

@Composable
private fun changeBadge(change: GitFileChange): Pair<MaterialSymbol, Color> =
    when (change) {
        GitFileChange.ADD -> MaterialSymbols.AddCircle to AddColor
        GitFileChange.DELETE -> MaterialSymbols.Cancel to DeleteColor
        GitFileChange.RENAME -> MaterialSymbols.AltRoute to MaterialTheme.colorScheme.primary
        GitFileChange.MODIFY -> MaterialSymbols.Edit to MaterialTheme.colorScheme.primary
    }

private fun gutterWidthFor(file: GitDiffFile): Dp {
    val maxLine =
        file.hunks.maxOfOrNull { hunk ->
            hunk.lines.maxOfOrNull { maxOf(it.oldNumber ?: 0, it.newNumber ?: 0) } ?: 0
        } ?: 0
    val digits = maxLine.toString().length.coerceAtLeast(2)
    return (digits * 8 + 8).dp
}
