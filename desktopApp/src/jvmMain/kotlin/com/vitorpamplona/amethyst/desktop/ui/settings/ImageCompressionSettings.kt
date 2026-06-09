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
package com.vitorpamplona.amethyst.desktop.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.service.upload.CompressionQuality
import com.vitorpamplona.amethyst.desktop.ImageCompressionStore

/**
 * Settings panel for image upload compression — quality preset +
 * strip-EXIF toggle. Backed by [ImageCompressionStore]; changes
 * persist to [com.vitorpamplona.amethyst.desktop.DesktopPreferences]
 * immediately.
 *
 * Per-post overrides (in the compose dialog) read the current
 * default from this store but do not write back.
 */
@Composable
fun ImageCompressionSettings(modifier: Modifier = Modifier) {
    val quality by ImageCompressionStore.quality.collectAsState()
    val stripExif by ImageCompressionStore.stripExif.collectAsState()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Image Compression",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))

        Text(
            "Default quality",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow {
            CompressionQuality.entries.forEachIndexed { index, preset ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = CompressionQuality.entries.size),
                    onClick = { ImageCompressionStore.setQuality(preset) },
                    selected = preset == quality,
                ) {
                    Text(preset.displayName)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            qualityHint(quality),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Strip metadata before upload",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Removes camera, GPS, and timestamp data from uploaded photos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(0.dp))
            Switch(
                checked = stripExif,
                onCheckedChange = ImageCompressionStore::setStripExif,
            )
        }
    }
}

private fun qualityHint(quality: CompressionQuality): String =
    when (quality) {
        CompressionQuality.LOW -> "Smallest files — long edge max 640 px. Best for slow uplinks."
        CompressionQuality.MEDIUM -> "Balanced — long edge max 640 px, moderate quality."
        CompressionQuality.DESKTOP_HIGH -> "Good quality for desktop — long edge max 1920 px."
    }
