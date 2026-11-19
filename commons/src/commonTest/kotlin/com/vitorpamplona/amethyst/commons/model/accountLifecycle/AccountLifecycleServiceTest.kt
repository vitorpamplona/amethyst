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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AccountLifecycleServiceTest {
    @Test
    fun manyRowsShareOneLookupAndOneValidation() =
        runTest {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var lookups = 0
            val store = InMemoryLifecycleAssociationStore()
            val service =
                AccountLifecycleService(
                    transport = {
                        lookups++
                        started.complete(Unit)
                        release.await()
                        AccountLifecycleTestFixtures.raw()
                    },
                    store = store,
                    scope = backgroundScope,
                )
            val request = request()
            val callers = List(8) { async { service.resolve(request, AccountLifecycleTestFixtures.metadata) } }
            runCurrent()
            started.await()
            assertEquals(1, lookups)

            callers.first().cancelAndJoin()
            release.complete(Unit)
            val evaluations = callers.drop(1).awaitAll()
            assertTrue(evaluations.all { it.result.isCompromiseWarning })
            assertEquals(SuccessorConfirmation.RECIPROCAL_ALIAS_CONFIRMED, evaluations.last().result.successorConfirmation)
            assertEquals(1, lookups)
            assertEquals(1, store.forSubject("viewer", AccountLifecycleTestFixtures.old).size)
        }

    @Test
    fun forcedRefreshWinsAndOlderTransportCannotOverwriteIt() =
        runTest {
            var lookups = 0
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val store = InMemoryLifecycleAssociationStore()
            val service =
                AccountLifecycleService(
                    transport = {
                        val call = ++lookups
                        if (call == 1) {
                            firstStarted.complete(Unit)
                            releaseFirst.await()
                            AccountLifecycleTestFixtures.raw(txid = "1".repeat(64))
                        } else {
                            AccountLifecycleTestFixtures.raw(value = AccountLifecycleTestFixtures.retired, txid = "2".repeat(64))
                        }
                    },
                    store = store,
                    scope = backgroundScope,
                )
            val state = service.stateFor(request())
            val oldRequest = async { service.resolve(request(), AccountLifecycleTestFixtures.metadata) }
            runCurrent()
            firstStarted.await()

            val newRequest = async { service.resolve(request(forceRefresh = true), AccountLifecycleTestFixtures.metadata) }
            runCurrent()
            assertEquals(2, lookups)
            assertEquals(AccountLifecycleState.RETIRED, newRequest.await().result.state)
            releaseFirst.complete(Unit)
            oldRequest.cancelAndJoin()
            runCurrent()

            assertEquals(
                AccountLifecycleState.RETIRED,
                state.value.evaluation
                    ?.result
                    ?.state,
            )
            assertEquals(AccountLifecycleTestFixtures.retired, store.load("viewer", "example.bit", AccountLifecycleTestFixtures.old, false)?.selectedRecord)
        }

    @Test
    fun transientFailureRetainsOriginalWarningRecordAndObservation() =
        runTest {
            var clock = 1_000L
            var fail = false
            val store = InMemoryLifecycleAssociationStore()
            val service =
                AccountLifecycleService(
                    transport = {
                        if (fail) throw LifecycleLookupException("server_timeout")
                        AccountLifecycleTestFixtures.raw()
                    },
                    store = store,
                    scope = backgroundScope,
                    now = { clock },
                )
            val current = service.resolve(request(), AccountLifecycleTestFixtures.metadata)
            assertEquals(AccountLifecycleStatus.CURRENT, current.result.status)
            val original = assertNotNull(store.load("viewer", "example.bit", AccountLifecycleTestFixtures.old, false))

            fail = true
            clock += 500
            val stale = service.resolve(request(forceRefresh = true), AccountLifecycleTestFixtures.metadata)
            assertEquals(AccountLifecycleStatus.STALE, stale.result.status)
            assertEquals(SuccessorConfirmation.STALE, stale.result.successorConfirmation)
            assertEquals(AccountLifecycleTestFixtures.compromised, stale.selectedRecord)
            val persisted = assertNotNull(store.load("viewer", "example.bit", AccountLifecycleTestFixtures.old, false))
            assertEquals(original.selectedRecord, persisted.selectedRecord)
            assertEquals(original.observedAt, persisted.observedAt)
            assertEquals(original.result.provenance?.txid, persisted.result.provenance?.txid)
            assertTrue(persisted.result.diagnostics.contains("stale_after_server_timeout"))
        }

    @Test
    fun retainedWarningIsExposedBeforeRestartRefreshReturns() =
        runTest {
            val store = InMemoryLifecycleAssociationStore()
            val initialService =
                AccountLifecycleService(
                    transport = { AccountLifecycleTestFixtures.raw() },
                    store = store,
                    scope = backgroundScope,
                )
            initialService.resolve(request(), AccountLifecycleTestFixtures.metadata)

            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val restartedService =
                AccountLifecycleService(
                    transport = {
                        started.complete(Unit)
                        release.await()
                        AccountLifecycleTestFixtures.raw()
                    },
                    store = store,
                    scope = backgroundScope,
                )
            val resolution =
                async {
                    restartedService.resolveSubject(
                        viewerNamespace = "viewer",
                        subjectPubkey = AccountLifecycleTestFixtures.old,
                        currentIdentifier = null,
                        oldEvents = emptyList(),
                        metadataProvider = AccountLifecycleTestFixtures.metadata,
                    )
                }
            runCurrent()
            started.await()

            val restored = restartedService.subjectState("viewer", AccountLifecycleTestFixtures.old).value
            assertTrue(restored.loading)
            val warning = restored.evaluations.single()
            assertEquals(AccountLifecycleStatus.STALE, warning.result.status)
            assertTrue(warning.result.isCompromiseWarning)
            assertEquals(SuccessorConfirmation.STALE, warning.result.successorConfirmation)
            assertEquals(AccountLifecycleTestFixtures.compromised, warning.selectedRecord)

            release.complete(Unit)
            assertEquals(
                AccountLifecycleStatus.CURRENT,
                resolution
                    .await()
                    .single()
                    .result.status,
            )
        }

    @Test
    fun negativeCacheIsNotStaleWarningEvidence() =
        runTest {
            var fail = false
            val noLifecycle = """{"nostr":{"names":{"_":"${AccountLifecycleTestFixtures.old}"}}}"""
            val service =
                AccountLifecycleService(
                    transport = {
                        if (fail) throw LifecycleLookupException("lookup_unavailable")
                        AccountLifecycleTestFixtures.raw(value = noLifecycle)
                    },
                    store = InMemoryLifecycleAssociationStore(),
                    scope = backgroundScope,
                )
            val negative = service.resolve(request(), AccountLifecycleTestFixtures.metadata)
            assertEquals(AccountLifecycleStatus.NO_DECLARATION, negative.result.status)

            fail = true
            val unavailable = service.resolve(request(forceRefresh = true), AccountLifecycleTestFixtures.metadata)
            assertEquals(AccountLifecycleStatus.UNAVAILABLE, unavailable.result.status)
            assertNull(unavailable.selectedRecord)
        }

    @Test
    fun negativeAndPositiveRetriesUseSeparateTtls() =
        runTest {
            var clock = 10_000L
            var lookups = 0
            var record: NamecoinRawRecord? = null
            val service =
                AccountLifecycleService(
                    transport = {
                        lookups++
                        record
                    },
                    store = InMemoryLifecycleAssociationStore(),
                    scope = backgroundScope,
                    now = { clock },
                )
            service.resolve(request(), AccountLifecycleTestFixtures.metadata)
            clock += 299
            service.resolve(request(), AccountLifecycleTestFixtures.metadata)
            assertEquals(1, lookups)

            clock += 1
            record = AccountLifecycleTestFixtures.raw()
            service.resolve(request(), AccountLifecycleTestFixtures.metadata)
            assertEquals(2, lookups)
            clock += 3_599
            service.resolve(request(), AccountLifecycleTestFixtures.metadata)
            assertEquals(2, lookups)
            clock += 1
            service.resolve(request(), AccountLifecycleTestFixtures.metadata)
            assertEquals(3, lookups)
        }

    @Test
    fun successorWithdrawalDoesNotEraseOldWarning() =
        runTest {
            var newEvent = AccountLifecycleTestFixtures.event("new_current")
            val provider =
                LifecycleMetadataProvider { pubkey ->
                    when (pubkey) {
                        AccountLifecycleTestFixtures.old -> listOf(AccountLifecycleTestFixtures.event("old_root"))
                        AccountLifecycleTestFixtures.new -> listOf(newEvent)
                        else -> emptyList()
                    }
                }
            val service =
                AccountLifecycleService(
                    transport = { AccountLifecycleTestFixtures.raw() },
                    store = InMemoryLifecycleAssociationStore(),
                    scope = backgroundScope,
                )
            val confirmed = service.resolve(request(), provider)
            assertEquals(SuccessorConfirmation.RECIPROCAL_ALIAS_CONFIRMED, confirmed.result.successorConfirmation)

            newEvent = AccountLifecycleTestFixtures.event("new_withdrawn")
            val withdrawn = service.resolve(request(), provider)
            assertEquals(SuccessorConfirmation.WITHDRAWN, withdrawn.result.successorConfirmation)
            assertTrue(withdrawn.result.isCompromiseWarning)
            assertEquals(AccountLifecycleStatus.CURRENT, withdrawn.result.status)
        }

    @Test
    fun oldProfileRewriteUsesRetainedSignedAssociation() =
        runTest {
            var oldEvent = AccountLifecycleTestFixtures.event("old_root")
            val provider =
                LifecycleMetadataProvider { pubkey ->
                    when (pubkey) {
                        AccountLifecycleTestFixtures.old -> listOf(oldEvent)
                        AccountLifecycleTestFixtures.new -> listOf(AccountLifecycleTestFixtures.event("new_current"))
                        else -> emptyList()
                    }
                }
            val store = InMemoryLifecycleAssociationStore()
            val service =
                AccountLifecycleService(
                    transport = { AccountLifecycleTestFixtures.raw() },
                    store = store,
                    scope = backgroundScope,
                )
            service.resolve(request(), provider)

            oldEvent = AccountLifecycleTestFixtures.event("old_root_withdrawn")
            val rewritten = service.resolve(request(oldEvents = listOf(oldEvent)), provider)
            assertTrue(rewritten.result.isCompromiseWarning)
            assertEquals(BaseNip05Status.UNVERIFIED, rewritten.result.baseNip05)
            assertTrue(rewritten.result.diagnostics.contains("historical_old_backlink"))
            assertFalse(rewritten.backlinks.single { it.localPart == "_" }.current)
        }

    @Test
    fun successorProfileGetsOnlyCurrentlyConfirmedPredecessorEvidence() =
        runTest {
            var newEvent = AccountLifecycleTestFixtures.event("new_current")
            val provider =
                LifecycleMetadataProvider { pubkey ->
                    when (pubkey) {
                        AccountLifecycleTestFixtures.old -> listOf(AccountLifecycleTestFixtures.event("old_root"))
                        AccountLifecycleTestFixtures.new -> listOf(newEvent)
                        else -> emptyList()
                    }
                }
            val store = InMemoryLifecycleAssociationStore()
            val service =
                AccountLifecycleService(
                    transport = { AccountLifecycleTestFixtures.raw() },
                    store = store,
                    scope = backgroundScope,
                )

            suspend fun resolveSuccessor() =
                service.resolveSubject(
                    viewerNamespace = "viewer",
                    subjectPubkey = AccountLifecycleTestFixtures.new,
                    currentIdentifier = "current@example.bit",
                    oldEvents = listOf(newEvent),
                    metadataProvider = provider,
                )

            val confirmed = resolveSuccessor()
            assertEquals(AccountLifecycleStatus.NO_DECLARATION, confirmed.single { it.subjectPubkey == AccountLifecycleTestFixtures.new }.result.status)
            val predecessor = confirmed.single { it.subjectPubkey == AccountLifecycleTestFixtures.old }
            assertTrue(predecessor.result.isCompromiseWarning)
            assertEquals(SuccessorConfirmation.RECIPROCAL_ALIAS_CONFIRMED, predecessor.result.successorConfirmation)

            newEvent = AccountLifecycleTestFixtures.event("new_withdrawn")
            val withdrawn = resolveSuccessor()
            assertEquals(1, withdrawn.size)
            assertEquals(AccountLifecycleTestFixtures.new, withdrawn.single().subjectPubkey)
            val oldWarning = assertNotNull(store.load("viewer", "example.bit", AccountLifecycleTestFixtures.old, true))
            assertTrue(oldWarning.result.isCompromiseWarning)
            assertEquals(SuccessorConfirmation.WITHDRAWN, oldWarning.result.successorConfirmation)
        }

    @Test
    fun subjectRefreshReturnsOnlyLatestResultSet() =
        runTest {
            val store = InMemoryLifecycleAssociationStore()
            val service =
                AccountLifecycleService(
                    transport = { AccountLifecycleTestFixtures.raw() },
                    store = store,
                    scope = backgroundScope,
                )
            val evaluations =
                service.resolveSubject(
                    viewerNamespace = "viewer",
                    subjectPubkey = AccountLifecycleTestFixtures.old,
                    currentIdentifier = "EXAMPLE.BIT",
                    oldEvents = listOf(AccountLifecycleTestFixtures.event("old_root")),
                    metadataProvider = AccountLifecycleTestFixtures.metadata,
                )
            assertEquals(1, evaluations.size)
            assertTrue(evaluations.single().result.isCompromiseWarning)
            val state = service.subjectState("viewer", AccountLifecycleTestFixtures.old)
            assertFalse(state.value.loading)
            assertEquals("example.bit", evaluations.single().context.host)
        }

    private fun request(
        oldEvents: List<Event> = listOf(AccountLifecycleTestFixtures.event("old_root")),
        forceRefresh: Boolean = false,
    ) = AccountLifecycleRequest(
        viewerNamespace = "viewer",
        identifier = "example.bit",
        subjectPubkey = AccountLifecycleTestFixtures.old,
        oldEvents = oldEvents,
        forceRefresh = forceRefresh,
    )
}
