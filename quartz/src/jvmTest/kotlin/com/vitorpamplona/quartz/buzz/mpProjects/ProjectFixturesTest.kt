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
package com.vitorpamplona.quartz.buzz.mpProjects

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs every case of Buzz's NIP-MP ingest oracle (`docs/nips/NIP-MP.fixtures.json`, copied
 * verbatim to `resources/buzz/NIP-MP.fixtures.json`) through [ProjectValidator]: each
 * `accept` must pass, and each `reject` must fail on one of the case's `reject_rules` — so the
 * port cannot pass a reject case by refusing it for an unrelated reason. Upstream's relay and
 * SDK validators run the same file.
 */
class ProjectFixturesTest {
    private val fixtures =
        Json
            .parseToJsonElement(
                this::class.java
                    .getResourceAsStream("/buzz/NIP-MP.fixtures.json")!!
                    .readBytes()
                    .decodeToString(),
            ).jsonObject

    private fun JsonArray.toTags() = map { tag -> tag.jsonArray.map { it.jsonPrimitive.content }.toTypedArray() }.toTypedArray()

    @Test
    fun everyFixtureCaseMatchesItsExpectation() {
        assertEquals(ProjectEvent.KIND, fixtures["kind"]!!.jsonPrimitive.int)
        assertEquals(ProjectValidator.MEMBER_CAP, fixtures["member_cap"]!!.jsonPrimitive.int)

        val cases = fixtures["cases"]!!.jsonArray
        assertTrue(cases.size >= 31, "fixture file shrank: ${cases.size} cases")

        var accepted = 0
        var rejected = 0
        for (case in cases) {
            val obj = case.jsonObject
            val name = obj["name"]!!.jsonPrimitive.content
            val template = obj["template"]!!.jsonObject
            assertEquals(ProjectEvent.KIND, template["kind"]!!.jsonPrimitive.int, name)
            val tags = template["tags"]!!.jsonArray.toTags()
            val result = ProjectValidator.validate(tags)

            when (obj["expect"]!!.jsonPrimitive.content) {
                "accept" -> {
                    assertNull(result, "$name should be accepted, got $result")
                    val event = ProjectEvent("0".repeat(64), "f".repeat(64), 1, tags, template["content"]!!.jsonPrimitive.content, "sig")
                    assertTrue(event.isWellFormed(), name)
                    accepted++
                }
                "reject" -> {
                    assertNotNull(result, "$name should be rejected")
                    val allowed = obj["reject_rules"]!!.jsonArray.map { it.jsonPrimitive.content }
                    assertTrue(result.rule.id in allowed, "$name rejected by ${result.rule.id}, expected one of $allowed")
                    rejected++
                }
                else -> error("unknown expectation in $name")
            }
        }
        assertEquals(11, accepted)
        assertEquals(20, rejected)
    }
}
