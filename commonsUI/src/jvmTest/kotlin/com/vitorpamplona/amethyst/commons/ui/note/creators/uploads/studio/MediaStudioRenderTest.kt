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
package com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.encrypt_files_label
import com.vitorpamplona.amethyst.commons.resources.media_studio_encrypt_description
import com.vitorpamplona.amethyst.commons.service.ai.AltTextSuggester
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.ImageCompressionPreview
import com.vitorpamplona.amethyst.commons.service.uploads.ImageCrop
import com.vitorpamplona.amethyst.commons.service.uploads.ImageFileStats
import com.vitorpamplona.amethyst.commons.service.uploads.MediaCompressorResult
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.StringMediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.UnavailableMediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.UploadError
import com.vitorpamplona.amethyst.commons.service.uploads.UploadingState
import com.vitorpamplona.amethyst.commons.service.uploads.VideoCompressionSupport
import com.vitorpamplona.amethyst.commons.service.uploads.VideoTrim
import com.vitorpamplona.amethyst.commons.ui.prototypes.mediacomposer.forestPhoto
import com.vitorpamplona.amethyst.commons.ui.prototypes.mediacomposer.simulateQuality
import com.vitorpamplona.amethyst.commons.ui.prototypes.mediacomposer.sunsetPhoto
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertTrue
import com.vitorpamplona.amethyst.commons.ui.prototypes.mediacomposer.QualityStop as SimulatedStop

/**
 * The production [MediaStudio], rendered offscreen with a fake uploader whose compression previews
 * are real, smaller image files, so the preview, the sizes on each level and the upload states all
 * show. Writes `commonsUI/build/media-studio/<name>.png`.
 */
class MediaStudioRenderTest {
    private val outDir = File("build/media-studio").apply { mkdirs() }
    private val tmp = File("build/media-studio/files").apply { mkdirs() }

