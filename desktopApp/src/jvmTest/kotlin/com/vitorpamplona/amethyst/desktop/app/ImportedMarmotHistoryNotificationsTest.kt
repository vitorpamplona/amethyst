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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.InMemoryGeohashIdentityStore
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.marmot.MarmotGroupNotifier
import com.vitorpamplona.amethyst.commons.model.marmot.isReadMarmotMessage
import com.vitorpamplona.amethyst.commons.model.marmot.newestNotificationNews
import com.vitorpamplona.amethyst.commons.model.marmotGroupLastReadRoute
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotChatSetting
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46ConsentPrompter
import com.vitorpamplona.amethyst.commons.notifications.MessageSetCard
import com.vitorpamplona.amethyst.commons.notifications.dal.NotificationFeedFilter
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.CashuMintDirectoryFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.nip47WalletConnect.NWCPaymentFilterAssembler
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.marmot.features.deviceGroup.MarmotDeviceGroupManager
import com.vitorpamplona.quartz.marmot.mip02Welcome.WelcomeEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip03Timestamp.EmptyOtsResolverBuilder
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.nip60Cashu.mintApi.OkHttpMintTransport
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import java.util.Collections
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * History a linked device delivers to this one (`account-sync.md` "History deliveries") stays in
 * Notifications, dated by each message's own time as if it had arrived then, and is no news: no
 * system notification (Marmot notifier, desktop toast), no unread badge, no unread chat.
 */
class ImportedMarmotHistoryNotificationsTest {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val me = KeyPair()
    private val friend = KeyPair()
    private val client = NostrClient(BasicOkHttpWebSocket.Builder(httpClient = { OkHttpClient() }, canDial = { false }), scope)

    /** Every system notification the Marmot path asked for. */
    private val notified = ArrayList<String>()

    /** Every chat whose shown notifications the Marmot path asked to take down. */
    private val cleared: MutableList<String> = Collections.synchronizedList(ArrayList())

    /** The system shade: what each chat still shows. */
    private val shade: MutableMap<String, MutableSet<String>> = Collections.synchronizedMap(HashMap())

    /** When set, a take-down waits for it after taking the chat's notifications down: a slow clear. */
    @Volatile private var clearGate: CompletableDeferred<Unit>? = null

    private val account =
        Account(
            settings = AccountSettings(keyPair = me),
            signer = NostrSignerInternal(me),
            geolocationFlow = { MutableStateFlow<LocationResult>(LocationResult.LackPermission) },
            nwcFilterAssembler = { NWCPaymentFilterAssembler(client) },
            cashuMintDirectoryFilterAssembler = { CashuMintDirectoryFilterAssembler(client) },
            cashuMintTransport = OkHttpMintTransport { OkHttpClient() },
            otsResolverBuilder = { EmptyOtsResolverBuilder.build() },
            cache = LocalCache,
            client = client,
            scope = scope,
            appVersion = "test",
            encryptionKeyCache = EncryptionKeyCache(),
            saveSettings = {},
            marmotNotifier = {
                object : MarmotGroupNotifier {
                    override suspend fun notifyWelcome(
                        event: WelcomeEvent,
                        account: Account,
                    ) {
                        notified.add(event.id)
                    }

                    override suspend fun notifyGroupMessage(
                        innerEvent: Event,
                        nostrGroupId: String,
                        account: Account,
                    ) {
                        notified.add(innerEvent.id)
                        shade.getOrPut(nostrGroupId) { Collections.synchronizedSet(HashSet()) }.add(innerEvent.id)
                    }

                    override suspend fun clearGroupNotifications(
                        nostrGroupId: String,
                        account: Account,
                    ) {
                        cleared.add(nostrGroupId)
                        shade.remove(nostrGroupId)
                        clearGate?.await()
                    }
                }
            },
            nip46Consent = Nip46ConsentPrompter.Unanswered,
            geohashIdentityStore = InMemoryGeohashIdentityStore(),
        )

    @AfterTest
    fun tearDown() = scope.cancel()

    private val chat = Random.nextBytes(32).toHexKey()

    private fun message(
        createdAt: Long,
        text: String,
    ): Event = RumorAssembler.assembleRumor(friend.pubKey.toHexKey(), EventTemplate<Event>(createdAt, 9, emptyArray(), "$text ${Random.nextLong()}"))

    /** A message that arrived live, as the decrypting side indexes one. */
    private fun live(event: Event): Note {
        val note = account.marmot.indexMarmotGroupInnerEvent(chat, event).note
        account.marmotGroupList.addMessage(chat, note)
        return note
    }

