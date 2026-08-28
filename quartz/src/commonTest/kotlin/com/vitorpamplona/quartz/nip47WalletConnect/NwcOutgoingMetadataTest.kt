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
package com.vitorpamplona.quartz.nip47WalletConnect

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcInfoEvent
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcTransaction
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcTransactionMetadata
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Request
import com.vitorpamplona.quartz.nip47WalletConnect.tags.ExtensionsTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * NWC-06 metadata on OUTGOING payments: what we send, when we are allowed to send
 * it, and what a row makes of it coming back.
 */
class NwcOutgoingMetadataTest {
    private val recipientHex = "ca89cb11f1c75d5b6622268ff43d2288ea8b2cb5b9aa996ff9ff704fc904b78b"
    private val payerHex = "f512822a89d2369a386bfeb1e687ccd26ceb6bb33e73b98417499bb9054bff1f"

    private fun zapRequest(
        content: String = "great post",
        relays: List<String> = listOf("wss://relay.damus.io"),
    ) = Event(
        id = "a".repeat(64),
        pubKey = payerHex,
        createdAt = 1756000000L,
        kind = 9734,
        tags = arrayOf(arrayOf("p", recipientHex), arrayOf("relays", *relays.toTypedArray())),
        content = content,
        sig = "b".repeat(128),
    )

    // --- build ---

    @Test
    fun nothingToSayProducesNoMetadataAtAll() {
        assertNull(NwcTransactionMetadata.build(null, null, null))
        assertNull(NwcTransactionMetadata.build(null, "   ", ""))
    }

    @Test
    fun leanPairIsSentWithoutAZapRequest() {
        val meta = assertNotNull(NwcTransactionMetadata.build(null, "user@domain.com", "thanks"))
        assertEquals(mapOf("identifier" to "user@domain.com"), meta["recipient_data"])
        assertEquals("thanks", meta["comment"])
        assertFalse(meta.containsKey("nostr"))
    }

    @Test
    fun zapRequestTravelsWithTypedFields() {
        val meta = assertNotNull(NwcTransactionMetadata.build(zapRequest(), "user@domain.com", "great post"))

        @Suppress("UNCHECKED_CAST")
        val nostr = meta["nostr"] as Map<String, Any?>

        // Integers, not floats: a verifying wallet recomputes the event id from these.
        assertEquals(9734, nostr["kind"])
        assertEquals(1756000000L, nostr["created_at"])
        assertEquals(payerHex, nostr["pubkey"])
        assertEquals("great post", nostr["content"])
        assertEquals(listOf(listOf("p", recipientHex), listOf("relays", "wss://relay.damus.io")), nostr["tags"])
    }

    @Test
    fun anOversizeZapRequestIsDroppedButTheRowStillNamesThePayee() {
        // NWC-06: over 4096 characters a wallet MUST drop the WHOLE object, so
        // breaching it would lose the payee entirely rather than degrade.
        val many = List(200) { "wss://relay-with-a-fairly-long-hostname-.example.com" }
        val meta = assertNotNull(NwcTransactionMetadata.build(zapRequest(relays = many), "user@domain.com", "hi"))

        assertFalse(meta.containsKey("nostr"))
        assertEquals(mapOf("identifier" to "user@domain.com"), meta["recipient_data"])
        assertEquals("hi", meta["comment"])
    }

    @Test
    fun whatWeSendStaysUnderTheSpecCeiling() {
        val meta = NwcTransactionMetadata.build(zapRequest(), "user@domain.com", "great post")
        val serialized = OptimizedJsonMapper.toJson(PayInvoiceMethod.create("lnbc50n1abc", meta))
        assertTrue(serialized.length < NwcTransactionMetadata.MAX_METADATA_CHARS)
    }

    // --- the wire ---

