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
package com.vitorpamplona.amethyst.ui.navigation.topbars

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.MemorySnapshot
import com.vitorpamplona.amethyst.collectMemorySnapshot
import com.vitorpamplona.amethyst.isDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun MemoryUsageChip() {
    if (!isDebug) return

    val context = LocalContext.current
    var showDetail by remember { mutableStateOf(false) }

    val snapshot by produceState<MemorySnapshot?>(null) {
        while (true) {
            // collectMemorySnapshot reads coil3.disk.DiskLruCache.size(), which is @Synchronized and
            // contends with the disk cache's own journal I/O. On cold start that lock is held by a
            // background worker for seconds (initial journal read + the burst of image writes), so
            // running this on the produceState default (main) dispatcher froze the UI thread —
            // the "Loading account" frame couldn't repaint until size() returned. Collect off-main.
            value = withContext(Dispatchers.IO) { collectMemorySnapshot(context) }
            delay(2_000)
        }
    }

    val s = snapshot ?: return

    val chipColor =
        when {
            s.heapFraction > 0.80f -> Color(0xFFE53935) // red
            s.heapFraction > 0.60f -> Color(0xFFFFA000) // amber
            else -> Color(0xFF43A047) // green
        }

    Text(
        text = "${s.heapUsedMb}/${s.heapMaxMb}MB",
        color = chipColor,
        style = MaterialTheme.typography.labelSmall,
        modifier =
            Modifier
                .padding(horizontal = 4.dp)
                .clickable { showDetail = true },
    )

    if (showDetail) {
        MemoryDetailDialog(snapshot = s, onDismiss = { showDetail = false })
    }
}

@Composable
private fun MemoryDetailDialog(
    snapshot: MemorySnapshot,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Memory Usage") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Device class: ${snapshot.memoryClassMb} MB")
                Text("JVM heap: ${snapshot.heapUsedMb} / ${snapshot.heapMaxMb} MB")
                Text("Native heap: ${snapshot.nativeHeapUsedMb} MB")
                Text("Coil memory: ${snapshot.imageCacheUsedMb} / ${snapshot.imageCacheMaxMb} MB")
                Text("Coil disk: ${snapshot.imageDiskUsedMb} / ${snapshot.imageDiskMaxMb} MB")
                Text("Notes: ${snapshot.noteCount}")
                Text("Users: ${snapshot.userCount}")
                Text("Addressables: ${snapshot.addressableCount}")
                Text("Chatrooms: ${snapshot.chatroomCount}")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}
