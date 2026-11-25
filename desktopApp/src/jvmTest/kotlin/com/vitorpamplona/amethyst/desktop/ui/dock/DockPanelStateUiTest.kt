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
package com.vitorpamplona.amethyst.desktop.ui.dock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.dock.DockLayoutPreferences
import com.vitorpamplona.amethyst.commons.model.dock.PanelId
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.dock_panels
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.DetachedPanelContent
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.DockPanelScope
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.DockPanelSpec
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.PanelContent
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.PanelPlacement
import com.vitorpamplona.amethyst.commons.ui.navigation.dock.rememberDockController
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * A panel keeps its saved UI state (here a counter in rememberSaveable) wherever the dock draws it:
 * folded and shown again in another place of the main window, and in a window of its own.
 */
class DockPanelStateUiTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // One account for the whole test: the dock is rebuilt for another one.
    private val account: AccountViewModel = mockk(relaxed = true)

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    private val probe =
        DockPanelSpec(
            id = PanelId("probe"),
            icon = MaterialSymbols.Tab,
            title = Res.string.dock_panels,
            defaultWidth = 300f,
            content = { Counter() },
        )

    @Composable
    private fun Counter() {
        var count by rememberSaveable { mutableIntStateOf(0) }
        Text("count $count", Modifier.clickable { count++ })
    }

    /** 0: in a slot; 1: folded (nowhere); 2: in another slot; 3: as a panel window draws it. */
    private var site by mutableIntStateOf(0)

    private fun show() {
        val prefs = DockLayoutPreferences(PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("dock.preferences_pb") }, scope)
        compose.setContent {
            val dock = rememberDockController(prefs, listOf(probe), supportsWindows = true, accountViewModel = account)
            val panelScope = remember { DockPanelScope(probe.id, PanelPlacement.DOCKED, account, mockk(relaxed = true), dock) }
            Column {
                when (site) {
                    0 -> Box { dock.PanelContent(panelScope) }
                    1 -> {}
                    2 ->
                        Row {
                            Text("another column")
                            Column { dock.PanelContent(panelScope) }
                        }
                    3 ->
                        Column {
                            Text("a window")
                            dock.DetachedPanelContent(panelScope)
                        }
                }
            }
        }
    }

    private fun countTo(n: Int) {
        repeat(n) { i -> compose.onNodeWithText("count $i").performClick() }
        compose.onNodeWithText("count $n").assertExists()
    }

    @Test
    fun foldedAndShownElsewhereItKeepsItsState() {
        show()
        countTo(2)
        site = 1
        compose.waitForIdle()
        site = 2
        compose.onNodeWithText("count 2").assertExists()
    }

    @Test
    fun inAWindowOfItsOwnItKeepsItsState() {
        show()
        countTo(3)
        // The dock lets go of a panel before its window draws it (a window waits its turn).
        site = 1
        compose.waitForIdle()
        site = 3
        compose.waitForIdle()
        compose.onNodeWithText("count 3").assertExists()
        site = 0
        compose.waitForIdle()
        compose.onNodeWithText("count 3").assertExists()
    }
}
