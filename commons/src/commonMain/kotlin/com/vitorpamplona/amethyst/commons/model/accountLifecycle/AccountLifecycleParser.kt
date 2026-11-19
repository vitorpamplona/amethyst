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
import com.vitorpamplona.quartz.nip01Core.crypto.generateId
import com.vitorpamplona.quartz.nip01Core.crypto.verifySignature
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val LOWERCASE_PUBKEY = Regex("^[0-9a-f]{64}$")
private val JSON_INTEGER = Regex("^(0|[1-9][0-9]*)$")

object AccountLifecycleParser {
    private data class Candidate(
        val localPart: String,
        val state: AccountLifecycleState,
        val since: Long?,
        val successor: String?,
        val invalidSuccessor: Boolean,
        val diagnostics: List<String>,
    )

    private class CandidateFailure(
        val diagnostic: String,
    ) : IllegalArgumentException(diagnostic)

    fun evaluateInlineRoot(
        identifier: String,
        subjectPubkey: String,
        subjectOnly: Boolean = false,
        rawRecord: String,
        oldEvents: List<Event> = emptyList(),
        newEvents: List<Event> = emptyList(),
        previousConfirmation: Boolean = false,
        observation: LifecycleObservation = LifecycleObservation.CURRENT,
        provenance: LifecycleProvenance? = null,
    ): AccountLifecycleEvaluation {
        val context = NamecoinNameContexts.parse(identifier, subjectOnly)
        if (context.mapPath.isNotEmpty()) {
            return evaluation(
                context,
                subjectPubkey,
                rawRecord,
                unsupportedTraversal("map_requires_traversal"),
            )
        }
        if (rawRecord.encodeToByteArray().size > NAMECOIN_MAX_VALUE_BYTES) {
            return evaluation(context, subjectPubkey, rawRecord, invalid("value_too_large"))
        }

        val root =
            try {
                StrictLifecycleJson.parse(rawRecord) as? JsonObject
            } catch (_: Exception) {
                null
            }
        if (root == null) return evaluation(context, subjectPubkey, rawRecord, invalid("malformed_record"))
        if (root["delegate"] != null && root["delegate"] !is JsonNull) {
            return evaluation(context, subjectPubkey, rawRecord, unsupportedTraversal("delegate_requires_traversal"))
        }
        if (root["import"] != null && root["import"] !is JsonNull) {
            return evaluation(context, subjectPubkey, rawRecord, unsupportedTraversal("import_requires_traversal"))
        }
        val defaultMap = (root["map"] as? JsonObject)?.get("")
        if (defaultMap != null && defaultMap !is JsonNull) {
            return evaluation(context, subjectPubkey, rawRecord, unsupportedTraversal("map_requires_traversal"))
        }

        val selectedProvenance =
            LifecycleProvenance(
                selectedName = context.name,
                dependencies = listOf(context.name),
                txid = provenance?.txid,
                height = provenance?.height,
                expiresIn = provenance?.expiresIn,
                configuredBackend = provenance?.configuredBackend,
            )
        val validOldEvents = oldEvents.filter { it.isValidKind0() }
        val validNewEvents = newEvents.filter { it.isValidKind0() }
        val latestOld = latestEvent(validOldEvents, subjectPubkey)
        val nostrElement = root["nostr"]
        val baseNip05 = baseNip05(context, subjectOnly, subjectPubkey, nostrElement, latestOld)

        if (nostrElement == null || (nostrElement is JsonPrimitive && nostrElement.isString)) {
            val result =
                AccountLifecycleResult(
                    status = AccountLifecycleStatus.NO_DECLARATION,
                    baseNip05 = baseNip05,
                    diagnostics = listOf("no_lifecycle"),
                    provenance = selectedProvenance,
                )
            return evaluation(context, subjectPubkey, rawRecord, finishObservation(result, observation))
        }
        if (nostrElement !is JsonObject) {
            return evaluation(
                context,
                subjectPubkey,
                rawRecord,
                invalid("invalid_nostr", baseNip05, selectedProvenance),
            )
        }

        val lifecycleElement = nostrElement["lifecycle"]
        if (lifecycleElement == null) {
            val result =
                AccountLifecycleResult(
                    status = AccountLifecycleStatus.NO_DECLARATION,
                    baseNip05 = baseNip05,
                    diagnostics = listOf("no_lifecycle"),
                    provenance = selectedProvenance,
                )
            return evaluation(context, subjectPubkey, rawRecord, finishObservation(result, observation))
        }
        if (lifecycleElement !is JsonObject) {
            return evaluation(
                context,
                subjectPubkey,
                rawRecord,
                invalid("invalid_lifecycle", baseNip05, selectedProvenance),
            )
        }

        val initialDiagnostics = if (nostrElement.containsKey("extension")) listOf("unknown_fields_ignored") else emptyList()
        val localParts =
            if (subjectOnly) {
                matchingSubjectLocalParts(nostrElement, subjectPubkey)
            } else {
                listOfNotNull(context.localPart)
            }
        val candidates = mutableListOf<Candidate>()
        var bindingError: String? = null
        for (localPart in localParts) {
            val error = exactBindingError(nostrElement, localPart, subjectPubkey)
            if (error != null) {
                bindingError = error
            } else if (lifecycleElement.containsKey(localPart)) {
                try {
                    candidates.add(parseCandidate(localPart, lifecycleElement.getValue(localPart), subjectPubkey))
                } catch (failure: CandidateFailure) {
                    val status =
                        if (failure.diagnostic == "unknown_state") {
                            AccountLifecycleStatus.UNSUPPORTED_STATE
                        } else {
                            AccountLifecycleStatus.INVALID
                        }
                    return evaluation(
                        context,
                        subjectPubkey,
                        rawRecord,
                        AccountLifecycleResult(
                            status = status,
                            baseNip05 = baseNip05,
                            diagnostics = listOf(failure.diagnostic),
                            provenance = selectedProvenance,
                        ),
                    )
                }
            }
        }

        if (candidates.isEmpty()) {
            val diagnostic = bindingError ?: "no_lifecycle"
            val status = if (bindingError == null) AccountLifecycleStatus.NO_DECLARATION else AccountLifecycleStatus.INVALID
            return evaluation(
                context,
                subjectPubkey,
                rawRecord,
                AccountLifecycleResult(
                    status = status,
                    baseNip05 = baseNip05,
                    diagnostics = listOf(diagnostic),
                    provenance = selectedProvenance,
                ),
            )
        }

        val backlinksByCandidate =
            candidates.associate { candidate ->
                candidate.localPart to
                    latestEvent(
                        validOldEvents.filter { eventMatches(it, subjectPubkey, context, candidate.localPart) },
                        subjectPubkey,
                    )
            }
        val backlinked =
            candidates.filter { candidate ->
                val backlink = backlinksByCandidate[candidate.localPart]
                backlink != null &&
                    (observation != LifecycleObservation.REREGISTERED || latestOld?.id == backlink.id)
            }
        if (backlinked.isEmpty()) {
            val candidate = candidates.first()
            val result =
                AccountLifecycleResult(
                    status = AccountLifecycleStatus.CURRENT,
                    scope = AccountLifecycleScope.DIRECT_RECORD,
                    state = candidate.state,
                    since = candidate.since,
                    baseNip05 = baseNip05,
                    diagnostics =
                        (initialDiagnostics + candidate.diagnostics + "direct_record_claim_only" + "old_backlink_missing")
                            .distinct()
                            .sorted(),
                    provenance = selectedProvenance,
                )
            return evaluation(context, subjectPubkey, rawRecord, finishObservation(result, observation))
        }

        val diagnostics = initialDiagnostics.toMutableSet()
        candidates.forEach { diagnostics.addAll(it.diagnostics) }
        if (backlinked.size > 1) diagnostics.add("multiple_exact_localparts")
        val states = backlinked.map { it.state }.toSet()
        val successors = backlinked.map { it.successor }.toSet()
        val backlinkEvidence =
            backlinked.map { candidate ->
                val event = requireNotNull(backlinksByCandidate[candidate.localPart])
                LifecycleBacklink(
                    localPart = candidate.localPart,
                    event = SignedLifecycleEvent.fromEvent(event),
                    current = latestOld?.let { eventMatches(it, subjectPubkey, context, candidate.localPart) } == true,
                )
            }

        if (states.size > 1 || successors.size > 1) {
            if (states.size > 1) diagnostics.add("conflicting_states")
            if (successors.size > 1) diagnostics.add("conflicting_successors")
            val result =
                AccountLifecycleResult(
                    status = AccountLifecycleStatus.CURRENT,
                    scope = AccountLifecycleScope.CONFLICTING,
                    state = if (AccountLifecycleState.COMPROMISED in states) AccountLifecycleState.COMPROMISED else AccountLifecycleState.RETIRED,
                    successorConfirmation = SuccessorConfirmation.CONFLICTING,
                    baseNip05 = baseNip05,
                    diagnostics = diagnostics.sorted(),
                    provenance = selectedProvenance,
                )
            return evaluation(context, subjectPubkey, rawRecord, finishObservation(result, observation), backlinkEvidence)
        }

        val candidate = backlinked.first()
        val sinceValues = backlinked.map { it.since }.toSet()
        val since =
            if (sinceValues.size == 1) {
                candidate.since
            } else {
                diagnostics.add("conflicting_since")
                null
            }
        if (backlinked.size == 1 && backlinkEvidence.firstOrNull()?.current == false) {
            diagnostics.add("historical_old_backlink")
        }
        val successor = candidate.successor
        val (confirmation, aliases, successorDiagnostics) =
            successorConfirmation(
                context = context,
                nostr = nostrElement,
                successor = successor,
                invalidSuccessor = backlinked.any { it.invalidSuccessor },
                validNewEvents = validNewEvents,
                previousConfirmation = previousConfirmation,
            )
        diagnostics.addAll(successorDiagnostics)
        if (hasNonExclusivePredecessor(nostrElement, successor)) diagnostics.add("non_exclusive_predecessor")
        val result =
            AccountLifecycleResult(
                status = AccountLifecycleStatus.CURRENT,
                scope = AccountLifecycleScope.ACCOUNT,
                state = candidate.state,
                since = since,
                successorPubkey = successor,
                successorConfirmation = confirmation,
                confirmationAliases = aliases,
                baseNip05 = baseNip05,
                diagnostics = diagnostics.sorted(),
                provenance = selectedProvenance,
            )
        return evaluation(context, subjectPubkey, rawRecord, finishObservation(result, observation), backlinkEvidence)
    }

