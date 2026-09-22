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
package com.vitorpamplona.quartz.nip01Core.metadata

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.diff.EventDiff
import com.vitorpamplona.quartz.nip01Core.diff.ListDiff
import com.vitorpamplona.quartz.nip01Core.diff.ValueChange
import com.vitorpamplona.quartz.nip39ExtIdentities.IdentityClaimTag

/**
 * Changes to a kind:0 profile, field by field as parsed by [UserMetadata]. A field that
 * went blank counts as removed. JSON fields [UserMetadata] doesn't model are kept as raw
 * `name → value` pairs in [otherFields], and NIP-39 identity claims are diffed as tags.
 */
@Immutable
class MetadataDiff(
    val name: ValueChange<String>?,
    val displayName: ValueChange<String>?,
    val picture: ValueChange<String>?,
    val banner: ValueChange<String>?,
    val website: ValueChange<String>?,
    val about: ValueChange<String>?,
    val pronouns: ValueChange<String>?,
    val nip05: ValueChange<String>?,
    val lud06: ValueChange<String>?,
    val lud16: ValueChange<String>?,
    val clinkOffer: ValueChange<String>?,
    val bot: ValueChange<Boolean>?,
    val birthday: ValueChange<Birthday>?,
    val otherFields: ListDiff<Pair<String, String>>,
    val identityClaims: ListDiff<IdentityClaimTag>,
) : EventDiff {
    private fun fieldChanges(): List<ValueChange<*>> = listOfNotNull(name, displayName, picture, banner, website, about, pronouns, nip05, lud06, lud16, clinkOffer, bot, birthday)

    override fun removesData() = fieldChanges().any { it.isRemoval() } || otherFields.hasRemovals() || identityClaims.hasRemovals()

    override fun isEmpty() = fieldChanges().isEmpty() && otherFields.isEmpty() && identityClaims.isEmpty()

    companion object {
        /** JSON keys [UserMetadata] parses into typed fields above. */
        val MODELED_FIELDS = setOf("name", "display_name", "picture", "banner", "website", "about", "pronouns", "nip05", "lud06", "lud16", "clink_offer", "bot", "birthday")
    }
}
