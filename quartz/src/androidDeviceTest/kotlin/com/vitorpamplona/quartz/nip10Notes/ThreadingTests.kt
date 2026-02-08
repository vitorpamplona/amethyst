/**
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
package com.vitorpamplona.quartz.nip10Notes

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUsers
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThreadingTests {
    @Test
    fun testLegacyEvent() {
        val note =
            TextNoteEvent(
                "",
                "",
                0,
                arrayOf(
                    arrayOf("p", "4ca4f5533e40da5e0508796d409e6bb35a50b26fc304345617ab017183d83ac0"),
                    arrayOf("p", "534780e44da7b494485e85cd4cca6af4f6caa1627472432b6f2a4ece0e9e54ec"),
                    arrayOf("p", "77ce56f89d1228f7ff3743ce1ad1b254857b9008564727ebd5a1f317362f6ca7"),
                    arrayOf("e", "89f220b63465c93542b1a78caa3a952cf4f196e91a50596493c8093c533ebc4d"),
                    arrayOf("e", "090c037b2e399ee74d9f134758928948dd9154413ca1a1acb37155046e03a051"),
                    arrayOf("e", "567b7c11f0fe582361e3cea6fcc7609a8942dfe196ee1b98d5604c93fbeea976"),
                    arrayOf("e", "49aff7ae6daeaaa2777931b90f9bb29f6cb01c5a3d7d88c8ba82d890f264afb4"),
                    arrayOf("e", "5e081ebb19153357d7c31e8a10b9ceeef29313f58dc8d701f66727fab02aef64"),
                    arrayOf("e", "bbd72f0ae14374aa8fb166b483cfcf99b57d7f4cf1600ccbf17c350040834631"),
                    arrayOf("e", "b857504288c18a15950dd05b9e8772c62ca6289d5aac373c0a8ee5b132e94e7c"),
                ),
                "",
                "",
            )

        val taggedUsers =
            listOf(
                PTag("4ca4f5533e40da5e0508796d409e6bb35a50b26fc304345617ab017183d83ac0"),
                PTag("534780e44da7b494485e85cd4cca6af4f6caa1627472432b6f2a4ece0e9e54ec"),
                PTag("77ce56f89d1228f7ff3743ce1ad1b254857b9008564727ebd5a1f317362f6ca7"),
            )

        assertEquals(taggedUsers, note.taggedUsers())

        val expectedReply = MarkedETag("b857504288c18a15950dd05b9e8772c62ca6289d5aac373c0a8ee5b132e94e7c", null, MarkedETag.MARKER.REPLY)

        // check replies
        assertEquals(null, note.markedReply())
        assertEquals(expectedReply, note.unmarkedReply())
        assertEquals(expectedReply, note.reply())

        val replyTos =
            listOf(
                "89f220b63465c93542b1a78caa3a952cf4f196e91a50596493c8093c533ebc4d",
                "090c037b2e399ee74d9f134758928948dd9154413ca1a1acb37155046e03a051",
                "567b7c11f0fe582361e3cea6fcc7609a8942dfe196ee1b98d5604c93fbeea976",
                "49aff7ae6daeaaa2777931b90f9bb29f6cb01c5a3d7d88c8ba82d890f264afb4",
                "5e081ebb19153357d7c31e8a10b9ceeef29313f58dc8d701f66727fab02aef64",
                "bbd72f0ae14374aa8fb166b483cfcf99b57d7f4cf1600ccbf17c350040834631",
                "b857504288c18a15950dd05b9e8772c62ca6289d5aac373c0a8ee5b132e94e7c",
            )

        assertEquals(emptyList<String>(), note.markedReplyTos())
        assertEquals(replyTos, note.unmarkedReplyTos())

        assertEquals(expectedReply.eventId, note.replyingTo())
        assertEquals(expectedReply.eventId, note.replyingToAddressOrEvent())

        // check root
        val expectedRoot = MarkedETag("89f220b63465c93542b1a78caa3a952cf4f196e91a50596493c8093c533ebc4d", null, MarkedETag.MARKER.ROOT)

        assertEquals(null, note.markedRoot())
        assertEquals(expectedRoot, note.unmarkedRoot())
        assertEquals(expectedRoot, note.root())

        // check quotes
        assertEquals(emptySet<String>(), note.citedUsers())
        assertEquals(emptySet<String>(), note.findCitations())
        assertEquals(replyTos, note.tagsWithoutCitations())
    }

    @Test
    fun testMarkedEvent() {
        val note =
            TextNoteEvent(
                "",
                "",
                0,
                arrayOf(
                    arrayOf("p", "4ca4f5533e40da5e0508796d409e6bb35a50b26fc304345617ab017183d83ac0"),
                    arrayOf("p", "534780e44da7b494485e85cd4cca6af4f6caa1627472432b6f2a4ece0e9e54ec"),
                    arrayOf("e", "77ce56f89d1228f7ff3743ce1ad1b254857b9008564727ebd5a1f317362f6ca7"),
                    arrayOf("e", "bbd72f0ae14374aa8fb166b483cfcf99b57d7f4cf1600ccbf17c350040834631", "", "root"),
                    arrayOf("e", "b857504288c18a15950dd05b9e8772c62ca6289d5aac373c0a8ee5b132e94e7c", "", "reply"),
                ),
                "",
                "",
            )

        val taggedUsers =
            listOf(
                PTag("4ca4f5533e40da5e0508796d409e6bb35a50b26fc304345617ab017183d83ac0"),
                PTag("534780e44da7b494485e85cd4cca6af4f6caa1627472432b6f2a4ece0e9e54ec"),
            )

        assertEquals(taggedUsers, note.taggedUsers())

        val expectedReply = MarkedETag("b857504288c18a15950dd05b9e8772c62ca6289d5aac373c0a8ee5b132e94e7c", null, MarkedETag.MARKER.REPLY)
        val expectedReplyWrong = MarkedETag("77ce56f89d1228f7ff3743ce1ad1b254857b9008564727ebd5a1f317362f6ca7", null, MarkedETag.MARKER.REPLY)

        // check replies
        assertEquals(expectedReply, note.markedReply())
        assertEquals(expectedReplyWrong, note.unmarkedReply())
        assertEquals(expectedReply, note.reply())

        val markedReplies =
            listOf(
                "bbd72f0ae14374aa8fb166b483cfcf99b57d7f4cf1600ccbf17c350040834631",
                "b857504288c18a15950dd05b9e8772c62ca6289d5aac373c0a8ee5b132e94e7c",
            )

        val unmarkedReplies =
            listOf(
                "77ce56f89d1228f7ff3743ce1ad1b254857b9008564727ebd5a1f317362f6ca7",
            )

        assertEquals(markedReplies, note.markedReplyTos())
        assertEquals(unmarkedReplies, note.unmarkedReplyTos())

        assertEquals(expectedReply.eventId, note.replyingTo())
        assertEquals(expectedReply.eventId, note.replyingToAddressOrEvent())

        // check root
        val expectedRoot = MarkedETag("bbd72f0ae14374aa8fb166b483cfcf99b57d7f4cf1600ccbf17c350040834631", null, MarkedETag.MARKER.ROOT)

        val expectedRootWrong = MarkedETag("77ce56f89d1228f7ff3743ce1ad1b254857b9008564727ebd5a1f317362f6ca7", null, MarkedETag.MARKER.ROOT)

        assertEquals(expectedRoot, note.markedRoot())
        assertEquals(expectedRootWrong, note.unmarkedRoot())
        assertEquals(expectedRoot, note.root())

        // check quotes
        assertEquals(emptySet<String>(), note.citedUsers())
        assertEquals(emptySet<String>(), note.findCitations())
        assertEquals(markedReplies, note.tagsWithoutCitations())
    }

    @Test
    fun testMarkedInverted() {
        val note =
            TextNoteEvent(
                "",
                "",
                0,
                arrayOf(
                    arrayOf("p", "4ca4f5533e40da5e0508796d409e6bb35a50b26fc304345617ab017183d83ac0", "wss://goiaba.com"),
                    arrayOf("p", "534780e44da7b494485e85cd4cca6af4f6caa1627472432b6f2a4ece0e9e54ec"),
                    arrayOf("p", "77ce56f89d1228f7ff3743ce1ad1b254857b9008564727ebd5a1f317362f6ca7"),
                    arrayOf("e", "5e081ebb19153357d7c31e8a10b9ceeef29313f58dc8d701f66727fab02aef64", "", "reply"),
                    arrayOf("e", "bbd72f0ae14374aa8fb166b483cfcf99b57d7f4cf1600ccbf17c350040834631", "wss://banana.com", "root", "4ca4f5533e40da5e0508796d409e6bb35a50b26fc304345617ab017183d83ac0"),
                    arrayOf("e", "b857504288c18a15950dd05b9e8772c62ca6289d5aac373c0a8ee5b132e94e7c"),
                ),
                "",
                "",
            )

        val taggedUsers =
            listOf(
                PTag("4ca4f5533e40da5e0508796d409e6bb35a50b26fc304345617ab017183d83ac0", RelayUrlNormalizer.normalize("wss://goiaba.com")),
                PTag("534780e44da7b494485e85cd4cca6af4f6caa1627472432b6f2a4ece0e9e54ec"),
                PTag("77ce56f89d1228f7ff3743ce1ad1b254857b9008564727ebd5a1f317362f6ca7"),
            )

        assertEquals(taggedUsers, note.taggedUsers())

        val expectedReply = MarkedETag("5e081ebb19153357d7c31e8a10b9ceeef29313f58dc8d701f66727fab02aef64", null, MarkedETag.MARKER.REPLY)
        val expectedReplyWrong = MarkedETag("b857504288c18a15950dd05b9e8772c62ca6289d5aac373c0a8ee5b132e94e7c", null, MarkedETag.MARKER.REPLY)

        // check replies
        assertEquals(expectedReply, note.markedReply())
        assertEquals(expectedReplyWrong, note.unmarkedReply())
        assertEquals(expectedReply, note.reply())

        val markedReplies =
            listOf(
                "bbd72f0ae14374aa8fb166b483cfcf99b57d7f4cf1600ccbf17c350040834631",
                "5e081ebb19153357d7c31e8a10b9ceeef29313f58dc8d701f66727fab02aef64",
            )

        val unmarkedReplies =
            listOf(
                "b857504288c18a15950dd05b9e8772c62ca6289d5aac373c0a8ee5b132e94e7c",
            )

        assertEquals(markedReplies, note.markedReplyTos())
        assertEquals(unmarkedReplies, note.unmarkedReplyTos())

        assertEquals(expectedReply.eventId, note.replyingTo())
        assertEquals(expectedReply.eventId, note.replyingToAddressOrEvent())

        // check root
        val expectedRoot = MarkedETag("bbd72f0ae14374aa8fb166b483cfcf99b57d7f4cf1600ccbf17c350040834631", null, MarkedETag.MARKER.ROOT)
        val expectedRootWrong = MarkedETag("b857504288c18a15950dd05b9e8772c62ca6289d5aac373c0a8ee5b132e94e7c", null, MarkedETag.MARKER.ROOT)

        assertEquals(expectedRoot, note.markedRoot())
        assertEquals(expectedRootWrong, note.unmarkedRoot())
        assertEquals(expectedRoot, note.root())

        // check quotes
        assertEquals(emptySet<String>(), note.citedUsers())
        assertEquals(emptySet<String>(), note.findCitations())
        assertEquals(markedReplies, note.tagsWithoutCitations())
    }

    @Test
    fun testOnlyRoot() {
        val note =
            TextNoteEvent(
                "",
                "",
                0,
                arrayOf(
                    arrayOf("p", "534780e44da7b494485e85cd4cca6af4f6caa1627472432b6f2a4ece0e9e54ec", "wss://banana.com"),
                    arrayOf("e", "9abbfd9b9ac5ecdab45d14b8bf8d746139ea039e931a1b376d19a239f1946590", "", "root", "534780e44da7b494485e85cd4cca6af4f6caa1627472432b6f2a4ece0e9e54ec"),
                ),
                "",
                "",
            )

        val taggedUsers =
            listOf(
                PTag("534780e44da7b494485e85cd4cca6af4f6caa1627472432b6f2a4ece0e9e54ec", RelayUrlNormalizer.normalize("wss://banana.com")),
            )

        assertEquals(taggedUsers, note.taggedUsers())

        val expectedReply = MarkedETag("9abbfd9b9ac5ecdab45d14b8bf8d746139ea039e931a1b376d19a239f1946590", null, MarkedETag.MARKER.REPLY)

        // check replies
        assertEquals(null, note.markedReply())
        assertEquals(null, note.unmarkedReply())
        assertEquals(null, note.reply())

        val replyTos =
            listOf(
                "9abbfd9b9ac5ecdab45d14b8bf8d746139ea039e931a1b376d19a239f1946590",
            )

        assertEquals(replyTos, note.markedReplyTos())
        assertEquals(emptyList<String>(), note.unmarkedReplyTos())

        assertEquals(expectedReply.eventId, note.replyingTo())
        assertEquals(expectedReply.eventId, note.replyingToAddressOrEvent())

        // check root
        val expectedRoot = MarkedETag("9abbfd9b9ac5ecdab45d14b8bf8d746139ea039e931a1b376d19a239f1946590", null, MarkedETag.MARKER.ROOT, "534780e44da7b494485e85cd4cca6af4f6caa1627472432b6f2a4ece0e9e54ec")

        assertEquals(expectedRoot, note.markedRoot())
        assertEquals(null, note.unmarkedRoot())
        assertEquals(expectedRoot, note.root())

        // check quotes
        assertEquals(emptySet<String>(), note.citedUsers())
        assertEquals(emptySet<String>(), note.findCitations())
        assertEquals(replyTos, note.tagsWithoutCitations())
    }

    @Test
    fun testOnlyReply() {
        val note =
            TextNoteEvent(
                "",
                "",
                0,
                arrayOf(
                    arrayOf("p", "534780e44da7b494485e85cd4cca6af4f6caa1627472432b6f2a4ece0e9e54ec", "wss://banana.com"),
                    arrayOf("e", "9abbfd9b9ac5ecdab45d14b8bf8d746139ea039e931a1b376d19a239f1946590", "", "reply", "534780e44da7b494485e85cd4cca6af4f6caa1627472432b6f2a4ece0e9e54ec"),
                ),
                "",
                "",
            )

        val taggedUsers =
            listOf(
                PTag("534780e44da7b494485e85cd4cca6af4f6caa1627472432b6f2a4ece0e9e54ec", RelayUrlNormalizer.normalize("wss://banana.com")),
            )

        assertEquals(taggedUsers, note.taggedUsers())

        val expectedReply = MarkedETag("9abbfd9b9ac5ecdab45d14b8bf8d746139ea039e931a1b376d19a239f1946590", null, MarkedETag.MARKER.REPLY)

        // check replies
        assertEquals(expectedReply, note.markedReply())
        assertEquals(null, note.unmarkedReply())
        assertEquals(expectedReply, note.reply())

        val replyTos =
            listOf(
                "9abbfd9b9ac5ecdab45d14b8bf8d746139ea039e931a1b376d19a239f1946590",
            )

        assertEquals(replyTos, note.markedReplyTos())
        assertEquals(emptyList<String>(), note.unmarkedReplyTos())

        assertEquals(expectedReply.eventId, note.replyingTo())
        assertEquals(expectedReply.eventId, note.replyingToAddressOrEvent())

        // check root
        val expectedRoot = MarkedETag("9abbfd9b9ac5ecdab45d14b8bf8d746139ea039e931a1b376d19a239f1946590", null, MarkedETag.MARKER.ROOT, "534780e44da7b494485e85cd4cca6af4f6caa1627472432b6f2a4ece0e9e54ec")

        assertEquals(null, note.markedRoot())
        assertEquals(null, note.unmarkedRoot())
        assertEquals(null, note.root())

        // check quotes
        assertEquals(emptySet<String>(), note.citedUsers())
        assertEquals(emptySet<String>(), note.findCitations())
        assertEquals(replyTos, note.tagsWithoutCitations())
    }
}
