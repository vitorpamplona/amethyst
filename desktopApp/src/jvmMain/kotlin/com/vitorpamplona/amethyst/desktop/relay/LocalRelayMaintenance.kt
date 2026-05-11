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
package com.vitorpamplona.amethyst.desktop.relay

import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.prefs.Preferences

class LocalRelayMaintenance(
    private val store: LocalRelayStore,
    private val scope: CoroutineScope,
) {
    private var maintenanceJob: Job? = null

    fun start() {
        maintenanceJob?.cancel()
        maintenanceJob =
            scope.launch(Dispatchers.IO) {
                // Startup maintenance
                try {
                    store.deleteExpiredEvents()
                    maybeVacuum()
                    store.checkDiskSpace()
                } catch (e: Exception) {
                    Log.w("LocalRelayMaintenance") { "Startup maintenance: ${e.message}" }
                }

                // Periodic maintenance (every 6 hours)
                while (isActive) {
                    delay(6 * 60 * 60 * 1000L)
                    try {
                        store.deleteExpiredEvents()
                        store.pruneOldEvents(maxAgeDays = 30)
                        store.checkDiskSpace()
                        store.refreshStats()
                    } catch (e: Exception) {
                        Log.w("LocalRelayMaintenance") { "Periodic maintenance: ${e.message}" }
                    }
                }
            }
    }

    private suspend fun maybeVacuum() {
        val prefs = Preferences.userRoot().node("amethyst/localrelay")
        val lastVacuum = prefs.getLong("lastVacuum", 0)
        val sevenDays = 7 * 24 * 60 * 60 * 1000L
        if (System.currentTimeMillis() - lastVacuum > sevenDays) {
            store.vacuum()
            prefs.putLong("lastVacuum", System.currentTimeMillis())
        }
    }

    fun stop() {
        maintenanceJob?.cancel()
        maintenanceJob = null
    }
}
