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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nipFAAgentKeys.AgentKeyEvent
import com.vitorpamplona.quartz.nipFAAgentKeys.UnreadableAgentKeysException
import com.vitorpamplona.quartz.nipFAAgentKeys.tags.AgentKeyTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What an edit to the AI keys does. */
enum class AgentKeyEditKind { SAVE, MAKE_DEFAULT, REMOVE }

/** Why an edit to the AI keys didn't go through, for the screen to put in words. */
enum class AgentKeyEditFailure {
    /** None of the write relays answered and nothing is known here: nothing was changed. */
    RELAYS_UNREACHABLE,

    /** The signer refused, or couldn't sign or decrypt. Nothing was changed. */
    SIGNER_REFUSED,

    /** Saved on this device, but none of the write relays accepted it. */
    NOT_ACCEPTED,

    /** The saved keys are in a form this app can't rewrite, so they were left alone. */
    UNREADABLE,

    /** Anything else. */
    FAILED,
}

/** The edit on its way, or how the last one ended. */
@Immutable
sealed interface AgentKeyEditState {
    data object Idle : AgentKeyEditState

    /**
     * Reading the relays, signing, then publishing. [savedHere] once the new list is on this
     * device: the screen shows it from then on, while the relays still have to take it.
     */
    data class Busy(
        val kind: AgentKeyEditKind,
        val url: String,
        val savedHere: Boolean = false,
    ) : AgentKeyEditState

    data class Done(
        val kind: AgentKeyEditKind,
        val url: String,
    ) : AgentKeyEditState

    data class Failed(
        val kind: AgentKeyEditKind,
        val url: String,
        val failure: AgentKeyEditFailure,
    ) : AgentKeyEditState

    val isBusy: Boolean get() = this is Busy
}

/**
 * One edit to the AI keys (kind 12000) at a time, on the account's scope so leaving the screen
 * never drops a save halfway, with its progress in [state] instead of fire-and-forget. NIP-FA's
 * read-modify-write: [readBeforeWriting] reads the write relays (seconds), [rewrite] signs the new
 * list, [keepHere] stores it on this device, [publish] sends it and says whether a relay took it.
 * An edit that changes nothing re-sends [current], so a retry after "not accepted" still sends.
 */
class AgentKeyEditor(
    private val scope: CoroutineScope,
    private val readBeforeWriting: suspend () -> Unit,
    private val rewrite: suspend (AgentKeyEditKind, AgentKeyTag) -> AgentKeyEvent?,
    private val current: () -> AgentKeyEvent?,
    private val keepHere: (Event) -> Unit,
    private val publish: suspend (Event) -> Boolean,
) {
    private val _state = MutableStateFlow<AgentKeyEditState>(AgentKeyEditState.Idle)
    val state: StateFlow<AgentKeyEditState> = _state.asStateFlow()

    /** Starts [kind] for [key]; null when another edit is still on its way (nothing started). */
    fun submit(
        kind: AgentKeyEditKind,
        key: AgentKeyTag,
    ): Deferred<AgentKeyEditState>? {
        if (!claim(kind, key)) return null
        return scope.async { runClaimed(kind, key) }
    }

    /** [submit] in the caller's coroutine; null when another edit is on its way. */
    suspend fun run(
        kind: AgentKeyEditKind,
        key: AgentKeyTag,
    ): AgentKeyEditState? {
        if (!claim(kind, key)) return null
        return runClaimed(kind, key)
    }

    /** Forgets how the last edit ended (the screen went away); an edit on its way is kept. */
    fun clearResult() {
        _state.update { if (it.isBusy) it else AgentKeyEditState.Idle }
    }

    private fun claim(
        kind: AgentKeyEditKind,
        key: AgentKeyTag,
    ): Boolean {
        var claimed = false
        _state.update {
            if (it.isBusy) {
                it
            } else {
                claimed = true
                AgentKeyEditState.Busy(kind, key.url)
            }
        }
        return claimed
    }

    private suspend fun runClaimed(
        kind: AgentKeyEditKind,
        key: AgentKeyTag,
    ): AgentKeyEditState {
        val ended =
            try {
                readBeforeWriting()
                val written = rewrite(kind, key)
                if (written != null) keepHere(written)
                _state.value = AgentKeyEditState.Busy(kind, key.url, savedHere = true)
                val toSend = written ?: current()
                if (toSend == null || publish(toSend)) {
                    AgentKeyEditState.Done(kind, key.url)
                } else {
                    AgentKeyEditState.Failed(kind, key.url, AgentKeyEditFailure.NOT_ACCEPTED)
                }
            } catch (e: CancellationException) {
                _state.value = AgentKeyEditState.Idle
                throw e
            } catch (e: AgentKeysUnreadableException) {
                AgentKeyEditState.Failed(kind, key.url, AgentKeyEditFailure.RELAYS_UNREACHABLE)
            } catch (e: SignerExceptions) {
                Log.w(TAG, "The signer didn't save the AI keys", e)
                AgentKeyEditState.Failed(kind, key.url, AgentKeyEditFailure.SIGNER_REFUSED)
            } catch (e: UnreadableAgentKeysException) {
                AgentKeyEditState.Failed(kind, key.url, AgentKeyEditFailure.UNREADABLE)
            } catch (e: Exception) {
                Log.w(TAG, "Could not save the AI keys", e)
                AgentKeyEditState.Failed(kind, key.url, AgentKeyEditFailure.FAILED)
            }
        _state.value = ended
        return ended
    }

    companion object {
        private const val TAG = "AgentKeyEditor"
    }
}
