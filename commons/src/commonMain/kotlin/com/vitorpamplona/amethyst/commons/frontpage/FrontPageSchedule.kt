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
package com.vitorpamplona.amethyst.commons.frontpage

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Each account's morning schedule. Kept on this device: a schedule is about this device's clock. */
interface FrontPageScheduleStore {
    fun flow(account: HexKey): Flow<FrontPageSchedule>

    suspend fun get(account: HexKey): FrontPageSchedule = flow(account).first()

    suspend fun set(
        account: HexKey,
        schedule: FrontPageSchedule,
    )

    /** Every account with a schedule switched on, for a scheduler waking up cold. */
    suspend fun enabled(): Map<HexKey, FrontPageSchedule>
}

/**
 * Starts or stops the morning edition on a platform: WorkManager on Android, a timer while the
 * app runs on desktop. Called whenever a schedule changes, and after each scheduled run.
 */
interface FrontPageScheduler {
    fun reschedule(
        account: HexKey,
        schedule: FrontPageSchedule,
    )

    object None : FrontPageScheduler {
        override fun reschedule(
            account: HexKey,
            schedule: FrontPageSchedule,
        ) {}
    }
}

/** In the app-wide shared settings file: `frontpage.schedule.<pubkey>` = `on|7|30`. */
class DataStoreFrontPageScheduleStore(
    private val stores: AppPreferenceStores,
) : FrontPageScheduleStore {
    private val store get() = stores.getDataStore(AppPreferenceStores.SHARED_SETTINGS)

    private fun keyFor(account: HexKey) = stringPreferencesKey(PREFIX + account.lowercase())

    override fun flow(account: HexKey): Flow<FrontPageSchedule> = store.data.map { decode(it[keyFor(account)]) }

    override suspend fun set(
        account: HexKey,
        schedule: FrontPageSchedule,
    ) {
        store.edit { it[keyFor(account)] = encode(schedule) }
    }

    override suspend fun enabled(): Map<HexKey, FrontPageSchedule> =
        store.data
            .first()
            .asMap()
            .mapNotNull { (key, value) ->
                val account = key.name.removePrefix(PREFIX).takeIf { key.name.startsWith(PREFIX) } ?: return@mapNotNull null
                val schedule = decode(value as? String)
                if (schedule.enabled) account to schedule else null
            }.toMap()

    companion object {
        private const val PREFIX = "frontpage.schedule."

        fun encode(schedule: FrontPageSchedule) = "${if (schedule.enabled) "on" else "off"}|${schedule.hour}|${schedule.minute}"

        fun decode(raw: String?): FrontPageSchedule {
            val parts = raw?.split('|') ?: return FrontPageSchedule()
            if (parts.size != 3) return FrontPageSchedule()
            val hour = parts[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return FrontPageSchedule()
            val minute = parts[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return FrontPageSchedule()
            return FrontPageSchedule(parts[0] == "on", hour, minute)
        }
    }
}
