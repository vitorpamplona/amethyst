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
package com.vitorpamplona.amethyst.commons.model.trustedAssertions

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip85TrustedAssertions.TrustProviderListDecryptionCache
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import com.vitorpamplona.amethyst.commons.model.nip85TrustedAssertions.TrustProviderListState as ITrustProviderListState

class TrustProviderListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: TrustProviderListDecryptionCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) : ITrustProviderListState {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val trustProviderListNote = cache.getOrCreateAddressableNote(getTrustProviderListAddress())

    fun getTrustProviderListAddress() = TrustProviderListEvent.createAddress(signer.pubKey)

    fun getTrustProviderListFlow(): StateFlow<NoteState> = trustProviderListNote.flow().metadata.stateFlow

    fun getTrustProviderList(): TrustProviderListEvent? = trustProviderListNote.event as? TrustProviderListEvent

    /**
     * A new kind 10040 in which the user-score entries (`30382:rank` and `30382:followers`)
     * name [providerKey] on [relay], replacing whichever provider they named before. Every
     * other entry, public or private, is kept. [isPrivate] puts the new entries in the
     * NIP-44 encrypted content instead of the public tags.
     */
    suspend fun withScoreProvider(
        providerKey: HexKey,
        relay: NormalizedRelayUrl,
        isPrivate: Boolean,
    ): TrustProviderListEvent {
        val entries = SCORE_SERVICES.map { ServiceProviderTag(it, providerKey, relay).toTagArray() }
        return rewriteScoreEntries(publicEntries = if (isPrivate) emptyList() else entries, privateEntries = if (isPrivate) entries else emptyList())
    }

    /** A new kind 10040 without any user-score entry, or null when there is nothing to remove. */
    suspend fun withoutScoreProvider(): TrustProviderListEvent? {
        val existing = getTrustProviderList() ?: settings.backupTrustProviderList ?: return null
        if (decryptionCache.serviceProviderSet(existing).none { it.service in SCORE_SERVICES }) return null
        return rewriteScoreEntries(emptyList(), emptyList())
    }

    private suspend fun rewriteScoreEntries(
        publicEntries: List<Array<String>>,
        privateEntries: List<Array<String>>,
    ): TrustProviderListEvent {
        val existing = getTrustProviderList() ?: settings.backupTrustProviderList
        val isScoreEntry = { tag: Array<String> -> ServiceProviderTag.parse(tag)?.service in SCORE_SERVICES }

        val publicTags = existing?.tags?.filterNot(isScoreEntry).orEmpty() + publicEntries
        val oldPrivate =
            if (existing == null || existing.content.isBlank()) {
                emptyArray()
            } else {
                // Never drop entries we cannot read: refuse instead.
                existing.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
            }
        val privateTags = oldPrivate.filterNot(isScoreEntry) + privateEntries

        return if (privateTags.isEmpty()) {
            TrustProviderListEvent.resign(content = "", tags = publicTags.toTypedArray(), signer = signer)
        } else {
            TrustProviderListEvent.resign(tags = publicTags.toTypedArray(), privateTags = privateTags.toTypedArray(), signer = signer)
        }
    }

    suspend fun trustProviderListWithBackup(note: Note): Set<ServiceProviderTag> {
        val event = note.event as? TrustProviderListEvent ?: settings.backupTrustProviderList
        return event?.let { decryptionCache.serviceProviderSet(it) } ?: emptySet()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveTrustProviderList: StateFlow<Set<ServiceProviderTag>> =
        getTrustProviderListFlow()
            .transformLatest { noteState ->
                emit(trustProviderListWithBackup(noteState.note))
            }.onStart {
                emit(trustProviderListWithBackup(trustProviderListNote))
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    override val liveUserRankProvider: StateFlow<ServiceProviderTag?> =
        liveTrustProviderList
            .map {
                it.firstOrNull { it.service == ProviderTypes.rank }
            }.onStart {
                emit(
                    liveTrustProviderList.value.firstOrNull {
                        it.service == ProviderTypes.rank
                    },
                )
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                null,
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    override val liveUserFollowerCount: StateFlow<ServiceProviderTag?> =
        liveTrustProviderList
            .map { tagList ->
                tagList.firstOrNull { it.service == ProviderTypes.followerCount }
            }.onStart {
                emit(
                    liveTrustProviderList.value.firstOrNull { it.service == ProviderTypes.followerCount },
                )
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                null,
            )

    init {
        settings.backupTrustProviderList?.let { event ->
            Log.d("AccountRegisterObservers", "Loading saved ephemeral chat list")
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) {
                LocalCache.justConsumeMyOwnEvent(event)
            }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "TrustProviderList Collector Start")
            getTrustProviderListFlow().collect { noteState ->
                Log.d("AccountRegisterObservers") { "TrustProviderList List for ${signer.pubKey}" }
                (noteState.note.event as? TrustProviderListEvent)?.let {
                    settings.updateTrustProviderListTo(it)
                }
            }
        }
    }

    companion object {
        /** The entries that name a user-score provider (what Brainstorm registers). */
        val SCORE_SERVICES = setOf(ProviderTypes.rank, ProviderTypes.followerCount)
    }
}
