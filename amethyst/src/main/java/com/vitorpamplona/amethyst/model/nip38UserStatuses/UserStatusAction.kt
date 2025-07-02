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
package com.vitorpamplona.amethyst.model.nip38UserStatuses

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip38UserStatus.StatusEvent

class UserStatusAction {
    companion object {
        fun create(
            newStatus: String,
            signer: NostrSigner,
            onDone: (StatusEvent) -> Unit,
        ) {
            if (!signer.isWriteable()) return

            StatusEvent.create(newStatus, "general", expiration = null, signer, onReady = onDone)
        }

        fun update(
            oldStatus: AddressableNote,
            newStatus: String,
            signer: NostrSigner,
            onDone: (StatusEvent) -> Unit,
        ) {
            if (!signer.isWriteable()) return
            val oldEvent = oldStatus.event as? StatusEvent ?: return

            StatusEvent.update(oldEvent, newStatus, signer, onReady = onDone)
        }

        fun delete(
            oldStatus: AddressableNote,
            signer: NostrSigner,
            onDone: (Event) -> Unit,
        ) {
            if (!signer.isWriteable()) return
            val oldEvent = oldStatus.event as? StatusEvent ?: return

            StatusEvent.clear(oldEvent, signer) { event ->
                onDone(event)

                signer.sign(
                    DeletionEvent.buildForVersionOnly(listOf(event)),
                    onReady = onDone,
                )
            }
        }
    }
}
