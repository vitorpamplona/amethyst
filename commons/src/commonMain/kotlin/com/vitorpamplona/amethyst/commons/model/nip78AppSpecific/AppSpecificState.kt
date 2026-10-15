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
package com.vitorpamplona.amethyst.commons.model.nip78AppSpecific

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.AccountSyncedSettingsInternal
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.keepEditsOverDefaults
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.core.awaitCreatedAtToSupersede
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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

        private const val TAG = "AppSpecificState"

        /** Waits between attempts to read a version the signer could not decrypt. */
        private val RETRY_DELAYS_MS = longArrayOf(1_000, 2_000, 5_000, 10_000, 30_000, 60_000, 120_000, 300_000)
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

    /** Guards [loadedAt]. Held while a decrypted version is applied, never while decrypting. */
    private val loadOrder = Mutex()

    /**
     * The created_at of the version whose settings are in memory, or -1 while memory still holds
     * the defaults.
     *
     * The synced settings have no local copy besides the encrypted event, so they start at the
     * defaults and only become the user's once a version is decrypted. That can fail at startup
     * (an external signer that refuses to work in the background when a push wakes the app, a
     * bunker that is still connecting), and it can finish late. This tracks what got applied so a
     * failed read is retried, a late one never rolls a newer version back, and a publish never
     * goes out with the defaults in place of settings it could not read.
     */
    private var loadedAt = -1L

    private suspend fun hasLoaded(event: AppSpecificDataEvent) = loadOrder.withLock { loadedAt >= event.createdAt }

    suspend fun saveNewAppSpecificData(): AppSpecificDataEvent {
        val (toInternal, createdAt) =
            stampOrder.withLock {
                catchUpBeforePublishing()
                val snapshot = settings.syncedSettings.toInternal(settings.mutedPublicChats.value)
                val stamp = awaitCreatedAtToSupersede(maxOf(lastPublishedAt, amethystSettingsNote.event?.createdAt ?: 0L))
                lastPublishedAt = stamp
                // What is in memory now supersedes every version up to this one.
                loadOrder.withLock { loadedAt = maxOf(loadedAt, stamp) }
                snapshot to stamp
            }

        return signer.sign(
            AppSpecificDataEvent.build(
                dTag = APP_SPECIFIC_DATA_D_TAG,
                description = signer.nip44Encrypt(JsonMapper.toJson(toInternal), signer.pubKey),
                createdAt = createdAt,
            ),
        )
    }

    /**
     * Reads the saved settings before an edit is published on top of the defaults.
     *
     * When no version could be decrypted yet, memory holds the defaults plus the edit that is being
     * published, and publishing that as is would reset every other synced setting. The edits are
     * laid over the saved version instead. If the signer still can't decrypt it, this throws and the
     * publish stops: losing one edit is better than wiping the rest.
     */
    private suspend fun catchUpBeforePublishing() {
        val newest = (amethystSettingsNote.event as? AppSpecificDataEvent) ?: settings.backupAppSpecificData ?: return
        if (newest.content.isBlank() || loadOrder.withLock { loadedAt >= 0 }) return

        val stored = decode(signer.decrypt(newest.content, newest.pubKey)) ?: return
        val local = settings.syncedSettings.toInternal(settings.mutedPublicChats.value)
        apply(newest, keepEditsOverDefaults(local, stored))
    }

    /** Puts [synced], read from [event], in memory unless a newer version is already there. */
    private suspend fun apply(
        event: AppSpecificDataEvent,
        synced: AccountSyncedSettingsInternal,
    ) = loadOrder.withLock {
        if (event.createdAt < loadedAt) return@withLock

        if (event.id == settings.backupAppSpecificData?.id) {
            // The saved copy itself, which the backup guard in updateAppSpecificData skips
            // because it is already the backup.
            settings.syncedSettings.updateFrom(synced)
            loadedAt = event.createdAt
        } else {
            settings.updateAppSpecificData(event, synced)
            // Only counts when the guard took it: a version held back as a conflict leaves the
            // saved one in force.
            if (settings.backupAppSpecificData?.id == event.id) loadedAt = event.createdAt
        }
    }

    private suspend fun load(event: AppSpecificDataEvent) {
        // A blob another app emptied has nothing to decrypt, so it used to die in the decrypt
        // — yet an empty version is exactly the wipe the backup guard exists to question, and
        // [AccountSettings] already accepts one (`isEmpty`). Without this it never got there:
        // the guard was never called, no conflict was raised, and the NIP-78 card could not
        // appear for the case it was written for.
        //
        // The settings handed over are never merged — updateAppSpecificData returns before
        // that whenever the content is empty — so defaults here cannot overwrite the real ones.
        if (event.content.isBlank()) {
            settings.updateAppSpecificData(event, AccountSyncedSettingsInternal())
            return
        }

        val decrypted = decryptRetrying(event) ?: return
        val synced = decode(decrypted) ?: return
        apply(event, synced)
    }

    /**
     * Decrypts [event], retrying while the signer can't. Gives up once a version at least as new
     * is in memory (a publish caught up, see [catchUpBeforePublishing]); a newer event arriving
     * cancels it through [collectLatest].
     */
    private suspend fun decryptRetrying(event: AppSpecificDataEvent): String? {
        var attempt = 0
        while (true) {
            try {
                return signer.decrypt(event.content, event.pubKey)
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                if (hasLoaded(event)) return null
                val wait = RETRY_DELAYS_MS[minOf(attempt, RETRY_DELAYS_MS.lastIndex)]
                Log.w(TAG, "Could not decrypt the synced settings, retrying in $wait ms", e)
                attempt++
                delay(wait)
            }
        }
    }

    private fun decode(json: String): AccountSyncedSettingsInternal? =
        try {
            JsonMapper.fromJson<AccountSyncedSettingsInternal>(json)
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Error Decoding the synced settings", e)
            null
        }

    init {
        if (settings.isWriteable()) {
            scope.launch(Dispatchers.IO) {
                settings.backupAppSpecificData?.let { event ->
                    Log.d(TAG) { "Loading saved app specific data ${event.id}" }
                    // The collector below decrypts it like any other version, so a failed read
                    // is retried there and a newer version from a relay simply replaces it.
                    LocalCache.justConsumeMyOwnEvent(event)
                }

                Log.d(TAG, "AppSpecificData Collector Start")
                getAppSpecificDataFlow().collectLatest {
                    val event = it.note.event as? AppSpecificDataEvent ?: return@collectLatest
                    Log.d(TAG) { "Updating AppSpecificData for ${signer.pubKey}" }
                    load(event)

                    // A newer version held back as a conflict leaves the saved one in force, and
                    // it may never have been read if this one arrived first.
                    val saved = settings.backupAppSpecificData
                    if (saved != null && saved.id != event.id && !hasLoaded(saved)) load(saved)
                }
            }
        }
    }
}
