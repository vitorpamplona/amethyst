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
package com.vitorpamplona.amethyst.commons.model.nip38UserStatuses

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.UserDependencies
import com.vitorpamplona.quartz.nip40Expiration.expiration
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class UserStatusCache : UserDependencies {
    val statuses = MutableStateFlow<ImmutableList<AddressableNote>>(persistentListOf())

    val sortModel: Comparator<Note> =
        compareBy(
            { it.event?.expiration() ?: it.event?.createdAt },
            { it.idHex },
        )

    fun addStatus(note: AddressableNote) {
        // if it's already there, quick exit
        if (statuses.value.contains(note) || note.event?.content.isNullOrBlank()) return

        statuses.update {
            (it + note).sortedWith(sortModel).toImmutableList()
        }
    }

    fun removeStatus(deleteNote: AddressableNote) {
        // if it's not already there, quick exit
        if (!statuses.value.contains(deleteNote)) return

        statuses.update {
            (it - deleteNote).toImmutableList()
        }
    }
}