    @Test
    fun metadataSurvivesASerializationRoundTrip() {
        val meta = NwcTransactionMetadata.build(zapRequest(), "user@domain.com", "great post")
        val json = OptimizedJsonMapper.toJson(PayInvoiceMethod.create("lnbc50n1abc", meta))

        assertTrue(json.contains("\"kind\":9734"), "kind must stay an integer: $json")
        assertTrue(json.contains("\"created_at\":1756000000"), "created_at must stay an integer: $json")

        val back = OptimizedJsonMapper.fromJsonTo<Request>(json)
        assertIs<PayInvoiceMethod>(back)
        val parsed = assertNotNull(NwcTransactionMetadata.parse(back.params?.metadata))
        assertEquals(recipientHex, parsed.recipientPubkeyHex())
        assertEquals("great post", parsed.displayComment())
    }

    @Test
    fun noMetadataMeansTheRequestIsUnchanged() {
        // The regression test that protects every wallet which has not advertised
        // NWC-06: what they receive must be byte-identical to what they receive today.
        val expected = OptimizedJsonMapper.toJson(PayInvoiceMethod.create("lnbc50n1abc"))
        val actual = OptimizedJsonMapper.toJson(PayInvoiceMethod.create("lnbc50n1abc", null))
        assertEquals(expected, actual)
        assertFalse(actual.contains("recipient_data"))
    }

    // --- the gate ---

    @Test
    fun extensionsTagIsReadFromTheInfoEvent() {
        assertEquals(listOf("02", "05", "06"), infoWith(arrayOf("extensions", "02 05 06")).extensions())
        assertTrue(infoWith(arrayOf("extensions", "05 06")).supportsExtension(ExtensionsTag.METADATA_CONVENTIONS))
    }

    @Test
    fun aWalletThatSaysNothingReadsAsNo() {
        assertFalse(infoWith().supportsExtension(ExtensionsTag.METADATA_CONVENTIONS))
        assertFalse(infoWith(arrayOf("extensions", "")).supportsExtension(ExtensionsTag.METADATA_CONVENTIONS))
        assertFalse(infoWith(arrayOf("extensions", "02 03")).supportsExtension(ExtensionsTag.METADATA_CONVENTIONS))
        assertTrue(infoWith().extensions().isEmpty())
    }

    private fun infoWith(vararg tags: Array<String>) =
        NwcInfoEvent(
            id = "c".repeat(64),
            pubKey = payerHex,
            createdAt = 1756000000L,
            tags = arrayOf(*tags),
            content = "pay_invoice get_balance",
            sig = "d".repeat(128),
        )

    // --- reading it back ---

    @Test
    fun anOutgoingRowResolvesThePayeeFromThePTag() {
        val meta = NwcTransactionMetadata.build(zapRequest(content = "for the article"), "user@domain.com", "")
        val parsed = assertNotNull(NwcTransactionMetadata.parse(meta))

        // The p tag, not the pubkey: on an outgoing zap the pubkey is US.
        assertEquals(recipientHex, parsed.recipientPubkeyHex())
        assertEquals("user@domain.com", parsed.recipientIdentifier())
        // A wallet storing only  still yields the message.
        assertEquals("for the article", parsed.displayComment())
    }

    @Test
    fun blankFieldsReadAsAbsent() {
        val parsed =
            assertNotNull(
                NwcTransactionMetadata.parse(
                    mapOf(
                        "comment" to "  ",
                        "recipient_data" to mapOf("identifier" to ""),
                        "nostr" to mapOf("content" to ""),
                    ),
                ),
            )
        assertNull(parsed.recipientIdentifier())
        assertNull(parsed.displayComment())
    }

    @Test
    fun emptyDescriptionIsNotAName() {
        // The wallet-side habit this exists for:  rather than an
        // omitted field. The row must fall back, not render an empty line.
        val tx = NwcTransaction(type = "outgoing", description = "", amount = 21000L)
        assertEquals("", tx.description)
        assertNull(tx.description?.ifBlank { null })
    }
}
