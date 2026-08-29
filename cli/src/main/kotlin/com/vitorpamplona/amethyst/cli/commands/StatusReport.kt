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
package com.vitorpamplona.amethyst.cli.commands

import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.OperatorKeys
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.cli.RunState
import com.vitorpamplona.amethyst.cli.secrets.IdentityFile
import com.vitorpamplona.amethyst.cli.secrets.IdentitySecret
import com.vitorpamplona.amethyst.cli.stores.ConcordStore
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import java.io.File

/**
 * What [StatusCommand] gathers about one account, and how it reads it off
 * disk. Kept apart from the command so the command stays what every other
 * one in `commands/` is: parse, call, emit.
 *
 * Everything here is read-only and prompt-free by construction — it parses
 * the account's own JSON files and asks the (already-open) shared event
 * store about the account's pubkey. It never resolves a private key, so it
 * cannot pop a keychain dialog or ask for a NIP-49 passphrase.
 */
internal object StatusReport {
    /**
     * The whole answer: the machine's `~/.amy/`, which account is selected,
     * every account, and the machine-level operator key if one exists.
     */
    data class Overview(
        val root: File,
        val currentPin: String?,
        /**
         * Whether [currentPin] resolves to a directory that is actually
         * there. A pin left behind by a deleted account makes *every* other
         * verb fail with "pins 'x' but … doesn't exist" — so status, the
         * command you run to work out why, has to be able to say so.
         */
        val currentExists: Boolean,
        val accounts: List<AccountReport>,
        val operator: Operator?,
    ) {
        /**
         * True when no account is selected and amy cannot pick one for you —
         * a stale pin, or several accounts and no pin. Until it's resolved
         * every verb needs an explicit `--account`.
         */
        val selectionBroken: Boolean
            get() = if (currentPin != null) !currentExists else accounts.size > 1

        fun toJson(): Map<String, Any?> =
            linkedMapOf(
                "root" to root.absolutePath,
                "current" to currentPin,
                "current_exists" to currentExists,
                "accounts" to accounts.map { it.toJson() },
                "operator" to operator?.toJson(),
            )
    }

    /**
     * The machine-level GrapeRank operator key at `~/.amy/operator/`, shared
     * by every account. `listAccounts` skips it as a reserved name, so
     * without this it is the one thing under `~/.amy/` nothing reports.
     */
    data class Operator(
        val pubkey: String,
        val npub: String,
        val relays: List<String>,
    ) {
        fun toJson(): Map<String, Any?> = linkedMapOf("pubkey" to pubkey, "npub" to npub, "relays" to relays)
    }

    /** One account's answer to "who is this" and "what have they got". */
    data class AccountReport(
        val name: String,
        val isCurrent: Boolean,
        val npub: String?,
        val pubkey: String?,
        val profileName: String?,
        val nip05: String?,
        val signer: SignerInfo,
        val saved: Saved,
    ) {
        /** The `--json` projection — this shape is the public contract. */
        fun toJson(): Map<String, Any?> {
            // LinkedHashMap so `--json` key order matches the text block.
            val row = LinkedHashMap<String, Any?>()
            row["name"] = name
            row["current"] = isCurrent
            row["npub"] = npub
            row["pubkey"] = pubkey
            row["profile_name"] = profileName
            row["nip05"] = nip05
            row["signer"] = signer.kind
            row["key_storage"] = signer.storage
            row["can_sign"] = signer.canSign
            if (signer.bunkerRelays != null) row["bunker_relays"] = signer.bunkerRelays
            row["saved"] = saved.toJson()
            return row
        }
    }

    /**
     * What this account has accumulated on this machine. [events] and
     * [cashuWallet] come from the shared event store; everything else from
     * the account's own directory.
     */
    data class Saved(
        val follows: Int,
        /**
         * NIP-65 (kind 10002). A bare `r` tag advertises a relay for both
         * directions, so [relaysWrite] + [relaysRead] can exceed [relays] —
         * that is the spec, not double counting.
         */
        val relays: Int,
        val relaysWrite: Int,
        val relaysRead: Int,
        /** NIP-17 DM inbox (kind 10050) — where others send this account DMs. */
        val dmRelays: Int,
        val events: Int,
        val newestEventAt: Long?,
        val contacts: Int,
        val marmotGroups: Int,
        val marmotMessages: Int,
        val keyPackage: Boolean,
        val concordCommunities: Int,
        val cashuWallet: Boolean,
        val dmCursorAt: Long?,
        val marmotGroupCursors: Int,
    ) {
        /** True when this account has nothing but its key — the fresh-`init` state. */
        val isEmpty: Boolean
            get() =
                follows == 0 && relays == 0 && dmRelays == 0 && events == 0 && contacts == 0 &&
                    marmotGroups == 0 && !keyPackage && concordCommunities == 0 && !cashuWallet &&
                    dmCursorAt == null

        fun toJson(): Map<String, Any?> =
            linkedMapOf(
                "follows" to follows,
                "relays" to relays,
                "relays_write" to relaysWrite,
                "relays_read" to relaysRead,
                "dm_relays" to dmRelays,
                "events" to events,
                "newest_event_at" to newestEventAt,
                "contacts" to contacts,
                "marmot_groups" to marmotGroups,
                "marmot_messages" to marmotMessages,
                "key_package" to keyPackage,
                "concord_communities" to concordCommunities,
                "cashu_wallet" to cashuWallet,
                "dm_cursor_at" to dmCursorAt,
                "marmot_group_cursors" to marmotGroupCursors,
            )
    }

