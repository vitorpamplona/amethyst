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
package com.vitorpamplona.amethyst.commons.marmot.scenario

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * One CGKA conformance scenario vector, as the reference implementation writes
 * them (`crates/cgka-conformance-simulator/vectors/`).
 *
 * A vector is a script, not a byte fixture: a client roster, an ordered list of
 * steps, and the trace an implementation is expected to produce. Their manifest
 * marks these `portable`, which is exactly the claim being tested — that a
 * second implementation replaying the same script reaches the same state.
 *
 * Parsed loosely on purpose. The step vocabulary is open (28 types across the
 * full set) and only some are implemented; a strict model would fail to LOAD a
 * vector the runner is entitled to refuse for a much clearer reason.
 */
class ScenarioVector(
    val name: String,
    val conformanceVersion: String,
    val clients: List<String>,
    val steps: List<Step>,
    val observations: List<Observation>,
) {
    class Step(
        val type: String,
        private val raw: JsonObject,
    ) {
        fun string(key: String): String? = (raw[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        fun strings(key: String): List<String> =
            (raw[key] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                .orEmpty()
    }

    /** What one client's state must look like when the script says to look. */
    class Observation(
        val client: String,
        val epoch: Long?,
        val memberCount: Int?,
        val groupName: String?,
        val receivedPayloads: List<String>,
    )

    companion object {
        private val parser = Json { ignoreUnknownKeys = true }

        fun parse(json: String): ScenarioVector {
            val root = parser.parseToJsonElement(json) as JsonObject
            val scenario = root["scenario"] as JsonObject
            val steps =
                (scenario["steps"] as JsonArray).map { element ->
                    val obj = element as JsonObject
                    Step(obj.getValue("type").jsonPrimitive.content, obj)
                }
            val observations =
                ((root["expected_trace"] as? JsonObject)?.get("observations") as? JsonArray)
                    ?.map { element ->
                        val obj = element as JsonObject
                        Observation(
                            client = obj.getValue("client").jsonPrimitive.content,
                            epoch = (obj["epoch"] as? JsonPrimitive)?.content?.toLongOrNull(),
                            memberCount = (obj["member_count"] as? JsonPrimitive)?.content?.toIntOrNull(),
                            groupName = (obj["group_name"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
                            receivedPayloads =
                                (obj["received_payloads"] as? JsonArray)
                                    ?.mapNotNull { (it as? JsonPrimitive)?.content }
                                    .orEmpty(),
                        )
                    }.orEmpty()
            return ScenarioVector(
                name = (root["scenario_name"] as JsonPrimitive).content,
                conformanceVersion = (root["conformance_version"] as? JsonPrimitive)?.content.orEmpty(),
                clients = (scenario["clients"] as JsonArray).map { (it as JsonPrimitive).content },
                steps = steps,
                observations = observations,
            )
        }
    }
}

/** Raised when a vector uses a step this runner has not implemented. */
class UnsupportedScenarioStep(
    val stepType: String,
) : IllegalStateException(
        "scenario step '$stepType' is not implemented — the runner refuses a vector it cannot " +
            "faithfully replay rather than reporting a pass it did not earn",
    )

/**
 * Raised where our engine cannot reach the vector's trace because it batches
 * differently, not because either side is wrong.
 *
 * The one case today: the reference adds every invitee named by `create_group`
 * in a SINGLE commit, so a group created with two invitees is at epoch 1. Our
 * `MarmotManager.addMember` stages one Add per commit, so the same group
 * reaches epoch 2. Both are valid MLS — a commit per Add is not a protocol
 * error, and a peer processes either — but the epoch numbers differ, and so
 * does the round-trip cost of creating a group.
 *
 * Kept as its own signal rather than folded into a trace mismatch: a divergence
 * we understand and have chosen not to fix yet should not read like a bug we
 * have not noticed.
 */
class ScenarioBatchingDivergence(
    message: String,
) : IllegalStateException(message)
