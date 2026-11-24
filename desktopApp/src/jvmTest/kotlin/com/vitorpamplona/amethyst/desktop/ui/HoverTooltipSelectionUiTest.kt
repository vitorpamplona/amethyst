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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.layouts.NavigationStyle
import com.vitorpamplona.amethyst.commons.ui.layouts.ScreenLayoutSpec
import org.junit.Rule
import kotlin.test.Test

/**
 * A hover tooltip inside selectable text, as on the coding-agent chat (a sent image's name over its
 * picture): pressing while the tooltip shows starts a selection that must not reach the tooltip's
 * text, which lives in a popup, another layout hierarchy. It crashed the window with
 * "layouts are not part of the same hierarchy".
 */
@OptIn(ExperimentalTestApi::class)
class HoverTooltipSelectionUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun pressingUnderAShownTooltipInSelectableTextDoesNotCrash() {
        compose.setContent {
            CompositionLocalProvider(LocalScreenLayout provides ScreenLayoutSpec(NavigationStyle.entries.last(), hasRoomForNotificationPanel = false)) {
                SelectionContainer {
                    Column {
                        Text("A message")
                        HoverTooltip("picture.png") { Box(Modifier.size(96.dp).testTag("image")) }
                    }
                }
            }
        }
        compose.onNodeWithTag("image", useUnmergedTree = true).performMouseInput { moveTo(center) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        // The tooltip is up, so the press below meets its text.
        compose.onNodeWithText("picture.png", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("image", useUnmergedTree = true).performMouseInput {
            press()
            moveBy(Offset(5f, 0f))
            release()
        }
        compose.waitForIdle()
    }
}
