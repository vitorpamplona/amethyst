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
package com.vitorpamplona.amethyst.commons.model.dock

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** Each [session] is one launch over the same file: restore, act, flush, shut down. */
@OptIn(ExperimentalCoroutinesApi::class)
class DockLayoutPreferencesTest {
    @get:Rule val folder = TemporaryFolder()

    private fun store(scope: CoroutineScope): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { folder.root.resolve("shared_settings.preferences_pb") }

    private fun TestScope.session(taps: DockLayoutPreferences.() -> Unit = {}): DockLayoutPreferences {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val prefs = DockLayoutPreferences(store(scope), scope)
        advanceUntilIdle()
        prefs.taps()
        advanceUntilIdle()
        scope.cancel()
        advanceUntilIdle()
        return prefs
    }

    private fun TestScope.legacy(
        width: Float?,
        folded: Boolean?,
    ) {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        scope.launch {
            store(scope).edit { prefs ->
                width?.let { prefs[floatPreferencesKey("ui.panes.notificationsWidth")] = it }
                folded?.let { prefs[booleanPreferencesKey("ui.notificationPanel.collapsed")] = it }
            }
        }
        advanceUntilIdle()
        scope.cancel()
        advanceUntilIdle()
    }

    @Test
    fun aNewDeviceStartsWithTheOpenNotificationPanel() =
        runTest(StandardTestDispatcher()) {
            val layout = session().flow.value
            assertTrue(layout.isOpen(PanelId.NOTIFICATIONS))
            assertNull(layout.columns.single().width)
        }

    @Test
    fun theOldPanelsWidthAndFoldCarryOver() =
        runTest(StandardTestDispatcher()) {
            legacy(width = 420f, folded = true)
            val prefs = session()
            val layout = prefs.flow.value
            assertEquals(420f, layout.columns.single().width)
            assertFalse(layout.isOpen(PanelId.NOTIFICATIONS))
            assertNotNull("a restored fold starts its clock at the restore", prefs.foldedAt.value[PanelId.NOTIFICATIONS])
        }

    @Test
    fun aMovedPanelComesBackMovedInTheNextLaunch() =
        runTest(StandardTestDispatcher()) {
            session {
                update { it.ensure(listOf(PanelId.NOTIFICATIONS, PanelId.ASSISTANT)).open(PanelId.ASSISTANT) }
                update { checkNotNull(it.move(PanelId.ASSISTANT, DropTarget.NewColumn(0))) }
            }
            val layout = session().flow.value
            assertEquals(PanelLocation(0, 0, 0), layout.locate(PanelId.ASSISTANT))
            assertTrue(layout.isOpen(PanelId.ASSISTANT))
        }

    @Test
    fun foldingStampsAndShowingClears() =
        runTest(StandardTestDispatcher()) {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            val prefs = DockLayoutPreferences(store(scope), scope)
            advanceUntilIdle()
            assertNull(prefs.foldedAt.value[PanelId.NOTIFICATIONS])
            prefs.update { it.fold(PanelId.NOTIFICATIONS) }
            assertNotNull(prefs.foldedAt.value[PanelId.NOTIFICATIONS])
            prefs.update { it.open(PanelId.NOTIFICATIONS) }
            assertNull(prefs.foldedAt.value[PanelId.NOTIFICATIONS])
            scope.cancel()
            advanceUntilIdle()
        }

    @Test
    fun theStoredKeyIsTheOneTheShellReadsBack() =
        runTest(StandardTestDispatcher()) {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            val store = store(scope)
            val prefs = DockLayoutPreferences(store, scope)
            advanceUntilIdle()
            prefs.update { it.fold(PanelId.NOTIFICATIONS) }
            advanceUntilIdle()
            val written =
                store.data
                    .first()
                    .asMap()
                    .mapKeys { it.key.name }
            assertNotNull(written["ui.dock.layout"])
            scope.cancel()
            advanceUntilIdle()
        }

    @Test
    fun aNamedLayoutComesBackInTheNextLaunch() =
        runTest(StandardTestDispatcher()) {
            session {
                update { it.ensure(listOf(PanelId.NOTIFICATIONS, PanelId.ASSISTANT)).open(PanelId.ASSISTANT) }
                saveAs("  Writing  ")
                saveAs(" ")
                update { it.fold(PanelId.ASSISTANT) }
            }
            val prefs = session()
            assertEquals(setOf("Writing"), prefs.saved.value.keys)
            assertFalse(prefs.flow.value.isOpen(PanelId.ASSISTANT))
            prefs.restore("Writing")
            assertTrue(prefs.flow.value.isOpen(PanelId.ASSISTANT))
        }

    @Test
    fun aForgottenLayoutStaysForgotten() =
        runTest(StandardTestDispatcher()) {
            session {
                saveAs("A")
                saveAs("B")
                forget("A")
            }
            assertEquals(setOf("B"), session().saved.value.keys)
        }

    @Test
    fun namedLayoutsSurviveTheirEncoding() {
        val layouts = mapOf("A" to DockLayout().ensure(listOf(PanelId.NOTIFICATIONS)), "B" to DockLayout())
        assertEquals(layouts, DockLayoutPreferences.decodeNamed(DockLayoutPreferences.encodeNamed(layouts)))
        assertTrue(DockLayoutPreferences.decodeNamed("garbage").isEmpty())
    }

    @Test
    fun aLayoutSavedBeforeTheReadJoinsTheOnesOnDisk() =
        runTest(StandardTestDispatcher()) {
            session { saveAs("Reading") }
            // Saved before the read finishes: nothing has run on the store's dispatcher yet.
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            DockLayoutPreferences(store(scope), scope).saveAs("Writing")
            advanceUntilIdle()
            scope.cancel()
            advanceUntilIdle()
            assertEquals(setOf("Reading", "Writing"), session().saved.value.keys)
        }

    @Test
    fun aFailedReadNeverOverwritesTheSavedLayouts() =
        runTest(StandardTestDispatcher()) {
            val writes = AtomicInteger()
            val broken =
                object : DataStore<Preferences> {
                    override val data: Flow<Preferences> = flow { throw IOException("unreadable") }

                    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                        writes.incrementAndGet()
                        return transform(emptyPreferences())
                    }
                }
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            val prefs = DockLayoutPreferences(broken, scope)
            advanceUntilIdle()
            prefs.saveAs("Coding")
            advanceUntilIdle()
            scope.cancel()
            assertEquals("kept for this run", setOf("Coding"), prefs.saved.value.keys)
            assertEquals("nothing written over the layouts it could not read", 0, writes.get())
        }
}
