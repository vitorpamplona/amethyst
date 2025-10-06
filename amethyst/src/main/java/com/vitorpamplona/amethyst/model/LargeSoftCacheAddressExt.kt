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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.cache.CacheCollectors

const val START_KEY = "0000000000000000000000000000000000000000000000000000000000000000"

fun kindStart(
    kind: Int,
    pubKey: HexKey,
) = Address(kind, pubKey, "")

fun kindEnd(
    kind: Int,
    pubKey: HexKey,
) = Address(kind + 1, pubKey, "")

fun kindStart(kind: Int) = kindStart(kind, START_KEY)

fun kindEnd(kind: Int) = kindEnd(kind + 1, START_KEY)

fun LargeSoftCache<Address, AddressableNote>.filterIntoSet(
    kind: Int,
    consumer: CacheCollectors.BiFilter<Address, AddressableNote>,
): Set<AddressableNote> = filterIntoSet(kindStart(kind), kindEnd(kind), consumer)

fun LargeSoftCache<Address, AddressableNote>.filterIntoSet(
    kinds: List<Int>,
    consumer: CacheCollectors.BiFilter<Address, AddressableNote>,
): Set<AddressableNote> {
    val set = mutableSetOf<AddressableNote>()
    kinds.forEach {
        set.addAll(filterIntoSet(kindStart(it), kindEnd(it), consumer))
    }
    return set
}

fun LargeSoftCache<Address, AddressableNote>.filterIntoSet(
    kind: Int,
    pubKey: HexKey,
    consumer: CacheCollectors.BiFilter<Address, AddressableNote>,
): Set<AddressableNote> = filterIntoSet(kindStart(kind, pubKey), kindEnd(kind, pubKey), consumer)

fun <R> LargeSoftCache<Address, AddressableNote>.mapNotNullIntoSet(
    kind: Int,
    consumer: CacheCollectors.BiMapper<Address, AddressableNote, R?>,
): Set<R> = mapNotNullIntoSet(kindStart(kind), kindEnd(kind), consumer)

fun <R> LargeSoftCache<Address, AddressableNote>.mapNotNullIntoSet(
    kind: Int,
    pubKey: HexKey,
    consumer: CacheCollectors.BiMapper<Address, AddressableNote, R?>,
): Set<R> = mapNotNullIntoSet(kindStart(kind, pubKey), kindEnd(kind, pubKey), consumer)
