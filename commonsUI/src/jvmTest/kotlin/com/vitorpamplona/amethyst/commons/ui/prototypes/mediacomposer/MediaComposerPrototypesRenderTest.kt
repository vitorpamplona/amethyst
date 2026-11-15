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
package com.vitorpamplona.amethyst.commons.ui.prototypes.mediacomposer

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Density
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * PROTOTYPE ONLY. Renders every prototype state offscreen, at phone size, to
 * `commonsUI/build/media-composer-prototypes/<name>.png`. It asserts only that each one rendered:
 * the point is the pictures (and that the prototypes compile and compose without crashing).
 */
class MediaComposerPrototypesRenderTest {
    private val outDir = File("build/media-composer-prototypes").apply { mkdirs() }
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    /** For hosts that start uploads: nothing runs, so each render shows exactly the state set. */
    private val idle = CoroutineScope(Job().apply { cancel() })

    // 1. Pick → Studio → the file lands in the post as a block.

    @Test
    fun postPickOpensStudio() =
        render("1-post-pick-studio") {
            val c = textOnlyComposer()
            c.pick(listOf(MediaDraft(50, DraftKind.IMAGE, sunsetPhoto(), 4032, 3024, 3_840_000), MediaDraft(51, DraftKind.IMAGE, forestPhoto(), 3024, 4032, 4_410_000)))
            PrototypeScreen(c, MarkerStyle.BLOCKS)
        }

    @Test
    fun postAfterAdd() =
        render("2-post-after-add") {
            val c = textOnlyComposer()
            c.pick(listOf(MediaDraft(50, DraftKind.IMAGE, sunsetPhoto(), 4032, 3024, 3_840_000)))
            c.confirmPick()
            c.message.edit { append("Worth the three-hour drive.") }
            PrototypeScreen(c, MarkerStyle.BLOCKS)
        }

    // Chat: the same Studio, with a caption and Send.

    @Test
    fun chatStudio() =
        render("5-chat-studio") {
            val chat = sampleChat(idle)
            chat.input.setTextAndPlaceCursorAtEnd("Here you go")
            chat.pick(listOf(MediaDraft(60, DraftKind.IMAGE, sunsetPhoto(), 4032, 3024, 3_840_000)))
            ChatPrototypeScreen(chat)
        }

    @Test
    fun chatSending() =
        render("6-chat-sending") {
            val chat = sampleChat(idle)
            val d = MediaDraft(60, DraftKind.IMAGE, sunsetPhoto(), 4032, 3024, 3_840_000)
            chat.input.setTextAndPlaceCursorAtEnd("Here you go")
            chat.pick(listOf(d))
            chat.send()
            d.status = DraftStatus.Uploading(0.55f)
            ChatPrototypeScreen(chat)
        }

    // Profile: the same Studio, locked to the shape the profile shows.

    @Test
    fun avatarStudio() =
        render("7-avatar-studio") {
            val p = ProfilePrototype(idle)
            p.pick(ProfileImage.AVATAR, MediaDraft(70, DraftKind.IMAGE, forestPhoto(), 3024, 4032, 4_410_000))
            ProfilePrototypeScreen(p)
        }

    @Test
    fun bannerStudio() =
        render("8-banner-studio") {
            val p = ProfilePrototype(idle)
            p.pick(ProfileImage.BANNER, MediaDraft(71, DraftKind.IMAGE, sunsetPhoto(), 4032, 3024, 3_840_000))
            ProfilePrototypeScreen(p)
        }

    @Test
    fun profileSaved() =
        render("9-profile-saved") {
            val p = ProfilePrototype(idle)
            val avatar = MediaDraft(70, DraftKind.IMAGE, forestPhoto(), 3024, 4032, 4_410_000)
            val banner = MediaDraft(71, DraftKind.IMAGE, sunsetPhoto(), 4032, 3024, 3_840_000)
            p.pick(ProfileImage.BANNER, banner)
            p.save()
            p.pick(ProfileImage.AVATAR, avatar)
            p.save()
            banner.status = DraftStatus.Uploaded("https://blossom.primal.net/b.jpg")
            avatar.status = DraftStatus.Uploading(0.6f)
            ProfilePrototypeScreen(p)
        }

    private fun textOnlyComposer() =
        PrototypeComposer(idle).also {
            it.message.setTextAndPlaceCursorAtEnd("Golden hour at the coast, then the city lights on the way home.")
        }

    // 3. The post composer, reopening the Studio, and posting.

    @Test
    fun aChipsEditing() = render("4-reply-compact-chips") { PrototypeScreen(sampleComposer(scope), MarkerStyle.CHIPS) }

