/**
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
package com.vitorpamplona.amethyst.model.preferences

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Stable
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.model.UiSettings
import com.vitorpamplona.amethyst.model.UiSettingsFlow
import com.vitorpamplona.amethyst.ui.tor.TorSettings
import com.vitorpamplona.amethyst.ui.tor.TorSettingsFlow
import com.vitorpamplona.quartz.nip01Core.jackson.JsonMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

val Context.sharedPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "shared_settings")

@Stable
class UiSharedPreferences(
    val context: Context,
    val scope: CoroutineScope,
) {
    companion object {
        val UI_SETTINGS = stringPreferencesKey("ui_settings")
    }

    // UI Preferences. Makes sure to wait for it to avoid blinking themes and language preferences
    val value =
        runBlocking {
            UiSettingsFlow.build(uiPreferences() ?: UiSettings())
        }

    val languageUpdate =
        value.preferredLanguage
            .onEach { language ->
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(language),
                )
            }.flowOn(Dispatchers.Main)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                value.toSettings(),
            )

    @OptIn(FlowPreview::class)
    val saving =
        value.propertyWatchFlow
            .debounce(1000)
            .distinctUntilChanged()
            .onEach(::save)
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                value.toSettings(),
            )

    suspend fun uiPreferences(): UiSettings? =
        try {
            // Get the preference flow and take the first value.
            val preferences = context.sharedPreferencesDataStore.data.first()
            val newVersion = preferences[UI_SETTINGS]?.let { JsonMapper.mapper.readValue<UiSettings>(it) }

            if (newVersion != null) {
                newVersion
            } else {
                val oldVersion = LocalPreferences.loadSharedSettings()
                if (oldVersion != null) {
                    save(oldVersion)
                }
                oldVersion
            }
        } catch (e: Exception) {
            // Log any errors that occur while reading the DataStore.
            Log.e("SharedPreferences", "Error reading DataStore preferences: ${e.message}")
            null
        }

    suspend fun save(sharedSettings: UiSettings) {
        try {
            val str = JsonMapper.mapper.writeValueAsString(sharedSettings)
            context.sharedPreferencesDataStore.edit { preferences ->
                preferences[UI_SETTINGS] = str
            }
        } catch (e: Exception) {
            // Log any errors that occur while reading the DataStore.
            Log.e("SharedPreferences", "Error saving DataStore preferences: ${e.message}")
        }
    }
}

@Stable
class TorSharedPreferences(
    val context: Context,
    val scope: CoroutineScope,
) {
    companion object {
        val TOR_SETTINGS = stringPreferencesKey("tor_settings")
    }

    // Tor Preferences. Makes sure to wait for it to avoid connecting with random IPs
    val value =
        runBlocking {
            TorSettingsFlow.build(torPreferences() ?: TorSettings())
        }

    @OptIn(FlowPreview::class)
    val saving =
        value.propertyWatchFlow
            .debounce(1000)
            .distinctUntilChanged()
            .onEach(::save)
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                value.toSettings(),
            )

    suspend fun torPreferences(): TorSettings? =
        try {
            // Get the preference flow and take the first value.
            val preferences = context.sharedPreferencesDataStore.data.first()
            println("AABBCC Loading TorSettings: ${preferences[TOR_SETTINGS]}")
            preferences[TOR_SETTINGS]?.let {
                println("AABBCC Loading TorSettings: $it")
                JsonMapper.mapper.readValue<TorSettings>(it)
            }
        } catch (e: Exception) {
            // Log any errors that occur while reading the DataStore.
            Log.e("SharedPreferences", "Error reading DataStore preferences: ${e.message}")
            null
        }

    suspend fun save(torSettings: TorSettings) {
        try {
            println("AABBCC Saving TorSettings: $torSettings")
            val str = JsonMapper.mapper.writeValueAsString(torSettings)
            context.sharedPreferencesDataStore.edit { preferences ->
                preferences[TOR_SETTINGS] = str
            }
        } catch (e: Exception) {
            // Log any errors that occur while reading the DataStore.
            Log.e("SharedPreferences", "Error saving DataStore preferences: ${e.message}")
        }
    }
}
