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
package com.vitorpamplona.amethyst.commons.notifications.dal

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.InMemoryGeohashIdentityStore
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserContext
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.marmot.MarmotGroupNotifier
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46ConsentPrompter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.TopFilter
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.CashuMintDirectoryFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.nip47WalletConnect.NWCPaymentFilterAssembler
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.amethyst.commons.wot.network.TrustNetwork
import com.vitorpamplona.amethyst.commons.wot.network.TrustVerdicts
import com.vitorpamplona.amethyst.commons.wot.pushNotificationsApplyTrust
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip03Timestamp.EmptyOtsResolverBuilder
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip60Cashu.mintApi.OkHttpMintTransport
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkBuilder
import com.vitorpamplona.quartz.nip85TrustedAssertions.users.index.TrustNetworkHeader
import com.vitorpamplona.quartz.utils.TimeUtils
import io.mockk.every
import io.mockk.spyk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The split screen's Everyone tab applies the Web of Trust while its switch is on: a stranger's
 * mention leaves, while someone the trust provider ranks (a friend of a friend) stays. A Global the
 * user picks in the single feed's spinner stays raw, and push follows what the screen shows.
 */
class NotificationEveryoneTrustTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val keyPair = KeyPair()
    private val me = keyPair.pubKey.toHexKey()
    private val providerKey = "a".repeat(64)
    private val friendOfFriend = "b".repeat(64)
    private val lowRanked = "c".repeat(64)
    private val stranger = "d".repeat(64)
    private val minScore = 10

    private val realAccount =
        EmptyNostrClient().let { client ->
            Account(
                settings = AccountSettings(keyPair),
                signer = NostrSignerInternal(keyPair),
                geolocationFlow = { MutableStateFlow<LocationResult>(LocationResult.Loading) },
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
            )
        }

    private fun rankCard(
        subject: String,
        rank: Int,
    ) = Event("f".repeat(64), providerKey, 1000, 30382, arrayOf(arrayOf("d", subject), arrayOf("rank", rank.toString())), "", "0".repeat(128))

    private val activeVerdicts: TrustVerdicts by lazy {
        val builder = TrustNetworkBuilder(providerKey, initialCapacity = 4)
        builder.add(rankCard(friendOfFriend, 50))
        builder.add(rankCard(lowRanked, 2))
        val now = TimeUtils.now()
        val network = TrustNetwork(TrustNetworkHeader(providerKey, "wss://rank.example.com/", now, now, now), builder.build().first)
        TrustVerdicts(network, emptyMap(), minScore, me, emptySet())
    }

    private val account = spyk(realAccount).also { every { it.currentTrustVerdicts() } answers { activeVerdicts } }

    private val everyoneTab = NotificationFeedFilter(account, TopFilter.Global)

    private val userContext = UserContext { addr -> AddressableNote(addr) }

    @AfterTest
    fun tearDown() = scope.cancel()

    private fun mentionOfMeFrom(
        author: String,
        idSuffix: Char,
    ) = Note("e".repeat(63) + idSuffix).also {
        it.event = TextNoteEvent(it.idHex, author, TimeUtils.now(), arrayOf(arrayOf("p", me)), "hi", "0".repeat(128))
        // The feed reads who a note is from off its author, as the cache sets it on load.
        it.author = User(author, userContext)
    }

    private fun NotificationFeedFilter.accepts(note: Note) = acceptableEvent(note, buildFilterParams(account))

    @Test
    fun theEveryoneTabFiltersByTrustByDefault() {
        assertTrue(account.settings.everyoneNotificationsUseTrust.value)
        assertTrue(everyoneTab.appliesTrust())
    }

    @Test
    fun aFriendOfAFriendStaysOnTheEveryoneTab() {
        assertTrue(everyoneTab.accepts(mentionOfMeFrom(friendOfFriend, '1')))
    }

    @Test
    fun aStrangerLeavesTheEveryoneTab() {
        assertFalse(everyoneTab.accepts(mentionOfMeFrom(stranger, '2')))
    }

    @Test
    fun someoneRankedBelowTheMinimumLeavesTheEveryoneTab() {
        assertFalse(everyoneTab.accepts(mentionOfMeFrom(lowRanked, '3')))
    }

    @Test
    fun turningTheSwitchOffBringsStrangersBack() {
        account.settings.changeEveryoneNotificationsUseTrust(false)

        assertFalse(everyoneTab.appliesTrust())
        assertTrue(everyoneTab.accepts(mentionOfMeFrom(stranger, '4')))
    }

    @Test
    fun theSwitchKeepsTheFeedKeySoRelayPagingSurvives() {
        // The filter is local: the relay pages stay valid, and a rebuild comes from the switch's
        // own collector instead of a key change that would reset them.
        val before = everyoneTab.feedKey()
        account.settings.changeEveryoneNotificationsUseTrust(false)

        assertEquals(before, everyoneTab.feedKey())
    }

    @Test
    fun globalPickedInTheSpinnerStaysRaw() {
        account.settings.changeDefaultNotificationFollowList(TopFilter.Global)
        val singleFeed = NotificationFeedFilter(account)

        assertFalse(singleFeed.appliesTrust())
        assertTrue(singleFeed.accepts(mentionOfMeFrom(stranger, '5')))
    }

    @Test
    fun pushFollowsTheEveryoneTabWhenSplit() {
        account.settings.changeDefaultNotificationFollowList(TopFilter.Global)
        account.settings.toggleSplitNotificationsEnabled()
        assertTrue(account.pushNotificationsApplyTrust())

        account.settings.changeEveryoneNotificationsUseTrust(false)
        assertFalse(account.pushNotificationsApplyTrust())
    }

    @Test
    fun pushFollowsTheSpinnerWhenNotSplit() {
        assertFalse(account.settings.splitNotificationsEnabled.value)
        // The Everyone switch plays no part without split: only Curated filters push.
        account.settings.changeEveryoneNotificationsUseTrust(false)
        account.settings.changeDefaultNotificationFollowList(TopFilter.Selected)
        assertTrue(account.pushNotificationsApplyTrust())

        account.settings.changeEveryoneNotificationsUseTrust(true)
        account.settings.changeDefaultNotificationFollowList(TopFilter.Global)
        assertFalse(account.pushNotificationsApplyTrust())
    }
}