    @Test
    fun bInlineEditing() =
        render("3-post-inline") {
            val c = sampleComposer(scope)
            // Cursor right after Photo 1's marker: the block reads as selected (Backspace removes it).
            val end = c.message.text.indexOf(c.draft(1)!!.token) + c.draft(1)!!.token.length
            c.message.edit { selection = TextRange(end) }
            PrototypeScreen(c, MarkerStyle.BLOCKS)
        }

    @Test
    fun bInlineLight() = render("3-post-inline-light", dark = false) { PrototypeScreen(sampleComposer(scope), MarkerStyle.BLOCKS) }

    @Test
    fun cStudioQuality() = studio("c-studio-quality", 1, StudioTool.QUALITY) { it.quality = QualityStop.SMALL }

    @Test
    fun cStudioHoldOriginal() = studio("c-studio-hold-original", 1, StudioTool.QUALITY, holding = true) { it.quality = QualityStop.SMALL }

    @Test
    fun cStudioCrop() = studio("c-studio-crop", 3, StudioTool.CROP)

    @Test
    fun cStudioTrim() = studio("c-studio-trim", 2, StudioTool.TRIM)

    @Test
    fun cStudioAlt() = studio("c-studio-alt", 1, StudioTool.ALT)

    @Test
    fun cStudioPrivacy() = studio("c-studio-privacy", 3, StudioTool.PRIVACY)

    @Test
    fun dPublishing() =
        render("d-publishing") {
            val c = sampleComposer(scope)
            c.draft(1)!!.status = DraftStatus.Uploaded("https://blossom.primal.net/a.jpg")
            c.draft(2)!!.status = DraftStatus.Uploading(0.42f)
            c.draft(3)!!.status = DraftStatus.Preparing(0.6f)
            c.phase = ComposerPhase.PUBLISHING
            PrototypeScreen(c, MarkerStyle.CHIPS)
        }

    @Test
    fun dFailedInline() =
        render("d-failed-inline") {
            val c = sampleComposer(scope)
            c.draft(1)!!.status = DraftStatus.Uploaded("https://blossom.primal.net/a.jpg")
            c.draft(2)!!.status = DraftStatus.Failed("blossom.primal.net refused the file: too large (413)")
            c.draft(3)!!.status = DraftStatus.Uploaded("https://blossom.primal.net/c.jpg")
            c.phase = ComposerPhase.NEEDS_ATTENTION
            // Scroll position is the top: the failed video is the second block.
            PrototypeScreen(c, MarkerStyle.BLOCKS)
        }

    @Test
    fun dFailedChips() =
        render("d-failed-chips") {
            val c = sampleComposer(scope)
            c.draft(1)!!.status = DraftStatus.Uploaded("https://blossom.primal.net/a.jpg")
            c.draft(2)!!.status = DraftStatus.Failed("blossom.primal.net refused the file: too large (413)")
            c.draft(3)!!.status = DraftStatus.Uploaded("https://blossom.primal.net/c.jpg")
            c.phase = ComposerPhase.NEEDS_ATTENTION
            PrototypeScreen(c, MarkerStyle.CHIPS)
        }

    private fun studio(
        name: String,
        id: Int,
        tool: StudioTool,
        holding: Boolean = false,
        setup: (MediaDraft) -> Unit = {},
    ) = render(name) {
        val c = sampleComposer(scope)
        val d = c.draft(id)!!
        setup(d)
        c.editing = d
        PrototypeScreen(c, MarkerStyle.BLOCKS, studioTool = tool, holdingOriginal = holding)
    }

    private fun render(
        name: String,
        dark: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        val density = 2f
        val scene =
            ImageComposeScene(width = (WIDTH_DP * density).toInt(), height = (HEIGHT_DP * density).toInt(), density = Density(density)) {
                AmethystPreviewTheme(dark = dark) {
                    Surface(color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        try {
            var image = scene.render(0)
            repeat(SETTLE_FRAMES) { frame ->
                Thread.sleep(FRAME_MILLIS)
                image = scene.render((frame + 1) * FRAME_MILLIS * 1_000_000L)
            }
            val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("could not encode")
            val file = File(outDir, "$name.png")
            file.writeBytes(png.bytes)
            assertTrue(file.length() > 0)
        } finally {
            scene.close()
        }
    }

    private companion object {
        const val WIDTH_DP = 400
        const val HEIGHT_DP = 860
        const val SETTLE_FRAMES = 12
        const val FRAME_MILLIS = 60L
    }
}
