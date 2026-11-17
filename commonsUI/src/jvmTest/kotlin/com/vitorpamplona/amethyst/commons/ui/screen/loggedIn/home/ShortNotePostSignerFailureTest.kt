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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.home

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.InMemoryGeohashIdentityStore
import com.vitorpamplona.amethyst.commons.model.UiSettingsFlow
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.location.LocationResult
import com.vitorpamplona.amethyst.commons.model.marmot.MarmotGroupNotifier
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46ConsentPrompter
import com.vitorpamplona.amethyst.commons.notices.PostNotSent
import com.vitorpamplona.amethyst.commons.notices.SignerNotice
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.CashuMintDirectoryFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.nip47WalletConnect.NWCPaymentFilterAssembler
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.RelaySubscriptionsCoordinator
import com.vitorpamplona.amethyst.commons.service.http.EmptyRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.http.EncryptionKeyCache
import com.vitorpamplona.amethyst.commons.service.lnurl.LnurlHttpTransport
import com.vitorpamplona.amethyst.commons.service.pow.PoWJobFailure
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.UnavailableMediaUploader
import com.vitorpamplona.amethyst.commons.state.UiSettingsState
import com.vitorpamplona.amethyst.commons.tor.MoneyOpRelayRouting
import com.vitorpamplona.amethyst.commons.tor.TorRelayEvaluation
import com.vitorpamplona.amethyst.commons.tor.TorRelaySettings
import com.vitorpamplona.amethyst.commons.tor.TorSettingsFlow
import com.vitorpamplona.amethyst.commons.tor.TorType
import com.vitorpamplona.amethyst.commons.ui.components.toasts.NoticeToastMsg
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModelHost
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayOfflineTracker
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.EmptyIAuthStatus
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthSnapshot
import com.vitorpamplona.quartz.nip01Core.relay.client.stats.RelayStats
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip03Timestamp.EmptyOtsResolverBuilder
import com.vitorpamplona.quartz.nip05DnsIdentifiers.EmptyNip05Client
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip60Cashu.mintApi.OkHttpMintTransport
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A NIP-46 bunker that is offline times out on every request, and one that says no refuses it.
 * Either way the post must not vanish: the composer keeps its text, tells the user, and lets
 * Post be pressed again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShortNotePostSignerFailureTest {
    private lateinit var scope: CoroutineScope
    private lateinit var bunker: FlakyBunker
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var postViewModel: ShortNotePostViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val keyPair = KeyPair()
        bunker = FlakyBunker(NostrSignerInternal(keyPair))
        // Writeable like a bunker login, so drafts are saved.
        accountViewModel = accountViewModel(bunker, keyPair, scope)
        postViewModel = ShortNotePostViewModel().also { it.init(accountViewModel) }
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun anOfflineBunkerKeepsThePostAndSaysWhy() =
        runTest {
            bunker.failWith = SignerExceptions.TimedOutException("Could not sign: User didn't accept or reject in time.")
            postViewModel.message.setTextAndPlaceCursorAtEnd("hello from behind a bunker")

            assertTrue(postViewModel.tryStartPosting())
            assertTrue(postViewModel.isPosting)
            assertFalse(postViewModel.tryStartPosting(), "a second press while posting sends nothing")

            assertFalse(postViewModel.sendPostSync())

            assertEquals("hello from behind a bunker", postViewModel.message.text.toString())
            assertFalse(postViewModel.isPosting, "Post must come back so it can be pressed again")
            assertEquals(PostNotSent.Reason.SignerTimedOut, shownNotice().reason)
        }

    @Test
    fun aRefusalKeepsThePostAndSaysWhy() =
        runTest {
            bunker.failWith = SignerExceptions.ManuallyUnauthorizedException("Could not sign: User has rejected the request.")
            postViewModel.message.setTextAndPlaceCursorAtEnd("not this one")

            assertTrue(postViewModel.tryStartPosting())
            assertFalse(postViewModel.sendPostSync())

            assertEquals("not this one", postViewModel.message.text.toString())
            assertEquals(PostNotSent.Reason.SignerRefused, shownNotice().reason)
        }

    @Test
    fun postingAgainOnceTheBunkerIsBackSendsAndClears() =
        runTest {
            bunker.failWith = SignerExceptions.TimedOutException("Could not sign: User didn't accept or reject in time.")
            postViewModel.message.setTextAndPlaceCursorAtEnd("second time lucky")
            assertTrue(postViewModel.tryStartPosting())
            assertFalse(postViewModel.sendPostSync())

            bunker.failWith = null
            assertTrue(postViewModel.tryStartPosting())
            assertTrue(postViewModel.sendPostSync())

            assertEquals("", postViewModel.message.text.toString())
            assertFalse(postViewModel.isPosting)
            assertEquals(1, bunker.signedNotes, "only the retry's note was signed")
        }

    @Test
    fun anEarlierPostsJobEndingLateLeavesTheNextPostRunning() =
        runTest {
            bunker.failWith = SignerExceptions.TimedOutException("Could not sign: User didn't accept or reject in time.")
            postViewModel.message.setTextAndPlaceCursorAtEnd("pressed twice")

            assertTrue(postViewModel.tryStartPosting())
            val first = postViewModel.postingGeneration
            assertFalse(postViewModel.sendPostSync())

            // Post pressed again before the first post's coroutine has quite finished...
            assertTrue(postViewModel.tryStartPosting())
            val second = postViewModel.postingGeneration
            // ...and that coroutine's completion arrives now, while the second post signs.
            postViewModel.postingJobFinished(first)
            assertTrue(postViewModel.isPosting, "the first post's job ended the second post")

            postViewModel.postingJobFinished(second)
            assertFalse(postViewModel.isPosting)
        }

    @Test
    fun aFailedPostSavesTheDraftItSkipped() =
        runTest {
            // Fails the note only, so the draft that follows can still be signed and encrypted.
            bunker.failWith = SignerExceptions.TimedOutException("Could not sign: User didn't accept or reject in time.")
            bunker.onlyKind = TextNoteEvent.KIND
            postViewModel.message.setTextAndPlaceCursorAtEnd("typed just before Post")

            assertTrue(postViewModel.tryStartPosting())
            // The debounced auto-save of the last keystrokes comes due while posting, and is skipped.
            postViewModel.sendDraftSync()
            assertFalse(postViewModel.sendPostSync())

            // The text stays, so the skipped save is asked for again, debounced like any edit.
            val draft = accountViewModel.account.getOrCreateDraftNote(postViewModel.draftTag.current)
            withContext(Dispatchers.Default) {
                withTimeout(10_000) { while (draft.event !is DraftWrapEvent) delay(50) }
            }
            assertEquals("typed just before Post", postViewModel.message.text.toString())
        }

    @Test
    fun aFailedPostWithNothingSkippedSavesNoDraft() =
        runTest {
            bunker.failWith = SignerExceptions.TimedOutException("Could not sign: User didn't accept or reject in time.")
            bunker.onlyKind = TextNoteEvent.KIND
            postViewModel.message.setTextAndPlaceCursorAtEnd("nothing came due")

            assertTrue(postViewModel.tryStartPosting())
            assertFalse(postViewModel.sendPostSync())

            // No auto-save was skipped, so the signer is not asked again: an offline bunker would
            // only time out a second time, and a signer app would prompt right after a refusal.
            // Three times the 1 s debounce a re-save would wait for.
            val draft = accountViewModel.account.getOrCreateDraftNote(postViewModel.draftTag.current)
            val saved =
                withContext(Dispatchers.Default) {
                    withTimeoutOrNull(3_000) { while (draft.event !is DraftWrapEvent) delay(50) }
                }
            assertNull(saved, "no draft may be saved")
        }

    @Test
    fun aSignerFailureWithItsOwnNoticeKeepsThePostToo() =
        runTest {
            bunker.failWith = SignerExceptions.ReadOnlyException()
            postViewModel.message.setTextAndPlaceCursorAtEnd("read-only key")

            assertTrue(postViewModel.tryStartPosting())
            assertFalse(postViewModel.sendPostSync())

            assertEquals("read-only key", postViewModel.message.text.toString())
            assertFalse(postViewModel.isPosting)
            val toast = assertIs<NoticeToastMsg>(accountViewModel.toastManager.toasts.value, "the user must be told")
            assertEquals(SignerNotice.ReadOnly, toast.notice)
        }

    @Test
    fun anUnexpectedFailureStillEndsPostingAndKeepsTheText() =
        runTest {
            bunker.failWith = IllegalStateException("a signer answer that could not be read")
            postViewModel.message.setTextAndPlaceCursorAtEnd("still here")

            assertTrue(postViewModel.tryStartPosting())
            assertFailsWith<IllegalStateException> { postViewModel.sendPostSync() }

            assertEquals("still here", postViewModel.message.text.toString())
            assertFalse(postViewModel.isPosting, "Post must come back so it can be pressed again")
        }

    @Test
    fun theComposerLeavesOnlyWhenThePostWentOut() =
        runTest {
            bunker.failWith = SignerExceptions.TimedOutException("Could not sign: User didn't accept or reject in time.")
            postViewModel.message.setTextAndPlaceCursorAtEnd("leave when sent")
            var left = 0

            assertTrue(postViewModel.tryStartPosting())
            assertFalse(postViewModel.sendPostSync(onSent = { left++ }))
            assertEquals(0, left)

            bunker.failWith = null
            assertTrue(postViewModel.tryStartPosting())
            assertTrue(postViewModel.sendPostSync(onSent = { left++ }))
            assertEquals(1, left)
        }

    private fun shownNotice(): PostNotSent {
        val toast = assertIs<NoticeToastMsg>(accountViewModel.toastManager.toasts.value, "the user must be told")
        return assertIs<PostNotSent>(toast.notice)
    }
}

