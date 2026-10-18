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
package com.vitorpamplona.amethyst.desktop.ui.qrcode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.vitorpamplona.amethyst.commons.qrcode.ScannedPayload
import com.vitorpamplona.amethyst.commons.qrcode.classifyScannedPayload
import com.vitorpamplona.amethyst.commons.qrcode.ui.ScanOutcomeSheet
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_clipboard_empty
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_desktop_hint
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_desktop_title
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_no_code_in_image
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_paste
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_scan_image
import com.vitorpamplona.amethyst.commons.ui.components.util.setText
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.ScanOutcome
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.File
import java.io.FilenameFilter
import javax.imageio.ImageIO
import org.jetbrains.skia.Image as SkiaImage

private const val TAG = "DesktopQrScanner"

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

/**
 * The QR scanner on a computer without a camera scanner: a picture of the code from a file, or
 * one pasted from the clipboard (a screenshot, or the code's text). It follows the camera
 * scanner's contract: a [ScanOutcome.Handled] scan closes it, and a code the screen cannot use
 * stays open on the shared explanation sheet.
 */
@Composable
fun DesktopQrScannerDialog(
    onDismiss: () -> Unit,
    onScan: (String) -> ScanOutcome,
) {
    val currentOnScan by rememberUpdatedState(onScan)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val uriHandler = LocalUriHandler.current

    var notice by remember { mutableStateOf<String?>(null) }
    var rejected by remember { mutableStateOf<ScannedPayload?>(null) }

    // A pick or paste in progress: the buttons wait for it, so a double click cannot read twice.
    var busy by remember { mutableStateOf(false) }

    // Callers expect one delivery: a second paste landing before the dialog leaves must not
    // hand them the code again.
    var handled by remember { mutableStateOf(false) }

    val pickTitle = stringRes(Res.string.qr_scanner_scan_image)
    val noCodeInImage = stringRes(Res.string.qr_scanner_no_code_in_image)
    val clipboardEmpty = stringRes(Res.string.qr_scanner_clipboard_empty)

    // Trimmed once here, as the camera scanner does: a code ending in a newline classified fine
    // but then failed to parse downstream.
    val submit: (String) -> Unit = submit@{ scanned ->
        if (handled) return@submit
        val text = scanned.trim()
        notice = null
        when (currentOnScan(text)) {
            ScanOutcome.Handled -> {
                handled = true
                currentOnDismiss()
            }
            ScanOutcome.NotSupported -> rejected = classifyScannedPayload(text)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.qr_scanner_desktop_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringRes(Res.string.qr_scanner_desktop_hint))
                notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                // The system dialog is modal and runs on the UI thread, like GallerySelect's.
                                val file = pickImageFile(pickTitle) ?: return@launch
                                val code = withContext(Dispatchers.IO) { readImageFile(file)?.let(::decodeQrCode) }
                                if (code == null) notice = noCodeInImage else submit(code)
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) {
                    Text(stringRes(Res.string.qr_scanner_scan_image))
                }
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                when (val clip = withContext(Dispatchers.IO) { readClipboardForQr() }) {
                                    is QrClip.Text -> submit(clip.text)
                                    is QrClip.Picture -> {
                                        val code = withContext(Dispatchers.IO) { decodeQrCode(clip.image) }
                                        if (code == null) notice = noCodeInImage else submit(code)
                                    }
                                    null -> notice = clipboardEmpty
                                }
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) {
                    Text(stringRes(Res.string.qr_scanner_paste))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.close)) }
        },
    )

    rejected?.let { payload ->
        ScanOutcomeSheet(
            payload = payload,
            onDismiss = { rejected = null },
            onOpenLink = { url ->
                rejected = null
                runCatching { uriHandler.openUri(url) }
                currentOnDismiss()
            },
            onCopy = { text ->
                rejected = null
                scope.launch { clipboard.setText(text) }
            },
            onOpenProfile = { npub ->
                rejected = null
                submit(npub)
            },
        )
    }
}

private fun pickImageFile(title: String): File? {
    val dialog =
        FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
            filenameFilter = FilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
        }
    dialog.isVisible = true
    return dialog.files?.firstOrNull()
}

/** ImageIO for the formats it knows, Skia for the rest (WebP). Null when neither can read it. */
private fun readImageFile(file: File): BufferedImage? =
    try {
        // ImageIO throws, rather than returning null, on some formats it half-knows (CMYK JPEGs).
        runCatching { ImageIO.read(file) }.getOrNull()
            ?: SkiaImage.makeFromEncoded(file.readBytes()).toComposeImageBitmap().toAwtImage()
    } catch (e: Exception) {
        Log.w(TAG, "Could not read the picture to scan", e)
        null
    }

private sealed interface QrClip {
    class Text(
        val text: String,
    ) : QrClip

    class Picture(
        val image: BufferedImage,
    ) : QrClip
}

/** A picture on the clipboard wins over its text; null when it holds neither. */
private fun readClipboardForQr(): QrClip? =
    try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        when {
            clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor) ->
                (clipboard.getData(DataFlavor.imageFlavor) as? Image)?.let { QrClip.Picture(it.toBufferedImage()) }
            clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor) ->
                (clipboard.getData(DataFlavor.stringFlavor) as? String)?.takeIf { it.isNotBlank() }?.let { QrClip.Text(it) }
            else -> null
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w(TAG, "Could not read the clipboard", e)
        null
    }

private fun Image.toBufferedImage(): BufferedImage {
    if (this is BufferedImage) return this
    val copy = BufferedImage(getWidth(null), getHeight(null), BufferedImage.TYPE_INT_ARGB)
    val graphics = copy.createGraphics()
    graphics.drawImage(this, 0, 0, null)
    graphics.dispose()
    return copy
}

private val QR_HINTS =
    mapOf(
        DecodeHintType.TRY_HARDER to true,
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
    )

/** The QR code in [image], light on dark too (a dark-mode screenshot), or null when there is none. */
fun decodeQrCode(image: BufferedImage): String? {
    val width = image.width
    val height = image.height
    if (width <= 0 || height <= 0) return null
    val source: LuminanceSource = RGBLuminanceSource(width, height, image.getRGB(0, 0, width, height, null, 0, width))
    for (candidate in listOf(source, source.invert())) {
        try {
            return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(candidate)), QR_HINTS).text
        } catch (_: ReaderException) {
            // Not in this polarity; try the other.
        }
    }
    return null
}
