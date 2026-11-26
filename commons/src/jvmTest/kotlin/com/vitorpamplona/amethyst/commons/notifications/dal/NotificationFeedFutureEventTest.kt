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
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip03Timestamp.EmptyOtsResolverBuilder
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip60Cashu.mintApi.OkHttpMintTransport
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An event dated ahead stays off the Notifications tab. On it, it would sit on top until that
 * date, and seeing it would carry the tab's last-read marker there: the badge would stay off and
 * every real notification, in the app and in the tray, would count as read.
 */
class NotificationFeedFutureEventTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val keyPair = KeyPair()
    private val me = keyPair.pubKey.toHexKey()
    private val someoneElse = "b".repeat(64)

    private val account =
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

    // Global: every event that tags me, so the follow check stays out of the way (and with no trust
    // network active, the Everyone tab's Web of Trust filter hides nobody).
    private val filter = NotificationFeedFilter(account, TopFilter.Global)

    @AfterTest
    fun tearDown() = scope.cancel()

    private fun mentionOfMeAt(
        createdAt: Long,
        idSuffix: Char,
    ) = Note("e".repeat(63) + idSuffix).also {
        it.event = TextNoteEvent(it.idHex, someoneElse, createdAt, arrayOf(arrayOf("p", me)), "hi", "0".repeat(128))
    }

    @Test
    fun aMentionFromNowIsANotification() {
        assertTrue(filter.acceptableEvent(mentionOfMeAt(TimeUtils.now(), '1'), filter.buildFilterParams(account)))
    }

    @Test
    fun aMentionFromAClockAFewMinutesFastIsANotification() {
        // Filtered once, a note is not checked again until the tab rebuilds: minutes of skew pass.
        val threeMinutesAhead = TimeUtils.now() + 3 * 60

        assertTrue(filter.acceptableEvent(mentionOfMeAt(threeMinutesAhead, '3'), filter.buildFilterParams(account)))
    }

    @Test
    fun aMentionDatedAYearAheadIsNot() {
        val nextYear = TimeUtils.now() + 365L * 24 * 60 * 60

        assertFalse(filter.acceptableEvent(mentionOfMeAt(nextYear, '2'), filter.buildFilterParams(account)))
    }
}
