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
package com.vitorpamplona.quartz.nip01Core.diff

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * What changed between two versions of the same replaceable/addressable event. Each event
 * kind defines its own diff class holding its own parsed objects (a follow list diffs
 * `ContactTag`s, a nutzap info diffs `NutzapMintTag`s and its P2PK key…), built with the
 * [ListDiff], [ValueChange] and [ContentChange] containers.
 */
interface EventDiff {
    /** The newer version dropped something the older one had. */
    fun removesData(): Boolean
}

/**
 * An event that knows how to compare itself with an older version of itself.
 *
 * [diffFrom] returns null when [older] isn't a version of the same event: a different kind,
 * author or, for addressables, d-tag.
 */
interface DiffableEvent<D : EventDiff> {
    fun diffFrom(older: Event): D?
}

/** An item present in both versions whose details differ. */
@Immutable
class ItemChange<T>(
    val before: T,
    val after: T,
)

/**
 * The difference between two lists of parsed items. Items are matched by an identity key;
 * a matched pair whose details differ is a change, not a removal plus an addition.
 */
@Immutable
class ListDiff<T>(
    val removed: List<T>,
    val added: List<T>,
    val changed: List<ItemChange<T>>,
) {
    fun isEmpty() = removed.isEmpty() && added.isEmpty() && changed.isEmpty()

    fun hasRemovals() = removed.isNotEmpty()

    companion object {
        /**
         * @param key the identity of an item inside the list.
         * @param same whether two items with the same key carry the same details.
         */
        fun <T, K> of(
            older: List<T>,
            newer: List<T>,
            key: (T) -> K,
            same: (T, T) -> Boolean = { a, b -> a == b },
        ): ListDiff<T> {
            val olderByKey = LinkedHashMap<K, T>(older.size)
            older.forEach { olderByKey.getOrPut(key(it)) { it } }
            val newerByKey = LinkedHashMap<K, T>(newer.size)
            newer.forEach { newerByKey.getOrPut(key(it)) { it } }

            val removed = mutableListOf<T>()
            val changed = mutableListOf<ItemChange<T>>()
            olderByKey.forEach { (k, before) ->
                val after = newerByKey[k]
                if (after == null) {
                    removed.add(before)
                } else if (!same(before, after)) {
                    changed.add(ItemChange(before, after))
                }
            }
            val added = newerByKey.filterKeys { it !in olderByKey }.values.toList()

            return ListDiff(removed, added, changed)
        }
    }
}

/** A single value that differs between two versions; null on a side means it wasn't set. */
@Immutable
class ValueChange<T>(
    val before: T?,
    val after: T?,
) {
    fun isRemoval() = before != null && after == null

    companion object {
        /** Null when both sides are the same. */
        fun <T> of(
            before: T?,
            after: T?,
            same: (T, T) -> Boolean = { a, b -> a == b },
        ): ValueChange<T>? =
            when {
                before == null && after == null -> null
                before != null && after != null && same(before, after) -> null
                else -> ValueChange(before, after)
            }
    }
}

/**
 * How content that can't be read without decrypting (NIP-44 private list items, encrypted
 * wallets and settings) changed as a whole.
 */
enum class ContentChange {
    NONE,
    ADDED,
    CHANGED,
    CLEARED,
    ;

    fun isRemoval() = this == CLEARED

    /**
     * Whether public items that disappeared alongside this change are really gone. When the
     * private section only just appeared, they were most likely moved into it (made
     * private), which loses nothing. A rewritten private section can't vouch for them: NIP-44
     * re-encrypts on every save, so it looks changed even when it holds the same items.
     */
    fun publicRemovalsAreLoss(hasPublicRemovals: Boolean) = hasPublicRemovals && this != ADDED

    companion object {
        fun between(
            older: String,
            newer: String,
        ): ContentChange =
            when {
                older == newer -> NONE
                older.isBlank() -> ADDED
                newer.isBlank() -> CLEARED
                else -> CHANGED
            }
    }
}
