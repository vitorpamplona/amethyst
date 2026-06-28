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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo.code

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.ui.components.RichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip34Git.git.GitRepoSnapshot
import com.vitorpamplona.quartz.nip34Git.git.GitTreeEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Renders a single file from the repository. Markdown files render as rich text,
 * text files render with syntax highlighting, and binary files show a notice.
 */
@Composable
fun GitFileViewer(
    snapshot: GitRepoSnapshot,
    viewModel: GitRepositoryBrowserViewModel,
    entry: GitTreeEntry,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val result by
        produceState<Result<ByteArray>?>(null, entry.oid) {
            value =
                try {
                    Result.success(viewModel.readBlob(snapshot, entry.oid))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
        }

    val bytes = result
    when {
        bytes == null -> CenteredMessage(stringRes(R.string.git_repo_code_loading), modifier)
        bytes.isFailure -> CenteredMessage(stringRes(R.string.git_repo_file_load_error), modifier)
        else -> {
            val data = bytes.getOrThrow()
            when {
                isProbablyBinary(data) ->
                    CenteredMessage(stringRes(R.string.git_repo_binary_file, humanSize(data.size)), modifier)
                isMarkdownFile(entry.name) ->
                    MarkdownFile(data.decodeToString(), accountViewModel, nav, modifier)
                else ->
                    HighlightedCode(data.decodeToString(), entry.name, modifier)
            }
        }
    }
}

@Composable
private fun MarkdownFile(
    content: String,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    val backgroundColor = remember { mutableStateOf(background) }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        RichTextViewer(
            content = content,
            canPreview = true,
            quotesLeft = 1,
            modifier = Modifier.fillMaxWidth(),
            tags = EmptyTagList,
            backgroundColor = backgroundColor,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun HighlightedCode(
    code: String,
    fileName: String,
    modifier: Modifier,
) {
    val darkMode = isSystemInDarkTheme()
    val annotated by
        produceState(AnnotatedString(code), code, fileName, darkMode) {
            value =
                withContext(Dispatchers.Default) {
                    val language = CodeHighlighter.languageForFile(fileName)
                    if (language == null) AnnotatedString(code) else CodeHighlighter.highlight(code, language, darkMode)
                }
        }

    Text(
        text = annotated,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        softWrap = false,
        color = MaterialTheme.colorScheme.onBackground,
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun CenteredMessage(
    text: String,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}

private fun isMarkdownFile(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext == "md" || ext == "markdown" || ext == "mdown" || ext == "mkd"
}

/** Heuristic: a NUL byte in the first chunk means the content isn't text. */
private fun isProbablyBinary(data: ByteArray): Boolean {
    val limit = minOf(data.size, 8000)
    for (i in 0 until limit) if (data[i].toInt() == 0) return true
    return false
}

private fun humanSize(bytes: Int): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
