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
    val encryptedMediaRealType by ImageCompressionStore.encryptedMediaRealType.collectAsState()

    Column(modifier = modifier.fillMaxWidth()) {
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
            quality.summary,
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

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Reveal media type on encrypted DM uploads",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Off (recommended): private DM attachments upload as opaque data, so the media " +
                        "server can't tell an image from a video or a voice note. This needs a server " +
                        "that accepts opaque uploads — the default (nostr.download) does.\n\n" +
                        "Turn on only if your media server rejects opaque uploads with a 415 error. " +
                        "It declares the real file type so those servers accept the upload, at the " +
                        "cost of the server seeing the media category (image / video / audio). File " +
                        "contents stay encrypted either way.\n\n" +
                        "Note: some servers (e.g. blossom.band) inspect the bytes and reject the " +
                        "encrypted blob in BOTH modes — they can't host private DM files at all. Use " +
                        "nostr.download for those.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(0.dp))
            Switch(
                checked = encryptedMediaRealType,
                onCheckedChange = ImageCompressionStore::setEncryptedMediaRealType,
            )
        }
    }
}
