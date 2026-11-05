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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
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
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.nip34Git.ui.CodeHighlighter
import com.vitorpamplona.amethyst.commons.nip34Git.ui.gitCard
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_diff_binary
import com.vitorpamplona.amethyst.commons.resources.git_diff_files_changed
import com.vitorpamplona.amethyst.commons.resources.git_diff_split
import com.vitorpamplona.amethyst.commons.resources.git_diff_unified
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import com.vitorpamplona.quartz.nip34Git.patch.CharSpan
import com.vitorpamplona.quartz.nip34Git.patch.GitDiffFile
import com.vitorpamplona.quartz.nip34Git.patch.GitDiffLine
import com.vitorpamplona.quartz.nip34Git.patch.GitDiffLineType
import com.vitorpamplona.quartz.nip34Git.patch.GitFileChange
import com.vitorpamplona.quartz.nip34Git.patch.IntralineDiff
import com.vitorpamplona.quartz.nip34Git.patch.ParsedPatch
import dev.snipme.highlights.model.SyntaxLanguage
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val AddColor = Color(0xFF1F883D)
private val DeleteColor = Color(0xFFCF222E)
private val DiffFontSize = 12.sp
private val DiffLineHeight = 17.sp

// A file with more diff lines than this is shown without syntax highlighting: highlighting runs
// per line as a file opens, and only opened files pay for it.
private const val FILE_HIGHLIGHT_LIMIT = 1500

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
    /** Tapping a line: to comment on it (a review). Null when lines can't be commented on. */
    onLineClick: ((GitDiffFile, GitDiffLine) -> Unit)? = null,
    /** Drawn under each line: the review comments on it, when there are any. */
    lineAnnotations: (@Composable (GitDiffFile, GitDiffLine) -> Unit)? = null,
) {
    if (!parsed.hasDiff) return
    // Side by side needs the width of a large window; a phone keeps the unified view.
    val wide = LocalScreenLayout.current.isLargeScreen
    var split by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { GitDiffStatSummary(parsed) }
            if (wide) {
                FilterChip(selected = !split, onClick = { split = false }, label = { Text(stringResource(Res.string.git_diff_unified)) })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = split, onClick = { split = true }, label = { Text(stringResource(Res.string.git_diff_split)) })
            }
        }
        parsed.files.forEach { file ->
            DiffFileCard(file, split && wide, onLineClick, lineAnnotations)
        }
    }
}

