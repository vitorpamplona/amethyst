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
package com.vitorpamplona.amethyst.ios.ui.communities

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent

/**
 * Display data for a community card (NIP-72, kind 34550).
 */
data class CommunityDisplayData(
    val addressId: String,
    val creatorPubKeyHex: String,
    val name: String,
    val description: String?,
    val image: String?,
    val rules: String?,
    val moderatorCount: Int,
    val createdAt: Long,
)

/**
 * Extension to convert a Note containing a CommunityDefinitionEvent to display data.
 */
fun Note.toCommunityDisplayData(): CommunityDisplayData? {
    val event = this.event as? CommunityDefinitionEvent ?: return null
    return CommunityDisplayData(
        addressId = event.addressTag(),
        creatorPubKeyHex = event.pubKey,
        name = event.name() ?: event.dTag(),
        description = event.description(),
        image = event.image()?.imageUrl,
        rules = event.rules(),
        moderatorCount = event.moderators().size,
        createdAt = event.createdAt,
    )
}
