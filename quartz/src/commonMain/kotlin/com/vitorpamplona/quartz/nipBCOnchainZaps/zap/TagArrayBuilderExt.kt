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
package com.vitorpamplona.quartz.nipBCOnchainZaps.zap

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.kinds.KindTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.tags.AmountTag
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.tags.BitcoinTxIdTag
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.tags.BlockTag
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.tags.ProofTag

fun TagArrayBuilder<OnchainZapEvent>.txid(txid: String) = addUnique(BitcoinTxIdTag.assemble(txid))

fun TagArrayBuilder<OnchainZapEvent>.recipient(recipientPubKey: HexKey) = addUnique(PTag.assemble(recipientPubKey, null))

fun TagArrayBuilder<OnchainZapEvent>.amountInSats(amountInSats: Long) = addUnique(AmountTag.assemble(amountInSats))

fun TagArrayBuilder<OnchainZapEvent>.zappedEvent(tag: ETag) = addUnique(tag.toTagArray())

fun TagArrayBuilder<OnchainZapEvent>.zappedAddress(tag: ATag) = addUnique(tag.toATagArray())

fun TagArrayBuilder<OnchainZapEvent>.zappedKind(kind: Int) = addUnique(KindTag.assemble(kind))

fun TagArrayBuilder<OnchainZapEvent>.block(
    blockHashHex: String,
    height: Long,
) = addUnique(BlockTag.assemble(blockHashHex, height))

fun TagArrayBuilder<OnchainZapEvent>.block(block: BlockTag) = addUnique(block.toTagArray())

fun TagArrayBuilder<OnchainZapEvent>.proof(
    rawTxHex: String,
    merkleProofHex: String,
) = addUnique(ProofTag.assemble(rawTxHex, merkleProofHex))

fun TagArrayBuilder<OnchainZapEvent>.proof(proof: ProofTag) = addUnique(proof.toTagArray())
