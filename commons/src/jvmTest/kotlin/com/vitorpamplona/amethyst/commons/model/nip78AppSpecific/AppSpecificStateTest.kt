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
package com.vitorpamplona.amethyst.commons.model.nip78AppSpecific

import com.vitorpamplona.amethyst.commons.model.AccountReactionPreferencesInternal
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.AccountSyncedSettingsInternal
import com.vitorpamplona.amethyst.commons.model.DefaultReactionRowItems
import com.vitorpamplona.amethyst.commons.model.ReactionRowAction
import com.vitorpamplona.amethyst.commons.model.ReactionRowItem
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.JsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The synced settings live only in the NIP-78 blob, so the copy in memory starts at the defaults
 * and is only right once that blob has been decrypted. A decrypt that fails at startup (an external
 * signer that is not reachable yet, a bunker still connecting) must not leave the account on the
 * defaults for the rest of the session: the next settings change would publish those defaults
 * over everything the user had set.
 */
class AppSpecificStateTest {
    private val customRow =
        listOf(
            ReactionRowItem(ReactionRowAction.Zap),
            ReactionRowItem(ReactionRowAction.Like, showCounter = false),
            ReactionRowItem(ReactionRowAction.Reply, enabled = false),
            ReactionRowItem(ReactionRowAction.Boost),
            ReactionRowItem(ReactionRowAction.Pay, enabled = false, showCounter = false),
            ReactionRowItem(ReactionRowAction.Share, showCounter = false),
        )

    /**
     * Fails the first [failures] decrypts, the way an unreachable signer does, and holds the
     * first one for [firstDecryptDelayMs], the way a signer that is slow to answer does.
     */
    private class FlakySigner(
        val inner: NostrSigner,
        failures: Int = 0,
        val firstDecryptDelayMs: Long = 0,
    ) : NostrSigner(inner.pubKey) {
        val failuresLeft = AtomicInteger(failures)
        val calls = AtomicInteger(0)

        override fun isWriteable() = inner.isWriteable()

        override suspend fun <T : Event> sign(
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): T = inner.sign(createdAt, kind, tags, content)

        override suspend fun nip04Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = inner.nip04Encrypt(plaintext, toPublicKey)

        override suspend fun nip04Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ) = inner.nip04Decrypt(ciphertext, fromPublicKey)

