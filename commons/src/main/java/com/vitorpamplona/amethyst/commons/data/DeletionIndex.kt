/**
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
package com.vitorpamplona.amethyst.commons.data

import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.AddressableEvent
import com.vitorpamplona.quartz.events.DeletionEvent
import com.vitorpamplona.quartz.events.Event

class DeletionIndex {
    data class DeletionRequest(val reference: String, val publicKey: HexKey) : Comparable<DeletionRequest> {
        override fun compareTo(other: DeletionRequest): Int {
            val compared = reference.compareTo(other.reference)

            return if (compared == 0) {
                publicKey.compareTo(publicKey)
            } else {
                compared
            }
        }
    }

    // stores a set of id OR atags (kind:pubkey:dtag) by pubkey with the created at of the deletion event.
    // Anything newer than the date should not be deleted.
    private val deletedReferencesBefore = LargeCache<DeletionRequest, Long>()

    fun add(event: DeletionEvent): Boolean {
        var atLeastOne = false

        event.tags.forEach {
            if (it.size > 1 && (it[0] == "a" || it[0] == "e")) {
                if (add(it[1], event.pubKey, event.createdAt)) {
                    atLeastOne = true
                }
            }
        }

        return atLeastOne
    }

    private fun add(
        ref: String,
        byPubKey: HexKey,
        createdAt: Long,
    ): Boolean {
        val key = DeletionRequest(ref, byPubKey)
        val previousDeletionTime = deletedReferencesBefore.get(key)

        if (previousDeletionTime == null || createdAt > previousDeletionTime) {
            deletedReferencesBefore.put(key, createdAt)
            return true
        }
        return false
    }

    fun hasBeenDeleted(event: Event): Boolean {
        val key = DeletionRequest(event.id, event.pubKey)
        if (hasBeenDeleted(key)) return true

        if (event is AddressableEvent) {
            if (hasBeenDeleted(DeletionRequest(event.addressTag(), event.pubKey), event.createdAt)) return true
        }

        return false
    }

    private fun hasBeenDeleted(key: DeletionRequest) = deletedReferencesBefore.containsKey(key)

    private fun hasBeenDeleted(
        key: DeletionRequest,
        createdAt: Long,
    ): Boolean {
        val deletionTime = deletedReferencesBefore.get(key)
        return deletionTime != null && createdAt <= deletionTime
    }
}