    /**
     * How this account can sign, derived purely from the on-disk
     * [IdentityFile] — never resolves the secret itself.
     *  - `local`      — an on-device private key ([storage] says where).
     *  - `bunker`     — a NIP-46 remote signer ([bunkerRelays] lists it).
     *  - `read-only`  — imported from an npub/nprofile/NIP-05; cannot sign.
     *  - `none`       — no `identity.json` at all; the directory is a shell.
     *  - `unreadable` — the file is there but won't parse. Kept distinct from
     *    `none` because the fixes are opposite: `none` wants `init`, and
     *    running `init` over a file amy merely failed to READ would mint a
     *    new key and strand the old one.
     */
    data class SignerInfo(
        val kind: String,
        val storage: String?,
        val canSign: Boolean,
        val bunkerRelays: List<String>?,
    )

    /**
     * Read every account under [rootBase], plus the selection state and the
     * machine-level operator key. [store] is the shared event store, already
     * open, or null when this machine has none.
     */
    suspend fun overview(
        rootBase: File,
        store: IEventStore?,
    ): Overview {
        val pin =
            File(rootBase, DataDir.CURRENT_MARKER_NAME)
                .takeIf { it.isFile }
                ?.readText()
                ?.trim()
                ?.ifEmpty { null }

        return Overview(
            root = rootBase,
            currentPin = pin,
            currentExists = pin != null && File(rootBase, pin).isDirectory,
            accounts =
                DataDir.listAccounts(rootBase).map { name ->
                    of(File(rootBase, name), name, name == pin, store)
                },
            operator =
                OperatorKeys.peek(rootBase)?.let { cfg ->
                    Operator(
                        pubkey = cfg.masterPubKey,
                        npub = runCatching { NPub.create(cfg.masterPubKey) }.getOrElse { cfg.masterPubKey },
                        relays = cfg.relays,
                    )
                },
        )
    }

    /**
     * Read one account rooted at [accountRoot]. [store] is the shared event
     * store, already open, or null when this machine has none — in which
     * case the store-backed fields simply come back empty.
     */
    suspend fun of(
        accountRoot: File,
        name: String,
        isCurrent: Boolean,
        store: IEventStore?,
    ): AccountReport {
        val identityFile = File(accountRoot, "identity.json")
        val identity = readIdentity(identityFile)
        val pubkey = identity?.pubKeyHex
        val runState = readRunState(File(accountRoot, "state.json"))

        // Everything the store can answer about this pubkey, in one hop.
        val own = if (pubkey != null && store != null) ownEvents(store, pubkey) else OwnEvents()

        return AccountReport(
            name = name,
            isCurrent = isCurrent,
            npub = identity?.npub,
            pubkey = pubkey,
            profileName = own.profile?.bestName()?.takeIf { it.isNotBlank() },
            nip05 = own.profile?.nip05?.takeIf { it.isNotBlank() },
            signer = classifySigner(identity, identityFile.isFile),
            saved =
                Saved(
                    follows = own.follows,
                    relays = own.relays,
                    relaysWrite = own.relaysWrite,
                    relaysRead = own.relaysRead,
                    dmRelays = own.dmRelays,
                    events = own.count,
                    newestEventAt = own.newestAt,
                    contacts = contactCount(File(accountRoot, "aliases.json"), identity?.npub),
                    marmotGroups = marmotFiles(accountRoot, ".state").size,
                    marmotMessages = marmotFiles(accountRoot, ".messages").sumOf { countLines(it) },
                    keyPackage = File(accountRoot, "marmot/keypackages.bundle").isFile,
                    concordCommunities = ConcordStore(File(accountRoot, "concord.json")).load().size,
                    cashuWallet = own.hasWallet,
                    dmCursorAt = runState.giftWrapSince,
                    marmotGroupCursors = runState.groupSince.size,
                ),
        )
    }

    /** What the shared store holds for one pubkey. All-empty when it holds nothing. */
    private data class OwnEvents(
        val count: Int = 0,
        val newestAt: Long? = null,
        val profile: UserMetadata? = null,
        val hasWallet: Boolean = false,
        val follows: Int = 0,
        val relays: Int = 0,
        val relaysWrite: Int = 0,
        val relaysRead: Int = 0,
        val dmRelays: Int = 0,
    )

