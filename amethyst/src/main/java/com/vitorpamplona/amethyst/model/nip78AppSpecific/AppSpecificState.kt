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
package com.vitorpamplona.amethyst.model.nip78AppSpecific

import android.util.Log
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.AccountSyncedSettingsInternal
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.quartz.nip01Core.jackson.JsonMapper
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class AppSpecificState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    companion object {
        const val APP_SPECIFIC_DATA_D_TAG = "AmethystSettings"
    }

    fun getAppSpecificDataAddress() = AppSpecificDataEvent.createAddress(signer.pubKey, APP_SPECIFIC_DATA_D_TAG)

    fun getAppSpecificDataNote() = cache.getOrCreateAddressableNote(getAppSpecificDataAddress())

    fun getAppSpecificDataFlow(): StateFlow<NoteState> = getAppSpecificDataNote().flow().metadata.stateFlow

    suspend fun saveNewAppSpecificData(): AppSpecificDataEvent {
        val toInternal = settings.syncedSettings.toInternal()
        return AppSpecificDataEvent.create(
            dTag = APP_SPECIFIC_DATA_D_TAG,
            description = signer.nip44Encrypt(JsonMapper.mapper.writeValueAsString(toInternal), signer.pubKey),
            otherTags = emptyArray(),
            signer = signer,
        )
    }

    init {
        settings.backupAppSpecificData?.let { event ->
            Log.d("AccountRegisterObservers", "Loading saved app specific data ${event.toJson()}")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) {
                LocalCache.justConsumeMyOwnEvent(event)
                try {
                    val decrypted = signer.decrypt(event.content, event.pubKey)
                    val syncedSettings = JsonMapper.mapper.readValue<AccountSyncedSettingsInternal>(decrypted)
                    settings.syncedSettings.updateFrom(syncedSettings)
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    Log.w("LocalPreferences", "Error Decoding latestAppSpecificData from Preferences with value", e)
                }
            }
        }

        scope.launch(Dispatchers.Default) {
            Log.d("AccountRegisterObservers", "AppSpecificData Collector Start")
            getAppSpecificDataFlow().collect {
                Log.d("AccountRegisterObservers", "Updating AppSpecificData for ${signer.pubKey}")
                (it.note.event as? AppSpecificDataEvent)?.let {
                    val decrypted = signer.decrypt(it.content, it.pubKey)
                    try {
                        val syncedSettings = JsonMapper.mapper.readValue<AccountSyncedSettingsInternal>(decrypted)
                        settings.updateAppSpecificData(it, syncedSettings)
                    } catch (e: Throwable) {
                        if (e is CancellationException) throw e
                        Log.w("LocalPreferences", "Error Decoding latestAppSpecificData from Preferences with value $decrypted", e)
                    }
                }
            }
        }
    }
}
