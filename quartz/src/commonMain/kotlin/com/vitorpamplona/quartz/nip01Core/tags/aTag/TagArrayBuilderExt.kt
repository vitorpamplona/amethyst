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
package com.vitorpamplona.quartz.nip01Core.tags.aTag

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

fun <T : Event> TagArrayBuilder<T>.aTag(tag: ATag) = add(tag.toATagArray())

fun <T : Event> TagArrayBuilder<T>.aTag(
    address: Address,
    relayHint: NormalizedRelayUrl? = null,
) = add(ATag.assemble(address, relayHint))

fun <T : Event> TagArrayBuilder<T>.aTags(tag: List<ATag>) = addAll(tag.map { it.toATagArray() })

fun <T : Event> TagArrayBuilder<T>.removeATag(tag: ATag) = this.removeIf(ATag::isSameAddress, tag.toATagArray())

fun <T : Event> TagArrayBuilder<T>.removeAddress(address: Address) = this.removeIf(ATag::isSameAddress, ATag.assemble(address, null))
