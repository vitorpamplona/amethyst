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
 * Which feeds this account shows and how they are laid out.

 * The two "disabled" keys store what is switched OFF, not what is on, so an
 * absent value means everything is enabled and a feed type added later defaults
 * to on for accounts that customised before it existed.
 *
 * Defaults match what the SharedPreferences implementation returned for a
 * missing key, so an account that never touched a setting behaves identically
 * before and after the migration.
 */
data class FeedVisibility(
    val disabledChatFeeds: String? = null,
    val disabledHomeFeedTypes: String? = null,
    val relayGroupViewMode: String? = null,
    val concordViewMode: String? = null,
    val callsEnabled: Boolean = true,
)

/** Reads and writes [FeedVisibility] in the account's DataStore. */
class FeedVisibilityStore(
    private val store: DataStore<Preferences>,
) {
    companion object {
        val disabledChatFeeds = stringPreferencesKey("disabled_chat_feeds")
        val disabledHomeFeedTypes = stringPreferencesKey("disabled_home_feed_types")
        val relayGroupViewMode = stringPreferencesKey("relay_group_view_mode")
        val concordViewMode = stringPreferencesKey("concord_view_mode")
        val callsEnabled = booleanPreferencesKey("calls_enabled")
    }

    suspend fun load(): FeedVisibility {
        val prefs =
            store.data
                .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
                .first()

        return FeedVisibility(
            disabledChatFeeds = prefs[disabledChatFeeds],
            disabledHomeFeedTypes = prefs[disabledHomeFeedTypes],
            relayGroupViewMode = prefs[relayGroupViewMode],
            concordViewMode = prefs[concordViewMode],
            callsEnabled = prefs[callsEnabled] ?: true,
        )
    }

    /** Writes the whole group in one edit, so a crash cannot half-apply it. */
    suspend fun save(value: FeedVisibility) {
        store.edit { prefs ->
            value.disabledChatFeeds.let { if (it != null) prefs[disabledChatFeeds] = it else prefs.remove(disabledChatFeeds) }
            value.disabledHomeFeedTypes.let { if (it != null) prefs[disabledHomeFeedTypes] = it else prefs.remove(disabledHomeFeedTypes) }
            value.relayGroupViewMode.let { if (it != null) prefs[relayGroupViewMode] = it else prefs.remove(relayGroupViewMode) }
            value.concordViewMode.let { if (it != null) prefs[concordViewMode] = it else prefs.remove(concordViewMode) }
            prefs[callsEnabled] = value.callsEnabled
        }
    }
}
