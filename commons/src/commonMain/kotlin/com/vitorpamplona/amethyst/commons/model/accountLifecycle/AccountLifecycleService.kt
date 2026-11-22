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
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import okio.IOException

const val ACCOUNT_LIFECYCLE_LOOKUP_TIMEOUT_MS = 40_000L
const val ACCOUNT_LIFECYCLE_POSITIVE_TTL_SECONDS = 3_600L
const val ACCOUNT_LIFECYCLE_RETRY_TTL_SECONDS = 300L
const val NAMECOIN_TARGET_BLOCK_SECONDS = 600L
const val NAMECOIN_REGISTRATION_EXPIRY_BLOCKS = 36_000L

data class NamecoinRawRecord(
    val value: String,
    val provenance: LifecycleProvenance,
)

fun interface AccountLifecycleTransport {
    suspend fun lookup(canonicalName: String): NamecoinRawRecord?
}

class LifecycleLookupException(
    val diagnostic: String,
) : Exception(diagnostic)

data class AccountLifecycleRequest(
    val viewerNamespace: String,
    val identifier: String,
    val subjectPubkey: String,
    val subjectOnly: Boolean = false,
    val oldEvents: List<Event> = emptyList(),
    val successorEventIds: List<String> = emptyList(),
    val forceRefresh: Boolean = false,
)

fun interface LifecycleMetadataProvider {
    suspend fun eventsFor(pubkey: String): List<Event>
}

data class AccountLifecycleRequestKey(
    val viewerNamespace: String,
    val identifier: String,
    val subjectPubkey: String,
    val subjectOnly: Boolean,
)

data class AccountLifecycleRequestState(
    val loading: Boolean = true,
    val evaluation: AccountLifecycleEvaluation? = null,
)

data class AccountLifecycleSubjectState(
    val loading: Boolean = true,
    val evaluations: List<AccountLifecycleEvaluation> = emptyList(),
)

private data class RawLookup(
    val record: NamecoinRawRecord?,
    val diagnostic: String?,
    val observedAt: Long,
    val generation: Long,
)

private sealed interface RawLookupState {
    data object Loading : RawLookupState

    data class Ready(
        val lookup: RawLookup,
    ) : RawLookupState
}

private class RawEntry(
    val startedAt: Long,
    val generation: Long,
) {
    val deferred = CompletableDeferred<RawLookup>()
    val state = MutableStateFlow<RawLookupState>(RawLookupState.Loading)
}

private class ValidatedEntry {
    val state = MutableStateFlow(AccountLifecycleRequestState())
    var latestRequest = 0L
    var fingerprint: ValidationFingerprint? = null
    var inFlight: ValidatedInFlight? = null
    var lastUsed = 0L
}

private data class ValidatedInFlight(
    val initialFingerprint: String,
    val jobId: Long,
    val deferred: Deferred<AccountLifecycleEvaluation>,
)

private data class ValidationFingerprint(
    val rawValue: String,
    val txid: String?,
    val oldEventIds: List<String>,
    val newEventIds: List<String>,
)

private data class EvaluationOutcome(
    val evaluation: AccountLifecycleEvaluation,
    val selectedRecord: String?,
    val observedAt: Long,
    val oldEventIds: List<String>,
    val newEventIds: List<String>,
    val persist: Boolean,
)

private class SubjectEntry {
    val state = MutableStateFlow(AccountLifecycleSubjectState())
    var latestRequest = 0L
    var inFlight: SubjectInFlight? = null
    var lastUsed = 0L
}

internal data class AccountLifecycleCacheStats(
    val raw: Int,
    val validated: Int,
    val subjects: Int,
)

private data class SubjectInFlight(
    val fingerprint: String,
    val requestId: Long,
    val deferred: Deferred<List<AccountLifecycleEvaluation>>,
)

/**
 * Coordinates Namecoin reads independently of visible note rows. A canonical name has one shared
 * in-flight lookup, while validated associations retain keyed StateFlows. Callers may cancel
 * without cancelling shared network work. New subject and association requests supersede older
 * ones, which cannot overwrite their state or persisted result.
 */
