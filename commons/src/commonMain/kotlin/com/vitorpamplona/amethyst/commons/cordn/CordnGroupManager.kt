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
import com.vitorpamplona.quartz.cordn.groups.CordnCredential
import com.vitorpamplona.quartz.cordn.groups.CordnGroupPolicy
import com.vitorpamplona.quartz.cordn.spec00Coordinator.ICoordinator
import com.vitorpamplona.quartz.cordn.spec00Coordinator.KeyPackagePublication
import com.vitorpamplona.quartz.cordn.spec01GroupMetadata.CordnGroupMetadata
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnApplicationMessage
import com.vitorpamplona.quartz.cordn.spec02Envelopes.CordnEnvelope
import com.vitorpamplona.quartz.cordn.spec02Envelopes.ReceivedMessage
import com.vitorpamplona.quartz.cordn.spec03Payloads.SealedPayload
import com.vitorpamplona.quartz.cordn.sync.CordnGroupSync
import com.vitorpamplona.quartz.cordn.sync.Ingestion
import com.vitorpamplona.quartz.mls.codec.TlsReader
import com.vitorpamplona.quartz.mls.framing.ContentType
import com.vitorpamplona.quartz.mls.framing.MlsMessage
import com.vitorpamplona.quartz.mls.framing.WireFormat
import com.vitorpamplona.quartz.mls.group.MlsGroup
import com.vitorpamplona.quartz.mls.group.MlsGroupState
import com.vitorpamplona.quartz.mls.messages.KeyPackageBundle
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The cordn groups this account holds on one coordinator.
 *
 * The cordn counterpart of `MarmotManager`, and deliberately **not** a reuse of
 * it. Marmot's manager is keyed on the Nostr group id throughout and its whole
 * delivery model — publish kinds 443/444/445 to relays, subscribe by `h` tag,
 * carry publish obligations — has no cordn analogue: here there is one
 * coordinator, one ordered stream per `gid`, and a cursor. What the two share
 * is the RFC 9420 engine underneath, which is what Stage 1 of the interop plan
 * made possible.
 *
 * ## One manager per coordinator
 *
 * Not per account. A `gid` is scoped to the coordinator that issued it
 * (`spec/00.md` §4-5), so the same string can name two unrelated groups on two
 * coordinators, and a cursor from one is meaningless to the other. Keying
 * groups by `gid` alone is only safe because the coordinator is fixed here.
 *
 * It also matches the privacy model: §8.2's ephemeral identity is per session
 * per coordinator, so the set of groups one manager touches is exactly the set
 * that coordinator can link together. [exposure] reports that from the real
 * count rather than from a guess.
 */
