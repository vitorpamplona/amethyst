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
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46ConsentPrompter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.CashuMintDirectoryFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.nip47WalletConnect.NWCPaymentFilterAssembler
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.marmotquic.QuicAgentTextStreamTransport
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip03Timestamp.EmptyOtsResolverBuilder
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip60Cashu.mintApi.OkHttpMintTransport
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quic.tls.JdkCertificateValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** OS toasts follow the Notifications tab's top-nav filter, as the in-app feed does. */
class DesktopNotificationsFilterTest {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val me = KeyPair()
    private val mySigner = NostrSignerSync(me)
    private val friend = NostrSignerSync(KeyPair())
    private val stranger = NostrSignerSync(KeyPair())
    private val client = NostrClient(BasicOkHttpWebSocket.Builder(httpClient = { OkHttpClient() }, canDial = { false }), scope)

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
            marmotNotifier = { MarmotGroupNotifier.None },
            nip46Consent = Nip46ConsentPrompter.Unanswered,
            geohashIdentityStore = InMemoryGeohashIdentityStore(),
            marmotStreamTransportFactory = { QuicAgentTextStreamTransport(parentScope = it, certificateValidator = JdkCertificateValidator()) },
        )

    private val notifies = DesktopNotifications.notifiesAbout(account)

    init {
        consumed(mySigner, ContactListEvent.KIND, "", arrayOf("p", friend.pubKey))
    }

    @AfterTest
    fun tearDown() = scope.cancel()

    private fun consumed(
        signer: NostrSignerSync,
        kind: Int,
        content: String,
        vararg tags: Array<String>,
    ): Note {
        val event = signer.sign<Event>(EventTemplate(TimeUtils.now(), kind, arrayOf(*tags), content))
        // LocalCache holds notes weakly: take the note before consuming, or a GC before a lookup drops it.
        val note = LocalCache.getOrCreateNote(event.id)
        LocalCache.justConsume(event, null, false)
        assertNotNull(note.event)
        return note
    }

    // Distinct text each time: the cache drops the same text from several authors as spam.
    private fun mentionBy(signer: NostrSignerSync) = consumed(signer, TextNoteEvent.KIND, "${signer.pubKey.take(8)} ${Random.nextLong()} nostr:${me.pubKey.toNpub()}", arrayOf("p", mySigner.pubKey))

    /** Picks [filter] on the Notifications top bar and waits for the account to rebuild its follow set. */
    private fun select(filter: TopFilter) =
        runBlocking {
            account.settings.defaultNotificationFollowList.value = filter
            val toastsStrangers = filter == TopFilter.Global
            withTimeout(10_000) {
                while (true) {
                    val lists = account.liveNotificationFollowLists.value
                    if (lists.matchAuthor(friend.pubKey) && lists.matchAuthor(stranger.pubKey) == toastsStrangers) break
                    delay(20)
                }
            }
        }

    @Test
    fun allFollowsOnlyToastsFollows() {
        select(TopFilter.AllFollows)

        assertTrue(notifies(mentionBy(friend)))
        assertFalse(notifies(mentionBy(stranger)))
    }

    @Test
    fun globalToastsAnyoneWhoTagsTheUser() {
        select(TopFilter.Global)

        assertTrue(notifies(mentionBy(friend)))
        assertTrue(notifies(mentionBy(stranger)))
    }

    @Test
    fun changingTheFilterAppliesToTheNextToast() {
        val strangerMention = mentionBy(stranger)

        select(TopFilter.Global)
        assertTrue(notifies(strangerMention))

        select(TopFilter.AllFollows)
        assertFalse(notifies(strangerMention))
    }
}
