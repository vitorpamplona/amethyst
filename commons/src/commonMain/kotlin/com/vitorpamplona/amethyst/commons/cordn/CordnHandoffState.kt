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
package com.vitorpamplona.amethyst.commons.cordn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether this device has handed its cordn state to another one.
 *
 * ## The property that makes migration safe
 *
 * `multi-device.md` §10 has one unresolved case: two devices of an identity
 * committing inside a single delivery round-trip reach epoch N+1 with different
 * states, and §15 concedes *equal-epoch MLS states have no merge function*. The
 * whole reason migration is buildable where continuous sync is not is that a
 * handoff has one writer.
 *
 * That is only true if the old phone actually stops. Two phones holding one
 * leaf and both committing fork the ratchet tree, and MLS does not recover —
 * after the fork every message silently fails to decrypt for somebody. So the
 * handoff is not finished when the code is scanned; it is finished when this
 * device has stood down, and [handedOff] is what says so.
 *
 * ## Why it is reversible
 *
 * A migration can fail after the export: the other phone is out of battery,
 * the QR does not scan, the user changes their mind. Locking irreversibly
 * would strand an account on a device that still holds the only copy of its
 * state. [resume] exists for that, and is safe exactly while the new device
 * has not started committing — which is why the UI asks rather than assumes.
 *
 * ## What it does not do
 *
 * It does not tell the coordinator anything, and it does not delete anything.
 * The state stays on disk so [resume] can work, and so a user who migrated by
 * mistake has not lost their groups.
 */
class CordnHandoffState(
    private val store: CordnHandoffStore,
) {
    private val _handedOff = MutableStateFlow(false)

    /** True once this device has exported and stood down. */
    val handedOff: StateFlow<Boolean> = _handedOff.asStateFlow()

    /** Reads the flag off disk. Call before the first sync loop starts. */
    suspend fun restore() {
        _handedOff.value = store.load()
    }

    /**
     * Records that this device has handed off.
     *
     * The caller is responsible for having stopped the sync loops first: this
     * records a decision, it does not enforce one.
     */
    suspend fun markHandedOff() {
        store.save(true)
        _handedOff.value = true
    }

    /** Takes the device back, for a migration that did not complete. */
    suspend fun resume() {
        store.save(false)
        _handedOff.value = false
    }

    /**
     * Throws if this device has handed off.
     *
     * Called on every path that would advance an epoch. A read is still
     * allowed: showing the user the conversations they had is harmless, and
     * refusing it would make a failed migration look like data loss.
     */
    fun requireNotHandedOff() {
        if (_handedOff.value) {
            throw CordnHandedOffException(
                "this device handed its cordn groups to another one; " +
                    "sending from both would fork the group state",
            )
        }
    }
}

/** An attempt to write from a device that has handed off. */
class CordnHandedOffException(
    message: String,
) : Exception(message)

/** Where the handoff flag lives between runs. */
interface CordnHandoffStore {
    suspend fun load(): Boolean

    suspend fun save(handedOff: Boolean)
}