    @Test
    fun importedHistoryIsListedByItsOwnDateAndIsNoNews() =
        runBlocking {
            val now = TimeUtils.now()
            account.marmotGroupList.getOrCreateGroup(chat)
            // What the user read here before, and a message that arrived live and is still unread.
            account.markAsRead(marmotGroupLastReadRoute(chat), now - 5_000)
            val unread = live(message(now - 1_000, "live"))

            // A linked device's history: one message older than everything, one newer than the live one.
            val old = message(now - 3_000, "old")
            val recent = message(now - 60, "recent")
            account.marmot.indexImportedMarmotHistory(
                listOf(MarmotDeviceGroupManager.ImportedHistory(chat, listOf(old, recent), mapOf(old.id to 1L, recent.id to 2L))),
            )

            assertTrue(notified.isEmpty(), "importing raises no system notification")
            assertEquals(now - 60, account.loadLastRead(marmotGroupLastReadRoute(chat)), "the chat reads the import as read")

            // Notifications lists every one, by each message's own date.
            val feed = NotificationFeedFilter(account).feed().filter { it.idHex in setOf(unread.idHex, old.id, recent.id) }
            assertEquals(listOf(recent.id, unread.idHex, old.id), feed.map { it.idHex })

            // None of them is news now: the live one was read with the import's newer time.
            val notifies = DesktopNotifications.notifiesAbout(account)
            for (note in feed) {
                assertTrue(account.isReadMarmotMessage(note))
                assertFalse(notifies(note), "no desktop toast for ${note.idHex.take(8)}")
            }
            assertEquals(null, account.newestNotificationNews(feed.map { MessageSetCard(it) }), "no Notifications badge")

            // A message that arrives live afterwards is news again.
            val later = live(message(now + 5, "later"))
            assertFalse(account.isReadMarmotMessage(later))
            assertTrue(notifies(later))
            assertEquals(later.idHex, (account.newestNotificationNews((listOf(later) + feed).map { MessageSetCard(it) }) as MessageSetCard).note.idHex)
        }

    @Test
    fun mutingAChatTakesDownItsShownNotifications() =
        runBlocking {
            account.marmotGroupList.getOrCreateGroup(chat)
            account.marmot.setMarmotChatSetting(chat, MarmotChatSetting.MUTE, true)
            withTimeout(5_000) { while (chat !in cleared) delay(20) }
            // Unmuting and muting again clears again; a chat that stays muted is not cleared twice.
            account.marmot.setMarmotChatSetting(chat, MarmotChatSetting.MUTE, false)
            account.marmot.setMarmotChatSetting(chat, MarmotChatSetting.MUTE, true)
            withTimeout(5_000) { while (cleared.count { it == chat } < 2) delay(20) }
            account.marmot.setMarmotChatSetting(chat, MarmotChatSetting.PIN, true)
            delay(300)
            assertEquals(2, cleared.count { it == chat })
        }

    /**
     * A chat muted again while the take-down of its last mute is still running is taken down
     * again: the settings flow is conflated, and the state after unmute + mute equals the one the
     * take-down started from, so watching that flow alone saw no new mute and left the notification
     * shown in between in the shade.
     */
    @Test
    fun aChatMutedAgainWhileTheLastTakeDownRunsIsTakenDownAgain() =
        runBlocking {
            account.marmotGroupList.getOrCreateGroup(chat)
            val gate = CompletableDeferred<Unit>()
            clearGate = gate
            account.marmot.setMarmotChatSetting(chat, MarmotChatSetting.MUTE, true)
            withTimeout(5_000) { while (chat !in cleared) delay(20) }

            // While that take-down still runs: unmuted, a message is shown, muted again.
            account.marmot.setMarmotChatSetting(chat, MarmotChatSetting.MUTE, false)
            val shown = message(TimeUtils.now(), "between")
            account.marmot.notifyMarmotGroupMessage(chat, shown)
            assertEquals(setOf(shown.id), shade[chat]?.toSet(), "an unmuted chat notifies")
            account.marmot.setMarmotChatSetting(chat, MarmotChatSetting.MUTE, true)
            clearGate = null
            gate.complete(Unit)

            withTimeout(5_000) { while (cleared.count { it == chat } < 2) delay(20) }
            assertEquals(null, shade[chat], "the muted chat leaves nothing in the shade")
        }

    /**
     * A mute with an end (a linked device's "mute for a while") that ran out is no mute: messages
     * notify again, and muting the chat again takes those notifications down.
     */
    @Test
    fun aMuteThatRanOutAndIsSetAgainTakesDownWhatWasShownMeanwhile() =
        runBlocking {
            account.marmotGroupList.getOrCreateGroup(chat)
            account.marmotChatSettings.applyRemote(chat, MarmotChatSetting.MUTE, (TimeUtils.now() + 2).toString())
            withTimeout(5_000) { while (chat !in cleared) delay(20) }
            withTimeout(5_000) {
                while (account.marmotChatSettings.state.value
                        .isMuted(chat)
                ) {
                    delay(50)
                }
            }

            val shown = message(TimeUtils.now(), "after the mute ran out")
            account.marmot.notifyMarmotGroupMessage(chat, shown)
            assertEquals(setOf(shown.id), shade[chat]?.toSet(), "a mute that ran out notifies again")

            account.marmot.setMarmotChatSetting(chat, MarmotChatSetting.MUTE, true)
            withTimeout(5_000) { while (cleared.count { it == chat } < 2) delay(20) }
            assertEquals(null, shade[chat], "the muted chat leaves nothing in the shade")
        }
}
