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
package com.vitorpamplona.amethyst.model.nipA3PaymentTargets

import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.NoteState
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTargetsEvent
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NipA3PaymentTargetsState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    val nipA3PaymentTargetsNote = cache.getOrCreateAddressableNote(getNipA3PaymentTargetsState())

    fun getNipA3PaymentTargetsFlow(): StateFlow<NoteState> = nipA3PaymentTargetsNote.flow().metadata.stateFlow

    fun getNipA3PaymentTargetsState() = PaymentTargetsEvent.createAddress(signer.pubKey)

    init {
        settings.backupNipA3PaymentTargets?.let {
            Log.d("AccountRegisterObservers", "Loading saved nipA3 Payment targets ${it.toJson()}")
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) { cache.justConsumeMyOwnEvent(it) }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "nipA3 Payment targets Collector Start")
            getNipA3PaymentTargetsFlow().collect {
                Log.d("AccountRegisterObservers", "Updating nipA3 Payment targets for ${signer.pubKey}")
                (it.note.event as? PaymentTargetsEvent)?.let { paymentTargetsEvent ->
                    settings.updateNIPA3PaymentTargets(paymentTargetsEvent)
                }
            }
        }
    }
}