/** Signs with a local key, unless told to fail the way a NIP-46 bunker does. */
private class FlakyBunker(
    private val inner: NostrSignerInternal,
) : NostrSigner(inner.pubKey) {
    @Volatile var failWith: Exception? = null

    /** When set, only signing this kind fails; everything else answers. */
    @Volatile var onlyKind: Int? = null

    @Volatile var signedNotes = 0

    private fun answer(kind: Int? = null) {
        val only = onlyKind
        if (only != null && kind != only) return
        failWith?.let { throw it }
    }

    override fun isWriteable() = true

    override suspend fun <T : Event> sign(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): T {
        answer(kind)
        return inner.sign<T>(createdAt, kind, tags, content).also { if (kind == TextNoteEvent.KIND) signedNotes++ }
    }

    override suspend fun nip04Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
    ): String {
        answer()
        return inner.nip04Encrypt(plaintext, toPublicKey)
    }

    override suspend fun nip04Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
    ): String {
        answer()
        return inner.nip04Decrypt(ciphertext, fromPublicKey)
    }

    override suspend fun nip44Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
    ): String {
        answer()
        return inner.nip44Encrypt(plaintext, toPublicKey)
    }

    override suspend fun nip44Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
    ): String {
        answer()
        return inner.nip44Decrypt(ciphertext, fromPublicKey)
    }

    override suspend fun decryptZapEvent(event: ZapRequestEvent): PrivateZapEvent = inner.decryptZapEvent(event)

    override suspend fun deriveKey(nonce: HexKey): HexKey = inner.deriveKey(nonce)

    override suspend fun signPsbt(psbtHex: String): String = inner.signPsbt(psbtHex)

    override fun hasForegroundSupport() = false
}

