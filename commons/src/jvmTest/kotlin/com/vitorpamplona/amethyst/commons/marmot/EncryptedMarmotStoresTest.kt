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
package com.vitorpamplona.amethyst.commons.marmot

import com.vitorpamplona.amethyst.commons.model.preferences.SecretEncryption
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Round trips for the file-backed Marmot stores, which had no coverage while they sat in `amethyst/`
 * behind an `Android` prefix they never earned.
 *
 * What matters here is that the bytes survive a restart: these hold MLS group state and the message
 * log, so a store that writes but cannot read back loses a group's history and its ratchet — and MLS
 * state that cannot be reloaded is not recoverable from the relays.
 */
class EncryptedMarmotStoresTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var seq = 0

    /** A fresh account directory plus its own key file, so tests cannot read each other's data. */
    private fun accountDir(): Pair<File, SecretEncryption> {
        val n = seq++
        val dir = folder.newFolder("account_$n")
        return dir to SecretEncryption(File(folder.root, "secret_$n.key"))
    }

    // Group ids must be hex: both stores validate, which is what stops a crafted id escaping the
    // account directory. Using a realistic 64-char id rather than a label keeps the tests honest.
    private val groupId = "a".repeat(63) + "1"
    private val otherGroupId = "b".repeat(63) + "2"

    @Test
    fun groupStateSurvivesANewStoreOverTheSameDirectory() =
        runTest {
            val (dir, encryption) = accountDir()
            val payload = byteArrayOf(1, 2, 3, 4, 5)

            EncryptedMlsGroupStateStore(dir.toOkioPath(), encryption).save(groupId, payload)

            val reopened = EncryptedMlsGroupStateStore(dir.toOkioPath(), encryption).load(groupId)
            assertEquals("the same bytes come back", payload.toList(), reopened?.toList())
        }

    @Test
    fun anUnknownGroupLoadsAsNull() =
        runTest {
            val (dir, encryption) = accountDir()

            assertNull(EncryptedMlsGroupStateStore(dir.toOkioPath(), encryption).load("c".repeat(63) + "3"))
        }

    @Test
    fun deletingAGroupRemovesItFromTheListing() =
        runTest {
            val (dir, encryption) = accountDir()
            val store = EncryptedMlsGroupStateStore(dir.toOkioPath(), encryption)
            store.save(groupId, byteArrayOf(9))
            store.save(otherGroupId, byteArrayOf(8))

            store.delete(groupId)

            assertEquals("only the other group is left", listOf(otherGroupId), store.listGroups())
            assertNull("and its state is gone", store.load(groupId))
        }

    /** The convergence window comes back after a restart, is not stored in the clear, and goes with the group. */
    @Test
    fun theConvergenceWindowRoundTripsEncryptedAndGoesWithTheGroup() =
        runTest {
            val (dir, encryption) = accountDir()
            val window = ByteArray(64) { (it * 7).toByte() }
            val store = EncryptedMlsGroupStateStore(dir.toOkioPath(), encryption)
            store.save(groupId, byteArrayOf(1))
            assertTrue(store.saveConvergenceWindow(groupId, window))

            assertEquals(window.toList(), EncryptedMlsGroupStateStore(dir.toOkioPath(), encryption).loadConvergenceWindow(groupId)?.toList())
            val onDisk = File(dir, "mls_groups/$groupId/convergence").readBytes()
            assertTrue("encrypted at rest", !onDisk.toList().windowed(window.size).contains(window.toList()))

            store.delete(groupId)
            assertNull(store.loadConvergenceWindow(groupId))
        }

    /** The sender ratchet is stored separately from the group blob; losing it breaks decryption. */
    @Test
    fun theSenderRatchetRoundTripsIndependentlyOfTheGroupState() =
        runTest {
            val (dir, encryption) = accountDir()
            val store = EncryptedMlsGroupStateStore(dir.toOkioPath(), encryption)

            store.save(groupId, byteArrayOf(1))
            store.saveSenderRatchet(groupId, byteArrayOf(7, 7, 7))

            val reopened = EncryptedMlsGroupStateStore(dir.toOkioPath(), encryption)
            assertEquals("ratchet preserved", listOf<Byte>(7, 7, 7), reopened.loadSenderRatchet(groupId)?.toList())
            assertEquals("and the group blob is untouched", listOf<Byte>(1), reopened.load(groupId)?.toList())
        }

    private fun innerEvent(
        id: Char,
        createdAt: Long,
    ) = """{"id":"${id.toString().repeat(64)}","pubkey":"${"e".repeat(64)}","created_at":$createdAt,"kind":9,"tags":[],"content":"m","sig":""}"""

    /**
     * Read on every start for every group, so it must come from the newest time kept as messages are
     * appended, not from decrypting the whole log: the log is gone here, and the answer stays.
     */
    @Test
    fun theNewestMessageTimeSurvivesAReopenWithoutReadingTheLog() =
        runTest {
            val (dir, encryption) = accountDir()
            val store = EncryptedMarmotMessageStore(dir.toOkioPath(), encryption)
            assertNull(store.newestMessageCreatedAt(groupId))

            store.appendMessage(groupId, innerEvent('1', 200))
            store.appendMessage(groupId, innerEvent('2', 500))
            store.appendMessage(groupId, innerEvent('3', 300))
            assertEquals(500L, store.newestMessageCreatedAt(groupId))

            assertTrue(File(dir, "mls_groups/$groupId/messages").delete())
            assertEquals("kept apart from the log", 500L, EncryptedMarmotMessageStore(dir.toOkioPath(), encryption).newestMessageCreatedAt(groupId))
            assertNull("another group has none", EncryptedMarmotMessageStore(dir.toOkioPath(), encryption).newestMessageCreatedAt(otherGroupId))
        }

    /** A log written before the newest time was kept: read once, then answered without it. */
    @Test
    fun aLogWithNoNewestTimeIsReadOnceAndTheAnswerKept() =
        runTest {
            val (dir, encryption) = accountDir()
            val store = EncryptedMarmotMessageStore(dir.toOkioPath(), encryption)
            store.appendMessage(groupId, innerEvent('1', 200))
            store.appendMessage(groupId, innerEvent('2', 700))
            File(dir, "mls_groups/$groupId/newest").delete()

            assertEquals(700L, EncryptedMarmotMessageStore(dir.toOkioPath(), encryption).newestMessageCreatedAt(groupId))
            assertTrue(File(dir, "mls_groups/$groupId/messages").delete())
            assertEquals(700L, EncryptedMarmotMessageStore(dir.toOkioPath(), encryption).newestMessageCreatedAt(groupId))
        }

    @Test
    fun deletingAGroupForgetsItsNewestMessageTime() =
        runTest {
            val (dir, encryption) = accountDir()
            val store = EncryptedMarmotMessageStore(dir.toOkioPath(), encryption)
            store.appendMessage(groupId, innerEvent('1', 200))
            store.delete(groupId)
            assertNull(store.newestMessageCreatedAt(groupId))
            assertNull(EncryptedMarmotMessageStore(dir.toOkioPath(), encryption).newestMessageCreatedAt(groupId))
        }

    @Test
    fun appendedMessagesComeBackInOrderAfterAReopen() =
        runTest {
            val (dir, encryption) = accountDir()
            val store = EncryptedMarmotMessageStore(dir.toOkioPath(), encryption)

            store.appendMessage(groupId, """{"id":"one"}""")
            store.appendMessage(groupId, """{"id":"two"}""")

            val reopened = EncryptedMarmotMessageStore(dir.toOkioPath(), encryption).loadMessages(groupId)
            assertEquals("both, in append order", listOf("""{"id":"one"}""", """{"id":"two"}"""), reopened)
        }

    /**
     * Disappearing-message expiries are kept in memory per group once read; that copy must agree
     * with the file through every write: first write wins, a reopen sees the same map, and removing
     * a message forgets its expiry in both.
     */
    @Test
    fun expiriesKeepTheirFirstValueSurviveAReopenAndAreForgottenWithTheirMessage() =
        runTest {
            val (dir, encryption) = accountDir()
            val store = EncryptedMarmotMessageStore(dir.toOkioPath(), encryption)
            val one = "1".repeat(64)
            val two = "2".repeat(64)

            assertEquals(emptyMap<String, Long>(), store.loadExpiries(groupId))
            store.recordExpiry(groupId, one, 100L)
            store.recordExpiry(groupId, one, 999L)
            store.recordExpiry(groupId, two, 200L)
            store.recordExpiry(otherGroupId, one, 300L)

            val expected = mapOf(one to 100L, two to 200L)
            assertEquals("first write wins", expected, store.loadExpiries(groupId))
            assertEquals("a reopen reads the same", expected, EncryptedMarmotMessageStore(dir.toOkioPath(), encryption).loadExpiries(groupId))

            store.removeMessages(groupId, setOf(one))
            assertEquals(mapOf(two to 200L), store.loadExpiries(groupId))
            assertEquals(mapOf(two to 200L), EncryptedMarmotMessageStore(dir.toOkioPath(), encryption).loadExpiries(groupId))
            assertEquals("another group is untouched", mapOf(one to 300L), store.loadExpiries(otherGroupId))

            store.recordExpiry(groupId, one, 400L)
            assertEquals(mapOf(two to 200L, one to 400L), EncryptedMarmotMessageStore(dir.toOkioPath(), encryption).loadExpiries(groupId))

            store.delete(groupId)
            assertEquals(emptyMap<String, Long>(), store.loadExpiries(groupId))
        }

    /**
     * Media keys, epoch admins and moderation verdicts decide what a restart shows: an
     * attachment that opens, a deleted message that stays deleted. Each is first-write-wins.
     */
    @Test
    fun epochRecordsSurviveAReopenAndKeepTheirFirstValue() =
        runTest {
            val (dir, encryption) = accountDir()
            val store = EncryptedMarmotMessageStore(dir.toOkioPath(), encryption)
            val verdictId = "c".repeat(64)

            store.recordMediaSecret(groupId, 3, byteArrayOf(1, 2))
            store.recordMediaSecret(groupId, 3, byteArrayOf(9))
            store.recordMediaSecret(groupId, 4, byteArrayOf(3))
            store.recordEpochAdmins(groupId, 3, setOf("a", "b"))
            store.recordEpochAdmins(groupId, 3, setOf("z"))
            store.recordModerationVerdict(groupId, verdictId, true)
            store.recordModerationVerdict(groupId, verdictId, false)

            val reopened = EncryptedMarmotMessageStore(dir.toOkioPath(), encryption)
            val secrets = reopened.loadMediaSecrets(groupId)
            assertEquals(listOf<Byte>(1, 2), secrets[3]?.toList())
            assertEquals(listOf<Byte>(3), secrets[4]?.toList())
            assertEquals(mapOf(3L to setOf("a", "b")), reopened.loadEpochAdmins(groupId))
            assertEquals(mapOf(verdictId to true), reopened.loadModerationVerdicts(groupId))
        }

    @Test
    fun aGroupWithNoMessagesLoadsEmptyRatherThanFailing() =
        runTest {
            val (dir, encryption) = accountDir()

            assertTrue(EncryptedMarmotMessageStore(dir.toOkioPath(), encryption).loadMessages("d".repeat(63) + "4").isEmpty())
        }

    @Test
    fun deletingAGroupDropsItsMessageLog() =
        runTest {
            val (dir, encryption) = accountDir()
            val store = EncryptedMarmotMessageStore(dir.toOkioPath(), encryption)
            store.appendMessage(groupId, """{"id":"one"}""")

            store.delete(groupId)

            assertTrue(EncryptedMarmotMessageStore(dir.toOkioPath(), encryption).loadMessages(groupId).isEmpty())
        }

    /** The group snapshot is what a cold start restores from before replaying the log. */
    @Test
    fun theGroupSnapshotRoundTrips() =
        runTest {
            val (dir, encryption) = accountDir()
            val store = EncryptedMarmotMessageStore(dir.toOkioPath(), encryption)

            store.recordGroupSnapshot(groupId, """{"epoch":4}""")

            assertEquals("""{"epoch":4}""", EncryptedMarmotMessageStore(dir.toOkioPath(), encryption).loadGroupSnapshot(groupId))
        }

    /** Two accounts are two directories: one must never read the other's groups. */
    @Test
    fun twoAccountDirectoriesDoNotSeeEachOther() =
        runTest {
            val (dirA, encA) = accountDir()
            val (dirB, encB) = accountDir()

            EncryptedMlsGroupStateStore(dirA.toOkioPath(), encA).save(groupId, byteArrayOf(1))

            assertNull("B cannot see A's group", EncryptedMlsGroupStateStore(dirB.toOkioPath(), encB).load(groupId))
            assertTrue("nor list it", EncryptedMlsGroupStateStore(dirB.toOkioPath(), encB).listGroups().isEmpty())
        }

    /** The hex check is a path-traversal guard: a crafted id must not be able to leave the account dir. */
    @Test
    fun aNonHexGroupIdIsRejected() =
        runTest {
            val (dir, encryption) = accountDir()
            val store = EncryptedMlsGroupStateStore(dir.toOkioPath(), encryption)

            listOf("../escape", "not hex", "abc/def", "").forEach { bad ->
                val thrown =
                    try {
                        store.load(bad)
                        false
                    } catch (e: IllegalArgumentException) {
                        true
                    }
                assertTrue("\"$bad\" must be rejected, not resolved to a path", thrown)
            }
        }
}
