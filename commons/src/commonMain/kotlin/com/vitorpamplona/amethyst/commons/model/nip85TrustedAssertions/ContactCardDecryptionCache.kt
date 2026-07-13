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
package com.vitorpamplona.amethyst.commons.model.nip85TrustedAssertions

import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEventCache
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.ContactCardEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.petName
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.summary

/**
 * Decrypts and caches the NIP-44 private tags of the account's own kind:30382
 * contact cards. Petname and summary always live in the encrypted part, so
 * reading them requires the account's main key ([signer]); cards signed by any
 * other key never decrypt here.
 */
class ContactCardDecryptionCache(
    val signer: NostrSigner,
) {
    val cachedPrivateCards = PrivateTagArrayEventCache<ContactCardEvent>(signer, cacheSize = 100)

    fun cachedPetName(event: ContactCardEvent) = cachedPrivateCards.mergeTagListPrecached(event).petName()

    fun cachedSummary(event: ContactCardEvent) = cachedPrivateCards.mergeTagListPrecached(event).summary()

    suspend fun petName(event: ContactCardEvent) = cachedPrivateCards.mergeTagList(event).petName()

    suspend fun summary(event: ContactCardEvent) = cachedPrivateCards.mergeTagList(event).summary()

    /**
     * The petname plus the card's full decrypted tag list, so renderers can
     * resolve the NIP-30 `emoji` mappings stored alongside it.
     */
    suspend fun petNameWithEmojis(event: ContactCardEvent): PetName? {
        val merged = cachedPrivateCards.mergeTagList(event)
        val name = merged.petName() ?: return null
        return PetName(name, merged.toImmutableListOfLists())
    }
}
