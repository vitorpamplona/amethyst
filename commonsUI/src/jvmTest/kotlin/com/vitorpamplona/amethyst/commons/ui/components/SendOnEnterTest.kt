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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A chat composer's Enter ([sendOnEnter]) with the suggestion lists and input methods it must leave alone (audit 3). */
@OptIn(InternalComposeUiApi::class)
class SendOnEnterTest {
    private class Composer {
        val field = TextFieldState("hi @al")
        var sent = 0
        var picked = 0
        var canPick = false
        var composing = false
    }

    private fun scene(composer: Composer): ImageComposeScene =
        ImageComposeScene(width = 400, height = 200, density = Density(1f)).apply {
            setContent {
                val focus = remember { FocusRequester() }
                BasicTextField(
                    state = composer.field,
                    modifier =
                        Modifier.focusRequester(focus).sendOnEnter(
                            enabled = true,
                            isActive = { true },
                            onSend = { composer.sent++ },
                            isComposing = { composer.composing },
                            onPickSuggestion = {
                                if (composer.canPick) composer.picked++
                                composer.canPick
                            },
                        ),
                )
                LaunchedEffect(Unit) { focus.requestFocus() }
            }
            render()
            render()
        }

    private fun ImageComposeScene.press(release: Boolean = true) {
        sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyDown))
        if (release) sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyUp))
        render()
    }

    @Test
    fun enterSendsWhenNoSuggestionIsOpen() {
        val composer = Composer()
        val scene = scene(composer)
        try {
            scene.press()
            assertEquals(1, composer.sent)
            assertEquals("hi @al", composer.field.text.toString())
        } finally {
            scene.close()
        }
    }

    @Test
    fun enterPicksTheOpenSuggestionInsteadOfSending() {
        val composer = Composer().apply { canPick = true }
        val scene = scene(composer)
        try {
            scene.press(release = false)
            assertEquals(1, composer.picked)
            assertEquals(0, composer.sent)
            // Held down past the pick (the list has closed), it does not go on to send.
            composer.canPick = false
            scene.press(release = false)
            assertEquals(0, composer.sent)
            scene.sendKeyEvent(KeyEvent(Key.Enter, KeyEventType.KeyUp))
            // The next press sends.
            scene.press()
            assertEquals(1, composer.sent)
            assertEquals("hi @al", composer.field.text.toString())
        } finally {
            scene.close()
        }
    }

    @Test
    fun enterIsLeftToAnInputMethodThatIsComposing() {
        val composer = Composer().apply { composing = true }
        val scene = scene(composer)
        try {
            scene.press()
            assertEquals(0, composer.sent)
            assertEquals(0, composer.picked)
            // The key went on to the field, where an input method gets its say.
            assertTrue(composer.field.text.contains('\n'), "the field should have had the Enter")
        } finally {
            scene.close()
        }
    }
}
