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
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    /** Whether this install already moved [account]'s old local schedule to the synced one (or found one there). */
    suspend fun isMigrated(account: HexKey): Boolean = false

    suspend fun setMigrated(account: HexKey) {}

    suspend fun forgetMigrated(account: HexKey) {}
}

/**
 * Starts or stops the morning edition on a platform: WorkManager on Android, a timer while the
 * app runs on desktop. Called whenever a schedule changes, and after each scheduled run.
 */
interface FrontPageScheduler {
    /** Schedules [account]'s next run of [schedule] on this platform, or cancels it when off. */
    fun reschedule(
        account: HexKey,
        schedule: FrontPageSchedule,
    )

    /**
     * Runs [account]'s scheduled print now: at launch, an occurrence may have been missed while
     * the app was closed ([FrontPageScheduleWatcher]). The claim decides whether there is
     * anything left to do.
     */
    fun runNow(account: HexKey) {}

    /** [account] is gone: its jobs go, a print that is running too. */
    fun forget(account: HexKey) = reschedule(account, FrontPageSchedule())

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

    private fun migratedKey(account: HexKey) = stringPreferencesKey(MIGRATED_PREFIX + account.lowercase())

    override suspend fun isMigrated(account: HexKey): Boolean = store.data.first()[migratedKey(account)] != null

    override suspend fun setMigrated(account: HexKey) {
        store.edit { it[migratedKey(account)] = "1" }
    }

    override suspend fun forgetMigrated(account: HexKey) {
        store.edit { it.remove(migratedKey(account)) }
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
        private const val MIGRATED_PREFIX = "frontpage.migrated."

        /** The old per-device format, `on|7|30`: only a daily time fits it, anything else is off. */
        fun encode(schedule: FrontPageSchedule): String {
            val simple = schedule.simple()?.takeIf { it.weekdays == null } ?: return "off|7|0"
            return "${if (schedule.enabled) "on" else "off"}|${simple.hour}|${simple.minute}"
        }

        fun decode(raw: String?): FrontPageSchedule {
            val parts = raw?.split('|') ?: return FrontPageSchedule()
            if (parts.size != 3) return FrontPageSchedule()
            val hour = parts[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return FrontPageSchedule()
            val minute = parts[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return FrontPageSchedule()
            // `on|7|30` is "30 7 * * *" on this device's clock.
            return FrontPageSchedule.daily(parts[0] == "on", hour, minute)
        }
    }
}

/**
 * This install's id for each account (NIP-FB `claim.id`): random, made on first use and kept in
 * this install's own settings, never synced or backed up, so no other device and no other account
 * signed in here ever has it.
 */
interface FrontPageInstallStore {
    suspend fun id(account: HexKey): String

    /** A removed account's id goes with it: signing in again makes a new one. */
    suspend fun forget(account: HexKey)
}

class DataStoreFrontPageInstallStore(
    private val stores: AppPreferenceStores,
) : FrontPageInstallStore {
    private val store get() = stores.getDataStore(AppPreferenceStores.SHARED_SETTINGS)

    private fun keyFor(account: HexKey) = stringPreferencesKey(PREFIX + account.lowercase())

    private val making = Mutex()

    override suspend fun id(account: HexKey): String {
        store.data.first()[keyFor(account)]?.let { return it }
        return making.withLock {
            var made = ""
            store.edit { prefs ->
                made = prefs[keyFor(account)] ?: newId().also { prefs[keyFor(account)] = it }
            }
            made
        }
    }

    override suspend fun forget(account: HexKey) {
        store.edit { it.remove(keyFor(account)) }
    }

    companion object {
        private const val PREFIX = "frontpage.install."

        /** 16 random bytes as hex: never guessed, never shared by two installs. */
        fun newId(): String = RandomInstance.bytes(16).toHexKey()
    }
}
