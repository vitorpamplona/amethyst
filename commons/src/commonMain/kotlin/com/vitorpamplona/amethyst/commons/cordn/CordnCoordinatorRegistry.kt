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

import com.vitorpamplona.quartz.cordn.spec00Coordinator.ICoordinator
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Everything one account needs to talk to one coordinator.
 *
 * The three pieces are scoped together because they are scoped the same way:
 * a `gid` (`spec/00.md` §3), a `kp_ref` (§4.2), and a cursor (`spec/02.md` §7)
 * are all only meaningful relative to the coordinator that issued them. Handing
 * a manager the wrong store is not a subtle bug — it is one group's MLS state
 * answering for another's.
 */
interface CordnCoordinatorScope {
    val coordinator: ICoordinator
    val groupStore: CordnGroupStore
    val keyPackageStore: CordnKeyPackageStore

    /** Releases the transport. The stores outlive it; only the wire closes. */
    suspend fun close()
}

/**
 * Opens a [CordnCoordinatorScope] — the seam where the real transport enters.
 *
 * Production supplies relay-backed `CvmTransport` and encrypted-at-rest stores;
 * tests supply the fixture. Nothing above this interface knows which.
 */
fun interface CordnCoordinatorScopeFactory {
    suspend fun open(
        accountPubKey: HexKey,
        config: CoordinatorConfig,
    ): CordnCoordinatorScope
}

/** One account's live connection to one coordinator. */
class CordnSession(
    val config: CoordinatorConfig,
    val manager: CordnGroupManager,
    val keyPackages: CordnKeyPackages,
    val health: CoordinatorHealth,
    private val scope: CordnCoordinatorScope,
) {
    val coordinatorPubKey: HexKey get() = config.pubKey

    /**
     * What this coordinator learns about [gid], answered from everything the
     * session holds rather than everything one object happens to see.
     *
     * The manager alone cannot say whether this account has a KeyPackage
     * published here: it only knows what it published itself, this run, so
     * after a relaunch it would report "no" about a KeyPackage sitting on the
     * coordinator right now. The KeyPackage store knows, and the session is
     * the first place that holds both.
     */
    suspend fun exposure(gid: String): GroupExposure = manager.exposure(gid, publishedKeyPackage = keyPackages.hasPublished())

    internal suspend fun close() = scope.close()
}

/**
 * One [CordnSession] per coordinator, for one account.
 *
 * ## Why this exists rather than a single manager
 *
 * `CordnGroupManager`'s KDoc says a `gid` is unique only within a coordinator.
 * That is not a caching detail — it is the reason a registry has to be keyed by
 * coordinator and can never merge two. Two coordinators can both serve
 * `gid = "abc"`, and they are two unrelated groups with different members,
 * different epochs and different ratchet trees. A flat map from `gid` to
 * manager would let one answer for the other; from the user's side that looks
 * like a group whose history changes when the coordinator it came from does.
 *
 * ## Why it is idempotent, under a lock
 *
 * [session] returns the *same* session for a coordinator it already holds, and
 * serialises concurrent opens. A second `CordnGroupManager` for one coordinator
 * would deserialise its own copy of every `MlsGroup` from the same store; both
 * would then commit against the same epoch and each would save over the other.
 * MLS has no recovery from that — the ratchet tree forks and every message
 * after it fails to decrypt. The UI and the sync loop both reach for a session,
 * so "two callers at once" is the ordinary case, not a race to dismiss.
 *
 * Nothing here is Marmot's, and Marmot has no equivalent to borrow. Marmot
 * groups live on relays, so one `MarmotManager` serves an account outright and
 * there is nothing to key a registry by; a cordn account has one of these per
 * coordinator because the coordinator is what makes its ids mean anything. The
 * two stay separate under the §3.1 rule in
 * `amethyst/plans/2026-09-19-cordn-ui.md`.
 */