    /**
     * The store-backed facts about an account: how many of its events we
     * hold, when the newest one is from, and the replaceables that say who
     * it is (kind 0) and whether it has a wallet (kind 17375). A store that
     * errors — locked by a concurrent writer, mid-migration — degrades the
     * whole group to empty rather than failing the command; the on-disk half
     * of the report is still worth printing.
     */
    private suspend fun ownEvents(
        store: IEventStore,
        pubkey: String,
    ): OwnEvents =
        runCatching {
            val authors = listOf(pubkey)
            var newestAt: Long? = null
            var profile: MetadataEvent? = null
            var contacts: ContactListEvent? = null
            var relayList: AdvertisedRelayListEvent? = null
            var dmInbox: ChatMessageRelayListEvent? = null
            var wallet = false

            // Newest-first with limit 1 gives the last-activity stamp.
            store.query<Event>(Filter(authors = authors, limit = 1)) { newestAt = it.createdAt }

            // Every replaceable that describes the account, in ONE pass —
            // each is at most one row, so adding a kind here is free.
            val describes =
                listOf(
                    MetadataEvent.KIND,
                    ContactListEvent.KIND,
                    AdvertisedRelayListEvent.KIND,
                    ChatMessageRelayListEvent.KIND,
                    CashuWalletEvent.KIND,
                )
            store.query<Event>(Filter(authors = authors, kinds = describes)) { event ->
                when (event) {
                    // The store supersedes replaceables, so each of these is
                    // normally a single row — keep the newest anyway.
                    is MetadataEvent -> if (isNewer(event, profile)) profile = event
                    is ContactListEvent -> if (isNewer(event, contacts)) contacts = event
                    is AdvertisedRelayListEvent -> if (isNewer(event, relayList)) relayList = event
                    is ChatMessageRelayListEvent -> if (isNewer(event, dmInbox)) dmInbox = event
                    is CashuWalletEvent -> wallet = true
                    else -> Unit
                }
            }

            OwnEvents(
                count = store.count(Filter(authors = authors)),
                newestAt = newestAt,
                profile = profile?.contactMetaData(),
                hasWallet = wallet,
                follows = contacts?.unverifiedFollowKeySet()?.size ?: 0,
                relays = relayList?.relays()?.size ?: 0,
                relaysWrite = relayList?.writeRelays()?.size ?: 0,
                relaysRead = relayList?.readRelays()?.size ?: 0,
                dmRelays = dmInbox?.relays()?.size ?: 0,
            )
        }.getOrElse { OwnEvents() }

    private fun isNewer(
        candidate: Event,
        known: Event?,
    ): Boolean = known == null || candidate.createdAt > known.createdAt

    /** Marmot per-group files with the given suffix, under `marmot/groups/`. */
    private fun marmotFiles(
        accountRoot: File,
        suffix: String,
    ): List<File> =
        File(accountRoot, "marmot/groups")
            .listFiles { f -> f.name.endsWith(suffix) }
            ?.toList()
            .orEmpty()

    /**
     * Lines in [file] — one decrypted group message per line, per
     * `FileMarmotMessageStore`. Streams bytes rather than reading the file in,
     * so a long chat history costs a scan and not its size in heap.
     */
    private fun countLines(file: File): Int =
        runCatching {
            file.inputStream().buffered().use { input ->
                var lines = 0
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    for (i in 0 until read) if (buffer[i] == '\n'.code.toByte()) lines++
                }
                lines
            }
        }.getOrDefault(0)

    private fun classifySigner(
        identity: IdentityFile?,
        fileExists: Boolean,
    ): SignerInfo {
        if (identity == null) return SignerInfo(if (fileExists) "unreadable" else "none", null, false, null)
        identity.bunker?.let { bunker ->
            return SignerInfo("bunker", secretStorageLabel(identity.secret), true, bunker.relays)
        }
        val storage = secretStorageLabel(identity.secret)
        return when {
            identity.secret != null -> SignerInfo("local", storage, true, null)
            // Pre-secret-store data-dirs kept the key inline; still signable.
            identity.privKeyHex != null || identity.nsec != null -> SignerInfo("local", "legacy-plaintext", true, null)
            else -> SignerInfo("read-only", null, false, null)
        }
    }

    private fun secretStorageLabel(secret: IdentitySecret?): String? =
        when (secret) {
            is IdentitySecret.Keychain -> "keychain:${secret.backend}"
            is IdentitySecret.Ncryptsec -> "ncryptsec"
            is IdentitySecret.Plaintext -> "plaintext"
            null -> null
        }

    /**
     * Address-book entries, minus the self-alias `init` writes so the user
     * can name their own account. Counting that one made every brand-new
     * account claim to have saved a contact.
     */
    private fun contactCount(
        file: File,
        ownNpub: String?,
    ): Int = readAliases(file).count { (_, npub) -> npub != ownNpub }

    private fun readIdentity(file: File): IdentityFile? = if (file.isFile) runCatching { Output.mapper.readValue<IdentityFile>(file.readText()) }.getOrNull() else null

    private fun readAliases(file: File): Map<String, String> = if (file.isFile) runCatching { Output.mapper.readValue<Map<String, String>>(file.readText()) }.getOrElse { emptyMap() } else emptyMap()

    private fun readRunState(file: File): RunState = if (file.isFile) runCatching { Output.mapper.readValue<RunState>(file.readText()) }.getOrElse { RunState() } else RunState()
}
