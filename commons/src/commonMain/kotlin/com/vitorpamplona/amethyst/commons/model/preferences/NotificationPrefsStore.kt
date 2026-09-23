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
package com.vitorpamplona.amethyst.commons.model.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import okio.IOException

/**
 * This account's share of the notification settings.

 * The global on/off switch is not here — it lives in plain, non-encrypted
 * storage because the restart layer must read it synchronously from a fresh
 * process (boot receiver, WorkManager) before any account is loaded.
 *
 * Defaults match what the SharedPreferences implementation returned for a
 * missing key, so an account that never touched a setting behaves identically
 * before and after the migration.
 */
data class NotificationPrefs(
    val alwaysOnService: Boolean = false,
    val showMessagesInNotifications: Boolean = true,
    val splitNotificationsEnabled: Boolean = false,
)

/** Reads and writes [NotificationPrefs] in the account's DataStore. */
class NotificationPrefsStore(
    private val store: DataStore<Preferences>,
) {
    companion object {
        val alwaysOnService = booleanPreferencesKey("always_on_notification_service")
        val showMessagesInNotifications = booleanPreferencesKey("show_messages_in_notifications")
        val splitNotificationsEnabled = booleanPreferencesKey("split_notifications_enabled")
        val globalToCuratedMigrated = booleanPreferencesKey("notif_global_to_curated_migrated")
        val lastReadPerRouteJson = stringPreferencesKey("last_read_per_route")
    }

    private suspend fun read(): Preferences =
        store.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()

    suspend fun load(): NotificationPrefs {
        val prefs = read()

        return NotificationPrefs(
            alwaysOnService = prefs[alwaysOnService] ?: false,
            showMessagesInNotifications = prefs[showMessagesInNotifications] ?: true,
            splitNotificationsEnabled = prefs[splitNotificationsEnabled] ?: false,
        )
    }

    /**
     * Whether the one-shot global-to-curated notification filter migration has
     * already run for this account.
     *
     * Kept out of [NotificationPrefs] and its bulk save because it is not a
     * user setting: it is written once, by that migration, and a bulk save that
     * carried a stale copy could re-run the migration or wrongly suppress it.
     */
    suspend fun hasRunGlobalToCuratedMigration(): Boolean = read()[globalToCuratedMigrated] ?: false

    suspend fun markGlobalToCuratedMigrated() {
        store.edit { prefs -> prefs[globalToCuratedMigrated] = true }
    }

    /**
     * The per-route read markers, as JSON.
     *
     * Written on its own path as the user reads things, at a different cadence
     * from the settings above, so it is not part of [save].
     */
    suspend fun lastReadPerRoute(): String? = read()[lastReadPerRouteJson]

    suspend fun saveLastReadPerRoute(json: String) {
        store.edit { prefs -> prefs[lastReadPerRouteJson] = json }
    }

    /** Writes the whole group in one edit, so a crash cannot half-apply it. */
    suspend fun save(value: NotificationPrefs) {
        store.edit { prefs ->
            prefs[alwaysOnService] = value.alwaysOnService
            prefs[showMessagesInNotifications] = value.showMessagesInNotifications
            prefs[splitNotificationsEnabled] = value.splitNotificationsEnabled
        }
    }
}
