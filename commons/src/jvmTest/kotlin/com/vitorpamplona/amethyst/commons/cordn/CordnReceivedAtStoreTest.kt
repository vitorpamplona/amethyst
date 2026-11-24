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

import com.vitorpamplona.cordn.client.CordnBlobCipher
import com.vitorpamplona.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.cordn.spec02Envelopes.CordnEnvelope
import com.vitorpamplona.cordn.spec02Envelopes.CordnMessageKinds
import com.vitorpamplona.cordn.spec02Envelopes.CordnMessageReferences
import com.vitorpamplona.cordn.spec02Envelopes.CordnPolls
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toOkioPath
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The coordinator's receive time survives Amethyst's encrypted message log, and a poll read back
 * from it judges its deadline by that time, as cordn-web does: a vote whose sender backdated it
 * past a closed poll stays out after a restart too.
 */
class CordnReceivedAtStoreTest {
    private class XorCipher : CordnBlobCipher {
        override fun encrypt(bytes: ByteArray): ByteArray = ByteArray(bytes.size) { (bytes[it].toInt() xor 0x5A).toByte() }

        override fun decrypt(bytes: ByteArray): ByteArray = encrypt(bytes)
    }

    private val rootFile: File = createTempDirectory("cordn-received-at").toFile()

    @AfterTest
    fun cleanup() {
        rootFile.deleteRecursively()
    }

    private fun store() = FileCordnGroupStore(CordnStorageLayout.directoryFor(rootFile.toOkioPath(), "a".repeat(64), "b".repeat(64)), XorCipher())

    private val alice = "aa".repeat(32)
    private val bob = "bb".repeat(32)
    private val carol = "cc".repeat(32)
    private val gid = "group"
    private val endsAt = 1_767_227_400L

    @Test
    fun aLateVoteStaysOutOfThePollAfterARestart() =
        runTest {
            val poll = CordnEnvelope.build(alice, endsAt - 1_800, CordnMessageKinds.POLL, CordnPolls.pollTags("Lunch?", listOf("Yes", "No"), endsAt = endsAt), "Lunch?")

            fun vote(
                who: String,
                option: String,
            ) = CordnEnvelope.build(who, endsAt - 60, CordnMessageKinds.POLL_RESPONSE, CordnPolls.voteTags(CordnMessageReferences.Target.of(poll), listOf(option)), "Voted")

            val written =
                listOf(
                    CordnDeliveredMessage(poll, 1, receivedAt = (endsAt - 1_800) * 1000),
                    CordnDeliveredMessage(vote(bob, "0"), 2, receivedAt = (endsAt - 60) * 1000),
                    // Written "before" the deadline by its sender's clock, received after it.
                    CordnDeliveredMessage(vote(carol, "1"), 3, receivedAt = (endsAt + 30) * 1000),
                )
            written.forEach { store().appendMessage(gid, it) }

            val reloaded = store().loadMessages(gid)
            assertEquals(written, reloaded)
            assertEquals(listOf((endsAt + 30) * 1000), listOf(reloaded[2].receivedAt))

            val tally = assertNotNull(CordnPolls.tally(reloaded, poll.id, alice, nowSeconds = endsAt + 60))
            assertEquals(listOf(1, 0), tally.options.map { it.votes })
        }
}
