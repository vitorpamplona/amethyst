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
package com.vitorpamplona.quartz.nip34Git.ci.secretUpdate

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip34Git.ci.ciSingleOrNull
import com.vitorpamplona.quartz.nip34Git.ci.coordinatorAdvertisement.CiCoordinatorAdvertisementEvent
import com.vitorpamplona.quartz.nip34Git.ci.tags.EncryptionSchemeTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.RecipientKeyTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.RepositoryTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.SecretsKeyAdvertisementTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.SecretsKeyTag
import com.vitorpamplona.quartz.nip34Git.ci.tags.SenderKeyTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Kind 29846 — Nostr CI Repository Secret Update (draft "CI Extension to NIP-34").
 *
 * An ephemeral event, signed by a maintainer, that provisions or removes CI secrets for one of
 * their repositories (`a`, which must be rooted at the signer) at one coordinator (`p`). `content`
 * is NIP-44 v2 ciphertext from a fresh single-use `sender` key to the `recipient` key that the
 * referenced Coordinator Advertisement (`e` with the `secrets-key` marker) published. Exactly one
 * tag of each type and no others; `encryption` is `nip44-v2`.
 *
 * This class only types the envelope. It decrypts nothing and is **never indexed**: the content is
 * ciphertext of secret values, so it is not a [com.vitorpamplona.quartz.nip50Search.SearchableEvent].
 * Being ephemeral, relays do not retain it either.
 */
@Immutable
class CiSecretUpdateEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    EventHintProvider,
    PubKeyHintProvider,
    AddressHintProvider {
    override fun eventHints() = tags.mapNotNull(SecretsKeyAdvertisementTag::parseAsHint)

    /** `SECRETS_KEY`: the Coordinator Advertisement whose `secrets-key` the update is encrypted to (`e`). */
    override fun linkedEventIds() = tags.mapNotNull(SecretsKeyAdvertisementTag::parseId)

    override fun pubKeyHints() = tags.mapNotNull(PTag::parseAsHint)

    /** `COORDINATOR`: the coordinator that receives the secrets (`p`). */
    override fun linkedPubKeys() = tags.mapNotNull(PTag::parseKey)

    override fun addressHints() = tags.mapNotNull(RepositoryTag::parseAsHint)

    /** `REPOSITORY`: the signer's repository the secrets belong to (`a`). */
    override fun linkedAddressIds() = tags.mapNotNull(RepositoryTag::parseAddressId)

    /** The repository; null when absent, repeated, or not rooted at the signer (the spec requires its pubkey to equal the signer's). */
    fun repository() = tags.ciSingleOrNull(RepositoryTag::parse)?.takeIf { it.pubKeyHex == pubKey }

    fun coordinator() = tags.ciSingleOrNull(PTag::parseKey)

    fun advertisementId() = tags.ciSingleOrNull(SecretsKeyAdvertisementTag::parseId)

    fun senderKey() = tags.ciSingleOrNull(SenderKeyTag::parse)

    fun recipientKey() = tags.ciSingleOrNull(RecipientKeyTag::parse)

    fun encryption() = tags.ciSingleOrNull(EncryptionSchemeTag::parse)

    companion object {
        const val KIND = 29846

        /**
         * Wraps an already-encrypted [ciphertext]. Encrypting the update — a fresh sender key, the
         * advertised recipient, the `{author, created_at, set, remove}` plaintext — is the caller's
         * job; [createdAt] must equal the `created_at` bound inside that plaintext.
         */
        fun build(
            ciphertext: String,
            repository: ATag,
            advertisement: EventHintBundle<CiCoordinatorAdvertisementEvent>,
            senderKey: HexKey,
            recipientKey: HexKey,
            coordinatorRelay: NormalizedRelayUrl? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CiSecretUpdateEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, ciphertext, createdAt) {
            addUnique(RepositoryTag.assemble(repository))
            addUnique(PTag.assemble(advertisement.event.pubKey, coordinatorRelay))
            addUnique(SecretsKeyAdvertisementTag.assemble(advertisement.event.id, advertisement.relay))
            addUnique(SenderKeyTag.assemble(senderKey))
            addUnique(RecipientKeyTag.assemble(recipientKey))
            addUnique(EncryptionSchemeTag.assemble(SecretsKeyTag.NIP44_V2))
            initializer()
        }
    }
}