class AccountLifecycleService(
    private val transport: AccountLifecycleTransport,
    private val store: LifecycleAssociationStore,
    private val scope: CoroutineScope,
    private val now: () -> Long = { TimeUtils.now() },
    private val lookupTimeoutMs: Long = ACCOUNT_LIFECYCLE_LOOKUP_TIMEOUT_MS,
    private val positiveTtlSeconds: Long = ACCOUNT_LIFECYCLE_POSITIVE_TTL_SECONDS,
    private val retryTtlSeconds: Long = ACCOUNT_LIFECYCLE_RETRY_TTL_SECONDS,
) {
    private val rawMutex = Mutex()
    private val rawEntries = mutableMapOf<String, RawEntry>()
    private val lookupPermits = Semaphore(MAX_CONCURRENT_LOOKUPS)
    private var generation = 0L

    private val mutableInvalidationVersion = MutableStateFlow(0L)
    val invalidationVersion: StateFlow<Long> = mutableInvalidationVersion

    private val validationMutex = Mutex()
    private val validatedEntries = mutableMapOf<AccountLifecycleRequestKey, ValidatedEntry>()
    private var validationSequence = 0L
    private var validatedUsageSequence = 0L

    private val subjectMutex = Mutex()
    private val subjectEntries = mutableMapOf<Pair<String, String>, SubjectEntry>()
    private var subjectUsageSequence = 0L

    suspend fun stateFor(request: AccountLifecycleRequest): StateFlow<AccountLifecycleRequestState> {
        val key = requestKey(request)
        return validationMutex.withLock {
            if (key !in validatedEntries) pruneValidatedEntriesLocked()
            validatedEntries.getOrPut(key) { ValidatedEntry() }.also { it.lastUsed = ++validatedUsageSequence }.state
        }
    }

    suspend fun subjectState(
        viewerNamespace: String,
        subjectPubkey: String,
    ): StateFlow<AccountLifecycleSubjectState> =
        subjectMutex.withLock {
            val key = viewerNamespace to subjectPubkey
            if (key !in subjectEntries) pruneSubjectEntriesLocked()
            subjectEntries.getOrPut(key) { SubjectEntry() }.also { it.lastUsed = ++subjectUsageSequence }.state
        }

    internal suspend fun cacheStatsForTest(): AccountLifecycleCacheStats =
        AccountLifecycleCacheStats(
            raw = rawMutex.withLock { rawEntries.size },
            validated = validationMutex.withLock { validatedEntries.size },
            subjects = subjectMutex.withLock { subjectEntries.size },
        )

    suspend fun resolve(
        request: AccountLifecycleRequest,
        metadataProvider: LifecycleMetadataProvider,
    ): AccountLifecycleEvaluation {
        val context = NamecoinNameContexts.parse(request.identifier, request.subjectOnly)
        val canonicalRequest = request.copy(identifier = NamecoinNameContexts.canonicalIdentifier(context, request.subjectOnly))
        val key = requestKey(canonicalRequest)
        val initialFingerprint = metadataFingerprint(canonicalRequest.oldEvents.map { it.id }, canonicalRequest.successorEventIds)
        val inFlight =
            validationMutex.withLock {
                if (key !in validatedEntries) pruneValidatedEntriesLocked()
                val entry = validatedEntries.getOrPut(key) { ValidatedEntry() }
                entry.lastUsed = ++validatedUsageSequence
                val existing = entry.inFlight
                if (!canonicalRequest.forceRefresh && existing != null && existing.initialFingerprint == initialFingerprint) {
                    existing
                } else {
                    existing?.deferred?.cancel()
                    val jobId = ++validationSequence
                    val deferred =
                        scope.async {
                            try {
                                performResolve(canonicalRequest, key, metadataProvider)
                            } finally {
                                validationMutex.withLock {
                                    if (entry.inFlight?.jobId == jobId) entry.inFlight = null
                                }
                            }
                        }
                    ValidatedInFlight(initialFingerprint, jobId, deferred).also { entry.inFlight = it }
                }
            }
        return inFlight.deferred.await()
    }

    private suspend fun performResolve(
        canonicalRequest: AccountLifecycleRequest,
        key: AccountLifecycleRequestKey,
        metadataProvider: LifecycleMetadataProvider,
    ): AccountLifecycleEvaluation {
        val context = NamecoinNameContexts.parse(canonicalRequest.identifier, canonicalRequest.subjectOnly)
        val (entry, requestId) =
            validationMutex.withLock {
                val validated = validatedEntries.getOrPut(key) { ValidatedEntry() }
                val id = ++validationSequence
                validated.latestRequest = id
                validated.state.value = validated.state.value.copy(loading = true)
                validated to id
            }

        try {
            val outcome = evaluate(canonicalRequest, context, key, entry, requestId, metadataProvider)
            val fingerprint =
                ValidationFingerprint(
                    rawValue = outcome.selectedRecord.orEmpty(),
                    txid =
                        outcome.evaluation.result.provenance
                            ?.txid,
                    oldEventIds = outcome.oldEventIds,
                    newEventIds = outcome.newEventIds,
                )
            validationMutex.withLock {
                if (entry.latestRequest == requestId) {
                    val previous = entry.state.value.evaluation
                    if (!canonicalRequest.forceRefresh && entry.fingerprint == fingerprint && previous == outcome.evaluation) {
                        entry.state.value = AccountLifecycleRequestState(loading = false, evaluation = previous)
                    } else {
                        if (outcome.persist) save(canonicalRequest, outcome)
                        entry.fingerprint = fingerprint
                        entry.state.value = AccountLifecycleRequestState(loading = false, evaluation = outcome.evaluation)
                    }
                }
            }
            return outcome.evaluation
        } finally {
            validationMutex.withLock {
                if (entry.latestRequest == requestId && entry.state.value.loading) {
                    entry.state.value = entry.state.value.copy(loading = false)
                }
            }
        }
    }

    /**
     * Rechecks the current kind-0 identifier and every previously verified association for OLD.
     * An ordinary DNS identifier is left to the existing NIP-05 path. Empty results mean that no
     * current or retained Namecoin association is known, not that the account is active or safe.
     */
    suspend fun resolveSubject(
        viewerNamespace: String,
        subjectPubkey: String,
        currentIdentifier: String?,
        oldEvents: List<Event>,
        metadataProvider: LifecycleMetadataProvider,
        successorEventIds: Map<String, String> = emptyMap(),
        forceRefresh: Boolean = false,
    ): List<AccountLifecycleEvaluation> {
        val successorIds = successorEventIds.values.sorted()
        val current = canonicalCurrentIdentifier(currentIdentifier)
        val retained = retainedAssociations(viewerNamespace, subjectPubkey)
        val requests =
            buildList {
                if (current != null) {
                    add(
                        AccountLifecycleRequest(
                            viewerNamespace = viewerNamespace,
                            identifier = current,
                            subjectPubkey = subjectPubkey,
                            oldEvents = oldEvents,
                            successorEventIds = successorIds,
                            forceRefresh = forceRefresh,
                        ),
                    )
                }
                retained.forEach { association ->
                    val identifier = canonicalCurrentIdentifier(association.identifier)
                    if (identifier != null && none { it.identifier == identifier && it.subjectOnly == association.subjectOnly }) {
                        add(
                            AccountLifecycleRequest(
                                viewerNamespace = viewerNamespace,
                                identifier = identifier,
                                subjectPubkey = subjectPubkey,
                                subjectOnly = association.subjectOnly,
                                oldEvents = oldEvents,
                                successorEventIds = successorIds,
                                forceRefresh = forceRefresh,
                            ),
                        )
                    }
                }
            }
        val restoredWarnings =
            requests.mapNotNull { request ->
                val cached = load(request)
                if (cached.selectedRecord == null || !cached.result.isAccountWarning) {
                    null
                } else {
                    AccountLifecycleEvaluation(
                        result = AccountLifecycleParser.stale(cached.result, "retained_association"),
                        context = NamecoinNameContexts.parse(request.identifier, request.subjectOnly),
                        subjectPubkey = request.subjectPubkey,
                        selectedRecord = cached.selectedRecord,
                        backlinks = cached.backlinks,
                    )
                }
            }
        val fingerprint =
            requests.joinToString(separator = "|") { "${it.identifier}:${it.subjectOnly}" } +
                ":" + metadataFingerprint(oldEvents.map { it.id }, successorIds)
        val subjectKey = viewerNamespace to subjectPubkey
        val inFlight =
            subjectMutex.withLock {
                if (subjectKey !in subjectEntries) pruneSubjectEntriesLocked()
                val entry = subjectEntries.getOrPut(subjectKey) { SubjectEntry() }
                entry.lastUsed = ++subjectUsageSequence
                val existing = entry.inFlight
                if (!forceRefresh && existing != null && existing.fingerprint == fingerprint) {
                    existing
                } else {
                    existing?.deferred?.cancel()
                    val requestId = ++entry.latestRequest
                    entry.state.value =
                        entry.state.value.copy(
                            loading = true,
                            evaluations =
                                entry.state.value.evaluations
                                    .ifEmpty { restoredWarnings },
                        )
                    val deferred =
                        scope.async {
                            try {
                                val evaluations =
                                    resolveWithConfirmedPredecessors(
                                        requests = requests,
                                        viewerNamespace = viewerNamespace,
                                        subjectPubkey = subjectPubkey,
                                        metadataProvider = metadataProvider,
                                        forceRefresh = forceRefresh,
                                    )
                                subjectMutex.withLock {
                                    if (entry.latestRequest == requestId) {
                                        entry.state.value = AccountLifecycleSubjectState(loading = false, evaluations = evaluations)
                                    }
                                }
                                evaluations
                            } finally {
                                subjectMutex.withLock {
                                    if (entry.latestRequest == requestId) {
                                        entry.inFlight = null
                                        if (entry.state.value.loading) entry.state.value = entry.state.value.copy(loading = false)
                                    }
                                }
                            }
                        }
                    SubjectInFlight(fingerprint, requestId, deferred).also { entry.inFlight = it }
                }
            }
        return inFlight.deferred.await()
    }

    /** One reviewed reverse hop. The OLD evaluation is returned as link evidence, never as NEW's warning. */
    private suspend fun resolveWithConfirmedPredecessors(
        requests: List<AccountLifecycleRequest>,
        viewerNamespace: String,
        subjectPubkey: String,
        metadataProvider: LifecycleMetadataProvider,
        forceRefresh: Boolean,
    ): List<AccountLifecycleEvaluation> {
        val evaluations = requests.map { resolve(it, metadataProvider) }.toMutableList()
        val successorEventIds = requests.flatMap { it.successorEventIds }.distinct().sorted()
        val predecessorRequests =
            evaluations
                .filter { it.subjectPubkey == subjectPubkey }
                .flatMap { own ->
                    val rawRecord = own.selectedRecord ?: return@flatMap emptyList()
                    val identifier = NamecoinNameContexts.canonicalIdentifier(own.context, subjectOnly = false)
                    AccountLifecycleParser
                        .predecessorSubjects(identifier, subjectPubkey, rawRecord)
                        .map { oldPubkey ->
                            AccountLifecycleRequest(
                                viewerNamespace = viewerNamespace,
                                identifier = NamecoinNameContexts.canonicalIdentifier(own.context, subjectOnly = true),
                                subjectPubkey = oldPubkey,
                                subjectOnly = true,
                                successorEventIds = successorEventIds,
                                forceRefresh = forceRefresh,
                            )
                        }
                }.distinctBy { it.identifier to it.subjectPubkey }

        predecessorRequests.forEach { request ->
            val predecessor = resolve(request, metadataProvider)
            if (predecessor.result.isAccountWarning &&
                predecessor.result.successorPubkey == subjectPubkey &&
                predecessor.result.successorConfirmation == SuccessorConfirmation.RECIPROCAL_ALIAS_CONFIRMED
            ) {
                evaluations += predecessor
            }
        }
        return evaluations
    }

    fun invalidate() {
        scope.launch { invalidateAndWait() }
    }

    suspend fun invalidateAndWait() {
        rawMutex.withLock {
            generation++
            rawEntries.clear()
        }
        validationMutex.withLock {
            validatedEntries.values.forEach { entry ->
                entry.fingerprint = null
                val current = entry.state.value
                entry.state.value =
                    current.copy(
                        loading = current.evaluation != null && entry.state.subscriptionCount.value > 0,
                        evaluation = current.evaluation?.let(::staleWhileRechecking),
                    )
            }
        }
        subjectMutex.withLock {
            subjectEntries.values.forEach { entry ->
                val current = entry.state.value
                entry.state.value =
                    current.copy(
                        loading = current.evaluations.isNotEmpty() && entry.state.subscriptionCount.value > 0,
                        evaluations = current.evaluations.map(::staleWhileRechecking),
                    )
            }
        }
        mutableInvalidationVersion.value++
    }

    private fun staleWhileRechecking(evaluation: AccountLifecycleEvaluation): AccountLifecycleEvaluation =
        if (evaluation.result.isAccountWarning && evaluation.result.status != AccountLifecycleStatus.STALE) {
            evaluation.copy(result = AccountLifecycleParser.stale(evaluation.result, "invalidated"))
        } else {
            evaluation
        }

    private suspend fun evaluate(
        request: AccountLifecycleRequest,
        context: NamecoinNameContext,
        key: AccountLifecycleRequestKey,
        entry: ValidatedEntry,
        requestId: Long,
        metadataProvider: LifecycleMetadataProvider,
    ): EvaluationOutcome {
        val cached = load(request)
        var lookup = raw(context.name, request.forceRefresh)
        while (lookup.generation != currentGeneration()) {
            ensureLatest(key, entry, requestId)
            lookup = raw(context.name, false)
        }
        ensureLatest(key, entry, requestId)

        val (currentOldEvents, oldMetadataAvailable) = metadataEvents(metadataProvider, request.subjectPubkey)
        val discoveryOldEvents = (request.oldEvents + currentOldEvents).distinctBy { it.id }
        val discoveryBacklinks = verifiedAssociations(request, discoveryOldEvents)
        val record = lookup.record
        if (record == null || lookup.diagnostic != null) {
            return unavailableOutcome(request, context, cached, lookup.diagnostic ?: "name_not_found", discoveryBacklinks)
        }
        if (record.provenance.expiresIn
                ?.let { it <= 0 } == true
        ) {
            return unavailableOutcome(request, context, cached, "name_expired", discoveryBacklinks)
        }

        val oldEvents = (request.oldEvents + cached.backlinks.map { it.event.toEvent() } + currentOldEvents).distinctBy { it.id }
        val verifiedAssociations = verifiedAssociations(request, oldEvents)
        val observation =
            when {
                record.provenance.height == 0 -> LifecycleObservation.PROVISIONAL
                requiresFreshAuthority(cached, record) -> LifecycleObservation.REREGISTERED
                else -> LifecycleObservation.CURRENT
            }
        val previousConfirmation =
            observation != LifecycleObservation.REREGISTERED &&
                cached.result.successorConfirmation == SuccessorConfirmation.RECIPROCAL_ALIAS_CONFIRMED
        val first =
            AccountLifecycleParser.evaluateInlineRoot(
                identifier = request.identifier,
                subjectPubkey = request.subjectPubkey,
                subjectOnly = request.subjectOnly,
                rawRecord = record.value,
                oldEvents = oldEvents,
                previousConfirmation = previousConfirmation,
                observation = observation,
                provenance = lookup.record.provenance,
            )
        val successor = first.result.successorPubkey
        val (successorEvents, newMetadataAvailable) =
            if (successor == null) {
                emptyList<Event>() to true
            } else {
                metadataEvents(metadataProvider, successor)
            }
        var evaluated =
            if (successorEvents.isEmpty()) {
                first
            } else {
                AccountLifecycleParser.evaluateInlineRoot(
                    identifier = request.identifier,
                    subjectPubkey = request.subjectPubkey,
                    subjectOnly = request.subjectOnly,
                    rawRecord = record.value,
                    oldEvents = oldEvents,
                    newEvents = successorEvents,
                    previousConfirmation = previousConfirmation,
                    observation = observation,
                    provenance = lookup.record.provenance,
                )
            }

        if (!newMetadataAvailable && successor != null) {
            evaluated =
                evaluated.copy(
                    result =
                        evaluated.result.copy(
                            successorConfirmation = if (previousConfirmation) SuccessorConfirmation.STALE else SuccessorConfirmation.UNCONFIRMED,
                            confirmationAliases = emptyList(),
                            diagnostics = (evaluated.result.diagnostics + "new_metadata_unavailable").distinct().sorted(),
                        ),
                )
        }
        if (!oldMetadataAvailable && currentOldEvents.isEmpty() && cached.result.isCompromiseWarning) {
            return staleOutcome(request, context, cached, "old_metadata_unavailable", verifiedAssociations)
        }
        if (observation == LifecycleObservation.REREGISTERED && !evaluated.result.isAccountWarning && cached.result.isAccountWarning) {
            return staleOutcome(request, context, cached, "reregistered_without_fresh_authority", verifiedAssociations)
        }
        if (shouldRetainStaleWarning(evaluated.result, cached.result)) {
            return staleOutcome(request, context, cached, "current_record_not_evaluable", verifiedAssociations)
        }

        val completed = evaluated.copy(backlinks = (evaluated.backlinks + verifiedAssociations).distinctBy { it.event.id })
        return EvaluationOutcome(
            evaluation = completed,
            selectedRecord = record.value,
            observedAt = lookup.observedAt,
            oldEventIds = oldEvents.map { it.id }.sorted(),
            newEventIds = successorEvents.map { it.id }.sorted(),
            persist = completed.backlinks.isNotEmpty() || cached.selectedRecord != null,
        )
    }

    private fun verifiedAssociations(
        request: AccountLifecycleRequest,
        oldEvents: List<Event>,
    ): List<LifecycleBacklink> =
        try {
            AccountLifecycleParser.verifiedBacklinks(
                identifier = request.identifier,
                subjectPubkey = request.subjectPubkey,
                oldEvents = oldEvents,
                subjectOnly = request.subjectOnly,
            )
        } catch (_: IllegalArgumentException) {
            emptyList()
        }

    private fun requiresFreshAuthority(
        cached: LifecycleAssociationRecord,
        current: NamecoinRawRecord,
    ): Boolean {
        if (cached.selectedRecord == null) return false
        if (cached.result.diagnostics.any { it == "stale_after_name_expired" || it == "stale_after_name_not_found" }) return true

        val previous = cached.result.provenance ?: return false
        if (previous.txid != null && current.provenance.txid != null && previous.txid == current.provenance.txid) return false
        val previousHeight = previous.height ?: return false
        val currentHeight = current.provenance.height ?: return false
        return currentHeight.toLong() > previousHeight.toLong() + NAMECOIN_REGISTRATION_EXPIRY_BLOCKS
    }

    private fun metadataFingerprint(
        oldEventIds: List<String>,
        successorEventIds: List<String>,
    ): String =
        oldEventIds.distinct().sorted().joinToString(",", prefix = "old=") +
            successorEventIds.distinct().sorted().joinToString(",", prefix = ";successor=")

    private fun cacheTtlSeconds(lookup: RawLookup): Long {
        val recordTtl =
            lookup.record
                ?.provenance
                ?.expiresIn
                ?.let { blocks ->
                    minOf(positiveTtlSeconds, blocks.toLong() * NAMECOIN_TARGET_BLOCK_SECONDS).coerceAtLeast(0)
                } ?: positiveTtlSeconds
        return if (lookup.record != null && lookup.diagnostic == null) recordTtl else retryTtlSeconds
    }

    private suspend fun raw(
        canonicalName: String,
        forceRefresh: Boolean,
    ): RawLookup {
        while (true) {
            var capacityWait: Deferred<RawLookup>? = null
            val entry =
                rawMutex.withLock {
                    val existing = rawEntries[canonicalName]
                    val ready = (existing?.state?.value as? RawLookupState.Ready)?.lookup
                    val fresh =
                        ready != null &&
                            now() - ready.observedAt < cacheTtlSeconds(ready)
                    if (existing != null && !forceRefresh && (ready == null || fresh)) {
                        existing
                    } else {
                        pruneCompletedEntries()
                        if (existing == null && rawEntries.size >= MAX_RAW_NAMES) {
                            capacityWait =
                                rawEntries.entries
                                    .minByOrNull { it.value.startedAt }
                                    ?.value
                                    ?.deferred
                            null
                        } else {
                            RawEntry(now(), generation).also { newEntry ->
                                rawEntries[canonicalName] = newEntry
                                startLookup(canonicalName, newEntry)
                            }
                        }
                    }
                }
            if (entry != null) return entry.deferred.await()

            val pending = capacityWait ?: continue
            try {
                pending.await()
            } catch (error: CancellationException) {
                if (!currentCoroutineContext().isActive) throw error
            }
        }
    }

    private fun startLookup(
        canonicalName: String,
        entry: RawEntry,
    ) {
        scope.launch {
            val lookup =
                try {
                    lookupPermits.withPermit {
                        try {
                            withTimeout(lookupTimeoutMs) {
                                val record = transport.lookup(canonicalName)
                                RawLookup(record, if (record == null) "name_not_found" else null, now(), entry.generation)
                            }
                        } catch (error: TimeoutCancellationException) {
                            if (!currentCoroutineContext().isActive) throw error
                            RawLookup(null, "lookup_timeout", now(), entry.generation)
                        }
                    }
                } catch (error: LifecycleLookupException) {
                    RawLookup(null, error.diagnostic, now(), entry.generation)
                } catch (error: CancellationException) {
                    entry.deferred.completeExceptionally(error)
                    throw error
                } catch (_: Exception) {
                    RawLookup(null, "lookup_unavailable", now(), entry.generation)
                }
            rawMutex.withLock {
                if (rawEntries[canonicalName] === entry) entry.state.value = RawLookupState.Ready(lookup)
            }
            entry.deferred.complete(lookup)
            val ttlSeconds = cacheTtlSeconds(lookup)
            if (ttlSeconds > 0) {
                scope.launch {
                    delay(ttlSeconds * 1_000)
                    val expired = rawMutex.withLock { rawEntries[canonicalName] === entry }
                    if (expired) invalidateAndWait()
                }
            }
        }
    }

    private fun pruneCompletedEntries() {
        while (rawEntries.size >= MAX_RAW_NAMES) {
            val oldest =
                rawEntries.entries
                    .filter { it.value.state.value is RawLookupState.Ready }
                    .minByOrNull { it.value.startedAt }
                    ?.key
                    ?: return
            rawEntries.remove(oldest)
        }
    }

    private suspend fun pruneValidatedEntriesLocked() {
        while (validatedEntries.size >= MAX_VALIDATED_REQUESTS) {
            val idle =
                validatedEntries.entries
                    .filter { it.value.inFlight == null && it.value.state.subscriptionCount.value == 0 }
                    .sortedBy { it.value.lastUsed }
            val candidate =
                idle.firstOrNull { canEvictValidatedEntry(it.key, it.value) }
                    // A full warning-only store must not pin unpersisted warnings in memory. Prefer
                    // ordinary/durable entries, then evict the oldest volatile warning if necessary.
                    ?: idle.firstOrNull()
                    ?: return
            validatedEntries.remove(candidate.key)
        }
    }

    private suspend fun canEvictValidatedEntry(
        key: AccountLifecycleRequestKey,
        entry: ValidatedEntry,
    ): Boolean {
        val evaluation = entry.state.value.evaluation ?: return true
        if (!evaluation.result.isAccountWarning) return true
        val persisted =
            load(
                AccountLifecycleRequest(
                    viewerNamespace = key.viewerNamespace,
                    identifier = key.identifier,
                    subjectPubkey = key.subjectPubkey,
                    subjectOnly = key.subjectOnly,
                ),
            )
        return persisted.selectedRecord == evaluation.selectedRecord && persisted.result.isAccountWarning
    }

    private suspend fun pruneSubjectEntriesLocked() {
        while (subjectEntries.size >= MAX_SUBJECTS) {
            val idle =
                subjectEntries.entries
                    .filter { it.value.inFlight == null && it.value.state.subscriptionCount.value == 0 }
                    .sortedBy { it.value.lastUsed }
            val candidate =
                idle.firstOrNull { canEvictSubjectEntry(it.key, it.value) }
                    ?: idle.firstOrNull()
                    ?: return
            subjectEntries.remove(candidate.key)
        }
    }

    private suspend fun canEvictSubjectEntry(
        key: Pair<String, String>,
        entry: SubjectEntry,
    ): Boolean {
        val warnings =
            entry.state.value.evaluations
                .filter { it.result.isAccountWarning }
        if (warnings.isEmpty()) return true
        return warnings.all { warning ->
            val subjectOnly = warning.context.localPart == null
            val persisted =
                load(
                    AccountLifecycleRequest(
                        viewerNamespace = key.first,
                        identifier = NamecoinNameContexts.canonicalIdentifier(warning.context, subjectOnly),
                        subjectPubkey = warning.subjectPubkey,
                        subjectOnly = subjectOnly,
                    ),
                )
            // A predecessor warning belongs to OLD, not the viewed NEW subject.
            persisted.selectedRecord == warning.selectedRecord && persisted.result.isAccountWarning
        }
    }

    private suspend fun currentGeneration(): Long = rawMutex.withLock { generation }

    private suspend fun ensureLatest(
        key: AccountLifecycleRequestKey,
        entry: ValidatedEntry,
        requestId: Long,
    ) = validationMutex.withLock {
        if (validatedEntries[key] !== entry || entry.latestRequest != requestId) {
            throw SupersededLifecycleRequestException()
        }
    }

    private suspend fun load(request: AccountLifecycleRequest): LifecycleAssociationRecord =
        try {
            store.load(request.viewerNamespace, request.identifier, request.subjectPubkey, request.subjectOnly) ?: emptyAssociation(request)
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            emptyAssociation(request)
        }

    private fun emptyAssociation(request: AccountLifecycleRequest) =
        LifecycleAssociationRecord(
            viewerNamespace = request.viewerNamespace,
            identifier = request.identifier,
            subjectPubkey = request.subjectPubkey,
            subjectOnly = request.subjectOnly,
            result = AccountLifecycleParser.unavailable("not_cached"),
            backlinks = emptyList(),
            observedAt = 0,
        )

    private fun unavailableOutcome(
        request: AccountLifecycleRequest,
        context: NamecoinNameContext,
        cached: LifecycleAssociationRecord,
        diagnostic: String,
        discoveryBacklinks: List<LifecycleBacklink>,
    ): EvaluationOutcome {
        if (cached.selectedRecord != null && cached.result.isAccountWarning) {
            return staleOutcome(request, context, cached, diagnostic, discoveryBacklinks)
        }
        val backlinks = (cached.backlinks + discoveryBacklinks).distinctBy { it.event.id }
        return EvaluationOutcome(
            evaluation =
                AccountLifecycleEvaluation(
                    result = AccountLifecycleParser.unavailable(diagnostic),
                    context = context,
                    subjectPubkey = request.subjectPubkey,
                    selectedRecord = null,
                    backlinks = backlinks,
                ),
            selectedRecord = null,
            observedAt = cached.observedAt,
            oldEventIds = backlinks.map { it.event.id }.sorted(),
            newEventIds = emptyList(),
            persist = backlinks.isNotEmpty(),
        )
    }

    private fun staleOutcome(
        request: AccountLifecycleRequest,
        context: NamecoinNameContext,
        cached: LifecycleAssociationRecord,
        diagnostic: String,
        additionalBacklinks: List<LifecycleBacklink> = emptyList(),
    ): EvaluationOutcome {
        val backlinks = (cached.backlinks + additionalBacklinks).distinctBy { it.event.id }
        return EvaluationOutcome(
            evaluation =
                AccountLifecycleEvaluation(
                    result = AccountLifecycleParser.stale(cached.result, diagnostic),
                    context = context,
                    subjectPubkey = request.subjectPubkey,
                    selectedRecord = cached.selectedRecord,
                    backlinks = backlinks,
                ),
            selectedRecord = cached.selectedRecord,
            observedAt = cached.observedAt,
            oldEventIds = backlinks.map { it.event.id }.sorted(),
            newEventIds = emptyList(),
            persist = cached.selectedRecord != null || backlinks.isNotEmpty(),
        )
    }

    private fun shouldRetainStaleWarning(
        current: AccountLifecycleResult,
        cached: AccountLifecycleResult,
    ): Boolean =
        cached.isCompromiseWarning &&
            current.status in
            setOf(
                AccountLifecycleStatus.NO_DECLARATION,
                AccountLifecycleStatus.INVALID,
                AccountLifecycleStatus.UNSUPPORTED_STATE,
                AccountLifecycleStatus.UNSUPPORTED_TRAVERSAL,
                AccountLifecycleStatus.UNAVAILABLE,
            )

    private suspend fun save(
        request: AccountLifecycleRequest,
        outcome: EvaluationOutcome,
    ) {
        try {
            store.save(
                LifecycleAssociationRecord(
                    viewerNamespace = request.viewerNamespace,
                    identifier = request.identifier,
                    subjectPubkey = request.subjectPubkey,
                    subjectOnly = request.subjectOnly,
                    selectedRecord = outcome.selectedRecord,
                    result = outcome.evaluation.result,
                    backlinks = outcome.evaluation.backlinks,
                    observedAt = outcome.observedAt,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            // The fresh evaluation remains useful if persistence is temporarily unavailable.
        }
    }

    private suspend fun metadataEvents(
        provider: LifecycleMetadataProvider,
        pubkey: String,
    ): Pair<List<Event>, Boolean> =
        try {
            provider.eventsFor(pubkey) to true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList<Event>() to false
        }

    private suspend fun retainedAssociations(
        viewerNamespace: String,
        subjectPubkey: String,
    ): List<LifecycleAssociationRecord> =
        try {
            store.forSubject(viewerNamespace, subjectPubkey)
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            emptyList()
        }

    private fun canonicalCurrentIdentifier(identifier: String?): String? =
        identifier?.let {
            try {
                val context = NamecoinNameContexts.parse(it)
                NamecoinNameContexts.canonicalIdentifier(context, subjectOnly = false)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

    private fun requestKey(request: AccountLifecycleRequest): AccountLifecycleRequestKey {
        val context = NamecoinNameContexts.parse(request.identifier, request.subjectOnly)
        return AccountLifecycleRequestKey(
            viewerNamespace = request.viewerNamespace,
            identifier = NamecoinNameContexts.canonicalIdentifier(context, request.subjectOnly),
            subjectPubkey = request.subjectPubkey,
            subjectOnly = request.subjectOnly,
        )
    }

    companion object {
        private const val MAX_RAW_NAMES = 128
        private const val MAX_VALIDATED_REQUESTS = 1_024
        private const val MAX_SUBJECTS = 1_024
        private const val MAX_CONCURRENT_LOOKUPS = 4
    }
}

private class SupersededLifecycleRequestException : CancellationException("Superseded by a newer lifecycle request")
