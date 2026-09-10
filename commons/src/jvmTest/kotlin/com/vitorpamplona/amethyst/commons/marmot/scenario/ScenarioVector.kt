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
    val pendingResolutions: List<PendingResolution> = emptyList(),
    val quiescentClients: List<String> = emptyList(),
    val unmodelledOutcomes: List<UnmodelledOutcome> = emptyList(),
) {
    class Step(
        val type: String,
        private val raw: JsonObject,
    ) {
        fun string(key: String): String? = (raw[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

        fun int(key: String): Int? = (raw[key] as? JsonPrimitive)?.content?.toIntOrNull()

        fun strings(key: String): List<String> =
            (raw[key] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                .orEmpty()

        /**
         * A step nested inside this one, as its own [Step].
         *
         * `in_group` is a wrapper: it names a group label and carries the real
         * step under `action`. Treating it as a leaf silently skipped every
         * create and every message inside it.
         */
        fun step(key: String): Step? =
            (raw[key] as? JsonObject)?.let {
                Step((it["type"] as? JsonPrimitive)?.content.orEmpty(), it)
            }

        /** A nested object read as a [Step] so the same accessors work on it. */
        fun obj(key: String): Step? = (raw[key] as? JsonObject)?.let { Step(key, it) }

        /** A nested array of objects, each read as a [Step]. */
        fun steps(key: String): List<Step> =
            (raw[key] as? JsonArray)
                ?.mapNotNull { element ->
                    (element as? JsonObject)?.let { Step((it["type"] as? JsonPrimitive)?.content.orEmpty(), it) }
                }.orEmpty()

        /**
         * The keys this object carries. A fault selector is checked key by key
         * so an unknown one can be refused rather than quietly widening it.
         */
        fun keys(): Set<String> = raw.keys
    }

    /** What one client's state must look like when the script says to look. */
    class Observation(
        val client: String,
        val epoch: Long?,
        val memberCount: Int?,
        val groupName: String?,
        val receivedPayloads: List<String>,
        /**
         * Members this client must have SEEN JOIN, by client name. Null when
         * the vector does not state it — which is not the same as an empty
         * list, and an empty list is itself an assertion.
         */
        val addedMembers: List<String>? = null,
        /** The group description the vector states, when it states one. */
        val groupDescription: String? = null,
    )

    /**
     * A publication the script named, and how the reference says it ended.
     *
     * `confirmed` means the relay accepted it and the commit became canonical;
     * `rolled_back` means it did not and the committer stayed where it was.
     * Checking these is the only thing that proves our publish-before-apply
     * gate resolved the same way the reference's did.
     */
    class PendingResolution(
        val client: String,
        val publication: String,
        val resolution: String,
    )

    /** The outcome types this runner has no check for, named so it can refuse. */
    class UnmodelledOutcome(
        val type: String,
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
            // TWO vector shapes ship side by side. The older one nests a
            // trace under `expected_trace.observations`; the newer one lists
            // typed entries under `expected_outcomes`. Reading only the first
            // meant SEVEN of the nine vectors here parsed to zero expectations
            // and "passed" without checking anything — the exact failure this
            // runner exists to avoid.
            val traced =
                ((root["expected_trace"] as? JsonObject)?.get("observations") as? JsonArray)
                    ?.map { observationOf(it as JsonObject) }
                    .orEmpty()

            val outcomes = (root["expected_outcomes"] as? JsonArray).orEmpty()
            val stated = mutableListOf<Observation>()
            val resolutions = mutableListOf<PendingResolution>()
            val quiescent = mutableListOf<String>()
            val unmodelled = mutableListOf<UnmodelledOutcome>()

            outcomes.forEach { element ->
                val obj = element as JsonObject
                when ((obj["type"] as? JsonPrimitive)?.content) {
                    "client_state" -> stated.add(observationOf(obj))

                    // A converged set states the same per-client facts for
                    // several clients at once.
                    "clients_converged" ->
                        (obj["clients"] as? JsonArray).orEmpty().forEach { name ->
                            stated.add(
                                Observation(
                                    client = (name as JsonPrimitive).content,
                                    epoch = (obj["epoch"] as? JsonPrimitive)?.content?.toLongOrNull(),
                                    memberCount = (obj["member_count"] as? JsonPrimitive)?.content?.toIntOrNull(),
                                    groupName = null,
                                    receivedPayloads = emptyList(),
                                ),
                            )
                        }

                    // A profile assertion is per-client state like any other,
                    // just stated separately because it is about the group's
                    // metadata rather than its membership.
                    "group_profile" ->
                        stated.add(
                            Observation(
                                client = obj.getValue("client").jsonPrimitive.content,
                                epoch = null,
                                memberCount = null,
                                groupName = (obj["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
                                receivedPayloads = emptyList(),
                                groupDescription = (obj["description"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
                            ),
                        )

                    "pending_resolution" ->
                        resolutions.add(
                            PendingResolution(
                                client = obj.getValue("client").jsonPrimitive.content,
                                publication = obj.getValue("pending").jsonPrimitive.content,
                                resolution = obj.getValue("resolution").jsonPrimitive.content,
                            ),
                        )

                    "no_pending_work" ->
                        (obj["clients"] as? JsonArray).orEmpty().forEach {
                            quiescent.add((it as JsonPrimitive).content)
                        }

                    else ->
                        unmodelled.add(
                            UnmodelledOutcome((obj["type"] as? JsonPrimitive)?.content.orEmpty()),
                        )
                }
            }

            return ScenarioVector(
                name = (root["scenario_name"] as JsonPrimitive).content,
                conformanceVersion = (root["conformance_version"] as? JsonPrimitive)?.content.orEmpty(),
                clients = (scenario["clients"] as JsonArray).map { (it as JsonPrimitive).content },
                steps = steps,
                observations = traced + stated,
                pendingResolutions = resolutions,
                quiescentClients = quiescent,
                unmodelledOutcomes = unmodelled,
            )
        }

        private fun observationOf(obj: JsonObject) =
            Observation(
                client = obj.getValue("client").jsonPrimitive.content,
                epoch = (obj["epoch"] as? JsonPrimitive)?.content?.toLongOrNull(),
                memberCount = (obj["member_count"] as? JsonPrimitive)?.content?.toIntOrNull(),
                groupName = (obj["group_name"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
                receivedPayloads =
                    (obj["received_payloads"] as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.content }
                        .orEmpty(),
                addedMembers =
                    (obj["added_members"] as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.content },
            )
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
 * Raised when a vector states an expected outcome this runner cannot check.
 *
 * Same contract as [UnsupportedScenarioStep], one level up: a vector whose
 * conclusion we cannot evaluate has not been conformed to, however cleanly its
 * steps replayed. Silently dropping the outcome would turn the vector into an
 * expensive no-op that reports green.
 */
class UnsupportedScenarioOutcome(
    val outcomeType: String,
) : IllegalStateException(
        "expected outcome '$outcomeType' has no check in this runner — the vector is refused " +
            "rather than passed on the outcomes that happen to be modelled",
    )