@OptIn(ExperimentalEncodingApi::class)
class CordnGroupManager(
    /** The account these groups belong to, as lowercase hex. */
    val accountPubKey: HexKey,
    val config: CoordinatorConfig,
    private val coordinator: ICoordinator,
    private val store: CordnGroupStore,
    val health: CoordinatorHealth = CoordinatorHealth(),
    /** Seconds. Injected so tests are not at the mercy of the wall clock. */
    private val clock: () -> Long = { TimeUtils.now() },
) : CordnSyncSource {
    private val groups = mutableMapOf<String, MlsGroup>()
    private val sync = CordnGroupSync(coordinator)

    private val _gids = MutableStateFlow<Set<String>>(emptySet())

    /** The groups this manager holds, for a UI to observe. */
    override val gids: StateFlow<Set<String>> = _gids.asStateFlow()

    private var publishedKeyPackage = false

    /** What happened to one delivered payload. */
    sealed interface Delivery {
        /**
         * The group it belongs to.
         *
         * On the interface because every outcome has one and callers route on
         * it — a delivery that cannot be filed under a group is not something
         * this type can represent.
         */
        val gid: String

        /** An application message that passed every §5 check. */
        data class Message(
            override val gid: String,
            val cursor: Long,
            val received: ReceivedMessage,
        ) : Delivery

        /** A handshake message; the group has advanced to [epoch]. */
        data class EpochAdvanced(
            override val gid: String,
            val cursor: Long,
            val epoch: Long,
        ) : Delivery

        /** Our own message coming back, already accounted for. */
        data class Echo(
            override val gid: String,
            val cursor: Long,
        ) : Delivery

        /**
         * A payload this group could not open.
         *
         * Not fatal and not silent: the cursor moves past it (the alternative
         * is a stream that never advances), but a UI that shows nothing here is
         * hiding a gap in a conversation.
         */
        data class Undecryptable(
            override val gid: String,
            val cursor: Long,
            val reason: String,
        ) : Delivery
    }

    // ---- membership ------------------------------------------------------

    /** The group behind [gid], if this manager holds it. */
    fun group(gid: String): MlsGroup? = groups[gid]

    /** Restores every group and cursor the store holds. Call once at startup. */
    suspend fun restore() {
        store.listGroups().forEach { gid ->
            val blob = store.loadGroup(gid) ?: return@forEach
            groups[gid] = MlsGroup.restore(MlsGroupState.decodeTls(blob), CordnGroupPolicy)
            store.loadCursor(gid)?.let { sync.restore(gid, it) }
        }
        _gids.value = groups.keys.toSet()
    }

    /**
     * Creates a group this account administers.
     *
     * [gid] is the caller's to choose and the coordinator never interprets it
     * (`spec/00.md` §4). The reference client uses a random UUID; anything
     * unique on this coordinator works, and it must not be derived from the MLS
     * `group_id`, which is secret.
     */
    suspend fun createGroup(
        gid: String,
        metadata: CordnGroupMetadata,
    ): MlsGroup {
        require(gid !in groups) { "already in a group with gid $gid" }
        val group =
            MlsGroup.create(
                identity = CordnCredential.of(accountPubKey).identity,
                policy = CordnGroupPolicy,
                initialExtensions = listOf(metadata.toExtension()),
                // See [gidFrom]: this is what lets a joiner -- ours or theirs --
                // learn the delivery id from the Welcome alone.
                groupId = gid.encodeToByteArray(),
            )
        groups[gid] = group
        persist(gid)
        _gids.value = groups.keys.toSet()
        return group
    }

    /**
     * Adds [targetPubKey] to [gid], taking their KeyPackage from the
     * coordinator and leaving them a Welcome.
     *
     * The order is load-bearing:
     *
     * 1. Take and **verify** the publication payload. §9/§10 require the client
     *    to check it even though §8 makes the coordinator check too — a
     *    coordinator that skipped the check could otherwise hand us any
     *    account's name over any account's key material, and we would invite
     *    the wrong person.
     * 2. Seal the Commit under the **pre-commit** epoch key (`spec/03.md` §5).
     *    The engine hands that key back on [CommitResult.preCommitExporterSecret]
     *    precisely because it is unobtainable afterwards: sealing under the
     *    epoch the Commit *creates* produces a payload only the sender can
     *    open, and every other member silently stops advancing.
     * 3. Post the Commit, then store the Welcome with the cursor it landed at,
     *    so the joiner starts from the epoch they can actually decrypt rather
     *    than replaying history that predates them.
     *
     * ## Wire format
     *
     * The Commit goes out **public-framed** — `MlsMessage(PublicMessage)`,
     * which is what this engine produces. cordn's reference client emits
     * private-framed handshake messages instead, but accepts either: its
     * `processMessageBase64` admits wireformat 1 and 2 and hands both to
     * ts-mls's `processMessage`. Nothing is weakened by the choice, because the
     * coordinator sees only the outer ChaCha seal either way (§8) — the framing
     * is visible only to members, who can already tell a Commit from a message.
     * [ingest] reads both.
     */
    suspend fun invite(
        gid: String,
        targetPubKey: HexKey,
    ): InviteResult {
        val group = requireGroup(gid)

        val taken =
            call { coordinator.takeKeyPackage(targetPubKey) }
                ?: throw CordnGroupException("the coordinator holds no KeyPackage for $targetPubKey")
        val verified = KeyPackagePublication.verify(taken.publicationEvent)
        if (verified.pubKey != targetPubKey) {
            throw CordnGroupException("the KeyPackage served for $targetPubKey belongs to ${verified.pubKey}")
        }

        val result = group.addMember(verified.bytes)
        val welcome = result.welcomeBytes ?: throw CordnGroupException("adding a member produced no Welcome")

        // framedCommitBytes, not commitBytes: the latter is the bare RFC 9420
        // Commit struct with no MLSMessage around it, which no receiver can
        // parse. preCommitExporterSecret is the epoch key the Commit leaves.
        val posted =
            call { sync.postCommit(gid, SealedPayload.seal(result.framedCommitBytes, result.preCommitExporterSecret)) }
        val welcomeAt =
            call {
                coordinator.storeWelcome(
                    targetPubKey = targetPubKey,
                    keyPackageRef = taken.keyPackageRef,
                    welcomeBase64 = Base64.encode(welcome),
                    after = posted.cursor,
                )
            }

        persist(gid)
        return InviteResult(gid, targetPubKey, posted.cursor, welcomeAt)
    }

    /**
     * Joins every group we have been invited to and can open.
     *
     * [bundleFor] resolves a `kp_ref` to the KeyPackageBundle we published
     * under it. A Welcome we have no private half for is left alone rather
     * than acknowledged, because acknowledging it retires it forever.
     *
     * [gidFor] overrides where the delivery id comes from — see [gidFrom] for
     * why it can be missing and what to pass when it is.
     */
    suspend fun joinPendingWelcomes(
        bundleFor: (String) -> KeyPackageBundle?,
        gidFor: (MlsGroup) -> String? = ::gidFrom,
    ): JoinResults {
        val pending = call { coordinator.takeWelcomes() }
        val joined = mutableListOf<String>()
        val skipped = mutableListOf<SkippedWelcome>()

        pending.forEach { welcome ->
            val bundle = bundleFor(welcome.keyPackageRef)
            if (bundle == null) {
                skipped += SkippedWelcome(welcome.keyPackageRef, "no KeyPackage private half for this ref")
                return@forEach
            }
            val group =
                try {
                    MlsGroup.processWelcome(Base64.decode(welcome.welcomeBase64), bundle, CordnGroupPolicy)
                } catch (e: Exception) {
                    skipped += SkippedWelcome(welcome.keyPackageRef, e.message ?: "the Welcome did not open")
                    return@forEach
                }
            val gid = gidFor(group)
            if (gid == null) {
                skipped += SkippedWelcome(welcome.keyPackageRef, UNKNOWN_GID)
                return@forEach
            }

            groups[gid] = group
            // `after` is the inviter saying where this member's history starts.
            // Without it a joiner replays epochs from before it existed and
            // every one lands as Undecryptable.
            welcome.after?.let { sync.restore(gid, sync.inbox(gid).cursor.advancedTo(it)) }
            persist(gid)
            joined += gid
        }

        _gids.value = groups.keys.toSet()
        return JoinResults(joined, skipped)
    }

    // ---- messages --------------------------------------------------------

    /** Sends [content] to [gid] as a cordn application message. */
    suspend fun send(
        gid: String,
        content: String,
        kind: Int = CHAT_KIND,
        tags: Array<Array<String>> = emptyArray(),
    ): CordnEnvelope {
        val group = requireGroup(gid)
        val envelope =
            CordnEnvelope.build(
                pubKey = accountPubKey,
                createdAt = clock(),
                kind = kind,
                tags = tags,
                content = content,
            )
        val sealed = CordnApplicationMessage.seal(group, accountPubKey, envelope)
        call { sync.postMessage(gid, sealed) }
        persist(gid)
        return envelope
    }

    /** Drains history for every group this manager holds. */
    override suspend fun catchUp(onDelivery: (Delivery) -> Unit): Int {
        if (groups.isEmpty()) return 0
        return call { sync.catchUp(groups.keys.toList()) { gid, ingestion -> onDelivery(ingest(gid, ingestion)) } }
            .also { persistAll() }
    }

    /**
     * Subscribes to live delivery for every group. Suspends until the
     * coordinator closes the stream, so give it its own coroutine.
     */
    override suspend fun subscribe(
        timeoutMs: Long,
        onDelivery: (Delivery) -> Unit,
    ) {
        if (groups.isEmpty()) return
        call { sync.subscribe(groups.keys.toList(), timeoutMs) { gid, ingestion -> onDelivery(ingest(gid, ingestion)) } }
    }

    private fun ingest(
        gid: String,
        ingestion: Ingestion,
    ): Delivery {
        val group = groups[gid] ?: return Delivery.Undecryptable(gid, ingestion.cursor, "no such group")

        val sealed =
            when (ingestion) {
                is Ingestion.OwnMessage -> return Delivery.Echo(gid, ingestion.cursor)
                // Our own Commit, already applied locally when we posted it.
                is Ingestion.SelfEchoConfirmed -> return Delivery.Echo(gid, ingestion.cursor)
                // Our own Commit that we posted but had not applied -- the echo
                // is the instruction to apply it, which is how a client that
                // died mid-post recovers.
                is Ingestion.SelfEchoUnapplied -> ingestion.sealedBase64
                is Ingestion.Process -> ingestion.sealedBase64
            }

        return try {
            // Our current epoch key opens both an application message sent at
            // this epoch and a Commit leaving it, which is the same key by
            // construction (spec/03.md §5).
            val opened = SealedPayload.open(sealed, SealedPayload.applicationKey(group))
            when (MlsMessage.decodeTls(TlsReader(opened)).wireFormat) {
                // Public-framed handshake: what this engine emits, and what
                // Marmot uses throughout.
                WireFormat.PUBLIC_MESSAGE -> {
                    group.processFramedCommit(opened)
                    Delivery.EpochAdvanced(gid, ingestion.cursor, group.epoch)
                }
                // Private-framed: what cordn's reference client emits, for both
                // application messages and handshake traffic.
                else -> {
                    val decrypted = group.decrypt(opened)
                    when (decrypted.contentType) {
                        ContentType.APPLICATION ->
                            Delivery.Message(gid, ingestion.cursor, CordnApplicationMessage.open(decrypted))
                        // decrypt() applies a Commit, so the epoch has moved already.
                        else -> Delivery.EpochAdvanced(gid, ingestion.cursor, group.epoch)
                    }
                }
            }
        } catch (e: Exception) {
            // The cursor has already advanced past it. Reporting rather than
            // throwing is what keeps one bad payload from stalling every other
            // group in the same page.
            Delivery.Undecryptable(gid, ingestion.cursor, e.message ?: "could not open payload")
        }
    }

    // ---- sharing and disclosure -----------------------------------------

    /** The `cordn1…` ref to share this group, pointing at this coordinator. */
    fun shareRef(gid: String): CordnGroupRef {
        requireGroup(gid)
        return CordnGroupRef(
            gid = gid,
            coordinatorPubKey = config.pubKey,
            relays = config.relays.map { it.url },
        )
    }

    /**
     * What this coordinator learns about [gid]. See [GroupExposure].
     *
     * @param joinedFromShareLink whether admission went through
     *   `join_request_store`, which names the asker's real npub (§8.1).
     */
    fun exposure(
        gid: String,
        joinedFromShareLink: Boolean = false,
    ): GroupExposure {
        requireGroup(gid)
        return GroupExposure(
            coordinator = config.pubKey,
            linkedGroupCount = groups.size,
            joinedFromShareLink = joinedFromShareLink,
            publishedKeyPackage = publishedKeyPackage,
            // :contextvm's CvmGiftWrap defaults to REQUIRED and CoordinatorClient
            // does not undo it, so this is a fact about our transport, not a
            // setting. It is reported rather than assumed so that a future
            // configurable transport cannot quietly make it false (§8.6).
            encryptionPinned = true,
        )
    }

    /** Publishes a KeyPackage so others can add this account. §8.4 applies. */
    suspend fun publishKeyPackage(
        keyPackageRef: String,
        keyPackageBase64: String,
    ) = call { coordinator.publishKeyPackage(keyPackageRef, keyPackageBase64) }
        .also { publishedKeyPackage = true }

    // ---- plumbing --------------------------------------------------------

    private fun requireGroup(gid: String) = groups[gid] ?: throw CordnGroupException("not a member of $gid")

    private suspend fun persist(gid: String) {
        val group = groups[gid] ?: return
        store.saveGroup(gid, group.saveState().encodeTls())
        sync.cursors()[gid]?.let { store.saveCursor(gid, it) }
    }

    private suspend fun persistAll() {
        groups.keys.forEach { persist(it) }
    }

    /** Runs a coordinator call, recording the outcome against [health]. */
    private suspend fun <T> call(block: suspend () -> T): T =
        try {
            block().also { health.recordSuccess(clock()) }
        } catch (e: Exception) {
            health.recordFailure(clock(), e.message)
            throw e
        }

    companion object {
        /** `spec/02.md` §6: a cordn chat message is a NIP-C7 kind 9. */
        const val CHAT_KIND = 9

        /** Why a Welcome was left in the inbox. Actionable, so it is a constant. */
        const val UNKNOWN_GID = "cannot tell which delivery group this Welcome is for"

        /**
         * The delivery `gid` a Welcome implies, or null when it implies none.
         *
         * **A Welcome does not carry the `gid`.** It carries the MLS
         * `group_id`, and `spec/03.md` §2 is explicit that the two are
         * decoupled — the coordinator's delivery id is not the MLS group id and
         * must not be assumed to be. So in principle a joiner has no way to
         * learn where to fetch from, and needs the `cordn1…` ref out of band.
         *
         * In practice the reference client closes that gap by convention:
         * cordn-web sets `group_id = utf8(gid)` when it creates a group and
         * reads the `gid` straight back out of the group context on join
         * (`chatGroupLifecycle.svelte.ts`). Verified against staircase's
         * fixtures, whose `group_id` decodes to exactly their published `gid`.
         *
         * This follows that convention — [createGroup] writes it and this reads
         * it — while treating it as what it is: an observation about one
         * implementation, not a guarantee. A group id that is not valid UTF-8
         * cannot be a `gid`, and a conformant peer is free to produce one, so
         * the answer is null and the caller is told rather than handed a
         * mojibake key that would quietly fetch nothing forever. Pass `gidFor`
         * to supply the id from a share ref instead.
         */
        fun gidFrom(group: MlsGroup): String? =
            try {
                group.groupId.decodeToString(throwOnInvalidSequence = true).takeIf { it.isNotEmpty() }
            } catch (e: CharacterCodingException) {
                null
            }
    }
}

/** Something went wrong that is this manager's to explain, not MLS's. */
class CordnGroupException(
    message: String,
) : IllegalStateException(message)

/** What [CordnGroupManager.joinPendingWelcomes] did with each pending Welcome. */
data class JoinResults(
    val joined: List<String>,
    /**
     * Welcomes left pending, with why. Not an error list: a Welcome for a
     * KeyPackage this device never held belongs to another device of the same
     * account, and draining it here would destroy it.
     */
    val skipped: List<SkippedWelcome>,
)

/** One Welcome that stayed in the inbox, and the reason. */
data class SkippedWelcome(
    val keyPackageRef: String,
    val reason: String,
)

/** The result of adding a member: where the Commit and the Welcome landed. */
data class InviteResult(
    val gid: String,
    val invited: HexKey,
    val commitCursor: Long,
    val welcomeAt: Long,
)