class CordnCoordinatorRegistry(
    val accountPubKey: HexKey,
    private val scopes: CordnCoordinatorScopeFactory,
) {
    private val lock = Mutex()
    private val sessions = mutableMapOf<HexKey, CordnSession>()

    private val _coordinators = MutableStateFlow<List<CoordinatorConfig>>(emptyList())

    /** The coordinators this account currently has a session for. */
    val coordinators: StateFlow<List<CoordinatorConfig>> = _coordinators.asStateFlow()

    /**
     * The session for [config], opening one if this account has none.
     *
     * Idempotent by coordinator pubkey: the same call twice gives the same
     * session, never a second manager over the same store.
     *
     * A [config] that differs from the open one reopens the transport, because
     * the transport is bound to the relays — but the same stores come back, so
     * the groups and published KeyPackages survive. Correcting a relay or
     * renaming a coordinator has not made it a different coordinator.
     */
    suspend fun session(config: CoordinatorConfig): CordnSession =
        lock.withLock {
            sessions[config.pubKey]?.let { existing ->
                if (existing.config != config) replaceConfig(existing, config) else existing
            } ?: open(config).also { publish() }
        }

    /** The session for [coordinatorPubKey], or null if none is open. */
    fun sessionOrNull(coordinatorPubKey: HexKey): CordnSession? = sessions[coordinatorPubKey]

    /**
     * Restores sessions for [configs] and closes any coordinator not in it.
     *
     * The closing half is what makes this a restore rather than a bulk add: a
     * coordinator the user removed on another launch must not come back to life
     * because its session happened to still be open.
     */
    suspend fun restore(configs: List<CoordinatorConfig>) {
        configs.forEach { session(it) }
        lock.withLock {
            val keep = configs.map { it.pubKey }.toSet()
            sessions.keys.filter { it !in keep }.forEach { sessions.remove(it)?.close() }
            publish()
        }
    }

    /**
     * Closes the session for [coordinatorPubKey] and forgets it.
     *
     * The stores are **not** wiped. Forgetting a coordinator is not the same as
     * leaving its groups: cordn-web separates removing a coordinator from
     * purging it (`CoordinatorPurgeDialog`), and quietly destroying group state
     * here would make an undo impossible. A purge is a store-level operation and
     * belongs to whoever owns the stores.
     */
    suspend fun forget(coordinatorPubKey: HexKey) {
        lock.withLock {
            sessions.remove(coordinatorPubKey)?.close()
            publish()
        }
    }

    /** Closes every session. Call when the account logs out. */
    suspend fun close() {
        lock.withLock {
            sessions.values.forEach { it.close() }
            sessions.clear()
            publish()
        }
    }

    private suspend fun replaceConfig(
        existing: CordnSession,
        config: CoordinatorConfig,
    ): CordnSession {
        // Same coordinator, different address or label. The transport is bound
        // to the relays, so it is reopened; the stores are not, so the groups
        // and published KeyPackages survive untouched.
        existing.close()
        sessions.remove(config.pubKey)
        return open(config).also { publish() }
    }

    private suspend fun open(config: CoordinatorConfig): CordnSession {
        val scope = scopes.open(accountPubKey, config)
        val health = CoordinatorHealth()
        val session =
            CordnSession(
                config = config,
                manager =
                    CordnGroupManager(
                        accountPubKey = accountPubKey,
                        config = config,
                        coordinator = scope.coordinator,
                        store = scope.groupStore,
                        health = health,
                    ),
                keyPackages = CordnKeyPackages(accountPubKey, scope.coordinator, scope.keyPackageStore),
                health = health,
                scope = scope,
            )
        // Restored here rather than left to the caller. A session that does not
        // know its own groups is not a usable session — it reports none, and
        // the first commit it makes for a group it forgot about starts from
        // epoch zero against a coordinator that is not there. Forgetting to
        // call this would look like "my groups are gone", which is also what a
        // real failure looks like.
        session.manager.restore()
        session.keyPackages.restore()

        sessions[config.pubKey] = session
        return session
    }

    private fun publish() {
        _coordinators.value = sessions.values.map { it.config }
    }
}
