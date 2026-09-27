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

import com.vitorpamplona.quartz.cordn.groups.CordnCredential
import com.vitorpamplona.quartz.cordn.groups.CordnGroupPolicy
import com.vitorpamplona.quartz.cordn.spec00Coordinator.AvailableKeyPackage
import com.vitorpamplona.quartz.cordn.spec00Coordinator.ICoordinator
import com.vitorpamplona.quartz.mls.group.MlsGroup
import com.vitorpamplona.quartz.mls.messages.KeyPackageBundle
import com.vitorpamplona.quartz.mls.messages.KeyPackageBundleCodec
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The private halves of the KeyPackages this account has published.
 *
 * **Implementations MUST encrypt at rest.** A bundle is key material: whoever
 * holds it can open the Welcome that admits its owner to a group.
 *
 * Separate from Marmot's `KeyPackageBundleStore` and not a refactor of it. That
 * one persists a single snapshot carrying Marmot's rotation state alongside the
 * bundles; cordn has no rotation state, no kind-443 event, and no publish
 * obligations — its KeyPackages live entirely in coordinator calls
 * (`spec/00.md` §4.2). The shapes differ because the protocols do.
 */
interface CordnKeyPackageStore {
    suspend fun save(
        keyPackageRef: String,
        bundle: ByteArray,
    )

    suspend fun load(keyPackageRef: String): ByteArray?

    suspend fun delete(keyPackageRef: String)

    /** Every ref with a stored private half. */
    suspend fun list(): List<String>
}

/** In-memory [CordnKeyPackageStore]. Tests, and nothing else. */
class InMemoryCordnKeyPackageStore : CordnKeyPackageStore {
    private val bundles = mutableMapOf<String, ByteArray>()

    override suspend fun save(
        keyPackageRef: String,
        bundle: ByteArray,
    ) {
        bundles[keyPackageRef] = bundle
    }

    override suspend fun load(keyPackageRef: String): ByteArray? = bundles[keyPackageRef]

    override suspend fun delete(keyPackageRef: String) {
        bundles.remove(keyPackageRef)
    }

    override suspend fun list(): List<String> = bundles.keys.toList()
}

/**
 * Publishing KeyPackages so other people can add this account to groups.
 *
 * ## Why this is not Marmot's rotation manager with the names changed
 *
 * Marmot publishes a KeyPackage as a kind-443 event to relays: a real Nostr
 * event, replaceable, discoverable, with its own relay list and its own publish
 * obligations. cordn has **no KeyPackage event kind at all** (`spec/00.md`
 * §4.2). Publishing is a `kp_publish` call, and the "signed publication
 * payload" §7 requires is the ContextVM request event the transport happens to
 * sign — which the coordinator stores and serves back verbatim. There is no
 * relay copy, nothing to rotate against, and nothing to re-publish if a relay
 * forgets. None of Marmot's machinery transfers; all of it would be wrong here.
 *
 * ## `kp_ref` is the RFC 9420 KeyPackageRef and nothing else
 *
 * It is the coordinator's primary key for a KeyPackage and the address a
 * Welcome is delivered to. Two implementations that compute it differently do
 * not fail loudly: every `kp_take` misses, every Welcome lands at an address
 * nobody is listening on, and the group simply never forms. This class owns the
 * computation so no caller invents one — [MlsKeyPackage.reference] is checked
 * against ts-mls's in `CordnLifecycleInteropTest`.
 */
