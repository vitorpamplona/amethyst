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

import androidx.compose.runtime.Stable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * Keeps the dock's [DockLayout] on the device: a window-layout choice, like the pane widths, so it
 * follows the monitor rather than the account and is never published. Loads on construction (build
 * it eagerly at startup), then writes changes back once a drag has rested.
 *
 * The first load of a device that has no saved dock builds one from what the app kept before the
 * dock existed: the notification panel's width and whether it was folded.
 *
 * [foldedAt] is when each panel was last folded, in epoch seconds, for the badges that count what
 * arrived since (the notification bell). It is in memory only: a fold restored from disk starts its
 * clock at the restore, so the downtime never shows as a stale count.
 */
@Stable
class DockLayoutPreferences(
    private val store: DataStore<Preferences>,
    scope: CoroutineScope,
) {
    private val layout = MutableStateFlow(DockLayout())

    val flow: StateFlow<DockLayout> = layout.asStateFlow()

    private val folds = MutableStateFlow<Map<PanelId, Long>>(emptyMap())

    val foldedAt: StateFlow<Map<PanelId, Long>> = folds.asStateFlow()

    private val named = MutableStateFlow<Map<String, DockLayout>>(emptyMap())

    /** Layouts the user saved by name, to go back to (as an IDE's Window › Layouts). */
    val saved: StateFlow<Map<String, DockLayout>> = named.asStateFlow()

    /** Keeps the layout on screen now as [name], replacing one saved under the same name. */
    fun saveAs(name: String) {
        val key = name.trim().ifEmpty { return }
        named.update { it + (key to layout.value) }
    }

    /** Puts the layout saved as [name] back on screen. */
    fun restore(name: String) {
        val chosen = named.value[name] ?: return
        update { chosen }
    }

    fun forget(name: String) {
        named.update { it - name }
    }

    init {
        scope.launch {
            // Saved layouts are written only once the ones on disk were read, so a failed read (or a
            // save made before it finished) never replaces them with what this run alone saved.
            val onDisk = restoreFromDisk()
            if (onDisk != null) launch { named.dropWhile { it == onDisk }.collect { persistNamed(it) } }
            @OptIn(FlowPreview::class)
            layout.drop(1).debounce(SAVE_DELAY_MS).collect { persist(it) }
        }
    }

    /** Changes the layout, stamping the panels it folds and clearing the ones it shows. */
    fun update(transform: (DockLayout) -> DockLayout) {
        val before = layout.value
        val after = transform(before)
        if (after == before) return
        layout.value = after
        val now = TimeUtils.now()
        folds.value =
            buildMap {
                putAll(folds.value)
                (before.states.keys + after.states.keys).forEach { id ->
                    val was = before.stateOf(id)
                    val isNow = after.stateOf(id)
                    if (isNow == PanelState.FOLDED && (was != PanelState.FOLDED || !before.contains(id))) put(id, now)
                    if (isNow != PanelState.FOLDED) remove(id)
                }
            }
    }

    /** Reads the layout and the saved layouts; returns the saved layouts on disk, or null when they could not be read. */
    private suspend fun restoreFromDisk(): Map<String, DockLayout>? =
        try {
            val prefs = store.data.first()
            val restored = prefs[LAYOUT]?.let { DockLayout.fromJson(it) } ?: migrate(prefs)
            val onDisk = prefs[SAVED]?.let { decodeNamed(it) }.orEmpty()
            // A layout saved before the read finished joins the ones on disk.
            named.update { onDisk + it }
            layout.value = restored
            val now = TimeUtils.now()
            folds.value = restored.states.filterValues { it == PanelState.FOLDED }.mapValues { now }
            onDisk
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("DockLayoutPrefs", "Error reading the dock layout", e)
            null
        }

    private suspend fun persist(value: DockLayout) {
        try {
            store.edit { prefs -> prefs[LAYOUT] = value.toJson() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("DockLayoutPrefs", "Error writing the dock layout", e)
        }
    }

    private suspend fun persistNamed(value: Map<String, DockLayout>) {
        try {
            store.edit { prefs -> prefs[SAVED] = encodeNamed(value) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("DockLayoutPrefs", "Error writing the saved dock layouts", e)
        }
    }

    companion object {
        const val SAVE_DELAY_MS = 400L
        private val LAYOUT = stringPreferencesKey("ui.dock.layout")
        private val SAVED = stringPreferencesKey("ui.dock.saved")

        private val namedJson = Json { ignoreUnknownKeys = true }

        /** Name to layout JSON, each layout as [DockLayout.toJson] writes it, so one that no longer reads is dropped alone. */
        internal fun encodeNamed(value: Map<String, DockLayout>): String = namedJson.encodeToString(MapSerializer(String.serializer(), String.serializer()), value.mapValues { it.value.toJson() })

        internal fun decodeNamed(value: String): Map<String, DockLayout> =
            try {
                namedJson
                    .decodeFromString(MapSerializer(String.serializer(), String.serializer()), value)
                    .mapNotNull { (name, json) -> DockLayout.fromJson(json)?.let { name to it } }
                    .toMap()
            } catch (e: IllegalArgumentException) {
                emptyMap()
            }

        // What the docked notification panel kept before the dock: read once, to build the first dock.
        private val LEGACY_NOTIFICATIONS_WIDTH = floatPreferencesKey("ui.panes.notificationsWidth")
        private val LEGACY_NOTIFICATIONS_FOLDED = booleanPreferencesKey("ui.notificationPanel.collapsed")

        /** The dock a device that never saved one starts with: the notification panel as it was left. */
        internal fun migrate(prefs: Preferences): DockLayout {
            val width = prefs[LEGACY_NOTIFICATIONS_WIDTH]
            val folded = prefs[LEGACY_NOTIFICATIONS_FOLDED] ?: false
            return DockLayout(
                columns = listOf(DockColumn(listOf(DockSlot(listOf(PanelId.NOTIFICATIONS), PanelId.NOTIFICATIONS)), width)),
                states = mapOf(PanelId.NOTIFICATIONS to if (folded) PanelState.FOLDED else PanelState.OPEN),
            )
        }
    }
}
