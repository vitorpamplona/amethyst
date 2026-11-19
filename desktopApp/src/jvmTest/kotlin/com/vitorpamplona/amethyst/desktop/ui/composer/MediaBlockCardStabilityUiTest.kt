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
package com.vitorpamplona.amethyst.desktop.ui.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import coil3.EventListener
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.request.ImageRequest
import com.vitorpamplona.amethyst.commons.model.mediaServers.ServerName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.what_s_on_your_mind
import com.vitorpamplona.amethyst.commons.service.uploads.MediaMarkers
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.StringMediaUri
import com.vitorpamplona.amethyst.commons.ui.components.ThinPaddingTextField
import com.vitorpamplona.amethyst.commons.ui.note.creators.messagefield.IMessageField
import com.vitorpamplona.amethyst.commons.ui.note.creators.messagefield.MediaBlocks
import com.vitorpamplona.amethyst.commons.ui.note.creators.messagefield.MessageField
import com.vitorpamplona.amethyst.commons.ui.note.creators.messagefield.rememberMediaMarkerField
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio.AttachedMediaState
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.studio.rememberAttachedMediaBlocks
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import org.junit.Rule
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A media card in the composer's text stays put while the person types. The text's layout reaches the
 * cards a frame late: placed by the new text on the old layout, a card jumped a line down and back, or
 * left the composition and reloaded its picture, so the picture blinked on every key.
 */
class MediaBlockCardStabilityUiTest {
    @get:Rule
    val compose = createComposeRule()

    private var entered = 0
    private var left = 0
    private val ys = mutableListOf<Float>()

    private fun show(state: TextFieldState) {
        val blocks =
            MediaBlocks(aspectRatio = { 1.5f }) { _, _, modifier ->
                DisposableEffect(Unit) {
                    entered++
                    onDispose { left++ }
                }
                Box(modifier.onGloballyPositioned { ys += it.positionInRoot().y })
            }
        compose.setContent {
            AmethystPreviewTheme(dark = true) {
                val markers = rememberMediaMarkerField(state, blocks)
                ThinPaddingTextField(
                    state = state,
                    onTextChanged = {},
                    outputTransformation = markers.outputTransformation,
                    cursorBrush = markers.cursorBrush,
                    textOverlay = markers.textOverlay,
                    modifier = Modifier,
                )
            }
        }
        compose.waitForIdle()
    }

    private fun type(
        state: TextFieldState,
        at: Int,
        text: String,
    ) {
        text.forEachIndexed { i, c ->
            compose.runOnIdle {
                state.edit {
                    replace(at + i, at + i, c.toString())
                    selection = TextRange(at + i + 1)
                }
            }
            compose.waitForIdle()
        }
    }

    @Test
    fun typingBeforeTheCardKeepsIt() {
        val state = TextFieldState("Hello\n" + MediaMarkers.of(1) + "\n")
        show(state)
        assertEquals(1, entered)
        type(state, 5, " world, typing before the picture")
        assertEquals(1, entered, "the card left composition while typing")
        assertEquals(0, left)
    }

    @Test
    fun typingBeforeTheCardAtTheEndKeepsIt() {
        val state = TextFieldState("Hello\n" + MediaMarkers.of(1))
        show(state)
        assertEquals(1, entered)
        type(state, 5, " world")
        assertEquals(1, entered, "the card left composition while typing")
        assertEquals(0, left)
    }

    @Test
    fun typingAfterTheCardKeepsIt() {
        val state = TextFieldState("Hello\n" + MediaMarkers.of(1) + "\n")
        state.edit { placeCursorAtEnd() }
        show(state)
        assertEquals(1, entered)
        type(state, state.text.length, "after the picture")
        assertEquals(1, entered, "the card left composition while typing")
        assertEquals(0, left)
    }

    /** The composer as the studio leaves it: the card under an empty first line, the cursor on that line, then typing. */
    @OptIn(DelicateCoilApi::class)
    @Test
    fun typingInTheComposerDoesNotReloadTheCardsImage() {
        var started = 0
        SingletonImageLoader.reset()
        SingletonImageLoader.setUnsafe { ctx: PlatformContext ->
            ImageLoader
                .Builder(ctx)
                .diskCache(null)
                .eventListener(
                    object : EventListener() {
                        override fun onStart(request: ImageRequest) {
                            started++
                        }
                    },
                ).build()
        }
        try {
            val png = File.createTempFile("card", ".png").apply { deleteOnExit() }
            ImageIO.write(BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB), "png", png)

            val field =
                object : IMessageField {
                    override val message = TextFieldState()

                    override fun onMessageChanged() {}
                }
            val media = AttachedMediaState(field.message)
            compose.setContent {
                AmethystPreviewTheme(dark = true) {
                    MessageField(
                        Res.string.what_s_on_your_mind,
                        field,
                        requestFocus = false,
                        mediaBlocks =
                            rememberAttachedMediaBlocks(media, null) { false }.let { real ->
                                remember(real) {
                                    MediaBlocks(real.aspectRatio, fixedHeight = real.fixedHeight) { id, selected, modifier ->
                                        DisposableEffect(Unit) {
                                            entered++
                                            onDispose { left++ }
                                        }
                                        real.card(id, selected, modifier.onGloballyPositioned { ys += it.positionInRoot().y })
                                    }
                                }
                            },
                    )
                }
            }
            compose.runOnIdle {
                media.pick(listOf(SelectedMedia(StringMediaUri(png.absolutePath), "image/png")), stripMetadata = false)
                media.attachPicked(ServerName("test", "https://example.com"))
            }
            compose.waitForIdle()
            val afterAttach = started
            val enteredAfterAttach = entered
            val placedAt = ys.last()
            ys.clear()
            assertEquals(1, afterAttach, "the card loads its image once")

            "Hello world".forEach { c ->
                compose.onNode(hasSetTextAction()).performTextInput(c.toString())
                compose.waitForIdle()
            }
            assertEquals("Hello world\n" + MediaMarkers.of(1) + "\n", field.message.text.toString())
            assertEquals(afterAttach, started, "typing reloaded the card's image")
            assertEquals(enteredAfterAttach, entered, "the card left composition while typing")
            // Typing on the line above the card doesn't move it, not even for a frame.
            assertTrue(ys.all { it == placedAt }, "the card moved from $placedAt while typing: $ys")
        } finally {
            SingletonImageLoader.reset()
        }
    }

    /** The text after one frame: [edit] applied, composed, measured and placed once. */
    private fun oneFrameAfter(edit: () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread(edit)
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun aLineAddedAboveMovesTheCardInTheSameFrame() {
        val state = TextFieldState("Hello\n" + MediaMarkers.of(1) + "\n")
        show(state)
        val before = ys.last()
        oneFrameAfter { state.edit { replace(0, 0, "Line\n") } }
        val firstFrame = ys.last()
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val after = ys.last()
        assertTrue(after > before, "a line above pushes the card down: $before -> $after")
        assertEquals(after, firstFrame, "the card followed the text a frame late: $ys")
    }

    @Test
    fun aCardFirstShowsWhereItStays() {
        val state = TextFieldState("Hello\n" + MediaMarkers.of(1) + "\n")
        show(state)
        assertEquals(1, ys.distinct().size, "the card moved as the field first laid out: $ys")
    }

    @Test
    fun anAddedCardFirstShowsWhereItStays() {
        val state = TextFieldState("Hello")
        show(state)
        compose.runOnIdle { state.edit { append("\n" + MediaMarkers.of(1) + "\n") } }
        compose.waitForIdle()
        assertEquals(1, ys.distinct().size, "the added card moved after it showed: $ys")
    }
}
