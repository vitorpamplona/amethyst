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
package com.vitorpamplona.quartz.nip34Git.ci

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip34Git.ci.coordinatorAdvertisement.CiCoordinatorAdvertisementEvent
import com.vitorpamplona.quartz.nip34Git.ci.secretUpdate.CiSecretUpdateEvent
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Kind 29846 — typed, never indexed. No sample exists on relays (it is ephemeral); the fixture is synthetic. */
class CiSecretUpdateEventTest {
    private val maintainer = "a".repeat(64)
    private val coordinator = "b".repeat(64)
    private val advertisementId = "c".repeat(64)
    private val sender = "d".repeat(64)
    private val recipient = "e".repeat(64)
    private val repo = "30617:$maintainer:my-repo"

    private fun update(
        tags: Array<Array<String>>,
        signer: String = maintainer,
    ) = EventFactory.create<Event>("1".repeat(64), signer, 1_000L, CiSecretUpdateEvent.KIND, tags, "AgVhc2NpcGhlcnRleHQ=", "0".repeat(128))

    private val specShape =
        arrayOf(
            arrayOf("a", repo, "wss://relay.ngit.dev"),
            arrayOf("p", coordinator),
            arrayOf("e", advertisementId, "wss://nos.lol", "secrets-key"),
            arrayOf("sender", sender),
            arrayOf("recipient", recipient),
            arrayOf("encryption", "nip44-v2"),
        )

    @Test
    fun factoryBuildsItButNeverIndexesIt() {
        val event = update(specShape)
        assertIs<CiSecretUpdateEvent>(event)
        assertTrue(EventFactory.isKnownKind(CiSecretUpdateEvent.KIND))
        assertFalse(event is SearchableEvent)
    }

    @Test
    fun readsTheEnvelope() {
        val event = assertIs<CiSecretUpdateEvent>(update(specShape))
        assertEquals(repo, event.repository()?.toTag())
        assertEquals(coordinator, event.coordinator())
        assertEquals(advertisementId, event.advertisementId())
        assertEquals(sender, event.senderKey())
        assertEquals(recipient, event.recipientKey())
        assertEquals("nip44-v2", event.encryption())

        assertEquals(listOf(advertisementId), event.linkedEventIds())
        assertEquals(listOf(advertisementId to "wss://nos.lol/"), event.eventHints().map { it.eventId to it.relay.url })
        // The sender and recipient are encryption keys, not users.
        assertEquals(listOf(coordinator), event.linkedPubKeys())
        assertEquals(listOf(repo), event.linkedAddressIds())
    }

    @Test
    fun theRepositoryMustBeRootedAtTheSigner() {
        val event = assertIs<CiSecretUpdateEvent>(update(specShape, signer = "f".repeat(64)))
        assertNull(event.repository())
    }

    @Test
    fun anEWithoutTheSecretsKeyMarkerIsNotTheAdvertisement() {
        val event = assertIs<CiSecretUpdateEvent>(update(arrayOf(arrayOf("e", advertisementId, "wss://nos.lol"), arrayOf("sender", "short"))))
        assertNull(event.advertisementId())
        assertNull(event.senderKey())
        assertEquals(emptyList(), event.linkedEventIds())
    }

    @Test
    fun builderRoundTrip() {
        val ad =
            EventFactory.create<CiCoordinatorAdvertisementEvent>(advertisementId, coordinator, 900L, CiCoordinatorAdvertisementEvent.KIND, emptyArray(), "", "0".repeat(128))
        val template =
            CiSecretUpdateEvent.build(
                ciphertext = "AgVhc2NpcGhlcnRleHQ=",
                repository = ATag(30617, maintainer, "my-repo", null),
                advertisement = EventHintBundle(ad, RelayUrlNormalizer.normalizeOrNull("wss://nos.lol")),
                senderKey = sender,
                recipientKey = recipient,
                createdAt = 1_000L,
            )
        assertEquals(
            listOf(
                listOf("a", repo),
                listOf("p", coordinator),
                listOf("e", advertisementId, "wss://nos.lol/", "secrets-key"),
                listOf("sender", sender),
                listOf("recipient", recipient),
                listOf("encryption", "nip44-v2"),
            ),
            template.tags.map { it.toList() },
        )
        val event = assertIs<CiSecretUpdateEvent>(EventFactory.create<Event>("1".repeat(64), maintainer, template.createdAt, template.kind, template.tags, template.content, "0".repeat(128)))
        assertEquals(repo, event.repository()?.toTag())
        assertEquals(advertisementId, event.advertisementId())
    }
}
