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
package com.vitorpamplona.amethyst.desktop.ui.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.rooms.ChatRowLabel
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A Messages row opens the chat on a tap anywhere; the community / relay [ChatRowLabel] inside it
 * opens the server page. The label is ~16dp tall, so Compose's 48dp minimum-touch-target expansion
 * used to let it steal taps on the room name and the preview line around it. Only a tap that lands on
 * the label itself may reach it.
 */
class ChatRowLabelTouchTargetUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val rowClicks = mutableIntStateOf(0)
    private val labelClicks = mutableIntStateOf(0)

    private fun setContent() {
        compose.setContent {
            Box(
                Modifier
                    .testTag("row")
                    .size(300.dp, 120.dp)
                    .clickable { rowClicks.intValue++ },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.testTag("label")) {
                    ChatRowLabel(symbol = MaterialSymbols.Group, text = "Community", onClick = { labelClicks.intValue++ })
                }
            }
        }
    }

    @Test
    fun tapJustOutsideTheLabelOpensTheRow() {
        setContent()
        val nearMiss = 6.dp.value * compose.density.density
        compose.onNodeWithTag("label", useUnmergedTree = true).performTouchInput { click(Offset(centerX, -nearMiss)) }
        compose.onNodeWithTag("label", useUnmergedTree = true).performTouchInput { click(Offset(centerX, height + nearMiss)) }
        compose.onNodeWithTag("label", useUnmergedTree = true).performTouchInput { click(Offset(-nearMiss, centerY)) }
        compose.waitForIdle()

        assertEquals(0, labelClicks.intValue)
        assertEquals(3, rowClicks.intValue)
    }

    @Test
    fun tapOnTheLabelOpensTheLabel() {
        setContent()
        compose.onNodeWithTag("label", useUnmergedTree = true).performTouchInput { click(center) }
        compose.waitForIdle()

        assertEquals(1, labelClicks.intValue)
        assertEquals(0, rowClicks.intValue)
    }
}