    /**
     * Returns signed OLD claims for this exact name context. These associations are retained even
     * when the current record has no lifecycle declaration, so a later kind-0 rewrite cannot make a
     * previously known name undiscoverable. Signature validity and the exact local-part are checked
     * here rather than trusted from the caller's metadata projection.
     */
    fun verifiedBacklinks(
        identifier: String,
        subjectPubkey: String,
        oldEvents: List<Event>,
        subjectOnly: Boolean = false,
    ): List<LifecycleBacklink> {
        val context = NamecoinNameContexts.parse(identifier, subjectOnly)
        val validEvents = oldEvents.filter { it.isValidKind0() && it.pubKey == subjectPubkey }
        val latest = latestEvent(validEvents, subjectPubkey)
        return validEvents
            .mapNotNull { event ->
                val claimedIdentifier = eventNip05(event) ?: return@mapNotNull null
                val claimed =
                    try {
                        NamecoinNameContexts.parse(claimedIdentifier)
                    } catch (_: Exception) {
                        return@mapNotNull null
                    }
                if (!NamecoinNameContexts.sameContext(claimed, context)) return@mapNotNull null
                val localPart = claimed.localPart ?: return@mapNotNull null
                if (!eventMatches(event, subjectPubkey, context, localPart)) return@mapNotNull null
                LifecycleBacklink(
                    localPart = localPart,
                    event = SignedLifecycleEvent.fromEvent(event),
                    current = latest?.id == event.id,
                )
            }.distinctBy { it.event.id }
            .sortedByDescending { it.event.createdAt }
    }

