/*
 * Copyright (c) 2024 Vitor Pamplona
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
import com.vitorpamplona.amethyst.model.TransactionPriority
import com.vitorpamplona.quartz.experimental.moneroTips.TipEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException

data class MoneroSettings(
    val daemon: String = "node.monerodevs.org:18089",
    val daemonUsername: String = "",
    val daemonPassword: String = "",
    val isSeedBackedUp: Boolean = false,
    val tipAmounts: List<Long> = listOf(10000000L, 50000000L, 100000000L),
    val defaultTipType: TipEvent.TipType = TipEvent.TipType.PUBLIC,
    val defaultTransactionPriority: TransactionPriority = TransactionPriority.UNIMPORTANT,
)

@Stable
class MoneroSharedPreferences(
    val context: Context,
) {
    companion object {
        val MONERO_DAEMON_KEY = stringPreferencesKey("monero.daemon")
        val MONERO_DAEMON_USERNAME_KEY = stringPreferencesKey("monero.daemonUsername")
        val MONERO_DAEMON_PASSWORD_KEY = stringPreferencesKey("monero.daemonPassword")
        val MONERO_SEED_BACKED_UP_KEY = booleanPreferencesKey("monero.isSeedBackedUp")
        val MONERO_TIP_AMOUNTS_KEY = stringPreferencesKey("monero.tipAmounts")
        val MONERO_DEFAULT_TIP_TYPE_KEY = stringPreferencesKey("monero.defaultTipType")
        val MONERO_DEFAULT_TX_PRIORITY_KEY = intPreferencesKey("monero.defaultTransactionPriority")
    }

    suspend fun load(): MoneroSettings =
        try {
            val preferences = context.sharedPreferencesDataStore.data.first()
            MoneroSettings(
                daemon = preferences[MONERO_DAEMON_KEY] ?: "node.monerodevs.org:18089",
                daemonUsername = preferences[MONERO_DAEMON_USERNAME_KEY] ?: "",
                daemonPassword = preferences[MONERO_DAEMON_PASSWORD_KEY] ?: "",
                isSeedBackedUp = preferences[MONERO_SEED_BACKED_UP_KEY] ?: false,
                tipAmounts = preferences[MONERO_TIP_AMOUNTS_KEY]?.split(",")?.mapNotNull { it.toLongOrNull() }
                    ?: listOf(10000000L, 50000000L, 100000000L),
                defaultTipType = preferences[MONERO_DEFAULT_TIP_TYPE_KEY]?.let {
                    try {
                        TipEvent.TipType.valueOf(it)
                    } catch (e: Exception) {
                        TipEvent.TipType.PUBLIC
                    }
                } ?: TipEvent.TipType.PUBLIC,
                defaultTransactionPriority = preferences[MONERO_DEFAULT_TX_PRIORITY_KEY]?.let {
                    TransactionPriority.entries.getOrNull(it)
                } ?: TransactionPriority.UNIMPORTANT,
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("MoneroPreferences", "Error reading Monero preferences: ${e.message}")
            MoneroSettings()
        }

    suspend fun save(settings: MoneroSettings) {
        try {
            context.sharedPreferencesDataStore.edit { preferences ->
                preferences[MONERO_DAEMON_KEY] = settings.daemon
                preferences[MONERO_DAEMON_USERNAME_KEY] = settings.daemonUsername
                preferences[MONERO_DAEMON_PASSWORD_KEY] = settings.daemonPassword
                preferences[MONERO_SEED_BACKED_UP_KEY] = settings.isSeedBackedUp
                preferences[MONERO_TIP_AMOUNTS_KEY] = settings.tipAmounts.joinToString(",")
                preferences[MONERO_DEFAULT_TIP_TYPE_KEY] = settings.defaultTipType.name
                preferences[MONERO_DEFAULT_TX_PRIORITY_KEY] = settings.defaultTransactionPriority.ordinal
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("MoneroPreferences", "Error saving Monero preferences: ${e.message}")
        }
    }
}
