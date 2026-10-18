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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.emojipacks.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.emoji_import_added
import com.vitorpamplona.amethyst.commons.resources.emoji_import_choose_files
import com.vitorpamplona.amethyst.commons.resources.emoji_import_explainer
import com.vitorpamplona.amethyst.commons.resources.emoji_import_failed
import com.vitorpamplona.amethyst.commons.resources.emoji_import_reading
import com.vitorpamplona.amethyst.commons.resources.emoji_import_skipped
import com.vitorpamplona.amethyst.commons.resources.emoji_import_title
import com.vitorpamplona.amethyst.commons.resources.emoji_import_too_large
import com.vitorpamplona.amethyst.commons.resources.emoji_import_unnamed
import com.vitorpamplona.amethyst.commons.resources.emoji_import_unreadable
import com.vitorpamplona.amethyst.commons.resources.emoji_import_unsupported
import com.vitorpamplona.amethyst.commons.resources.emoji_import_uploading
import com.vitorpamplona.amethyst.commons.resources.emoji_private_explainer
import com.vitorpamplona.amethyst.commons.resources.emoji_private_toggle
import com.vitorpamplona.amethyst.commons.resources.emoji_public_explainer
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.MultiDocumentSelect
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import org.jetbrains.compose.resources.StringResource

private val EMOJI_IMPORT_MIME_TYPES = listOf("image/*", "application/zip", "application/x-zip-compressed")

/**
 * Bulk import into one of the user's packs: pick images and .zip archives, watch the uploads,
 * then read what was added and what was left out. The progress and the result live in
 * [viewModel], so they outlast a recomposition of the screen.
 */
@Composable
fun ImportEmojisDialog(
    viewModel: EmojiPackViewModel,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    var isPrivate by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val progress = viewModel.importProgress
    val result = viewModel.importResult

    if (picking) {
        MultiDocumentSelect(EMOJI_IMPORT_MIME_TYPES) { files ->
            picking = false
            if (files.isNotEmpty()) {
                accountViewModel.launchSigner {
                    viewModel.importFiles(files, accountViewModel.host.mediaUploader, isPrivate)
                }
            }
        }
    }

    val close = {
        viewModel.clearImportResult()
        onDismiss()
    }

    AlertDialog(
        // Leaving mid-import would hide the progress, not stop the uploads.
        onDismissRequest = { if (progress == null) close() },
        title = { Text(text = stringRes(Res.string.emoji_import_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    progress != null -> ImportProgress(progress)
                    result != null -> ImportSummary(result)
                    else -> ImportOptions(isPrivate) { isPrivate = it }
                }
            }
        },
        confirmButton = {
            when {
                progress != null -> {}
                result != null -> Button(onClick = close) { Text(stringRes(Res.string.close)) }
                else -> Button(onClick = { picking = true }) { Text(stringRes(Res.string.emoji_import_choose_files)) }
            }
        },
        dismissButton = {
            if (progress == null && result == null) {
                TextButton(onClick = close) { Text(stringRes(Res.string.cancel)) }
            }
        },
    )
}

@Composable
private fun ImportOptions(
    isPrivate: Boolean,
    onPrivateChange: (Boolean) -> Unit,
) {
    Text(
        text = stringRes(Res.string.emoji_import_explainer),
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = if (isPrivate) MaterialSymbols.Lock else MaterialSymbols.LockOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringRes(Res.string.emoji_private_toggle),
            modifier = Modifier.weight(1f),
        )
        Switch(checked = isPrivate, onCheckedChange = onPrivateChange)
    }
    Text(
        text = stringRes(if (isPrivate) Res.string.emoji_private_explainer else Res.string.emoji_public_explainer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ImportProgress(progress: EmojiImportProgress) {
    if (progress.total == 0) {
        Text(stringRes(Res.string.emoji_import_reading))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    } else {
        Text(stringRes(Res.string.emoji_import_uploading, minOf(progress.done + 1, progress.total), progress.total))
        LinearProgressIndicator(
            progress = { progress.done.toFloat() / progress.total },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ImportSummary(result: EmojiImportResult) {
    Text(
        text = pluralStringRes(Res.plurals.emoji_import_added, result.added, result.added),
        style = MaterialTheme.typography.titleMedium,
    )
    SummaryLine(Res.string.emoji_import_skipped, result.skipped.map { ":$it:" })
    SummaryLine(Res.string.emoji_import_failed, result.failed)
    if (result.failed.isNotEmpty() && result.failureReason != null) {
        Text(
            text = result.failureReason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    SummaryLine(Res.string.emoji_import_unnamed, result.unnamed)
    SummaryLine(Res.string.emoji_import_too_large, result.tooLarge)
    SummaryLine(Res.string.emoji_import_unsupported, result.unsupported)
    SummaryLine(Res.string.emoji_import_unreadable, result.unreadable)
}

@Composable
private fun SummaryLine(
    label: StringResource,
    names: List<String>,
) {
    if (names.isEmpty()) return
    Text(
        text = stringRes(label, names.joinToString(", ")),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
