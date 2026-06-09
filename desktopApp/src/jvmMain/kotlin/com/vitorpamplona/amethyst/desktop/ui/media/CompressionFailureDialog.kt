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
package com.vitorpamplona.amethyst.desktop.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.commons.service.upload.CompressionException
import com.vitorpamplona.amethyst.commons.service.upload.ImageFormat
import java.io.File

/**
 * Captured failure for an attachment that the reencoder refused or
 * could not process. The dialog renders one row per failure.
 */
data class FailedAttachment(
    val file: File,
    val exception: CompressionException,
    val originalBytes: Long,
    val sourceFormat: ImageFormat,
)

/**
 * Fail-loud dialog: never silently downgrade. When one or more
 * attachments could not be compressed, the dialog shows the file
 * names + reasons + original byte counts and lets the user choose
 * between "Send Original" (upload raw bytes — still EXIF-stripped
 * for JPEG when the setting is on) and "Cancel post."
 *
 * Built as `Dialog { Surface }` rather than `AlertDialog` because:
 *   - The body is a LazyColumn that grows with N failures; AlertDialog's
 *     `text` slot is a single column with fixed width constraints.
 *   - `LaunchedEffect` and `produceState` don't fire inside
 *     AlertDialog's `text` slot (see `custom-feeds-alertdialog.md`),
 *     leaving a path for future per-row actions (e.g., per-file
 *     retry) that AlertDialog would block.
 */
@Composable
fun CompressionFailureDialog(
    failures: List<FailedAttachment>,
    stripExifOnByPass: Boolean,
    onSendOriginal: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.width(560.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    if (failures.size == 1) {
                        "1 image could not be compressed"
                    } else {
                        "${failures.size} images could not be compressed"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "You can send the original file(s) without compression, " +
                        "or cancel and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                ) {
                    items(failures, key = { it.file.canonicalPath }) { failure ->
                        FailureRow(failure = failure, stripExifOnByPass = stripExifOnByPass)
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onCancel) {
                        Text("Cancel post")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onSendOriginal) {
                        Text(
                            if (failures.size == 1) {
                                "Send original"
                            } else {
                                "Send originals (${failures.size})"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FailureRow(
    failure: FailedAttachment,
    stripExifOnByPass: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            failure.file.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            "Could not compress: ${friendlyReason(failure.exception)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            buildString {
                append("Original: ").append(formatBytes(failure.originalBytes))
                append(" • ").append(privacyHint(failure, stripExifOnByPass))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun friendlyReason(e: CompressionException): String =
    when (e) {
        is CompressionException.UnsupportedFormat -> "format not supported (${e.format})"
        is CompressionException.InputTooLarge -> "image is larger than ${e.limit / 1_000_000} megapixels"
        is CompressionException.EncodeFailed -> "encoder error (${e.cause?.message ?: e.message})"
    }

private fun privacyHint(
    failure: FailedAttachment,
    stripExifOnByPass: Boolean,
): String {
    val isJpeg = failure.sourceFormat is ImageFormat.Jpeg
    return when {
        stripExifOnByPass && isJpeg -> "EXIF will be stripped before upload"
        stripExifOnByPass && !isJpeg -> "metadata may still be present (non-JPEG)"
        else -> "metadata preserved per your settings"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
    return "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
