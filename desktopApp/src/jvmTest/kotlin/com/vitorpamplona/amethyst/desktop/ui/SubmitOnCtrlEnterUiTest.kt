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
package com.vitorpamplona.amethyst.desktop.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import com.vitorpamplona.amethyst.commons.ui.components.submitOnCtrlEnter
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/** Ctrl+Enter (Cmd+Enter on macOS) posts from the composer; a plain Enter still breaks the line. */
class SubmitOnCtrlEnterUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = TextFieldState()
    private var active by mutableStateOf(true)
    private var submits = 0

    private fun setComposer() {
        compose.setContent {
            Box(Modifier.submitOnCtrlEnter(isActive = { active }, onSubmit = { submits++ })) {
                BasicTextField(state = state, modifier = Modifier.testTag("message"))
            }
        }
    }

    @Test
    fun ctrlEnterSubmitsWithoutAddingALine() {
        setComposer()
        compose.onNodeWithTag("message").performTextInput("gm")
        compose.onNodeWithTag("message").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Enter) } }
        compose.waitForIdle()

        assertEquals(1, submits)
        assertEquals("gm", state.text.toString())
    }

    @Test
    fun cmdEnterSubmits() {
        setComposer()
        compose.onNodeWithTag("message").performTextInput("gm")
        compose.onNodeWithTag("message").performKeyInput { withKeyDown(Key.MetaLeft) { pressKey(Key.Enter) } }
        compose.waitForIdle()

        assertEquals(1, submits)
    }

    @Test
    fun plainEnterStillBreaksTheLine() {
        setComposer()
        compose.onNodeWithTag("message").performTextInput("gm")
        compose.onNodeWithTag("message").performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()

        assertEquals(0, submits)
        assertEquals("gm\n", state.text.toString())
    }

    @Test
    fun doesNothingWhileThePostButtonIsDisabled() {
        active = false
        setComposer()
        compose.onNodeWithTag("message").performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Enter) } }
        compose.waitForIdle()

        assertEquals(0, submits)
        assertEquals("", state.text.toString())
    }

    @Test
    fun eachPressSubmitsAgain() {
        // A post that fails (a signer that says no) leaves the composer open: the next press retries.
        setComposer()
        compose.onNodeWithTag("message").performTextInput("gm")
        compose.onNodeWithTag("message").performKeyInput {
            withKeyDown(Key.CtrlLeft) {
                pressKey(Key.Enter)
                pressKey(Key.Enter)
            }
        }
        compose.waitForIdle()

        assertEquals(2, submits)
        assertEquals("gm", state.text.toString())
    }
}