    /**
     * Finds exact OLD bindings whose valid lifecycle entry names this NEW key. This is only a
     * candidate list: the caller must still evaluate OLD's signed backlink and NEW's latest kind 0
     * before exposing a reciprocal predecessor link. Unknown states and malformed entries do not
     * become candidates. At most the entries in one 520-byte inline record are returned.
     */
    fun predecessorSubjects(
        identifier: String,
        successorPubkey: String,
        rawRecord: String,
    ): List<String> {
        if (!LOWERCASE_PUBKEY.matches(successorPubkey) || rawRecord.encodeToByteArray().size > NAMECOIN_MAX_VALUE_BYTES) {
            return emptyList()
        }
        val context =
            try {
                NamecoinNameContexts.parse(identifier)
            } catch (_: IllegalArgumentException) {
                return emptyList()
            }
        if (context.mapPath.isNotEmpty()) return emptyList()
        val root =
            try {
                StrictLifecycleJson.parse(rawRecord) as? JsonObject
            } catch (_: Exception) {
                null
            } ?: return emptyList()
        if (root["delegate"].isPresentNonNull() || root["import"].isPresentNonNull()) return emptyList()
        if ((root["map"] as? JsonObject)?.get("").isPresentNonNull()) return emptyList()
        val nostr = root["nostr"] as? JsonObject ?: return emptyList()
        val lifecycle = nostr["lifecycle"] as? JsonObject ?: return emptyList()
        val names = nostr["names"] as? JsonObject

        return lifecycle.entries
            .mapNotNull { (localPart, element) ->
                val bound =
                    when {
                        names?.containsKey(localPart) == true -> names[localPart]
                        localPart == "_" -> nostr["pubkey"]
                        else -> null
                    }
                val subject = (bound as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                if (subject == null ||
                    !LOWERCASE_PUBKEY.matches(subject) ||
                    subject == successorPubkey ||
                    exactBindingError(nostr, localPart, subject) != null
                ) {
                    return@mapNotNull null
                }
                val candidate =
                    try {
                        parseCandidate(localPart, element, subject)
                    } catch (_: CandidateFailure) {
                        return@mapNotNull null
                    }
                subject.takeIf { candidate.successor == successorPubkey }
            }.distinct()
            .sorted()
    }

    private fun JsonElement?.isPresentNonNull(): Boolean = this != null && this !is JsonNull

    fun unavailable(diagnostic: String = "lookup_unavailable"): AccountLifecycleResult =
        AccountLifecycleResult(
            status = AccountLifecycleStatus.UNAVAILABLE,
            diagnostics = listOf(diagnostic),
        )

    fun stale(
        cached: AccountLifecycleResult?,
        observation: String,
    ): AccountLifecycleResult {
        if (cached == null) return unavailable()
        return cached.copy(
            status = AccountLifecycleStatus.STALE,
            successorConfirmation =
                if (cached.successorPubkey == null) cached.successorConfirmation else SuccessorConfirmation.STALE,
            confirmationAliases = if (cached.successorPubkey == null) cached.confirmationAliases else emptyList(),
            baseNip05 = BaseNip05Status.UNAVAILABLE,
            diagnostics = (cached.diagnostics + "stale_after_$observation").distinct().sorted(),
        )
    }

    fun discover(
        subjectPubkey: String,
        knownName: String?,
        backlinkEvents: List<Event>,
    ): LifecycleDiscoveryResult {
        if (knownName == null) return LifecycleDiscoveryResult(warning = "undiscoverable")
        val context = NamecoinNameContexts.parse(knownName)
        val signedAssociation =
            backlinkEvents
                .filter { it.isValidKind0() }
                .any { eventMatches(it, subjectPubkey, context, requireNotNull(context.localPart)) }
        return LifecycleDiscoveryResult(
            candidateName = context.name,
            signedAssociation = signedAssociation,
            warning = "recheck_required",
        )
    }

    private fun matchingSubjectLocalParts(
        nostr: JsonObject,
        subjectPubkey: String,
    ): List<String> {
        val names = nostr["names"] as? JsonObject
        val matches =
            names
                ?.entries
                .orEmpty()
                .filter { (_, value) -> (value as? JsonPrimitive)?.takeIf { it.isString }?.content == subjectPubkey }
                .map { it.key }
                .toMutableSet()
        if (names?.containsKey("_") != true && (nostr["pubkey"] as? JsonPrimitive)?.contentOrNull == subjectPubkey) matches.add("_")
        return matches.sorted()
    }

    private fun exactBindingError(
        nostr: JsonObject,
        localPart: String,
        subjectPubkey: String,
    ): String? {
        val namesElement = nostr["names"]
        if (namesElement != null && namesElement !is JsonObject) return "invalid_subject_binding"
        val names = namesElement as? JsonObject
        if (localPart == "_" && names?.containsKey("_") == true && nostr.containsKey("pubkey")) {
            if (names["_"] != nostr["pubkey"]) return "conflicting_root_bindings"
        }
        val bound =
            when {
                names?.containsKey(localPart) == true -> names[localPart]
                localPart == "_" && nostr.containsKey("pubkey") -> nostr["pubkey"]
                else -> return "exact_binding_missing"
            }
        val boundString = (bound as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (boundString == null || !LOWERCASE_PUBKEY.matches(boundString)) return "invalid_subject_binding"
        return if (boundString == subjectPubkey) null else "exact_binding_missing"
    }

    private fun parseCandidate(
        localPart: String,
        element: JsonElement,
        subjectPubkey: String,
    ): Candidate {
        val entry = element as? JsonObject ?: throw CandidateFailure("invalid_lifecycle_entry")
        val stateElement = entry["state"] ?: throw CandidateFailure("missing_state")
        val stateString = (stateElement as? JsonPrimitive)?.takeIf { it.isString }?.content
        val state =
            when (stateString) {
                "retired" -> AccountLifecycleState.RETIRED
                "compromised" -> AccountLifecycleState.COMPROMISED
                else -> throw CandidateFailure("unknown_state")
            }
        val diagnostics = mutableListOf<String>()
        var since: Long? = null
        if (entry.containsKey("since")) {
            val primitive = entry["since"] as? JsonPrimitive
            val raw = primitive?.takeIf { !it.isString }?.content
            since =
                if (primitive != null && !primitive.isString && raw != null && JSON_INTEGER.matches(raw)) {
                    raw.toLongOrNull()?.takeIf { it >= 0 && it <= ACCOUNT_LIFECYCLE_MAX_SINCE }
                } else {
                    null
                }
            if (since == null) diagnostics.add("invalid_since")
        }
        var successor: String? = null
        var invalidSuccessor = false
        if (entry.containsKey("successor")) {
            val candidate = (entry["successor"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (candidate == null || !LOWERCASE_PUBKEY.matches(candidate) || candidate == subjectPubkey) {
                invalidSuccessor = true
                diagnostics.add("invalid_successor")
            } else {
                successor = candidate
            }
        }
        if (entry.keys.any { it !in setOf("state", "since", "successor") }) diagnostics.add("unknown_fields_ignored")
        return Candidate(localPart, state, since, successor, invalidSuccessor, diagnostics)
    }

    private fun baseNip05(
        context: NamecoinNameContext,
        subjectOnly: Boolean,
        subjectPubkey: String,
        nostr: JsonElement?,
        latestOld: Event?,
    ): BaseNip05Status {
        if (subjectOnly) return BaseNip05Status.NOT_APPLICABLE
        val localPart = context.localPart ?: return BaseNip05Status.UNVERIFIED
        var bound: String? = null
        if (nostr is JsonPrimitive && nostr.isString && localPart == "_" && LOWERCASE_PUBKEY.matches(nostr.content)) {
            bound = nostr.content
        } else if (nostr is JsonObject) {
            val names = nostr["names"] as? JsonObject
            val named = names?.get(localPart) as? JsonPrimitive
            val legacy = nostr["pubkey"] as? JsonPrimitive
            if (named?.isString == true) {
                bound = named.content
            } else if (localPart == "_" && names?.containsKey("_") != true && legacy?.isString == true) {
                bound = legacy.content
            }
            if (localPart == "_" && names?.containsKey("_") == true && nostr.containsKey("pubkey") && names["_"] != nostr["pubkey"]) {
                return BaseNip05Status.UNVERIFIED
            }
        }
        if (bound != subjectPubkey || latestOld == null) return BaseNip05Status.UNVERIFIED
        return if (eventMatches(latestOld, subjectPubkey, context, localPart)) BaseNip05Status.VERIFIED else BaseNip05Status.UNVERIFIED
    }

    private fun successorConfirmation(
        context: NamecoinNameContext,
        nostr: JsonObject,
        successor: String?,
        invalidSuccessor: Boolean,
        validNewEvents: List<Event>,
        previousConfirmation: Boolean,
    ): Triple<SuccessorConfirmation, List<String>, List<String>> {
        if (successor == null) {
            return Triple(if (invalidSuccessor) SuccessorConfirmation.INVALID else SuccessorConfirmation.ABSENT, emptyList(), emptyList())
        }
        val names = nostr["names"] as? JsonObject
        val aliases =
            names
                ?.entries
                ?.filter { (_, value) -> (value as? JsonPrimitive)?.takeIf { it.isString }?.content == successor }
                ?.map { it.key }
                ?.sorted()
                .orEmpty()
        if (context.namespace == LifecycleNameNamespace.ID) {
            return Triple(SuccessorConfirmation.UNCONFIRMED, emptyList(), listOf("successor_alias_requires_d_name"))
        }
        if (aliases.isEmpty()) {
            return Triple(SuccessorConfirmation.UNCONFIRMED, emptyList(), listOf("successor_alias_missing"))
        }
        val latest = latestEvent(validNewEvents, successor) ?: return Triple(SuccessorConfirmation.UNCONFIRMED, emptyList(), emptyList())
        val confirmed = aliases.filter { eventMatches(latest, successor, context, it) }
        if (confirmed.isNotEmpty()) {
            return Triple(
                SuccessorConfirmation.RECIPROCAL_ALIAS_CONFIRMED,
                confirmed,
                listOf("reciprocity_is_not_consent"),
            )
        }
        val hadMatch =
            previousConfirmation ||
                validNewEvents.any { event -> aliases.any { alias -> eventMatches(event, successor, context, alias) } }
        return if (hadMatch) {
            Triple(SuccessorConfirmation.WITHDRAWN, emptyList(), listOf("reciprocal_withdrawn"))
        } else {
            Triple(SuccessorConfirmation.UNCONFIRMED, emptyList(), listOf("new_wrong_localpart"))
        }
    }

    private fun hasNonExclusivePredecessor(
        nostr: JsonObject,
        successor: String?,
    ): Boolean {
        if (successor == null) return false
        val lifecycle = nostr["lifecycle"] as? JsonObject ?: return false
        val names = nostr["names"] as? JsonObject ?: return false
        val predecessors =
            lifecycle.entries
                .mapNotNull { (localPart, element) ->
                    val entry = element as? JsonObject ?: return@mapNotNull null
                    val state = (entry["state"] as? JsonPrimitive)?.contentOrNull
                    val declared = (entry["successor"] as? JsonPrimitive)?.contentOrNull
                    val bound = (names[localPart] as? JsonPrimitive)?.contentOrNull
                    if (state in setOf("retired", "compromised") &&
                        declared == successor &&
                        bound != null &&
                        LOWERCASE_PUBKEY.matches(bound) &&
                        bound != successor
                    ) {
                        bound
                    } else {
                        null
                    }
                }.toSet()
        return predecessors.size > 1
    }

    private fun eventMatches(
        event: Event,
        pubkey: String,
        context: NamecoinNameContext,
        localPart: String,
    ): Boolean {
        if (event.pubKey != pubkey || event.kind != MetadataEvent.KIND) return false
        val expectedIdentifier = NamecoinNameContexts.identifier(context, localPart) ?: return false
        val claimedIdentifier = eventNip05(event) ?: return false
        val claimed =
            try {
                NamecoinNameContexts.parse(claimedIdentifier)
            } catch (_: Exception) {
                return false
            }
        return NamecoinNameContexts.sameContext(claimed, context) &&
            claimed.localPart == localPart &&
            NamecoinNameContexts.identifier(context, localPart) == expectedIdentifier
    }

    private fun eventNip05(event: Event): String? =
        try {
            val content = Json.parseToJsonElement(event.content) as? JsonObject
            (content?.get("nip05") as? JsonPrimitive)?.takeIf { it.isString }?.content
        } catch (_: Exception) {
            null
        }

    private fun Event.isValidKind0(): Boolean {
        if (kind != MetadataEvent.KIND) return false
        return try {
            generateId() == id && verifySignature()
        } catch (_: Exception) {
            false
        }
    }

    private fun latestEvent(
        events: List<Event>,
        pubkey: String,
    ): Event? = events.filter { it.pubKey == pubkey }.maxWithOrNull(compareBy({ it.createdAt }, { it.id }))

    private fun finishObservation(
        result: AccountLifecycleResult,
        observation: LifecycleObservation,
    ): AccountLifecycleResult =
        when (observation) {
            LifecycleObservation.PROVISIONAL ->
                if (result.status == AccountLifecycleStatus.CURRENT) {
                    result.copy(
                        status = AccountLifecycleStatus.PROVISIONAL,
                        diagnostics = (result.diagnostics + "provisional_chain_observation").distinct().sorted(),
                    )
                } else {
                    result
                }
            LifecycleObservation.REREGISTERED ->
                if (result.status == AccountLifecycleStatus.CURRENT) {
                    result.copy(diagnostics = (result.diagnostics + "fresh_registration").distinct().sorted())
                } else {
                    result
                }
            LifecycleObservation.CURRENT -> result
        }

    private fun invalid(
        diagnostic: String,
        baseNip05: BaseNip05Status = BaseNip05Status.UNVERIFIED,
        provenance: LifecycleProvenance? = null,
    ) = AccountLifecycleResult(
        status = AccountLifecycleStatus.INVALID,
        baseNip05 = baseNip05,
        diagnostics = listOf(diagnostic),
        provenance = provenance,
    )

    private fun unsupportedTraversal(diagnostic: String) =
        AccountLifecycleResult(
            status = AccountLifecycleStatus.UNSUPPORTED_TRAVERSAL,
            baseNip05 = BaseNip05Status.UNSUPPORTED,
            diagnostics = listOf(diagnostic),
        )

    private fun evaluation(
        context: NamecoinNameContext,
        subjectPubkey: String,
        rawRecord: String?,
        result: AccountLifecycleResult,
        backlinks: List<LifecycleBacklink> = emptyList(),
    ) = AccountLifecycleEvaluation(result, context, subjectPubkey, rawRecord, backlinks)
}