        override suspend fun nip44Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ) = inner.nip44Encrypt(plaintext, toPublicKey)

        override suspend fun nip44Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ): String {
            if (calls.getAndIncrement() == 0) delay(firstDecryptDelayMs)
            if (failuresLeft.getAndDecrement() > 0) throw IllegalStateException("signer not reachable")
            return inner.nip44Decrypt(ciphertext, fromPublicKey)
        }

        override suspend fun decryptZapEvent(event: ZapRequestEvent): PrivateZapEvent = inner.decryptZapEvent(event)

        override suspend fun deriveKey(nonce: HexKey) = inner.deriveKey(nonce)

        override suspend fun signPsbt(psbtHex: String) = inner.signPsbt(psbtHex)

        override fun hasForegroundSupport() = inner.hasForegroundSupport()
    }

    private suspend fun savedBlob(
        signer: NostrSigner,
        row: List<ReactionRowItem>,
        createdAt: Long,
    ): AppSpecificDataEvent {
        val synced = AccountSyncedSettingsInternal(reactions = AccountReactionPreferencesInternal(reactionRowItems = row))
        return signer.sign(
            AppSpecificDataEvent.build(
                dTag = AppSpecificState.APP_SPECIFIC_DATA_D_TAG,
                description = signer.nip44Encrypt(JsonMapper.toJson(synced), signer.pubKey),
                createdAt = createdAt,
            ),
        )
    }

    private suspend fun awaitRow(
        settings: AccountSettings,
        expected: List<ReactionRowItem>,
    ): List<ReactionRowItem> {
        withTimeoutOrNull(10_000) {
            while (settings.syncedSettings.reactions.reactionRowItems.value != expected) delay(20)
        }
        return settings.syncedSettings.reactions.reactionRowItems.value
    }

    @Test
    fun savedSettingsLoadEvenWhenTheFirstDecryptFails() =
        runBlocking {
            val keyPair = KeyPair()
            val signer = FlakySigner(NostrSignerInternal(keyPair), failures = 2)
            val settings = AccountSettings(keyPair = keyPair)
            settings.backupAppSpecificData = savedBlob(signer.inner, customRow, TimeUtils.now() - 60)
            assertEquals(DefaultReactionRowItems, settings.syncedSettings.reactions.reactionRowItems.value)

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                AppSpecificState(signer, LocalCache, scope, settings)
                assertEquals(customRow, awaitRow(settings, customRow))
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun aSlowStartupDecryptDoesNotRollBackANewerVersion() =
        runBlocking {
            val keyPair = KeyPair()
            val signer = FlakySigner(NostrSignerInternal(keyPair), firstDecryptDelayMs = 1_000)
            val settings = AccountSettings(keyPair = keyPair)
            val older = savedBlob(signer.inner, DefaultReactionRowItems.reversed(), TimeUtils.now() - 120)
            val newer = savedBlob(signer.inner, customRow, TimeUtils.now() - 60)
            settings.backupAppSpecificData = older

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                AppSpecificState(signer, LocalCache, scope, settings)
                // The newer version comes from a relay while the saved one is still decrypting.
                delay(200)
                LocalCache.justConsumeMyOwnEvent(newer)

                assertEquals(customRow, awaitRow(settings, customRow))
                delay(2_000)
                assertEquals(customRow, settings.syncedSettings.reactions.reactionRowItems.value)
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun anEditBeforeTheSavedSettingsLoadKeepsTheRestOfThem() =
        runBlocking {
            val keyPair = KeyPair()
            val signer = FlakySigner(NostrSignerInternal(keyPair), failures = Int.MAX_VALUE)
            val settings = AccountSettings(keyPair = keyPair)
            settings.backupAppSpecificData = savedBlob(signer.inner, customRow, TimeUtils.now() - 60)

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val state = AppSpecificState(signer, LocalCache, scope, settings)
                delay(200)

                // Unreadable yet: memory holds the defaults plus the edit, so nothing may go out.
                settings.changeZapAmounts(listOf(7L, 77L))
                assertFailsWith<IllegalStateException> { state.saveNewAppSpecificData() }

                // Once the signer answers, the edit is published over the saved settings.
                signer.failuresLeft.set(0)
                val published = state.saveNewAppSpecificData()
                val synced = JsonMapper.fromJson<AccountSyncedSettingsInternal>(signer.inner.nip44Decrypt(published.content, published.pubKey))

                assertEquals(customRow, synced.reactions.reactionRowItems)
                assertEquals(listOf(7L, 77L), synced.zaps.zapAmountChoices)
                assertEquals(customRow, settings.syncedSettings.reactions.reactionRowItems.value)
                assertEquals(listOf(7L, 77L), settings.syncedSettings.zaps.zapAmountChoices.value)
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun aPublishWithNothingSavedGoesStraightOut() =
        runBlocking {
            val keyPair = KeyPair()
            val signer = FlakySigner(NostrSignerInternal(keyPair))
            val settings = AccountSettings(keyPair = keyPair)

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val state = AppSpecificState(signer, LocalCache, scope, settings)
                settings.changeReactionRowItems(customRow)
                val published = state.saveNewAppSpecificData()
                val synced = JsonMapper.fromJson<AccountSyncedSettingsInternal>(signer.inner.nip44Decrypt(published.content, published.pubKey))
                assertEquals(customRow, synced.reactions.reactionRowItems)
            } finally {
                scope.cancel()
            }
        }
}
