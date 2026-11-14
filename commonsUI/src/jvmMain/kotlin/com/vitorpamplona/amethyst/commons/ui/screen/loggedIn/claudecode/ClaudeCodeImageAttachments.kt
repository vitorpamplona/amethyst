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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.claudecode

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.claudecode.SentImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.claude_code_attach_remove
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.claudecode.protocol.ImageInput
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt
import java.awt.Image as AwtImage
import org.jetbrains.skia.Image as SkiaImage

/** An image waiting in the composer: read for sending, with a small picture of it. */
@Stable
class ComposerImage(
    val file: File,
    val input: ImageInput,
    val thumbnail: ImageBitmap?,
)

/** The images the next message will carry, and the last one that couldn't be added. */
@Stable
class ComposerAttachments {
    val images = mutableStateListOf<ComposerImage>()

    /** The name of a file that couldn't be attached, until the next attempt. */
    var refused by mutableStateOf<String?>(null)
        private set

    /** Reads [files] for sending, off the main thread. */
    suspend fun add(files: List<File>) {
        refused = null
        val read =
            withContext(Dispatchers.IO) {
                files.map { file ->
                    try {
                        val input = ImageInput.fromFile(file)
                        ComposerImage(file, input, thumbnail(file.readBytes()))
                    } catch (e: IOException) {
                        Log.w(TAG, "Could not attach ${file.name}", e)
                        file.name
                    }
                }
            }
        read.forEach { if (it is ComposerImage) images.add(it) else refused = it as String }
    }

    /**
     * Attaches what the clipboard holds when it is an image or image files, and says whether it
     * did, so a paste of text goes on as usual.
     */
    suspend fun pasteFromClipboard(): Boolean {
        val files =
            withContext(Dispatchers.IO) {
                try {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    when {
                        clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor) ->
                            (clipboard.getData(DataFlavor.javaFileListFlavor) as? List<*>)
                                ?.filterIsInstance<File>()
                                ?.filter { it.extension.lowercase() in ImageInput.EXTENSIONS }
                                .orEmpty()
                        clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor) ->
                            (clipboard.getData(DataFlavor.imageFlavor) as? AwtImage)?.let { listOf(savePasted(it)) }.orEmpty()
                        else -> emptyList()
                    }
                } catch (e: Exception) {
                    // The clipboard can be busy, or hold something it then fails to hand over.
                    Log.w(TAG, "Could not read the clipboard", e)
                    emptyList()
                }
            }
        if (files.isEmpty()) return false
        add(files)
        return true
    }

    /** Whether a paste now would attach an image; checked as the key goes down, so it is quick. */
    fun clipboardHasImage(): Boolean =
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor) || clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)
        } catch (e: IllegalStateException) {
            false
        }

    fun remove(image: ComposerImage) {
        images.remove(image)
        refused = null
    }

    /** Hands the images over for sending and empties the composer. */
    fun take(): List<ComposerImage> =
        images.toList().also {
            images.clear()
            refused = null
        }

    private fun savePasted(image: AwtImage): File {
        val buffered =
            image as? BufferedImage
                ?: BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB).also {
                    val g = it.createGraphics()
                    g.drawImage(image, 0, 0, null)
                    g.dispose()
                }
        // Kept for the chat to show it from; the system's temp cleanup removes it.
        val file = File.createTempFile("pasted-image-", ".png")
        file.deleteOnExit()
        if (!ImageIO.write(buffered, "png", file)) throw IOException("No PNG writer")
        return file
    }

    private companion object {
        const val TAG = "ClaudeCodeAttachments"
    }
}

/** A small picture of an encoded image, or null when Skia can't read it. */
internal fun thumbnail(
    bytes: ByteArray,
    side: Int = THUMBNAIL_PX,
): ImageBitmap? =
    try {
        val image = SkiaImage.makeFromEncoded(bytes)
        val scale = minOf(1f, side.toFloat() / max(image.width, image.height))
        val width = max(1, (image.width * scale).roundToInt())
        val height = max(1, (image.height * scale).roundToInt())
        val bitmap = Bitmap().apply { allocN32Pixels(width, height) }
        Canvas(bitmap).drawImageRect(image, Rect.makeWH(image.width.toFloat(), image.height.toFloat()), Rect.makeWH(width.toFloat(), height.toFloat()), SamplingMode.LINEAR, null, true)
        bitmap.setImmutable()
        bitmap.asComposeImageBitmap()
    } catch (e: IllegalArgumentException) {
        null
    }

private const val THUMBNAIL_PX = 240

/** The composer's images in a row, each with its remove button. */
@Composable
internal fun ComposerImageRow(
    attachments: ComposerAttachments,
    modifier: Modifier = Modifier,
) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        attachments.images.forEach { image ->
            Box {
                ImageTile(image.thumbnail, 64.dp)
                val remove = stringRes(Res.string.claude_code_attach_remove, image.file.name)
                Box(Modifier.align(Alignment.TopEnd).padding(3.dp)) {
                    HoverTooltip(remove) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.8f))
                                .clickable { attachments.remove(image) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(MaterialSymbols.Close, contentDescription = remove, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

/** The images a message carried: their pictures while the files are there, a placeholder after. */
@Composable
internal fun SentImageRow(
    images: List<SentImage>,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        images.forEach { image ->
            val picture by produceState<ImageBitmap?>(null, image.file) {
                value =
                    image.file?.let { path ->
                        withContext(Dispatchers.IO) {
                            val file = File(path)
                            if (file.isFile) thumbnail(file.readBytes()) else null
                        }
                    }
            }
            val name = image.name
            if (name != null) HoverTooltip(name) { ImageTile(picture, 96.dp) } else ImageTile(picture, 96.dp)
        }
    }
}

@Composable
private fun ImageTile(
    picture: ImageBitmap?,
    size: Dp,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (picture != null) {
            Image(picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Icon(MaterialSymbols.Image, contentDescription = null, tint = MaterialTheme.colorScheme.grayText, modifier = Modifier.size(size / 3))
        }
    }
}
