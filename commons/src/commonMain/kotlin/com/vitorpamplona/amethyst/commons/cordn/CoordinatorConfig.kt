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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.cordn.appGroupRef.CordnGroupRef
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One coordinator this account talks to.
 *
 * A coordinator is not a relay and the difference matters to the user: relays
 * are interchangeable and redundant, a coordinator is the single authority for
 * the groups it serves. Losing it loses the ordering; a second one does not
 * mirror the first. So this is a named, first-class thing a user chooses,
 * never a URL buried in settings.
 *
 * [relays] is where the coordinator is *reachable* — the ContextVM kind-25910
 * traffic goes over Nostr, so the coordinator has no address of its own beyond
 * its pubkey (§8.5: the relay sees the traffic pattern, the coordinator never
 * sees an IP).
 */
data class CoordinatorConfig(
    val pubKey: HexKey,
    val relays: List<NormalizedRelayUrl>,
    /** How this account came to know about this coordinator. */
    val origin: Origin = Origin.MANUAL,
    /** What the user calls it. Never a claim — a coordinator cannot prove a name. */
    val label: String? = null,
) {
    init {
        require(pubKey.length == PUBKEY_HEX_LENGTH) { "a coordinator pubkey is 32 bytes of hex" }
        require(relays.isNotEmpty()) { "a coordinator with no relays cannot be reached" }
    }

    /** Where a coordinator came from, because it changes how much to trust it. */
    enum class Origin {
        /** Typed or pasted by the user. */
        MANUAL,

        /** Read out of a `cordn1…` group ref someone shared. */
        GROUP_REF,

        /** The application default. */
        DEFAULT,
    }

    companion object {
        private const val PUBKEY_HEX_LENGTH = 64

        /**
         * The coordinator a shared `cordn1…` ref points at, or null when the
         * ref carries only a `gid`.
         *
         * A ref without a coordinator is not broken — §2 makes both optional —
         * it just means the recipient has to already know which coordinator
         * serves that group.
         */
        fun from(ref: CordnGroupRef): CoordinatorConfig? {
            val pubKey = ref.coordinatorPubKey ?: return null
            val relays = ref.relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
            if (relays.isEmpty()) return null
            return CoordinatorConfig(pubKey, relays, Origin.GROUP_REF)
        }
    }
}

/**
 * Whether a coordinator is answering, kept per coordinator.
 *
 * Deliberately thin. This is not a health *check* — nothing here polls, because
 * a poll is a call, and every call to a coordinator is metadata (§8). It
 * records what the calls the app was making anyway have observed.
 */
class CoordinatorHealth {
    private val _state = MutableStateFlow(State())

    val state: StateFlow<State> = _state.asStateFlow()

    data class State(
        val lastSuccessAt: Long? = null,
        val lastFailureAt: Long? = null,
        val lastFailure: String? = null,
        /** Failures since the last success. Resets on any success. */
        val consecutiveFailures: Int = 0,
    ) {
        /**
         * Nothing has worked since the last success, repeatedly.
         *
         * A single failure is a network blip and worth no UI at all; the
         * threshold is what separates "retrying" from "tell the user their
         * groups are not syncing".
         */
        val isDown: Boolean get() = consecutiveFailures >= DOWN_AFTER

        /** True before the first call of the session — not the same as down. */
        val isUnknown: Boolean get() = lastSuccessAt == null && lastFailureAt == null
    }

    fun recordSuccess(atSeconds: Long) {
        _state.value = _state.value.copy(lastSuccessAt = atSeconds, consecutiveFailures = 0, lastFailure = null)
    }

    fun recordFailure(
        atSeconds: Long,
        reason: String?,
    ) {
        val previous = _state.value
        _state.value =
            previous.copy(
                lastFailureAt = atSeconds,
                lastFailure = reason,
                consecutiveFailures = previous.consecutiveFailures + 1,
            )
    }

    companion object {
        const val DOWN_AFTER = 3
    }
}
