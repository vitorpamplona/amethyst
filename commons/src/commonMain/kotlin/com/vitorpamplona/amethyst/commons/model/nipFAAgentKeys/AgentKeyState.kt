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
package com.vitorpamplona.amethyst.commons.model.nipFAAgentKeys

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEventCache
import com.vitorpamplona.quartz.nipFAAgentKeys.AgentKeyEvent
import com.vitorpamplona.quartz.nipFAAgentKeys.agentKeys
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * The user's AI agent keys (kind 12000), decrypted, first one preferred. Every mutation
 * returns a signed event; publishing it is the caller's job (`Account.addAgentKey` & co).
 */
class AgentKeyState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
) {
    private val decryptionCache = PrivateTagArrayEventCache<AgentKeyEvent>(signer)

    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val agentKeyNote = cache.getOrCreateAddressableNote(AgentKeyEvent.createAddress(signer.pubKey))

    fun getAgentKeyEvent(): AgentKeyEvent? = agentKeyNote.event as? AgentKeyEvent

    private suspend fun decrypt(event: AgentKeyEvent?): List<AgentKeyTag> = event?.let { decryptionCache.privateTags(it)?.agentKeys() } ?: emptyList()

    /** Every key, in the user's order of preference: the first one is what apps use. */
    val flow: StateFlow<List<AgentKeyTag>> =
        agentKeyNote
            .flow()
            .metadata.stateFlow
            .map { decrypt(it.note.event as? AgentKeyEvent) }
            .onStart { emit(decrypt(getAgentKeyEvent())) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** The key apps use, or null when the user hasn't set one. */
    fun preferred(): AgentKeyTag? = flow.value.firstOrNull()

    /** Adds [key], or moves it, to the front: a key the user just added is the one they mean to use. */
    suspend fun add(key: AgentKeyTag): AgentKeyEvent {
        val earlier = getAgentKeyEvent()
        val template =
            if (earlier != null) {
                AgentKeyEvent.setPreferred(earlier, key, signer)
            } else {
                AgentKeyEvent.build(listOf(key), signer)
            }
        return signer.sign(template)
    }

    suspend fun setPreferred(key: AgentKeyTag): AgentKeyEvent? = getAgentKeyEvent()?.let { signer.sign(AgentKeyEvent.setPreferred(it, key, signer)) }

    suspend fun remove(key: AgentKeyTag): AgentKeyEvent? = getAgentKeyEvent()?.let { signer.sign(AgentKeyEvent.remove(it, key, signer)) }
}

/** No outbox relay answered and nothing is cached: an edit would start from nothing and delete the other keys. */
class AgentKeysUnreadableException : IllegalStateException("Couldn't reach your relays to read your AI keys, so nothing was changed")
