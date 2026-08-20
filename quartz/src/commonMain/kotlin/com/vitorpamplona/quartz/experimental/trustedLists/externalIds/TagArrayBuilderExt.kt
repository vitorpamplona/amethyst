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
package com.vitorpamplona.quartz.experimental.trustedLists.externalIds

import com.vitorpamplona.quartz.experimental.trustedLists.externalIds.tags.ExternalIdMemberTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip73ExternalIds.ExternalId

fun TagArrayBuilder<ExternalIdTrustedListEvent>.member(
    externalId: String,
    hint: String? = null,
    score: Int? = null,
) = add(ExternalIdMemberTag.assemble(externalId, hint, score))

fun TagArrayBuilder<ExternalIdTrustedListEvent>.member(
    externalId: ExternalId,
    score: Int? = null,
) = add(ExternalIdMemberTag.assemble(externalId, score))

fun TagArrayBuilder<ExternalIdTrustedListEvent>.member(member: ExternalIdMemberTag) = add(ExternalIdMemberTag.assemble(member))

fun TagArrayBuilder<ExternalIdTrustedListEvent>.members(members: List<ExternalIdMemberTag>) = addAll(ExternalIdMemberTag.assemble(members))

fun TagArrayBuilder<ExternalIdTrustedListEvent>.aboutAddress(address: ATag) = addUniqueValueIfNew(address.toATagArray())

fun TagArrayBuilder<ExternalIdTrustedListEvent>.aboutPubKey(pubKey: PTag) = addUniqueValueIfNew(pubKey.toTagArray())

fun TagArrayBuilder<ExternalIdTrustedListEvent>.aboutPubKey(
    pubKey: HexKey,
    relayHint: NormalizedRelayUrl? = null,
) = addUniqueValueIfNew(PTag.assemble(pubKey, relayHint))
