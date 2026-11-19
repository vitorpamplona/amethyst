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
package com.vitorpamplona.amethyst.commons.model.accountLifecycle

import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountLifecycleConformanceTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val document = json.parseToJsonElement(ACCOUNT_LIFECYCLE_CONFORMANCE_VECTORS).jsonObject
    private val vectors = document.getValue("vectors").jsonArray.map { it.jsonObject }
    private val events = document.getValue("events").jsonObject

    @Test
    fun allNormativeVectorsAreAccountedFor() {
        assertEquals(71, vectors.size)
        assertEquals(50, vectors.count { it.string("mode") == "inline_root" })
        assertEquals(7, vectors.count { it.string("mode") == "encoding" })
        assertEquals(7, vectors.count { it.string("mode") == "full" })
        assertEquals(4, vectors.count { it.string("mode") == "availability" })
        assertEquals(2, vectors.count { it.string("mode") == "discovery" })
        assertEquals(1, vectors.count { it.string("mode") == "policy" })
    }

    @Test
    fun encodingVectors() {
        vectors.filter { it.string("mode") == "encoding" }.forEach { vector ->
            val value = vector.string("value")
            assertEquals(vector.int("utf8_bytes"), value.encodeToByteArray().size, vector.string("id"))
            assertEquals(
                vector
                    .getValue("expected")
                    .jsonObject
                    .getValue("publisher_accepts")
                    .jsonPrimitive.boolean,
                publisherAcceptsValueSize(value),
                vector.string("id"),
            )
        }
    }

    @Test
    fun inlineRootVectors() {
        vectors.filter { it.string("mode") == "inline_root" }.forEach { vector ->
            val identifier = vector.string("identifier")
            val context = NamecoinNameContexts.parse(identifier, vector.bool("subject_only"))
            val rawRecord =
                vector
                    .getValue("records")
                    .jsonObject
                    .getValue(context.name)
                    .jsonPrimitive.content
            val actual =
                AccountLifecycleParser
                    .evaluateInlineRoot(
                        identifier = identifier,
                        subjectPubkey = vector.string("subject"),
                        subjectOnly = vector.bool("subject_only"),
                        rawRecord = rawRecord,
                        oldEvents = vector.events("old_events"),
                        newEvents = vector.events("new_events"),
                        previousConfirmation = vector.bool("previous_confirmation"),
                        observation =
                            when (vector.string("observation")) {
                                "provisional" -> LifecycleObservation.PROVISIONAL
                                "reregistered" -> LifecycleObservation.REREGISTERED
                                else -> LifecycleObservation.CURRENT
                            },
                    ).result
            assertEquals(expectedResult(vector), actual, vector.string("id"))
        }
    }

    @Test
    fun availabilityVectors() {
        vectors.filter { it.string("mode") == "availability" }.forEach { vector ->
            val cached = (vector["cached"] as? JsonObject)?.let { json.decodeFromJsonElement(AccountLifecycleResult.serializer(), it) }
            assertEquals(
                expectedResult(vector),
                AccountLifecycleParser.stale(cached, vector.string("observation")),
                vector.string("id"),
            )
        }
    }

    @Test
    fun discoveryVectors() {
        vectors.filter { it.string("mode") == "discovery" }.forEach { vector ->
            val actual =
                AccountLifecycleParser.discover(
                    subjectPubkey = vector.string("subject"),
                    knownName = vector["known_name"]?.jsonPrimitive?.takeIf { it.isString }?.content,
                    backlinkEvents = vector.events("backlink_events"),
                )
            val expected = vector.getValue("expected").jsonObject
            assertEquals(expected["candidate_name"]?.jsonPrimitive?.takeIf { it.isString }?.content, actual.candidateName, vector.string("id"))
            assertEquals(expected.getValue("signed_association").jsonPrimitive.boolean, actual.signedAssociation, vector.string("id"))
            assertEquals(expected.getValue("warning").jsonPrimitive.content, actual.warning, vector.string("id"))
        }
    }

    @Test
    fun timestampsNeverClassifyEvents() {
        val vector = vectors.single { it.string("mode") == "policy" }
        val since = vector.long("since")
        val classifications = vector.getValue("event_created_at_values").jsonArray.map { accountLifecycleEventClassification(it.jsonPrimitive.long, since) }
        assertTrue(classifications.all { it == LifecycleEventClassification.UNCLASSIFIED })
        assertTrue(AccountLifecycleResult(status = AccountLifecycleStatus.CURRENT).automaticActions.isEmpty())
    }

    @Test
    fun duplicateKeysFailClosed() {
        val old =
            document
                .getValue("constants")
                .jsonObject
                .getValue("OLD")
                .jsonPrimitive.content
        val result =
            AccountLifecycleParser
                .evaluateInlineRoot(
                    identifier = "example.bit",
                    subjectPubkey = old,
                    rawRecord =
                        "{\"nostr\":{\"names\":{\"_\":\"$old\"},\"lifecycle\":{\"_\":{\"state\":\"compromised\",\"state\":\"retired\"}}}}",
                    oldEvents = listOf(event("old_root")),
                ).result
        assertEquals(AccountLifecycleStatus.INVALID, result.status)
        assertEquals(listOf("malformed_record"), result.diagnostics)
        assertTrue(result.successorPubkey == null)
    }

    @Test
    fun fullTraversalVectorsRemainExplicitlyDeferred() {
        val deferred = vectors.filter { it.string("mode") == "full" }.map { it.string("id") }.toSet()
        assertEquals(
            setOf(
                "full_import_complete_nostr",
                "full_import_importer_wins_no_deep_merge",
                "full_import_failure_fail_closed",
                "full_delegate_exclusive_success",
                "full_delegate_failure_fail_closed",
                "full_map_selected_child_only",
                "full_map_no_ancestor_lifecycle",
            ),
            deferred,
        )
    }

    private fun expectedResult(vector: JsonObject): AccountLifecycleResult = json.decodeFromJsonElement(AccountLifecycleResult.serializer(), vector.getValue("expected"))

    private fun JsonObject.events(key: String): List<Event> = getValue(key).jsonArray.map { event(it.jsonPrimitive.content) }

    private fun event(label: String): Event {
        val value =
            events
                .getValue(label)
                .jsonObject
                .getValue("event")
                .jsonObject
        return SignedLifecycleEvent(
            id = value.string("id"),
            pubkey = value.string("pubkey"),
            createdAt = value.long("created_at"),
            kind = value.int("kind"),
            tags = value.getValue("tags").jsonArray.map { tag -> tag.jsonArray.map { it.jsonPrimitive.content } },
            content = value.string("content"),
            sig = value.string("sig"),
        ).toEvent()
    }

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private fun JsonObject.int(key: String): Int = getValue(key).jsonPrimitive.int

    private fun JsonObject.long(key: String): Long = getValue(key).jsonPrimitive.long

    private fun JsonObject.bool(key: String): Boolean = getValue(key).jsonPrimitive.boolean
}
