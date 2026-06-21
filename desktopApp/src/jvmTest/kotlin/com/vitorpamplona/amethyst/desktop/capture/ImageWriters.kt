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
package com.vitorpamplona.amethyst.desktop.capture

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.FileImageOutputStream

/** Writes a single [BufferedImage] to [file] as PNG using the JDK's ImageIO. */
fun writePng(
    image: BufferedImage,
    file: File,
) {
    file.parentFile?.mkdirs()
    require(ImageIO.write(image, "png", file)) { "No PNG ImageIO writer available" }
}

/**
 * Stitches [frames] into an animated GIF at [file] — the dependency-free stand-in
 * for a short feature "video" clip. Uses only `javax.imageio`'s GIF writer, so it
 * works in any JVM with no ffmpeg / native tooling.
 *
 * @param delayMs per-frame display time in milliseconds (rounded to GIF's 10ms unit).
 * @param loop when true the clip repeats forever (NETSCAPE2.0 loop extension).
 */
fun writeAnimatedGif(
    frames: List<BufferedImage>,
    file: File,
    delayMs: Int = 700,
    loop: Boolean = true,
) {
    require(frames.isNotEmpty()) { "Cannot write an animated GIF with no frames" }
    file.parentFile?.mkdirs()

    // GIF is a paletted RGB format — normalise every frame to TYPE_INT_RGB so the
    // metadata we attach matches the pixels and ARGB frames don't trip the writer.
    val rgbFrames = frames.map { it.toType(BufferedImage.TYPE_INT_RGB) }

    val writer =
        ImageIO.getImageWritersBySuffix("gif").asSequence().firstOrNull()
            ?: error("No GIF ImageIO writer available")
    val params = writer.defaultWriteParam
    val imageType = ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB)
    val metadata = writer.getDefaultImageMetadata(imageType, params)
    val format = metadata.nativeMetadataFormatName
    val root = metadata.getAsTree(format) as IIOMetadataNode

    childNode(root, "GraphicControlExtension").apply {
        setAttribute("disposalMethod", "none")
        setAttribute("userInputFlag", "FALSE")
        setAttribute("transparentColorFlag", "FALSE")
        setAttribute("delayTime", (delayMs / 10).coerceAtLeast(1).toString())
        setAttribute("transparentColorIndex", "0")
    }

    if (loop) {
        // NETSCAPE2.0 application extension = "loop forever" (loop count 0).
        val appExtensions = childNode(root, "ApplicationExtensions")
        val appNode =
            IIOMetadataNode("ApplicationExtension").apply {
                setAttribute("applicationID", "NETSCAPE")
                setAttribute("authenticationCode", "2.0")
                userObject = byteArrayOf(0x1, 0, 0)
            }
        appExtensions.appendChild(appNode)
    }

    metadata.setFromTree(format, root)

    FileImageOutputStream(file).use { out ->
        writer.output = out
        writer.prepareWriteSequence(null)
        rgbFrames.forEach { writer.writeToSequence(IIOImage(it, null, metadata), params) }
        writer.endWriteSequence()
    }
    writer.dispose()
}

/** Returns this node's first child named [name], creating and appending it if absent. */
private fun childNode(
    parent: IIOMetadataNode,
    name: String,
): IIOMetadataNode {
    for (i in 0 until parent.length) {
        val node = parent.item(i)
        if (node.nodeName.equals(name, ignoreCase = true)) return node as IIOMetadataNode
    }
    return IIOMetadataNode(name).also { parent.appendChild(it) }
}

/** Returns this image converted to [targetType] (no-op when it already matches). */
private fun BufferedImage.toType(targetType: Int): BufferedImage {
    if (type == targetType) return this
    val converted = BufferedImage(width, height, targetType)
    val g = converted.createGraphics()
    g.drawImage(this, 0, 0, null)
    g.dispose()
    return converted
}
