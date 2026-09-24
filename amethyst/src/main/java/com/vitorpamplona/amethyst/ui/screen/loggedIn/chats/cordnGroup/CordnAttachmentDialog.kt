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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.cordnGroup

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.ui.stringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the picker handed over, resolved enough to show it before it is sent. */
internal data class PickedAttachment(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
)

/**
 * The confirmation step between picking a file and sending it.
 *
 * Attaching used to be irreversible the instant the picker closed: no preview, no
 * caption, and no way back if you picked the wrong file — which matters more here than
 * in a public chat, because a cordn attachment is encrypted, uploaded and announced to
 * the room in one action that cannot be recalled.
 *
 * The caption rides as the message's own content, so an attachment with a caption is
 * one message rather than two.
 */
@Composable
internal fun CordnAttachmentDialog(
    uri: Uri,
    sending: Boolean,
    onDismiss: () -> Unit,
    onSend: (picked: PickedAttachment, caption: String) -> Unit,
) {
    val context = LocalContext.current
    var caption by remember(uri) { mutableStateOf("") }

    // Resolving the name is a binder call into a DocumentsProvider, which is not
    // something to do on the frame that opens the dialog.
    val picked by
        produceState<PickedAttachment?>(null, uri) {
            value = withContext(Dispatchers.IO) { resolvePickedAttachment(context, uri) }
        }
    val resolved = picked

    AlertDialog(
        // A dismiss mid-upload would leave the dialog's own progress unobservable
        // while the work carried on, so it only closes when it is idle.
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(stringRes(R.string.cordn_attachment_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (resolved != null) AttachmentPreview(resolved)

                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    enabled = !sending,
                    placeholder = { Text(stringRes(R.string.cordn_attachment_caption_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (sending) {
                    // Indeterminate on purpose: the upload is one suspend call that
                    // encrypts and posts, and it reports nothing in between. A bar
                    // that invented a percentage would be a lie about what is known.
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringRes(R.string.cordn_attachment_uploading),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { resolved?.let { onSend(it, caption) } },
                enabled = !sending && resolved != null,
            ) {
                Text(stringRes(R.string.cordn_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !sending) {
                Text(stringRes(Res.string.cancel))
            }
        },
    )
}

/** A thumbnail for an image, the name and type for everything else. */
@Composable
private fun AttachmentPreview(picked: PickedAttachment) {
    val context = LocalContext.current

    val thumbnail by
        produceState<ImageBitmap?>(null, picked.uri) {
            value =
                if (picked.mimeType.startsWith("image/")) {
                    withContext(Dispatchers.IO) { decodeThumbnail(context, picked.uri) }
                } else {
                    null
                }
        }

    val shown = thumbnail
    if (shown != null) {
        Image(
            bitmap = shown,
            contentDescription = picked.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
        )
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = picked.displayName,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = picked.mimeType,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** Longest edge of the preview bitmap. A dialog never needs the full image. */
private const val THUMBNAIL_MAX_EDGE = 1024

/**
 * Decodes [uri] small enough to preview.
 *
 * Bounds first, then a subsampled decode: a modern phone photo decoded at full size is
 * tens of megabytes, and this runs to draw something a few hundred pixels tall.
 */
private fun decodeThumbnail(
    context: Context,
    uri: Uri,
): ImageBitmap? =
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        var sample = 1
        while (bounds.outWidth / sample > THUMBNAIL_MAX_EDGE || bounds.outHeight / sample > THUMBNAIL_MAX_EDGE) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver
            .openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, options) }
            ?.asImageBitmap()
    }.getOrNull()

/**
 * The file's real name and type.
 *
 * `uri.lastPathSegment` is a document id on a `content://` URI, not a name — it is what
 * the room used to show for every attachment, and what the dialog would otherwise put
 * in front of the person confirming the send.
 */
internal fun resolvePickedAttachment(
    context: Context,
    uri: Uri,
): PickedAttachment {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: OPAQUE_MIME

    val name =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        }.getOrNull()
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: FALLBACK_NAME

    return PickedAttachment(uri, name, mime)
}

private const val OPAQUE_MIME = "application/octet-stream"
private const val FALLBACK_NAME = "file"
