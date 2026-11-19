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

import com.vitorpamplona.amethyst.commons.model.preferences.AppPreferenceStores
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LifecycleAssociationStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun stores() = AppPreferenceStores(rootFilesDir = { folder.root.toOkioPath() })

    @Test
    fun signedAssociationAndCompleteRecordSurviveReopen() =
        runTest {
            val preferences = stores()
            val store = DataStoreLifecycleAssociationStore(preferences.getDataStore(ACCOUNT_LIFECYCLE_STORE))
            val backlink =
                LifecycleBacklink(
                    localPart = "_",
                    event = SignedLifecycleEvent.fromEvent(AccountLifecycleTestFixtures.event("old_root")),
                    current = false,
                )
            val original =
                record(
                    identifier = "example.bit",
                    subject = AccountLifecycleTestFixtures.old,
                    state = AccountLifecycleState.COMPROMISED,
                    observedAt = 1234,
                ).copy(
                    selectedRecord = AccountLifecycleTestFixtures.compromised,
                    backlinks = listOf(backlink),
                    result =
                        AccountLifecycleParser
                            .evaluateInlineRoot(
                                identifier = "example.bit",
                                subjectPubkey = AccountLifecycleTestFixtures.old,
                                rawRecord = AccountLifecycleTestFixtures.compromised,
                                oldEvents = listOf(AccountLifecycleTestFixtures.event("old_root")),
                                newEvents = listOf(AccountLifecycleTestFixtures.event("new_current")),
                                provenance = AccountLifecycleTestFixtures.raw().provenance,
                            ).result,
                )
            store.save(original)
            assertTrue(preferences.release(ACCOUNT_LIFECYCLE_STORE))

            val reopened = DataStoreLifecycleAssociationStore(preferences.getDataStore(ACCOUNT_LIFECYCLE_STORE))
            val restored = assertNotNull(reopened.load("viewer", "example.bit", AccountLifecycleTestFixtures.old, false))
            assertEquals(original, restored)
            assertEquals(AccountLifecycleTestFixtures.compromised, restored.selectedRecord)
            assertEquals(
                backlink.event.id,
                restored.backlinks
                    .single()
                    .event.id,
            )
            assertEquals("a".repeat(64), restored.result.provenance?.txid)
            assertEquals(SuccessorConfirmation.RECIPROCAL_ALIAS_CONFIRMED, restored.result.successorConfirmation)
        }

    @Test
    fun capacityPrunesOrdinaryHistoryBeforeCompromiseWarnings() =
        runTest {
            val store =
                DataStoreLifecycleAssociationStore(
                    stores().getDataStore(ACCOUNT_LIFECYCLE_STORE),
                    maxRecords = 2,
                )
            val compromise1 = record("one.bit", AccountLifecycleTestFixtures.old, AccountLifecycleState.COMPROMISED, 1)
            val ordinary = record("two.bit", AccountLifecycleTestFixtures.new, AccountLifecycleState.RETIRED, 2)
            val compromise2 = record("three.bit", AccountLifecycleTestFixtures.other, AccountLifecycleState.COMPROMISED, 3)
            store.save(compromise1)
            store.save(ordinary)
            store.save(compromise2)

            assertNotNull(store.load("viewer", "one.bit", AccountLifecycleTestFixtures.old, false))
            assertNull(store.load("viewer", "two.bit", AccountLifecycleTestFixtures.new, false))
            assertNotNull(store.load("viewer", "three.bit", AccountLifecycleTestFixtures.other, false))

            val rejected = record("four.bit", "a".repeat(64), AccountLifecycleState.RETIRED, 4)
            store.save(rejected)
            assertNull(store.load("viewer", "four.bit", "a".repeat(64), false))
            assertEquals(2, store.forSubject("viewer", AccountLifecycleTestFixtures.old).size + store.forSubject("viewer", AccountLifecycleTestFixtures.other).size)
        }

    @Test
    fun inMemoryStoreUsesTheSameWarningCapacityPolicy() =
        runTest {
            val store = InMemoryLifecycleAssociationStore(maxRecords = 1)
            val warning = record("one.bit", AccountLifecycleTestFixtures.old, AccountLifecycleState.COMPROMISED, 1)
            store.save(warning)
            store.save(record("two.bit", AccountLifecycleTestFixtures.new, AccountLifecycleState.RETIRED, 2))
            assertNotNull(store.load("viewer", "one.bit", AccountLifecycleTestFixtures.old, false))
            assertNull(store.load("viewer", "two.bit", AccountLifecycleTestFixtures.new, false))

            store.save(record("three.bit", AccountLifecycleTestFixtures.other, AccountLifecycleState.RETIRED, 3))
            assertNotNull(store.load("viewer", "one.bit", AccountLifecycleTestFixtures.old, false))
            assertNull(store.load("viewer", "three.bit", AccountLifecycleTestFixtures.other, false))
        }

    @Test
    fun associationsAreViewerNamespaced() =
        runTest {
            val store = InMemoryLifecycleAssociationStore()
            store.save(record("example.bit", AccountLifecycleTestFixtures.old, AccountLifecycleState.COMPROMISED, 1, viewer = "viewer-a"))
            assertNull(store.load("viewer-b", "example.bit", AccountLifecycleTestFixtures.old, false))
            assertTrue(store.forSubject("viewer-b", AccountLifecycleTestFixtures.old).isEmpty())
        }

    private fun record(
        identifier: String,
        subject: String,
        state: AccountLifecycleState,
        observedAt: Long,
        viewer: String = "viewer",
    ) = LifecycleAssociationRecord(
        viewerNamespace = viewer,
        identifier = identifier,
        subjectPubkey = subject,
        selectedRecord = "{}",
        result =
            AccountLifecycleResult(
                status = AccountLifecycleStatus.CURRENT,
                scope = AccountLifecycleScope.ACCOUNT,
                state = state,
                successorConfirmation = SuccessorConfirmation.ABSENT,
                baseNip05 = BaseNip05Status.VERIFIED,
                provenance = AccountLifecycleTestFixtures.raw().provenance,
            ),
        observedAt = observedAt,
    )
}
