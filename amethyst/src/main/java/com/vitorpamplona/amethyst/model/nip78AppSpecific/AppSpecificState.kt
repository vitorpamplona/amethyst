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
package com.vitorpamplona.amethyst.model.nip78AppSpecific

import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.AccountSyncedSettingsInternal
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

class AppSpecificState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    companion object {
        const val APP_SPECIFIC_DATA_D_TAG = "AmethystSettings"

        /**
         * The `created_at` for the next version of a replaceable event: one second past the newest
         * version we already know about, or the wall clock when that is already ahead.
         *
         * `created_at` is whole seconds, and the settings pickers republish this event on every
         * discrete toggle, so two edits a few hundred milliseconds apart would otherwise carry the
         * *same* timestamp. That silently loses the second edit: [LocalCache] keeps the strictly
         * newest version per address, so it drops the republish, the app-specific-data collector
         * never fires, `backupAppSpecificData` keeps the first edit, and the next app start restores
         * that. Relays are no safer — NIP-01 breaks a `created_at` tie by lowest id, so which of the
         * two edits survives there is arbitrary.
         */
        fun nextCreatedAt(
            newestKnown: Long,
            now: Long,
        ): Long = (newestKnown + 1).coerceAtLeast(now)
    }

    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val amethystSettingsNote = cache.getOrCreateAddressableNote(getAppSpecificDataAddress())

    fun getAppSpecificDataAddress() = AppSpecificDataEvent.createAddress(signer.pubKey, APP_SPECIFIC_DATA_D_TAG)

    fun getAppSpecificDataFlow(): StateFlow<NoteState> = amethystSettingsNote.flow().metadata.stateFlow

    /**
     * Serializes the state snapshot and the timestamp it is stamped with. Two rapid toggles publish
     * from separate coroutines on the signer's dispatcher; without this they could read the same
     * previous timestamp and collide again, or take timestamps in the opposite order to the state
     * they captured. Signing and encrypting stay outside the lock — those can wait on an external
     * signer, and they don't affect ordering.
     */
    private val stampOrder = Mutex()

    /**
     * The newest version this instance has published, which is not always in [amethystSettingsNote]
     * yet: the cache is only updated once the event comes back through the broadcaster.
     */
    private var lastPublishedAt = 0L

    suspend fun saveNewAppSpecificData(): AppSpecificDataEvent {
        val toInternal: AccountSyncedSettingsInternal
        val createdAt: Long

        stampOrder.withLock {
            toInternal = settings.syncedSettings.toInternal(settings.mutedPublicChats.value)
            createdAt =
                nextCreatedAt(
                    newestKnown = maxOf(lastPublishedAt, amethystSettingsNote.event?.createdAt ?: 0L),
                    now = TimeUtils.now(),
                )
            lastPublishedAt = createdAt
        }

        return signer.sign(
            AppSpecificDataEvent.build(
                dTag = APP_SPECIFIC_DATA_D_TAG,
                description = signer.nip44Encrypt(JsonMapper.toJson(toInternal), signer.pubKey),
                createdAt = createdAt,
            ),
        )
    }

    init {
        if (settings.isWriteable()) {
            settings.backupAppSpecificData?.let { event ->
                Log.d("AccountRegisterObservers") { "Loading saved app specific data ${event.toJson()}" }
                @OptIn(DelicateCoroutinesApi::class)
                scope.launch(Dispatchers.IO) {
                    LocalCache.justConsumeMyOwnEvent(event)
                    try {
                        val decrypted = signer.decrypt(event.content, event.pubKey)
                        val syncedSettings = JsonMapper.fromJson<AccountSyncedSettingsInternal>(decrypted)
                        settings.syncedSettings.updateFrom(syncedSettings)
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        Log.w("LocalPreferences", "Error Decoding latestAppSpecificData from Preferences", e)
                    }
                }
            }

            scope.launch(Dispatchers.IO) {
                Log.d("AccountRegisterObservers", "AppSpecificData Collector Start")
                getAppSpecificDataFlow().collect {
                    try {
                        Log.d("AccountRegisterObservers") { "Updating AppSpecificData for ${signer.pubKey}" }
                        (it.note.event as? AppSpecificDataEvent)?.let {
                            val decrypted = signer.decrypt(it.content, it.pubKey)
                            try {
                                val syncedSettings = JsonMapper.fromJson<AccountSyncedSettingsInternal>(decrypted)
                                settings.updateAppSpecificData(it, syncedSettings)
                            } catch (e: Throwable) {
                                if (e is CancellationException) throw e
                                Log.w("LocalPreferences", "Error Decoding latestAppSpecificData from Preferences", e)
                            }
                        }
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        Log.w("LocalPreferences", "Error Decrypting latestAppSpecificData from Preferences", e)
                    }
                }
            }
        }
    }
}
