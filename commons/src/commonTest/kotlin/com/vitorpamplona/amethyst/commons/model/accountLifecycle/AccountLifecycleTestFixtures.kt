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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

internal object AccountLifecycleTestFixtures {
    private val document = Json.parseToJsonElement(ACCOUNT_LIFECYCLE_CONFORMANCE_VECTORS).jsonObject
    private val events = document.getValue("events").jsonObject
    private val constants = document.getValue("constants").jsonObject
    private val examples = document.getValue("fixed_example_values").jsonObject

    val old = constants.getValue("OLD").jsonPrimitive.content
    val new = constants.getValue("NEW").jsonPrimitive.content
    val other = constants.getValue("OTHER").jsonPrimitive.content
    val compromised = examples.getValue("compromised_309").jsonPrimitive.content
    val retired = examples.getValue("retired_286").jsonPrimitive.content

    fun event(label: String): Event {
        val value =
            events
                .getValue(label)
                .jsonObject
                .getValue("event")
                .jsonObject
        return SignedLifecycleEvent(
            id = value.getValue("id").jsonPrimitive.content,
            pubkey = value.getValue("pubkey").jsonPrimitive.content,
            createdAt = value.getValue("created_at").jsonPrimitive.long,
            kind = value.getValue("kind").jsonPrimitive.int,
            tags = value.getValue("tags").jsonArray.map { tag -> tag.jsonArray.map { it.jsonPrimitive.content } },
            content = value.getValue("content").jsonPrimitive.content,
            sig = value.getValue("sig").jsonPrimitive.content,
        ).toEvent()
    }

    val metadata =
        LifecycleMetadataProvider { pubkey ->
            when (pubkey) {
                old -> listOf(event("old_root"))
                new -> listOf(event("new_current"))
                else -> emptyList()
            }
        }

    fun raw(
        value: String = compromised,
        txid: String = "a".repeat(64),
        height: Int = 900_000,
        expiresIn: Int = 20_000,
    ) = NamecoinRawRecord(
        value = value,
        provenance =
            LifecycleProvenance(
                selectedName = "d/example",
                dependencies = listOf("d/example"),
                txid = txid,
                height = height,
                expiresIn = expiresIn,
                configuredBackend = "fake",
            ),
    )
}
