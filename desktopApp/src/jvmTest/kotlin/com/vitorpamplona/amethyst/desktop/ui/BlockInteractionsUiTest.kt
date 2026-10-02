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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.components.blockInteractions
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [blockInteractions] guards the signer consent preview: nothing inside may be clicked, long-pressed
 * or keyboard-activated (a poll vote there would sign a second event), yet the dialog must still
 * scroll when a drag starts on the preview.
 */
class BlockInteractionsUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val clicks = mutableIntStateOf(0)
    private val longClicks = mutableIntStateOf(0)
    private val scroll = ScrollState(0)

    @OptIn(ExperimentalFoundationApi::class)
    private fun setContent(blocked: Boolean) {
        compose.setContent {
            Box(Modifier.size(300.dp)) {
                Column(Modifier.verticalScroll(scroll)) {
                    Column(Modifier.testTag("preview").then(if (blocked) Modifier.blockInteractions() else Modifier)) {
                        Button(onClick = { clicks.intValue++ }) { Text("Vote") }
                        Box(
                            Modifier
                                .testTag("pressable")
                                .fillMaxWidth()
                                .height(800.dp)
                                .combinedClickable(onLongClick = { longClicks.intValue++ }, onClick = { clicks.intValue++ }),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun withoutTheModifierAClickLands() {
        setContent(blocked = false)
        compose.onNodeWithText("Vote").performClick()
        compose.waitForIdle()
        assertEquals(1, clicks.intValue)
    }

    @Test
    fun withoutTheModifierALongPressLands() {
        setContent(blocked = false)
        compose.onNodeWithTag("pressable").performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(1, longClicks.intValue)
    }

    @Test
    fun withoutTheModifierEnterActivates() {
        setContent(blocked = false)
        compose.onNodeWithText("Vote").requestFocus()
        compose.onNodeWithText("Vote").performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals(1, clicks.intValue)
    }

    @Test
    fun aClickIsSwallowed() {
        setContent(blocked = true)
        compose.onNodeWithText("Vote").performClick()
        compose.onNodeWithTag("pressable").performClick()
        compose.waitForIdle()
        assertEquals(0, clicks.intValue)
    }

    @Test
    fun aLongPressIsSwallowed() {
        setContent(blocked = true)
        compose.onNodeWithTag("pressable").performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(0, longClicks.intValue)
        assertEquals(0, clicks.intValue)
    }

    @Test
    fun keyboardActivationIsSwallowed() {
        setContent(blocked = true)
        compose.onNodeWithText("Vote").requestFocus()
        compose.onNodeWithText("Vote").performKeyInput { pressKey(Key.Enter) }
        compose.onNodeWithText("Vote").performKeyInput { pressKey(Key.Spacebar) }
        compose.waitForIdle()
        assertEquals(0, clicks.intValue)
    }

    @Test
    fun aDragStartingOnThePreviewStillScrollsTheParent() {
        setContent(blocked = true)
        compose.onNodeWithTag("pressable").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue(scroll.value > 0, "parent did not scroll: ${scroll.value}")
        assertEquals(0, clicks.intValue)
    }
}
