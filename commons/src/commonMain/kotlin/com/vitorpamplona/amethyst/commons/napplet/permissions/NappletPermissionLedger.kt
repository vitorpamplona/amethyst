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
package com.vitorpamplona.amethyst.commons.napplet.permissions

import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock

/**
 * The per-applet permission ledger: the single place that decides whether a brokered
 * operation may run. It layers two scopes:
 *
 * - **persistent** grants ([GrantState.ALLOW_ALWAYS] / [GrantState.DENY]) from the
 *   injected [NappletPermissionStore], and
 * - **session** grants ([GrantState.ALLOW_SESSION]) held in memory for this instance.
 *
 * A persistent [GrantState.DENY] always wins — a user who blocked a capability is never
 * silently re-prompted or overridden by a session grant. Recording an [GrantState.ALLOW_ONCE]
 * is a no-op (it authorizes only the in-flight request and is applied by the broker, not
 * remembered here).
 */
class NappletPermissionLedger(
    private val store: NappletPermissionStore,
) {
    private val lock = KmpLock()

    // coordinate -> capability -> ALLOW_SESSION (the only state kept here).
    private val session = mutableMapOf<String, MutableMap<NappletCapability, GrantState>>()

    /**
     * The standing decision for ([identity], [capability]) without prompting. A persistent
     * [GrantState.DENY] takes precedence over any session allow.
     */
    suspend fun decide(
        identity: NappletIdentity,
        capability: NappletCapability,
    ): PermissionDecision {
        val persistent = store.load(identity.coordinate)[capability]
        if (persistent == GrantState.DENY) return PermissionDecision.DENY
        if (persistent == GrantState.ALLOW_ALWAYS) return PermissionDecision.ALLOW

        val sessionGrant = lock.withLock { session[identity.coordinate]?.get(capability) }
        if (sessionGrant == GrantState.ALLOW_SESSION) return PermissionDecision.ALLOW

        return PermissionDecision.ASK
    }

    /**
     * Persists or remembers a user's decision. [GrantState.ALLOW_ALWAYS] and
     * [GrantState.DENY] go to the store; [GrantState.ALLOW_SESSION] is kept in memory;
     * [GrantState.ALLOW_ONCE] and [GrantState.ASK] are not recorded.
     */
    suspend fun record(
        identity: NappletIdentity,
        capability: NappletCapability,
        grant: GrantState,
    ) {
        when (grant) {
            GrantState.ALLOW_ALWAYS, GrantState.DENY -> {
                // A new persistent decision supersedes any lingering session grant.
                lock.withLock { session[identity.coordinate]?.remove(capability) }
                store.store(identity.coordinate, capability, grant)
            }
            GrantState.ALLOW_SESSION ->
                lock.withLock {
                    session.getOrPut(identity.coordinate) { mutableMapOf() }[capability] = grant
                }
            GrantState.ALLOW_ONCE, GrantState.ASK -> Unit // transient; not remembered
        }
    }

    /** Forgets all grants for [identity] — both the persisted and the in-session ones. */
    suspend fun revokeAll(identity: NappletIdentity) {
        lock.withLock { session.remove(identity.coordinate) }
        store.clear(identity.coordinate)
    }

    /** Drops only the in-memory session grants (e.g. when the user closes all applets). */
    fun endSession() {
        lock.withLock { session.clear() }
    }
}
