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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedToggles
import com.vitorpamplona.amethyst.commons.model.privateChats.ChatroomList
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftEventCache
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A DM sent from one phone deletes its draft with a kind-5 signed in the same second as the last
 * auto-save, so another phone gets the draft and its deletion together. The draft is indexed into
 * its chat room only after it is decrypted, which with an external signer takes seconds: a
 * deletion that lands meanwhile finds nothing in the room to remove, and must not be outrun.
 */
@OptIn(ExperimentalUuidApi::class)
class DraftEventHandlerTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val peer = KeyPair().pubKey.toHexKey()

    private val chatroomList = ChatroomList(signer.pubKey)
    private val drafts = DraftEventCache(signer)

    private fun account(admitRunsFirst: () -> Unit = {}): Account {
        val toggles =
            mockk<ChatFeedToggles>().also {
                every { it.admit(any(), any()) } answers {
                    admitRunsFirst()
                    secondArg<() -> Unit>().invoke()
                    true
                }
            }
        return mockk<Account>().also {
            every { it.signer } returns signer
            every { it.draftsDecryptionCache } returns drafts
            every { it.chatroomList } returns chatroomList
            every { it.chatFeedToggles } returns toggles
        }
    }

    private suspend fun savedDraft(): DraftWrapEvent {
        val rumor = RumorAssembler.assembleRumor(signer.pubKey, ChatMessageEvent.build("hello", listOf(PTag(peer))))
        val draft = DraftWrapEvent.create(Uuid.random().toString(), rumor, signer)
        LocalCache.justConsume(draft, null, true)
        return draft
    }

    // What Account.deleteDraftInner signs when the message goes out.
    private suspend fun deletionOf(draft: DraftWrapEvent): DeletionRequestEvent {
        val emptied = DraftWrapEvent.createDeletedEvent(draft.dTag(), signer, createdAt = draft.createdAt)
        return signer.sign(DeletionRequestEvent.build(listOf(emptied), createdAt = draft.createdAt))
    }

    private fun roomMessages() = chatroomList.getOrCreatePrivateChatroom(ChatroomKey(persistentSetOf(peer))).messages

    private fun noteOf(draft: DraftWrapEvent) = LocalCache.getOrCreateAddressableNote(draft.address())

    @Test
    fun aDraftNobodyDeletedShowsInItsRoom() =
        runTest {
            val draft = savedDraft()
            val note = noteOf(draft)

            DraftEventHandler(account(), LocalCache).add(draft, note, note)

            assertTrue(note in roomMessages())
        }

    @Test
    fun aDeletionThatArrivesWhileTheDraftDecryptsKeepsItOutOfTheRoom() =
        runTest {
            val draft = savedDraft()
            val note = noteOf(draft)
            val deletion = deletionOf(draft)

            val handler = DraftEventHandler(account(), LocalCache) { LocalCache.justConsume(deletion, null, true) }
            handler.add(draft, note, note)

            assertFalse(note in roomMessages(), "a deleted draft was put in the chat room")
        }

    @Test
    fun aDeletionThatArrivesJustBeforeTheRoomAddIsUndone() =
        runTest {
            val draft = savedDraft()
            val note = noteOf(draft)
            val deletion = deletionOf(draft)

            DraftEventHandler(account { LocalCache.justConsume(deletion, null, true) }, LocalCache).add(draft, note, note)

            assertFalse(note in roomMessages(), "a deleted draft was put in the chat room")
        }
}
