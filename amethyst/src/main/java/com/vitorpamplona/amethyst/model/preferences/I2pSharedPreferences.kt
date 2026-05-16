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
package com.vitorpamplona.amethyst.model.preferences

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.i2p.I2pSettings
import com.vitorpamplona.amethyst.commons.i2p.I2pType
import com.vitorpamplona.amethyst.ui.i2p.I2pSettingsFlow
import com.vitorpamplona.quartz.utils.Log
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
import kotlin.coroutines.cancellation.CancellationException

@Stable
class I2pSharedPreferences(
    prefs: I2pSettings,
    val context: Context,
    val scope: CoroutineScope,
) {
    val value = I2pSettingsFlow.build(prefs)

    @OptIn(FlowPreview::class)
    val saving =
        value.propertyWatchFlow
            .debounce(1000)
            .distinctUntilChanged()
            .onEach {
                save(it, context)
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                value.toSettings(),
            )

    companion object {
        val I2P_TYPE_KEY = stringPreferencesKey("i2p.i2pType")
        val EXTERNAL_SOCKS_PORT_KEY = intPreferencesKey("i2p.externalSocksPort")
        val I2P_RELAYS_VIA_I2P_KEY = booleanPreferencesKey("i2p.i2pRelaysViaI2p")
        val DM_RELAYS_VIA_I2P_KEY = booleanPreferencesKey("i2p.dmRelaysViaI2p")
        val NEW_RELAYS_VIA_I2P_KEY = booleanPreferencesKey("i2p.newRelaysViaI2p")
        val TRUSTED_RELAYS_VIA_I2P_KEY = booleanPreferencesKey("i2p.trustedRelaysViaI2p")
        val URL_PREVIEWS_VIA_I2P_KEY = booleanPreferencesKey("i2p.urlPreviewsViaI2p")
        val PROFILE_PICS_VIA_I2P_KEY = booleanPreferencesKey("i2p.profilePicsViaI2p")
        val IMAGES_VIA_I2P_KEY = booleanPreferencesKey("i2p.imagesViaI2p")
        val VIDEOS_VIA_I2P_KEY = booleanPreferencesKey("i2p.videosViaI2p")
        val MONEY_OPERATIONS_VIA_I2P_KEY = booleanPreferencesKey("i2p.moneyOperationsViaI2p")
        val NIP05_VERIFICATIONS_VIA_I2P_KEY = booleanPreferencesKey("i2p.nip05VerificationsViaI2p")
        val MEDIA_UPLOADS_VIA_I2P_KEY = booleanPreferencesKey("i2p.mediaUploadsViaI2p")

        suspend fun i2pPreferences(context: Context): I2pSettings? =
            try {
                val preferences = context.sharedPreferencesDataStore.data.first()
                I2pSettings(
                    i2pType = preferences[I2P_TYPE_KEY]?.let { I2pType.valueOf(it) } ?: I2pType.OFF,
                    externalSocksPort = preferences[EXTERNAL_SOCKS_PORT_KEY] ?: 4447,
                    i2pRelaysViaI2p = preferences[I2P_RELAYS_VIA_I2P_KEY] ?: true,
                    dmRelaysViaI2p = preferences[DM_RELAYS_VIA_I2P_KEY] ?: false,
                    newRelaysViaI2p = preferences[NEW_RELAYS_VIA_I2P_KEY] ?: false,
                    trustedRelaysViaI2p = preferences[TRUSTED_RELAYS_VIA_I2P_KEY] ?: false,
                    urlPreviewsViaI2p = preferences[URL_PREVIEWS_VIA_I2P_KEY] ?: false,
                    profilePicsViaI2p = preferences[PROFILE_PICS_VIA_I2P_KEY] ?: false,
                    imagesViaI2p = preferences[IMAGES_VIA_I2P_KEY] ?: false,
                    videosViaI2p = preferences[VIDEOS_VIA_I2P_KEY] ?: false,
                    moneyOperationsViaI2p = preferences[MONEY_OPERATIONS_VIA_I2P_KEY] ?: false,
                    nip05VerificationsViaI2p = preferences[NIP05_VERIFICATIONS_VIA_I2P_KEY] ?: false,
                    mediaUploadsViaI2p = preferences[MEDIA_UPLOADS_VIA_I2P_KEY] ?: false,
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("SharedPreferences") { "Error reading I2P DataStore preferences: ${e.message}" }
                null
            }

        suspend fun save(
            settings: I2pSettings,
            context: Context,
        ) {
            try {
                context.sharedPreferencesDataStore.edit { preferences ->
                    preferences[I2P_TYPE_KEY] = settings.i2pType.name
                    preferences[EXTERNAL_SOCKS_PORT_KEY] = settings.externalSocksPort
                    preferences[I2P_RELAYS_VIA_I2P_KEY] = settings.i2pRelaysViaI2p
                    preferences[DM_RELAYS_VIA_I2P_KEY] = settings.dmRelaysViaI2p
                    preferences[NEW_RELAYS_VIA_I2P_KEY] = settings.newRelaysViaI2p
                    preferences[TRUSTED_RELAYS_VIA_I2P_KEY] = settings.trustedRelaysViaI2p
                    preferences[URL_PREVIEWS_VIA_I2P_KEY] = settings.urlPreviewsViaI2p
                    preferences[PROFILE_PICS_VIA_I2P_KEY] = settings.profilePicsViaI2p
                    preferences[IMAGES_VIA_I2P_KEY] = settings.imagesViaI2p
                    preferences[VIDEOS_VIA_I2P_KEY] = settings.videosViaI2p
                    preferences[MONEY_OPERATIONS_VIA_I2P_KEY] = settings.moneyOperationsViaI2p
                    preferences[NIP05_VERIFICATIONS_VIA_I2P_KEY] = settings.nip05VerificationsViaI2p
                    preferences[MEDIA_UPLOADS_VIA_I2P_KEY] = settings.mediaUploadsViaI2p
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("SharedPreferences") { "Error saving I2P DataStore preferences: ${e.message}" }
            }
        }
    }
}