    private fun ImageBitmap.writePng(name: String): File {
        val file = File(tmp, name)
        val data = Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG) ?: error("encode")
        file.writeBytes(data.bytes)
        return file
    }

    private val sunset = sunsetPhoto().writePng("sunset.png")
    private val forest = forestPhoto().writePng("forest.png")

    /** Claims a 12 MP original; each level's preview is the stand-in scaled down as that level would. */
    private inner class FakeUploader(
        override val videoCompression: VideoCompressionSupport = VideoCompressionSupport.Supported,
    ) : MediaUploader by UnavailableMediaUploader {
        override suspend fun previewImageCompression(
            uri: MediaUri,
            mimeType: String?,
            compressionQuality: CompressorQuality,
        ): ImageCompressionPreview? {
            val edge = compressionQuality.imageMaxDimension ?: return null
            if (uri.toString().endsWith("-crop.png")) {
                // A crop's copy: its preview is the copy itself, so the stage shows the cropped part.
                val img = ImageIO.read(File(uri.toString()))
                val stats = ImageFileStats(img.width, img.height, File(uri.toString()).length())
                return ImageCompressionPreview(stats, stats, uri)
            }
            val portrait = uri.toString().contains("forest")
            val (w, h) = if (portrait) 3024 to 4032 else 4032 to 3024
            val scale = edge.toFloat() / max(w, h)
            val source = if (portrait) forestPhoto() else sunsetPhoto()
            val stop = SimulatedStop.entries.firstOrNull { (it.maxEdge ?: 0) >= edge } ?: SimulatedStop.HIGH
            val file = simulateQuality(source, max(w, h), stop).writePng("${uri.toString().substringAfterLast('/')}-$compressionQuality.png")
            val bytes = (3_840_000L * scale * scale * (compressionQuality.imageQuality / 100f)).roundToLong()
            return ImageCompressionPreview(
                original = ImageFileStats(w, h, 3_840_000),
                compressed = ImageFileStats((w * scale).roundToInt(), (h * scale).roundToInt(), bytes),
                compressedUri = StringMediaUri(file.absolutePath),
            )
        }

        override fun fileSize(uri: MediaUri): Long = 3_840_000

        override val videoTrim: Boolean = true

        override suspend fun videoDurationMs(uri: MediaUri): Long = 12_000

        // Frames alternate between the two stand-ins, so the strip shows where each one falls.
        override suspend fun videoFrame(
            uri: MediaUri,
            atMs: Long,
            maxEdge: Int,
        ): ByteArray = if ((atMs / 1_500) % 2 == 0L) sunset.readBytes() else forest.readBytes()

        override suspend fun cropImage(
            uri: MediaUri,
            mimeType: String?,
            crop: ImageCrop,
        ): MediaCompressorResult {
            val img = ImageIO.read(File(uri.toString()))
            val (x, y, w, h) = crop.pixels(img.width, img.height).toList()
            val out = File(tmp, File(uri.toString()).nameWithoutExtension + "-${crop.hashCode()}-crop.png")
            ImageIO.write(img.getSubimage(x, y, w, h), "png", out)
            return MediaCompressorResult(StringMediaUri(out.absolutePath), "image/png", out.length())
        }
    }

    private val suggester =
        object : AltTextSuggester {
            override suspend fun suggestAltText(uri: MediaUri) = "The sun setting over a calm sea behind a mountain ridge"

            override fun close() = Unit
        }

    private val servers = listOf(ServerName("blossom.primal.net", "https://blossom.primal.net"), ServerName("nostr.download", "https://nostr.download"))

    private fun photos() =
        MultiOrchestrator(
            listOf(SelectedMedia(StringMediaUri(sunset.absolutePath), "image/png"), SelectedMedia(StringMediaUri(forest.absolutePath), "image/png")),
        )

    @Test
    fun quality() = render("quality") { Studio(photos()) }

    @Test
    fun qualityLight() = render("quality-light", dark = false) { Studio(photos()) }

    @Test
    fun holdForOriginal() = render("hold-original") { Studio(photos(), holding = true) }

    @Test
    fun altSuggested() = render("alt-suggested") { Studio(photos(), tool = StudioTool.ALT, withSuggester = true) }

    @Test
    fun crop() =
        render("crop") {
            val media = photos()
            media.get(0).displayAspectRatio = 4f / 3f
            media.get(0).options = media.get(0).options.copy(crop = ImageCrop.centered(4f / 3f, 1f))
            Studio(media, tool = StudioTool.CROP)
        }

    @Test
    fun croppedQuality() =
        render("quality-cropped") {
            val media = photos()
            media.get(0).displayAspectRatio = 4f / 3f
            media.get(0).options = media.get(0).options.copy(crop = ImageCrop(0.5f, 0.1f, 1f, 0.9f))
            Studio(media)
        }

    @Test
    fun trim() =
        render("trim") {
            val media = MultiOrchestrator(listOf(SelectedMedia(StringMediaUri("/tmp/clip.mp4"), "video/mp4")))
            media.get(0).options = media.get(0).options.copy(trim = VideoTrim(2_500, 9_000))
            Studio(media, tool = StudioTool.TRIM)
        }

    @Test
    fun caption() = render("caption") { Studio(photos(), caption = "Golden hour over the bay #photography") }

    @Test
    fun captionEmpty() = render("caption-empty") { Studio(photos(), caption = "") }

    @Test
    fun privacyInAChat() =
        render("privacy-chat") {
            Studio(photos(), tool = StudioTool.PRIVACY, privacyExtras = { SwitchRow(stringRes(Res.string.encrypt_files_label), stringRes(Res.string.media_studio_encrypt_description), true, MaterialSymbols.Lock) {} })
        }

    @Test
    fun videoQualityLight() =
        render("video-light", dark = false) {
            Studio(MultiOrchestrator(listOf(SelectedMedia(StringMediaUri("/tmp/clip.mp4"), "video/mp4"))))
        }

    @Test
    fun privacyLight() = render("privacy-light", dark = false) { Studio(photos(), tool = StudioTool.PRIVACY) }

    @Test
    fun altLight() = render("alt-light", dark = false) { Studio(photos(), tool = StudioTool.ALT, withSuggester = true) }

    @Test
    fun audio() = render("audio") { Studio(MultiOrchestrator(listOf(SelectedMedia(StringMediaUri("/tmp/song.mp3"), "audio/mpeg")))) }

    @Test
    fun pdf() = render("pdf") { Studio(MultiOrchestrator(listOf(SelectedMedia(StringMediaUri("/tmp/report.pdf"), "application/pdf")))) }

    @Test
    fun fileCards() =
        render("file-cards") {
            val fake = FakeUploader()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    SelectedMedia(StringMediaUri("/tmp/song.mp3"), "audio/mpeg"),
                    // A voice memo saved as .mp4, once the Studio found it is sound.
                    SelectedMedia(StringMediaUri("/tmp/voice-memo-2026-11-17.mp4"), "audio/mp4"),
                    SelectedMedia(StringMediaUri("/tmp/Quarterly report with a long name.pdf"), "application/pdf"),
                ).forEachIndexed { index, media ->
                    val item = MultiOrchestrator(listOf(media)).get(0)
                    if (index == 2) item.options = item.options.copy(sensitive = true)
                    AttachedMediaCard(item, selected = index == 1, locked = false, onOpen = {}, onRemove = {}, modifier = Modifier.fillMaxWidth().height(FILE_CARD_HEIGHT), uploader = fake)
                }
            }
        }

    @Test
    fun privacy() = render("privacy") { Studio(photos(), tool = StudioTool.PRIVACY) }

    @Test
    fun video() =
        render("video") {
            Studio(MultiOrchestrator(listOf(SelectedMedia(StringMediaUri("/tmp/clip.mp4"), "video/mp4"))))
        }

    @Test
    fun uploading() =
        render("uploading") {
            val media = photos()
            media.get(0).orchestrator.updateState(0.42, UploadingState.Uploading)
            media.get(1).orchestrator.updateState(0.1, UploadingState.Compressing)
            Studio(media, uploading = true)
        }

    @Test
    fun failed() =
        render("failed") {
            val media = photos()
            media.get(0).orchestrator.error(UploadError.FAILED_TO_UPLOAD_MEDIA, "413 Payload Too Large")
            Studio(media)
        }

    @Composable
    private fun Studio(
        media: MultiOrchestrator,
        tool: StudioTool = StudioTool.QUALITY,
        holding: Boolean = false,
        uploading: Boolean = false,
        withSuggester: Boolean = false,
        caption: String? = null,
        privacyExtras: (@Composable ColumnScope.() -> Unit)? = null,
    ) = MediaStudio(
        media = media,
        servers = servers,
        defaultServer = servers[0],
        isUploading = uploading,
        uploader = FakeUploader(),
        altSuggester = if (withSuggester) suggester else null,
        onAdd = {},
        onRemove = {},
        onCancel = {},
        initialTool = tool,
        holdingOriginal = holding,
        caption = caption,
        privacyExtras = privacyExtras,
    )

    private fun render(
        name: String,
        dark: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        val density = 2f
        val scene =
            ImageComposeScene(width = (400 * density).toInt(), height = (860 * density).toInt(), density = Density(density)) {
                AmethystPreviewTheme(dark = dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } }
            }
        try {
            var image = scene.render(0)
            // Long enough for the 250 ms settle, the fake encodes and Coil's decodes.
            repeat(40) { frame ->
                Thread.sleep(60)
                image = scene.render((frame + 1) * 60 * 1_000_000L)
            }
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("could not encode")
            val file = File(outDir, "$name.png")
            file.writeBytes(png.bytes)
            assertTrue(file.length() > 0)
        } finally {
            scene.close()
        }
    }
}
