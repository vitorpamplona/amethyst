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
package com.vitorpamplona.amethyst.desktop.ui.chats

import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.desktop.service.media.EncryptedMediaService
import com.vitorpamplona.quartz.nip17Dm.files.ChatMessageEncryptedFileHeaderEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import org.jetbrains.skia.Image as SkiaImage

@Composable
fun ChatFileAttachment(
    event: ChatMessageEncryptedFileHeaderEvent,
    onForward: ((ChatMessageEncryptedFileHeaderEvent) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val url = event.url()
    val mimeType = event.mimeType()
    val keyBytes = event.key()
    val nonce = event.nonce()
    val isImage = mimeType?.startsWith("image/") == true
    val scope = rememberCoroutineScope()

    var decryptedImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var decryptedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }

    // Auto-decrypt images
    LaunchedEffect(url) {
        if (keyBytes != null && nonce != null) {
            isLoading = true
            try {
                val bytes = EncryptedMediaService.downloadAndDecrypt(url, keyBytes, nonce)
                decryptedBytes = bytes
                if (isImage) {
                    withContext(Dispatchers.Default) {
                        val skImage = SkiaImage.makeFromEncoded(bytes)
                        decryptedImage = skImage.toComposeImageBitmap()
                    }
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                isLoading = false
            }
        }
    }

    Box {
        Card(
            modifier =
                modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                // Right-click detection handled via awaiting press
                                // Context menu is shown on secondary button via the other modifier
                            },
                        )
                    }.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.buttons.isSecondaryPressed &&
                                    event.changes.any { it.pressed && !it.previousPressed }
                                ) {
                                    showContextMenu = true
                                }
                            }
                        }
                    },
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                // Encryption indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        MaterialSymbols.Lock,
                        contentDescription = "Encrypted",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Encrypted file",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(4.dp))

                when {
                    isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally),
                        )
                    }

                    decryptedImage != null -> {
                        androidx.compose.foundation.Image(
                            bitmap = decryptedImage!!,
                            contentDescription = "Encrypted image",
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.FillWidth,
                        )
                    }

                    error != null -> {
                        Text(
                            "Failed to decrypt: $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    else -> {
                        // Non-image file
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                MaterialSymbols.AutoMirrored.InsertDriveFile,
                                contentDescription = "File",
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    mimeType ?: "Unknown file",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                event.size()?.let { size ->
                                    Text(
                                        "${size / 1024}KB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Right-click context menu
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text("Save to disk") },
                leadingIcon = {
                    Icon(MaterialSymbols.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                enabled = decryptedBytes != null,
                onClick = {
                    showContextMenu = false
                    scope.launch {
                        saveDecryptedFile(decryptedBytes!!, mimeType, event.hash())
                    }
                },
            )
            if (onForward != null) {
                DropdownMenuItem(
                    text = { Text("Forward") },
                    leadingIcon = {
                        Icon(MaterialSymbols.AutoMirrored.Forward, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    onClick = {
                        showContextMenu = false
                        onForward(event)
                    },
                )
            }
        }
    }
}

/**
 * Save decrypted bytes to disk via a native save dialog.
 */
private suspend fun saveDecryptedFile(
    bytes: ByteArray,
    mimeType: String?,
    hash: String?,
) {
    val extension = mimeTypeToExtension(mimeType)
    val suggestedName = "${hash?.take(12) ?: "file"}.$extension"

    val file =
        withContext(Dispatchers.Main) {
            val dialog =
                FileDialog(null as Frame?, "Save Decrypted File", FileDialog.SAVE).apply {
                    this.file = suggestedName
                }
            dialog.isVisible = true
            val dir = dialog.directory ?: return@withContext null
            File(dir, dialog.file ?: return@withContext null)
        } ?: return

    withContext(Dispatchers.IO) {
        file.writeBytes(bytes)
    }
}

private fun mimeTypeToExtension(mimeType: String?): String =
    when (mimeType) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/svg+xml" -> "svg"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        "audio/mpeg" -> "mp3"
        "audio/ogg" -> "ogg"
        "audio/wav" -> "wav"
        "audio/flac" -> "flac"
        else -> "bin"
    }
