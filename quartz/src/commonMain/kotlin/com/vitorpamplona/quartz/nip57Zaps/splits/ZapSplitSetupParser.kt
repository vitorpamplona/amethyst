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
package com.vitorpamplona.quartz.nip57Zaps.splits

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.ensure

class ZapSplitSetupParser {
    companion object {
        fun isTagged(tags: Array<String>) = tags.has(1) && tags[0] == BaseZapSplitSetup.TAG_NAME

        /**
         * The beneficiary's pubkey of a positive-weight pubkey split, or null for a lightning-address
         * split, a zero weight, or anything that is not a 64-char key. Mirrors [parse]'s rules so
         * the hint providers never link a pubkey a zap would not actually pay.
         */
        fun parseKey(tag: Array<String>): HexKey? {
            val split = parse(tag) as? ZapSplitSetup ?: return null
            ensure(split.pubKeyHex.length == 64) { return null }
            return split.pubKeyHex
        }

        /** [parseKey] plus the split's relay hint (`["zap", <pubkey>, <relay>, <weight>]`), when it has one. */
        fun parseAsHint(tag: Array<String>): PubKeyHint? {
            val split = parse(tag) as? ZapSplitSetup ?: return null
            ensure(split.pubKeyHex.length == 64) { return null }
            val relay = split.relay ?: return null
            return PubKeyHint(split.pubKeyHex, relay)
        }

        fun parse(tags: Array<String>): BaseZapSplitSetup? {
            ensure(tags.has(1)) { return null }
            ensure(tags[0] == BaseZapSplitSetup.TAG_NAME) { return null }

            val isLnAddress = tags[1].contains("@") || tags[1].startsWith("LNURL", true)
            // NIP-57 Appendix G is `["zap", <pubkey>, <relay>, <weight>]`. Builders before the
            // serializer kept an empty relay slot wrote `["zap", <pubkey>, <weight>]` when there
            // was no relay, so a three-element tag whose slot 2 is a number is that legacy shape.
            val legacyWeight = if (!isLnAddress && tags.size == 3) tags[2].toDoubleOrNull() else null
            val weight = if (isLnAddress) 1.0 else (legacyWeight ?: tags.getOrNull(3)?.toDoubleOrNull() ?: 0.0)

            return if (weight > 0) {
                if (isLnAddress) {
                    ZapSplitSetupLnAddress(tags[1], 1.0)
                } else {
                    // Guarded: an unguarded normalize turns a stray weight or key into `wss://<it>/`.
                    val relayHint = if (legacyWeight != null) null else RelayUrlNormalizer.normalizeHintOrNull(tags.getOrNull(2))

                    ZapSplitSetup(
                        tags[1],
                        relayHint,
                        weight,
                    )
                }
            } else {
                null
            }
        }
    }
}
