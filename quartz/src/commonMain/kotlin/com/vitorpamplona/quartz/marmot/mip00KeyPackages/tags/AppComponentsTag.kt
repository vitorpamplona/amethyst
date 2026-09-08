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
package com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

/**
 * `app_components` — the Marmot app-component ids a current-profile KeyPackage
 * advertises support for (`transports/nostr.md`, "KeyPackage publication").
 *
 * ```text
 * ["app_components", "0x0001", "0x8009"]
 * ```
 *
 * An id-list tag: exactly one tag, values following the name in a single array,
 * each `0x` plus four lowercase hex digits. A producer MUST NOT split one list
 * across repeated tags, and a consumer MUST reject an event carrying more than
 * one — reading the first and ignoring the rest is how a second tag smuggles in
 * an advertisement nobody validated.
 *
 * The tag MUST include `0x8009`. It is only an advertisement and a fetch
 * filter, though: a receiver still has to validate the decoded KeyPackage
 * LeafNode's own support list and proof data. A tag can claim anything.
 */
class AppComponentsTag {
    companion object {
        const val TAG_NAME = "app_components"

        /** `marmot.member.account-identity-proof.v2` — mandatory in this tag. */
        const val ACCOUNT_IDENTITY_PROOF_V2 = "0x8009"

        fun parse(tag: Array<String>): List<String>? {
            ensure(tag.has(1) && tag[0] == TAG_NAME) { return null }
            return tag.drop(1).filter { it.isNotEmpty() }
        }

        fun assemble(componentIds: List<String>) = arrayOf(TAG_NAME, *componentIds.toTypedArray())
    }
}
