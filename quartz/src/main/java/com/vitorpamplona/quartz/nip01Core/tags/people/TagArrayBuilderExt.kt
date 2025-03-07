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
package com.vitorpamplona.quartz.nip01Core.tags.people

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder

fun <T : Event> TagArrayBuilder<T>.pTag(
    pubkey: HexKey,
    relayHint: String? = null,
) = add(PTag.assemble(pubkey, relayHint))

fun <T : Event> TagArrayBuilder<T>.pTagIds(tag: Set<HexKey>) = addAll(tag.map { PTag.assemble(it, null) })

fun <T : Event> TagArrayBuilder<T>.pTag(tag: PTag) = add(tag.toTagArray())

fun <T : Event> TagArrayBuilder<T>.pTags(tag: List<PTag>) = addAll(tag.map { it.toTagArray() })

fun <T : Event> TagArrayBuilder<T>.pTags(tag: Set<PTag>) = addAll(tag.map { it.toTagArray() })
