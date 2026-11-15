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

import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalScreenLayout
import com.vitorpamplona.amethyst.commons.ui.layouts.NavigationStyle
import com.vitorpamplona.amethyst.commons.ui.layouts.ScreenLayoutSpec
import com.vitorpamplona.amethyst.commons.ui.note.creators.toolbar.ComposerToolbar
import com.vitorpamplona.amethyst.commons.ui.platform.AppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposerToolbarUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val docked = ScreenLayoutSpec(NavigationStyle.PERMANENT_DRAWER, hasRoomForNotificationPanel = true)

    private val mouse =
        object : AppPlatform {
            override val hasFinePointer: Boolean get() = true
        }

    private var spec by mutableStateOf(ScreenLayoutSpec.Phone)
    private var platform: AppPlatform by mutableStateOf(AppPlatform.None)

    private var created = 0
    private var lastWrapped: Boolean? = null
    private var lastTarget: Dp? = null

    private fun setContent() {
        compose.setContent {
            CompositionLocalProvider(LocalScreenLayout provides spec, LocalAppPlatform provides platform) {
                ComposerToolbar { wrapped ->
                    // Stands in for a tool with state of its own, such as a recording in progress.
                    val id = remember { ++created }
                    lastWrapped = wrapped
                    lastTarget = LocalMinimumInteractiveComponentSize.current
                    Text("tool $id")
                }
            }
        }
    }

    @Test
    fun aToolKeepsItsStateWhenTheWindowCrossesTiers() {
        setContent()
        compose.onNodeWithText("tool 1").assertExists()
        assertEquals(false, lastWrapped)

        spec = docked
        compose.waitForIdle()
        assertEquals(true, lastWrapped)

        spec = ScreenLayoutSpec.Phone
        compose.waitForIdle()
        assertEquals(false, lastWrapped)

        // Moved, not rebuilt: the same tool instance throughout.
        compose.onNodeWithText("tool 1").assertExists()
        assertEquals(1, created)
    }

    @Test
    fun aMouseDropsTheTouchTargetOnlyWhereTheToolsWrap() {
        platform = mouse
        setContent()
        assertEquals(48.dp, lastTarget)

        spec = docked
        compose.waitForIdle()
        assertEquals(Dp.Unspecified, lastTarget)

        // Back in the strip, the moved tools get the touch target again.
        spec = ScreenLayoutSpec.Phone
        compose.waitForIdle()
        assertEquals(48.dp, lastTarget)
    }

    @Test
    fun aFingerKeepsTheTouchTargetOnTheWideLayout() {
        // A landscape tablet: the docked tier, but touch.
        spec = docked
        setContent()
        assertEquals(true, lastWrapped)
        assertEquals(48.dp, lastTarget)
    }
}
