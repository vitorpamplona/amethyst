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
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.hints.types.PubKeyHint

fun TagArray.hasZapSplitSetup() = this.any(ZapSplitSetupParser::isTagged)

fun TagArray.zapSplitSetup(): List<BaseZapSplitSetup> = this.mapNotNull(ZapSplitSetupParser::parse)

/** Pubkeys of every positive-weight NIP-57 `zap` split; the beneficiaries a hint provider should link. */
fun TagArray.zapSplitPubKeys(): List<HexKey> = this.mapNotNull(ZapSplitSetupParser::parseKey)

/** [zapSplitPubKeys] that carry a relay hint, as [PubKeyHint]s. */
fun TagArray.zapSplitHints(): List<PubKeyHint> = this.mapNotNull(ZapSplitSetupParser::parseAsHint)

/**
 * [zapSplitPubKeys], appended to [dest] in tag order. The hint providers of the hottest kinds
 * (notes, comments, articles, chat messages) run on every relay copy of every event and almost
 * never carry a `zap` tag, so they collect into one list instead of paying for an empty list
 * plus a concatenation per call.
 */
fun <C : MutableCollection<in HexKey>> TagArray.zapSplitPubKeysTo(dest: C): C = this.mapNotNullTo(dest, ZapSplitSetupParser::parseKey)

/** [zapSplitHints], appended to [dest] in tag order; see [zapSplitPubKeysTo]. */
fun <C : MutableCollection<in PubKeyHint>> TagArray.zapSplitHintsTo(dest: C): C = this.mapNotNullTo(dest, ZapSplitSetupParser::parseAsHint)
