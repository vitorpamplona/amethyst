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

import com.vitorpamplona.quartz.cordn.appMultiDevice.CordnCarriedKeyPackage
import com.vitorpamplona.quartz.cordn.sync.GroupCursor
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import java.io.File
import java.util.Base64

/**
 * Reading a handoff snapshot off the cordn stores, and writing one back.
 *
 * Here rather than in the Android runtime because `amy` needs exactly the same
 * two operations, and duplicating them would let the CLI and the app disagree
 * about what a migration carries — which the user would discover as a
 * conversation that arrived on one path and not the other.
 */
object CordnMigrationStores {
    /**
     * Collects every group on disk for [configs].
     *
     * Reads the stores rather than any in-memory state: a coordinator whose
     * session failed to open today is still migrated, because a handoff that
     * silently omitted the groups the app could not reach would be wrong
     * precisely when it matters.
     */
    suspend fun read(
        root: File,
        accountPubKey: HexKey,
        cipher: CordnBlobCipher,
        configs: List<CoordinatorConfig>,
    ): CordnMigrationSnapshot {
        val groups = mutableListOf<CordnMigrationGroup>()
        val keyPackages = mutableListOf<CordnCarriedKeyPackage>()

        configs.forEach { config ->
            val dir = CordnStorageLayout.directoryFor(root, accountPubKey, config.pubKey)
            val groupStore = FileCordnGroupStore(dir, cipher)
            val keyPackageStore = FileCordnKeyPackageStore(dir, cipher)

            groupStore.listGroups().forEach { gid ->
                val state = groupStore.loadGroup(gid) ?: return@forEach
                groups +=
                    CordnMigrationGroup(
                        coordinatorPubKey = config.pubKey,
                        coordinatorRelays = config.relays.map { it.url },
                        gid = gid,
                        clientStateBase64 = state.toBase64(),
                        cursor = groupStore.loadCursor(gid)?.fetchCursor ?: 0L,
                        roomStateBase64 = groupStore.loadRoomState(gid)?.let { CordnRoomStateCodec.encode(it).toBase64() },
                        echoStateBase64 = groupStore.loadEchoState(gid)?.let { EchoStateCodec.encode(it).toBase64() },
                        joinedViaRequest = groupStore.loadJoinedViaRequest(gid),
                    )
            }

            keyPackageStore.list().forEach { ref ->
                val bundle = keyPackageStore.load(ref) ?: return@forEach
                keyPackages += CordnCarriedKeyPackage(config.pubKey, ref, bundle.toBase64())
            }
        }

        return CordnMigrationSnapshot(accountPubKey, groups, keyPackages = keyPackages)
    }

    /**
     * Replaces this device's cordn tree with [snapshot]'s, returning the
     * coordinator list to start.
     *
     * **Replaces, and is not a merge.** Two devices holding one group's state
     * and both committing fork the ratchet tree, and MLS does not recover —
     * merging would produce that on purpose. The old tree goes first, so a
     * `gid` present in both cannot end up half from each.
     */
    suspend fun write(
        root: File,
        accountPubKey: HexKey,
        cipher: CordnBlobCipher,
        snapshot: CordnMigrationSnapshot,
    ): List<CoordinatorConfig> {
        require(snapshot.accountPubKey == accountPubKey) {
            "this migration belongs to a different account"
        }

        File(root, "cordn/$accountPubKey").deleteRecursively()

        snapshot.groups.forEach { group ->
            val store = FileCordnGroupStore(CordnStorageLayout.directoryFor(root, accountPubKey, group.coordinatorPubKey), cipher)
            store.saveGroup(group.gid, group.clientStateBase64.fromBase64())
            // Both halves of the cursor: the writer's snapshot was consistent at
            // fetchCursor (§4.1), and starting behind it would re-fetch
            // messages the state has already advanced past.
            store.saveCursor(group.gid, GroupCursor(fetchCursor = group.cursor, lastCursor = group.cursor))
            group.roomStateBase64?.let { store.saveRoomState(group.gid, CordnRoomStateCodec.decode(it.fromBase64())) }
            group.echoStateBase64?.let { store.saveEchoState(group.gid, EchoStateCodec.decode(it.fromBase64())) }
            if (group.joinedViaRequest) store.saveJoinedViaRequest(group.gid)
        }

        snapshot.keyPackages.forEach { keyPackage ->
            FileCordnKeyPackageStore(CordnStorageLayout.directoryFor(root, accountPubKey, keyPackage.coordinatorPubKey), cipher)
                .save(keyPackage.keyPackageRef, keyPackage.bundle.fromBase64())
        }

        return snapshot.groups
            .groupBy { it.coordinatorPubKey }
            .mapNotNull { (pubKey, groups) ->
                val relays =
                    groups
                        .flatMap { it.coordinatorRelays }
                        .distinct()
                        .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                // A coordinator with no reachable relay cannot be talked to, and
                // CoordinatorConfig refuses to be built without one.
                if (relays.isEmpty()) null else CoordinatorConfig(pubKey, relays, CoordinatorConfig.Origin.MANUAL)
            }
    }

    private fun ByteArray.toBase64() = Base64.getEncoder().encodeToString(this)

    private fun String.fromBase64(): ByteArray = Base64.getDecoder().decode(this)
}