private fun accountViewModel(
    signer: NostrSigner,
    keyPair: KeyPair,
    scope: CoroutineScope,
): AccountViewModel {
    val client = EmptyNostrClient()
    val nwcFilters = NWCPaymentFilterAssembler(client)

    val account =
        Account(
            settings = AccountSettings(keyPair),
            signer = signer,
            geolocationFlow = { MutableStateFlow<LocationResult>(LocationResult.Loading) },
            nwcFilterAssembler = { nwcFilters },
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
            marmotStreamTransportFactory = { error("Tests do not stream") },
        )

    return AccountViewModel(
        account = account,
        settings =
            UiSettingsState(
                uiSettingsFlow = UiSettingsFlow(),
                isMobileOrMeteredConnection = MutableStateFlow(false),
                scope = scope,
            ),
        torSettings = TorSettingsFlow(torType = MutableStateFlow(TorType.OFF)),
        httpClientBuilder = EmptyRoleBasedHttpClientBuilder(),
        dataSources = RelaySubscriptionsCoordinator(LocalCache, client, EmptyIAuthStatus, RelayOfflineTracker(client), scope),
        nip05ClientBuilder = { EmptyNip05Client() },
        host = TestHost,
    )
}

/** Nothing to probe, dismiss or pay from inside a test. */
private object TestHost : AccountViewModelHost {
    override val memoryPressure: Flow<Unit> = emptyFlow()
    override val localBlossomCacheAvailable: Flow<Boolean> = flowOf(false)
    override val powPublishFailures: Flow<PoWJobFailure> = emptyFlow()
    override val relayStats = RelayStats(EmptyNostrClient())
    override val torRelayEvaluation: StateFlow<TorRelayEvaluation> = MutableStateFlow(TorRelayEvaluation(TorRelaySettings()))
    override val relayAuthState: StateFlow<PersistentMap<NormalizedRelayUrl, RelayAuthSnapshot>> = MutableStateFlow(persistentMapOf())
    override val mediaUploader: MediaUploader = UnavailableMediaUploader
    override val websocketBuilder = BasicOkHttpWebSocket.Builder { OkHttpClient() }
    override val lnurlTransport = LnurlHttpTransport { throw IllegalStateException("Tests do not reach the network") }
    override val moneyOpRelays = MoneyOpRelayRouting.None
    override val savedAccounts: Flow<Set<HexKey>> = flowOf(emptySet())

    override fun dismissNotificationFor(eventId: HexKey) = Unit

    override fun openLightningWallet(invoice: String) = false
}
