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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnEnvelope
import com.vitorpamplona.quartz.cordn.sync.GroupCursor
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CordnBackupTest {
    private val account = "a".repeat(64)
    private val coordinator = "b".repeat(64)

    /** A cheap cost: these tests exercise the format, not the KDF's strength. */
    private val cheap = 10

    private val archive =
        CordnBackup.Archive(
            accountPubKey = account,
            coordinators =
                listOf(
                    CoordinatorConfig(
                        coordinator,
                        listOf(RelayUrlNormalizer.normalizeOrNull("wss://one.example.com")!!),
                        CoordinatorConfig.Origin.MANUAL,
                        "Work",
                    ),
                ),
            groups =
                listOf(
                    CordnBackup.Archive.Group(
                        coordinatorPubKey = coordinator,
                        gid = "room-1",
                        state = byteArrayOf(1, 2, 3, 4),
                        cursor = GroupCursor(fetchCursor = 7, lastCursor = 9),
                        joinedViaRequest = true,
                        messages = listOf(delivered("hello", 7), delivered("again", 8)),
                    ),
                    CordnBackup.Archive.Group(
                        coordinatorPubKey = coordinator,
                        gid = "room-2",
                        state = byteArrayOf(5, 6),
                        cursor = null,
                        joinedViaRequest = false,
                    ),
                ),
            keyPackages =
                listOf(
                    CordnBackup.Archive.KeyPackage(coordinator, "kp-ref", byteArrayOf(9, 9, 9)),
                ),
        )

    @Test
    fun `an archive round-trips`() {
        val opened = CordnBackup.open(CordnBackup.seal(archive, "correct horse", cheap), "correct horse")

        assertEquals(archive, opened)
    }

    @Test
    fun `a group with no cursor stays a group with no cursor`() {
        // The optional field is the one a length-prefixed format gets wrong by
        // writing a zero and reading it back as a real position, which would
        // make a restored room resume from the start of its history.
        val opened = CordnBackup.open(CordnBackup.seal(archive, "pw", cheap), "pw")

        assertEquals(null, opened.groups.single { it.gid == "room-2" }.cursor)
        assertEquals(
            7L,
            opened.groups
                .single { it.gid == "room-1" }
                .cursor
                ?.fetchCursor,
        )
    }

    @Test
    fun `the wrong passphrase fails, and says nothing about how wrong`() {
        val sealed = CordnBackup.seal(archive, "correct horse", cheap)

        assertFailsWith<Exception> { CordnBackup.open(sealed, "correct hors") }
    }

    @Test
    fun `rewriting the KDF cost down does not weaken the file`() {
        // The attack this refuses: edit logN to something trivially cheap and
        // brute-force against that instead of the real key. It fails because
        // logN feeds the derivation, so a changed cost is a changed key --
        // not because the header is under the AEAD, which is defence in depth
        // for header fields this format does not have yet.
        val sealed = CordnBackup.seal(archive, "pw", cheap)
        val weakened = sealed.copyOf().also { it[MAGIC_LENGTH + 2] = 1 }

        assertFailsWith<Exception> { CordnBackup.open(weakened, "pw") }
    }

    @Test
    fun `a salt swapped between two files does not open either`() {
        val mine = CordnBackup.seal(archive, "pw", cheap)
        val theirs = CordnBackup.seal(archive, "pw", cheap)
        val spliced = mine.copyOf().also { theirs.copyOfRange(SALT_AT, SALT_AT + 16).copyInto(it, SALT_AT) }

        assertFailsWith<Exception> { CordnBackup.open(spliced, "pw") }
    }

    @Test
    fun `a truncated file fails rather than restoring half a group`() {
        val sealed = CordnBackup.seal(archive, "pw", cheap)

        assertFailsWith<Exception> { CordnBackup.open(sealed.copyOf(sealed.size - 8), "pw") }
    }

    @Test
    fun `something that is not a backup is refused by name`() {
        val notABackup = ByteArray(200) { it.toByte() }

        val failure = assertFailsWith<IllegalArgumentException> { CordnBackup.open(notABackup, "pw") }
        assertTrue(failure.message!!.contains("not a cordn backup"))
    }

    @Test
    fun `MLS state and key material never appear in the clear`() {
        // The state blob is a ratchet tree and the bundle is private key
        // material. A backup that leaked either is worse than no backup.
        val sealed = CordnBackup.seal(archive, "pw", cheap)

        assertFalse(sealed.asList().windowed(4).any { it == listOf<Byte>(1, 2, 3, 4) }, "group state in the clear")
        assertFalse(sealed.asList().windowed(3).any { it == listOf<Byte>(9, 9, 9) }, "key material in the clear")
        assertFalse(sealed.decodeToString().contains("room-1"), "a gid in the clear")
    }

    @Test
    fun `two seals of one archive differ, so a file never reveals a repeat`() {
        val first = CordnBackup.seal(archive, "pw", cheap)
        val second = CordnBackup.seal(archive, "pw", cheap)

        assertFalse(first.contentEquals(second), "a fresh salt and nonce per seal")
    }

    @Test
    fun `an unreasonable cost is refused instead of hanging the device`() {
        assertFailsWith<IllegalArgumentException> { CordnBackup.seal(archive, "pw", logN = 40) }
    }

    /**
     * The id has to hash the contents: `CordnEnvelope.fromJsonObject` checks
     * it, so an envelope with a made-up id decodes to null and the archive
     * appears to have lost the message.
     */
    private fun delivered(
        content: String,
        cursor: Long,
    ): CordnDeliveredMessage {
        val unsigned =
            CordnEnvelope(
                id = "",
                pubKey = account,
                createdAt = 1_700_000_000L + cursor,
                kind = 9,
                tags = arrayOf(arrayOf("h", "room-1")),
                content = content,
            )
        return CordnDeliveredMessage(unsigned.copy(id = unsigned.computedId()), cursor)
    }

    @Test
    fun `messages survive the round trip`() {
        val opened = CordnBackup.open(CordnBackup.seal(archive, "pw", cheap), "pw")

        val room1 = opened.groups.first { it.gid == "room-1" }
        assertEquals(listOf("hello", "again"), room1.messages.map { it.envelope.content })
        assertEquals(listOf(7L, 8L), room1.messages.map { it.cursor })
        // The group carrying none still round-trips as none.
        assertEquals(emptyList(), opened.groups.first { it.gid == "room-2" }.messages)
    }

    private companion object {
        const val MAGIC_LENGTH = 8

        /** magic(8) + version(2) + logN(1). */
        const val SALT_AT = 11
    }
}
