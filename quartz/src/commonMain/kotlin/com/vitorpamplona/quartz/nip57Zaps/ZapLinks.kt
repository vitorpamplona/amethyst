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
package com.vitorpamplona.quartz.nip57Zaps

import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip01Core.links.Link
import com.vitorpamplona.quartz.nip01Core.links.Relation
import com.vitorpamplona.quartz.nip01Core.links.links
import com.vitorpamplona.quartz.utils.BigDecimal
import com.vitorpamplona.quartz.utils.toDoubleValue
import com.vitorpamplona.quartz.utils.toLongValue

/**
 * The NIP-57 tags a zap request, its receipt and a decrypted private zap share: the `e`/`a` is
 * the `ZAPPED` content and the `p` the `ZAP_RECIPIENT` (NIP-57's "recipient"), each with
 * [props] (the `msats`, when the kind knows them); `k` is the zapped kind. With [withSender], the
 * receipt's `P` is the `ZAP_SENDER`: NIP-57 copies it from the zap request's pubkey.
 */
internal fun zapLinks(
    tags: TagArray,
    props: Map<String, Any>?,
    withSender: Boolean = false,
): List<Link<*>> =
    links {
        tags.fastForEach { tag ->
            if (tag.size < 2) return@fastForEach
            when (tag[0]) {
                "e" -> event(Relation.ZAPPED, tag[1], "e", props)
                "a" -> address(Relation.ZAPPED, tag[1], "a", props)
                "p" -> user(Relation.ZAP_RECIPIENT, tag[1], "p", props)
                "P" -> if (withSender) user(Relation.ZAP_SENDER, tag[1], "P")
                "k" -> tag(Relation.TAG, "k", tag[1])
            }
        }
    }

private val MSATS_PER_SAT = BigDecimal(1000)

/** An invoice amount in sats as whole msats, or null when it is not positive or does not fit a Long (a crafted invoice). */
internal fun satsToMsats(sats: BigDecimal?): Long? =
    sats
        ?.multiply(MSATS_PER_SAT)
        ?.takeIf { it.signum() > 0 && it.toDoubleValue() < Long.MAX_VALUE.toDouble() }
        ?.toLongValue()
