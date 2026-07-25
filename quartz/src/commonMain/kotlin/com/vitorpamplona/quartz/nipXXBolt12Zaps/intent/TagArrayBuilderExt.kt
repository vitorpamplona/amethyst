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
package com.vitorpamplona.quartz.nipXXBolt12Zaps.intent

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.kinds.KindTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nipXXBolt12Zaps.tags.AmountTag
import com.vitorpamplona.quartz.nipXXBolt12Zaps.tags.OfferTag
import com.vitorpamplona.quartz.nipXXBolt12Zaps.tags.ZapIdTag

fun TagArrayBuilder<Bolt12ZapIntentEvent>.recipient(recipientPubKey: HexKey) = addUnique(PTag.assemble(recipientPubKey, null))

fun TagArrayBuilder<Bolt12ZapIntentEvent>.amountInMillisats(amountInMillisats: Long) = addUnique(AmountTag.assemble(amountInMillisats))

fun TagArrayBuilder<Bolt12ZapIntentEvent>.offer(canonicalOffer: String) = addUnique(OfferTag.assemble(canonicalOffer))

fun TagArrayBuilder<Bolt12ZapIntentEvent>.zapId(zapId: String) = addUnique(ZapIdTag.assemble(zapId))

fun TagArrayBuilder<Bolt12ZapIntentEvent>.zappedEvent(tag: ETag) = addUnique(tag.toTagArray())

fun TagArrayBuilder<Bolt12ZapIntentEvent>.zappedAddress(tag: ATag) = addUnique(tag.toATagArray())

fun TagArrayBuilder<Bolt12ZapIntentEvent>.zappedKind(kind: Int) = addUnique(KindTag.assemble(kind))