@OptIn(ExperimentalEncodingApi::class)
class CordnKeyPackages(
    private val accountPubKey: HexKey,
    private val coordinator: ICoordinator,
    private val store: CordnKeyPackageStore,
) {
    private val _published = MutableStateFlow<Set<String>>(emptySet())

    /** Refs this account has published and still holds the private half for. */
    val published: StateFlow<Set<String>> = _published.asStateFlow()

    /** One published KeyPackage, as this account sees it. */
    data class Published(
        val keyPackageRef: String,
        val lastResort: Boolean,
        val at: Long,
    )

    private val snapshotLock = Mutex()
    private var snapshot: Snapshot? = null

    /**
     * What `kp_list` reported, the last time we asked.
     *
     * `kp_list` takes no arguments and returns every KeyPackage the coordinator
     * holds for **every** identity (`spec/00.md` leaves retrieval scoping to the
     * coordinator; the reference implementation answers with the whole table).
     * So one call answers a question about anybody, and asking it once per
     * person would be the same download repeated.
     *
     * [identities] keeps only pubkeys, not the entries. Another account's
     * `kp_ref` is useless to us -- a Welcome is addressed to a ref we obtained
     * by *taking* the package, never to one we read out of a listing -- and on
     * a busy coordinator those refs are the bulk of the response. Dropping them
     * bounds what we retain even though nothing bounds what arrives.
     */
    class Snapshot(
        /** Every identity the coordinator holds at least one KeyPackage for. */
        val identities: Set<HexKey>,
        /** The full entries for this account, which are the ones we act on. */
        val mine: List<AvailableKeyPackage>,
        /** When this was fetched, for [snapshot]'s age check. */
        val at: Long,
    )

    /**
     * Refetches unconditionally and replaces the snapshot.
     *
     * Every decision about whether to *publish* reads through here rather than
     * through [snapshot]. Minting against a stale listing is the one place
     * staleness does damage: the coordinator keeps a single last-resort package
     * per identity, so publishing a second silently evicts the first and
     * strands every invite that referenced it.
     */
    suspend fun refresh(): Snapshot = snapshotLock.withLock { fetchLocked() }

    /**
     * The snapshot, refetched if it is older than [maxAgeSeconds].
     *
     * For reads that only *describe* what the coordinator holds. A minute of
     * staleness costs at most a missing badge for somebody who published within
     * it, and the act itself still fails loudly at the coordinator if the
     * listing was wrong -- whereas every refresh is another full-table
     * download, so asking less often is the cheap side of the trade.
     */
    suspend fun snapshot(maxAgeSeconds: Long = SNAPSHOT_TTL_SECONDS): Snapshot =
        snapshotLock.withLock {
            snapshot?.takeIf { TimeUtils.now() - it.at < maxAgeSeconds } ?: fetchLocked()
        }

    /**
     * Which identities the coordinator can deliver a Welcome to, or null.
     *
     * Null means **we do not know** -- the coordinator is unreachable, throttled
     * us, or answered with more than the transport admits. It does not mean
     * nobody has published. Callers must keep those apart: rendering a failed
     * lookup as "this person cannot be added" states something about a person
     * on the strength of a network error.
     */
    suspend fun identitiesWithKeyPackages(): Set<HexKey>? =
        try {
            snapshot().identities
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    /** Must hold [snapshotLock]. */
    private suspend fun fetchLocked(): Snapshot {
        val all = coordinator.listKeyPackages()
        return Snapshot(
            identities = all.mapTo(mutableSetOf()) { it.pubKey },
            mine = all.filter { it.pubKey == accountPubKey },
            at = TimeUtils.now(),
        ).also { snapshot = it }
    }

    /** Forgets the snapshot after we changed what the coordinator holds. */
    private suspend fun invalidate() = snapshotLock.withLock { snapshot = null }

    /** Reloads which refs we hold private halves for. Call at startup. */
    suspend fun restore() {
        _published.value = store.list().toSet()
    }

    /**
     * Whether this account has a KeyPackage published on this coordinator.
     *
     * Read from the store rather than from [published], so it is right before
     * [restore] has run and right after a relaunch. It answers the §8.4 half
     * of the exposure disclosure, which is about what the coordinator holds —
     * not about what this process has done since it started.
     *
     * A private half with no published counterpart is possible (the publish
     * failed and the store was not cleaned), and reporting that as "published"
     * overstates the exposure by one KeyPackage rather than understating it by
     * all of them — the direction a privacy surface should err in.
     */
    suspend fun hasPublished(): Boolean = store.list().isNotEmpty()

    /**
     * What the coordinator currently serves for this account.
     *
     * The coordinator's answer, not ours. `kp_take` consumes a single-use
     * package, so our own record of what we published drifts upward from what
     * is actually available the moment anyone invites us — and it is what is
     * available that decides whether the next invitation can happen.
     */
    suspend fun listPublished(): List<AvailableKeyPackage> = refresh().mine

    /**
     * Generates a KeyPackage, keeps its private half, and publishes it.
     *
     * The private half is stored **before** the call, not after: a publish that
     * succeeds on the coordinator and then fails locally would leave a
     * KeyPackage other people can use to invite an account that can no longer
     * open the Welcome. Storing first makes the failure the harmless direction —
     * an unused bundle on disk.
     *
     * @param lastResort marks it reusable. `spec/00.md` lets a last-resort
     *   package back several Welcomes, which is why a Welcome record is keyed
     *   by `(kp_ref, at)` rather than by ref alone.
     */
    suspend fun publishNew(lastResort: Boolean = false): Published {
        val bundle = generate(lastResort)
        val ref = refOf(bundle)

        store.save(ref, KeyPackageBundleCodec.encode(bundle))
        _published.value = _published.value + ref

        val result =
            try {
                coordinator.publishKeyPackage(ref, Base64.encode(bundle.keyPackage.toTlsBytes()))
            } catch (e: Exception) {
                // The coordinator never took it, so nobody can invite us with
                // it. Drop the local half rather than accumulate bundles that
                // will never be used.
                store.delete(ref)
                _published.value = _published.value - ref
                throw e
            }

        invalidate()
        return Published(result.keyPackageRef, result.lastResort || lastResort, result.at)
    }

    /**
     * The private half for [keyPackageRef], if this device published it.
     *
     * Hand this to [CordnGroupManager.joinPendingWelcomes]. Null is ordinary and
     * not an error: a Welcome addressed to a KeyPackage another device of the
     * same account published belongs to that device, and draining it here would
     * destroy it.
     */
    suspend fun bundleFor(keyPackageRef: String): KeyPackageBundle? =
        store.load(keyPackageRef)?.let {
            try {
                KeyPackageBundleCodec.decode(it)
            } catch (e: IllegalArgumentException) {
                // A bundle we cannot read is a bundle we cannot use. Reporting
                // null sends the Welcome back to the inbox, where another
                // device — or a later version of this one — may manage it.
                null
            }
        }

    /**
     * Withdraws [keyPackageRefs] from the coordinator and forgets their halves.
     *
     * Order matters the other way here: the coordinator is told first, because
     * deleting locally while the coordinator still serves the package is the
     * bad direction — somebody invites us and we cannot open the Welcome.
     */
    suspend fun withdraw(keyPackageRefs: List<String>): List<String> {
        if (keyPackageRefs.isEmpty()) return emptyList()
        val removed = coordinator.removeKeyPackages(keyPackageRefs)
        removed.forEach { store.delete(it) }
        _published.value = _published.value - removed.toSet()
        invalidate()
        return removed
    }

    /**
     * Tops up to [minimum] single-use KeyPackages on the coordinator.
     *
     * Counts what the coordinator actually holds for this account rather than
     * what we think we published: `kp_take` consumes a single-use package, so
     * the coordinator's count falls as people invite us and ours does not.
     *
     * @return the refs published by this call.
     */
    suspend fun topUp(minimum: Int = DEFAULT_POOL): List<String> {
        require(minimum >= 0) { "a KeyPackage pool cannot be negative" }
        val theirs = refresh().mine.filter { !it.lastResort }
        val missing = minimum - theirs.size
        if (missing <= 0) return emptyList()
        return List(missing) { publishNew().keyPackageRef }
    }

    /**
     * Ensures exactly one last-resort KeyPackage is published.
     *
     * Its job is that an invite can always be made, even after the single-use
     * pool is drained. More than one is not better — each is an extra reusable
     * package, and `spec/00.md` treats a last-resort package as the fallback,
     * not the norm.
     */
    suspend fun ensureLastResort(): String? {
        val existing = refresh().mine.filter { it.lastResort }
        // Only one, and only one we can still open: a last-resort package whose
        // private half this device never had is useless to it.
        val usable = existing.firstOrNull { store.load(it.keyPackageRef) != null }
        if (usable != null) return usable.keyPackageRef
        return publishNew(lastResort = true).keyPackageRef
    }

    /** A fresh bundle carrying this account's cordn credential. */
    private fun generate(lastResort: Boolean): KeyPackageBundle {
        // A scratch group only to reach the engine's KeyPackage builder; it is
        // never joined, committed to, or stored.
        val identity = CordnCredential.of(accountPubKey).identity
        val scratch = MlsGroup.create(identity, policy = CordnGroupPolicy)
        return scratch.createKeyPackage(
            identity = identity,
            // Ignored by the engine, which generates the leaf signature keypair
            // itself and signs with that. Every caller in the tree passes empty
            // for the same reason; it is a dead parameter, not a key we are
            // failing to supply.
            signingKey = ByteArray(0),
            keyPackageExtensions = if (lastResort) listOf(CordnGroupPolicy.lastResortExtension()) else emptyList(),
            // A last-resort package carries its marker inside app_data_dictionary
            // (0x0006), so its leaf has to say it understands that carrier.
            // CordnGroupPolicy has had the right set for this all along and
            // nothing was calling it, which left every last-resort KeyPackage
            // advertising one type short of what it used.
            capabilities =
                if (lastResort) CordnGroupPolicy.lastResortLeafCapabilities() else CordnGroupPolicy.defaultLeafCapabilities,
        )
    }

    /** The RFC 9420 KeyPackageRef, hex. See the class KDoc. */
    fun refOf(bundle: KeyPackageBundle): String = bundle.keyPackage.reference().toHexKey()

    companion object {
        /**
         * How many single-use KeyPackages to keep available.
         *
         * Each one is one invite somebody can make without falling back to the
         * reusable package. Small because they cost a round trip each and the
         * last-resort package is the safety net.
         */
        const val DEFAULT_POOL = 5

        /**
         * How long a [Snapshot] may be reused by [snapshot].
         *
         * Bounded by what a refresh costs rather than by how fast the answer
         * changes: `kp_list` is unpaginated, so each one downloads the
         * coordinator's whole table. A minute is long enough that opening a
         * screen repeatedly costs one call and short enough that somebody who
         * has just published becomes visible while you are still looking.
         */
        const val SNAPSHOT_TTL_SECONDS = 60L
    }
}