/** "3 files changed +12 -4", the line that heads a diff and stands for it where the diff isn't drawn. */
@Composable
fun GitDiffStatSummary(parsed: ParsedPatch) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val fileCount = parsed.files.size
        Text(
            text = pluralStringResource(Res.plurals.git_diff_files_changed, fileCount, fileCount),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text("+${parsed.totalAdditions}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = AddColor)
        Text("-${parsed.totalDeletions}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = DeleteColor)
    }
}

/** Files with more diff lines than this start folded. */
private const val FOLD_LINES = 200

@Composable
private fun DiffFileCard(
    file: GitDiffFile,
    split: Boolean,
    onLineClick: ((GitDiffFile, GitDiffLine) -> Unit)?,
    lineAnnotations: (@Composable (GitDiffFile, GitDiffLine) -> Unit)?,
) {
    val highlightEnabled = remember(file) { file.hunks.sumOf { it.lines.size } <= FILE_HIGHLIGHT_LIMIT }
    // A long file starts folded, so one generated lockfile doesn't bury the rest of the change.
    var expanded by rememberSaveable(file.displayPath) { mutableStateOf(file.hunks.sumOf { it.lines.size } <= FOLD_LINES) }
    val language = remember(file.displayPath) { CodeHighlighter.languageForFile(file.displayPath) }
    // The app's theme, not the system's: they differ when the user picks one in Settings, and
    // the highlighter paints plain code and punctuation for the background it is told about.
    val darkMode = !MaterialTheme.colorScheme.isLight

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .gitCard(),
    ) {
        // File header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
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
                    text = stringResource(Res.string.git_diff_binary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            } else if (split) {
                val gutterWidth = remember(file) { gutterWidthFor(file) }
                Column(Modifier.fillMaxWidth()) {
                    file.hunks.forEach { hunk ->
                        HunkHeaderRow(hunk.header)
                        val emphasis = remember(hunk) { IntralineDiff.emphasis(hunk.lines) }
                        val rows = remember(hunk) { splitRows(hunk.lines) }
                        rows.forEach { (left, right) ->
                            Row(Modifier.fillMaxWidth()) {
                                SplitCell(file, left, true, emphasis, hunk.lines, gutterWidth, language.takeIf { highlightEnabled }, darkMode, onLineClick, Modifier.weight(1f))
                                SplitCell(file, right, false, emphasis, hunk.lines, gutterWidth, language.takeIf { highlightEnabled }, darkMode, onLineClick, Modifier.weight(1f))
                            }
                            // Comments on either side, under the pair.
                            lineAnnotations?.let { annotate ->
                                left?.let { annotate(file, it) }
                                right?.takeIf { it !== left }?.let { annotate(file, it) }
                            }
                        }
                    }
                }
            } else {
                val gutterWidth = remember(file) { gutterWidthFor(file) }
                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        file.hunks.forEach { hunk ->
                            HunkHeaderRow(hunk.header)
                            val emphasis = remember(hunk) { IntralineDiff.emphasis(hunk.lines) }
                            hunk.lines.forEachIndexed { index, line ->
                                DiffLineRow(
                                    line,
                                    gutterWidth,
                                    language.takeIf { highlightEnabled },
                                    darkMode,
                                    emphasis[index],
                                    onClick = onLineClick?.let { { it(file, line) } },
                                )
                                lineAnnotations?.invoke(file, line)
                            }
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
    onClick: (() -> Unit)? = null,
    showOld: Boolean = true,
    showNew: Boolean = true,
    modifier: Modifier = Modifier,
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
        modifier =
            modifier
                .background(background)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.Top,
    ) {
        if (showOld) LineNumber(line.oldNumber, gutterWidth, numberColor)
        if (showNew) LineNumber(line.newNumber, gutterWidth, numberColor)
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

/**
 * The rows of a side-by-side hunk: an unchanged line on both sides, and each run of removed lines
 * beside the added lines that replace it, padded with blanks where one side is longer.
 */
private fun splitRows(lines: List<GitDiffLine>): List<Pair<GitDiffLine?, GitDiffLine?>> {
    val rows = ArrayList<Pair<GitDiffLine?, GitDiffLine?>>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.type == GitDiffLineType.CONTEXT) {
            rows.add(line to line)
            i++
            continue
        }
        val deleted = ArrayList<GitDiffLine>()
        while (i < lines.size && lines[i].type == GitDiffLineType.DELETE) deleted.add(lines[i++])
        val added = ArrayList<GitDiffLine>()
        while (i < lines.size && lines[i].type == GitDiffLineType.ADD) added.add(lines[i++])
        for (k in 0 until maxOf(deleted.size, added.size)) rows.add(deleted.getOrNull(k) to added.getOrNull(k))
    }
    return rows
}

/** One side of a side-by-side row: the old side shows old numbers, the new side new ones. */
@Composable
private fun SplitCell(
    file: GitDiffFile,
    line: GitDiffLine?,
    oldSide: Boolean,
    emphasis: Map<Int, CharSpan>,
    hunkLines: List<GitDiffLine>,
    gutterWidth: Dp,
    language: SyntaxLanguage?,
    darkMode: Boolean,
    onLineClick: ((GitDiffFile, GitDiffLine) -> Unit)?,
    modifier: Modifier,
) {
    if (line == null) {
        Box(modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)).height(with(LocalDensity.current) { DiffLineHeight.toDp() }))
        return
    }
    val index = hunkLines.indexOf(line)
    // An unchanged line is drawn on both sides; each shows its own number.
    DiffLineRow(
        line,
        gutterWidth,
        language,
        darkMode,
        emphasis[index],
        onClick = onLineClick?.let { { it(file, line) } },
        showOld = oldSide,
        showNew = !oldSide,
        modifier = modifier.clipToBounds(),
    )
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
