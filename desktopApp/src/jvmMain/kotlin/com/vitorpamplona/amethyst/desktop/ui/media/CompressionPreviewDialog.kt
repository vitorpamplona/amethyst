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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.service.upload.ImageReencoder.PassReason

/**
 * Preview-then-publish dialog: every attachment gets a row showing
 * what will actually be uploaded. Re-encoded rows show original-vs-
 * compressed thumbnails with dim/size/savings stats; click a row
 * to open a side-by-side zoom. Pass-through and fail rows render
 * with a badge so the user knows what's being shipped untouched.
 *
 * Built with `Dialog { Surface }` (not AlertDialog) — see the
 * compose-expert review notes in the plan: AlertDialog cramps wide
 * content and the state-isolation trap prevents LaunchedEffect /
 * subscription patterns inside its `text` slot.
 */
@Composable
fun CompressionPreviewDialog(
    items: List<PreviewItem>,
    stripExifSetting: Boolean,
    onPublish: () -> Unit,
    onCancel: () -> Unit,
) {
    var zoomed by remember { mutableStateOf<PreviewItem.Reencoded?>(null) }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.width(820.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Preview & publish",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    summaryLine(items),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.source.canonicalPath }) { item ->
                        when (item) {
                            is PreviewItem.Reencoded ->
                                ReencodedRow(item = item, onZoom = { zoomed = item })
                            is PreviewItem.PassThrough -> PassThroughRow(item = item)
                            is PreviewItem.Failed -> FailedRow(item = item, stripExifSetting = stripExifSetting)
                            is PreviewItem.NonImage -> NonImageRow(item = item)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onPublish) {
                        Text("Publish (${items.size})")
                    }
                }
            }
        }
    }

    zoomed?.let { item ->
        ZoomCompareDialog(item = item, onDismiss = { zoomed = null })
    }
}

// ---------- row composables ----------

@Composable
private fun ReencodedRow(
    item: PreviewItem.Reencoded,
    onZoom: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onZoom() },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Thumbnail(item.source)
            Spacer(Modifier.width(8.dp))
            Text("→", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Thumbnail(item.compressedFile)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.source.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${formatDims(item.originalDims)} → ${formatDims(item.compressedDims)} · ${item.quality.chipLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${formatBytes(item.originalSize)} → ${formatBytes(item.compressedSize)} · saves ${"%.0f".format(item.savings * 100)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Click to compare",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PassThroughRow(item: PreviewItem.PassThrough) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Thumbnail(item.source)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.source.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${formatDims(item.originalDims)} · ${formatBytes(item.originalSize)} · uploaded as-is",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AssistChip(
                onClick = {},
                label = { Text(passReasonBadge(item.reason), style = MaterialTheme.typography.labelSmall) },
                colors = AssistChipDefaults.assistChipColors(),
            )
        }
    }
}

@Composable
private fun FailedRow(
    item: PreviewItem.Failed,
    stripExifSetting: Boolean,
) {
    val isJpeg = item.sourceFormat is com.vitorpamplona.amethyst.commons.service.upload.ImageFormat.Jpeg
    val privacy =
        when {
            stripExifSetting && isJpeg -> "EXIF will be stripped before upload"
            stripExifSetting && !isJpeg -> "metadata may still be present (non-JPEG)"
            else -> "metadata preserved per your settings"
        }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.20f),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Thumbnail(item.source)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.source.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    "Could not compress: ${friendlyFailReason(item.exception)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    "Will send original (${formatBytes(item.originalSize)}) · $privacy",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NonImageRow(item: PreviewItem.NonImage) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.source.extension
                        .uppercase()
                        .take(4),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.source.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    "${formatBytes(item.originalSize)} · uploaded as-is (non-image)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(file: java.io.File) {
    AsyncImage(
        model = file,
        contentDescription = file.name,
        modifier =
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(4.dp)),
        contentScale = ContentScale.Crop,
    )
}

// ---------- zoom sub-dialog ----------

@Composable
private fun ZoomCompareDialog(
    item: PreviewItem.Reencoded,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.width(960.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    item.source.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Saves ${"%.0f".format(item.savings * 100)}% (${formatBytes(item.originalSize)} → ${formatBytes(item.compressedSize)}) at ${item.quality.chipLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ZoomSide(label = "Original", dims = item.originalDims, sizeBytes = item.originalSize, file = item.source)
                    ZoomSide(label = "Compressed", dims = item.compressedDims, sizeBytes = item.compressedSize, file = item.compressedFile)
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ZoomSide(
    label: String,
    dims: Pair<Int, Int>?,
    sizeBytes: Long,
    file: java.io.File,
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "${formatDims(dims)} · ${formatBytes(sizeBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        AsyncImage(
            model = file,
            contentDescription = "$label: ${file.name}",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = ContentScale.Fit,
        )
    }
}

// ---------- helpers ----------

private fun summaryLine(items: List<PreviewItem>): String {
    val reencoded = items.count { it is PreviewItem.Reencoded }
    val passthrough = items.count { it is PreviewItem.PassThrough }
    val failed = items.count { it is PreviewItem.Failed }
    val nonImage = items.count { it is PreviewItem.NonImage }
    val totalSaving =
        items
            .filterIsInstance<PreviewItem.Reencoded>()
            .sumOf { it.originalSize - it.compressedSize }
    val parts = mutableListOf<String>()
    if (reencoded > 0) parts.add("$reencoded reencoded (saves ${formatBytes(totalSaving)})")
    if (passthrough > 0) parts.add("$passthrough pass-through")
    if (failed > 0) parts.add("$failed couldn't be compressed")
    if (nonImage > 0) parts.add("$nonImage non-image")
    return parts.joinToString(" · ")
}

private fun passReasonBadge(reason: PassReason): String =
    when (reason) {
        PassReason.Animated -> "Animated · uploaded as-is"
        PassReason.Vector -> "Vector · uploaded as-is"
        PassReason.BypassByUser -> "Original · uploaded as-is"
    }
