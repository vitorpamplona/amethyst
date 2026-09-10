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

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.marmot.MarmotManager
import com.vitorpamplona.amethyst.commons.service.upload.BlossomAuth
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.amethyst.commons.util.deleteOrWarn
import com.vitorpamplona.quartz.marmot.OutboundGroupEvent
import com.vitorpamplona.quartz.marmot.appComponents.GroupAvatarUrlV1
import com.vitorpamplona.quartz.marmot.appComponents.GroupBlossomImageV1
import com.vitorpamplona.quartz.marmot.appComponents.MarmotWebUrl
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupImageEncryption
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import java.io.File

/**
 * Metadata-only commits: rename, promote/demote, set/clear image. Each loads current
 * metadata, edits the right field, publishes a GCE commit to the group relays.
 */
object GroupMetadataCommands {
    suspend fun rename(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Output.error("bad_args", "group rename <gid> <name>")
        return commit(dataDir, rest[0]) { ctx, gid, view ->
            ctx.marmot.setGroupProfile(gid, rest[1], view.description)
        }
    }

    suspend fun promote(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Output.error("bad_args", "group promote <gid> <npub>")
        return commit(dataDir, rest[0]) { ctx, gid, view ->
            val newAdmin = ctx.requireUserHex(rest[1])
            ctx.marmot.setGroupAdmins(gid, (view.adminPubkeys + newAdmin).distinct())
        }
    }

    suspend fun demote(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Output.error("bad_args", "group demote <gid> <npub>")
        return commit(dataDir, rest[0]) { ctx, gid, view ->
            val target = ctx.requireUserHex(rest[1])
            ctx.marmot.setGroupAdmins(gid, view.adminPubkeys.filter { it != target })
        }
    }

    /**
     * Set the group avatar (MIP-01 v2): encrypt the image, optionally push the
     * ciphertext to Blossom (`--server`), and commit the image fields into the group
     * metadata. The encryption is byte-for-byte interoperable with mdk/whitenoise.
     *
     * `group set-image <gid> <image-file> [--server URL] [--mime TYPE]`
     */
    suspend fun setImage(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val gid = args.positional(0, "gid")
        val path = args.positional(1, "image-file")
        val server = args.flag("server")
        args.rejectUnknown()
        val file = File(path)
        if (!file.isFile) return Output.error("bad_args", "no such file: $path")

        val plaintext = file.readBytes()
        val enc = MarmotGroupImageEncryption.encrypt(plaintext)
        val uploadKeySeed = MarmotGroupImageEncryption.generateUploadKey()

        // Optionally push the encrypted blob to Blossom, signed by the keypair derived
        // from image_upload_key so an admin holding the seed can later replace/delete it.
        var uploadedUrl: String? = null
        if (server != null) {
            val uploadSigner = NostrSignerInternal(KeyPair(privKey = MarmotGroupImageEncryption.deriveUploadKeypairSecret(uploadKeySeed)))
            val tmp = File.createTempFile("marmot-icon", ".bin")
            try {
                tmp.writeBytes(enc.ciphertext)
                val auth = BlossomAuth.createUploadAuth(enc.imageHash, enc.ciphertext.size.toLong(), "Group image", uploadSigner)
                val result = BlossomClient().upload(tmp, "application/octet-stream", server, auth)
                if (result.sha256 != null && result.sha256 != enc.imageHash) {
                    return Output.error("hash_mismatch", "blossom returned ${result.sha256}, expected ${enc.imageHash}")
                }
                uploadedUrl = result.url
            } finally {
                tmp.deleteOrWarn("GroupMetadataCommands", "encrypted group image")
            }
        }

        return commit(dataDir, gid, mapOf("image_hash" to enc.imageHash, "image_url" to uploadedUrl)) { ctx, resolved, _ ->
            ctx.marmot.setGroupImage(
                resolved,
                GroupBlossomImageV1(
                    imageHash = enc.imageHash.hexToByteArray(),
                    imageKey = enc.imageKey,
                    imageNonce = enc.imageNonce,
                    imageUploadKey = uploadKeySeed,
                    mediaType = args.flag("mime") ?: "image/jpeg",
                ),
            )
        }
    }

    /** Remove the group avatar. `group clear-image <gid>` */
    suspend fun clearImage(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Output.error("bad_args", "group clear-image <gid>")
        return commit(dataDir, rest[0]) { ctx, gid, _ -> ctx.marmot.setGroupImage(gid, null) }
    }

    /**
     * Point the group avatar at a plain `https` URL (`0x8007`).
     *
     * The URL is normalized by the component's encoder, so what gets committed
     * may differ from what was typed — the emitted `avatar_url` is the stored
     * form, not the argument.
     *
     * `group set-avatar-url <gid> <https-url> [--dim WIDTHxHEIGHT] [--thumbhash TEXT]`
     */
    suspend fun setAvatarUrl(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val gid = args.positional(0, "gid")
        val url = args.positional(1, "url")
        val dim = args.flag("dim")
        val thumbhash = args.flag("thumbhash")
        args.rejectUnknown()

        val avatar =
            try {
                GroupAvatarUrlV1(
                    url = MarmotWebUrl.normalize(url, label = "avatar URL"),
                    dim = dim?.encodeToByteArray() ?: ByteArray(0),
                    thumbhash = thumbhash?.encodeToByteArray() ?: ByteArray(0),
                )
            } catch (e: IllegalArgumentException) {
                return Output.error("bad_args", e.message ?: "invalid avatar URL")
            }

        return commit(dataDir, gid, mapOf("avatar_url" to avatar.url)) { ctx, resolved, _ ->
            ctx.marmot.setGroupAvatarUrl(resolved, avatar)
        }
    }

    /**
     * Set the disappearing-message duration. `group set-retention <gid> <secs>`
     *
     * `0` turns disappearing messages off. The change is not retroactive:
     * every message already carries the expiry pinned from the epoch that
     * delivered it, so this only governs what arrives after the commit.
     */
    suspend fun setRetention(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val gid = args.positional(0, "gid")
        val raw = args.positional(1, "secs")
        args.rejectUnknown()

        val secs =
            raw.toULongOrNull()
                ?: return Output.error("bad_args", "secs must be a whole number of seconds (0 disables)")

        return commit(dataDir, gid, mapOf("disappearing_secs" to secs.toString())) { ctx, resolved, _ ->
            ctx.marmot.setMessageRetention(resolved, secs)
        }
    }

    /** Remove the https avatar link. `group clear-avatar-url <gid>` */
    suspend fun clearAvatarUrl(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Output.error("bad_args", "group clear-avatar-url <gid>")
        return commit(dataDir, rest[0]) { ctx, gid, _ -> ctx.marmot.setGroupAvatarUrl(gid, null) }
    }

    /**
     * Run one metadata commit and report it.
     *
     * The mutation goes through [MarmotManager]'s profile-agnostic setters
     * rather than being applied to a legacy `MarmotGroupData` here. Building
     * that blob locally was the bug: `groupMetadata` is null for every
     * current-profile group, so this bootstrapped a legacy `0xF2EE` extension
     * and committed it INTO a current-profile group — the rename appeared to
     * succeed locally and every peer kept showing the old name.
     */
    private suspend fun commit(
        dataDir: DataDir,
        rawGid: HexKey,
        extra: Map<String, Any?> = emptyMap(),
        mutate: suspend (Context, HexKey, MarmotManager.GroupView) -> OutboundGroupEvent,
    ): Int {
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(rawGid)
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", "not a member of group $gid")
            val view = ctx.marmot.groupView(gid) ?: return Output.error("not_member", "not a member of group $gid")

            val commit = mutate(ctx, gid, view)
            val targets = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            val ack = ctx.publish(commit.signedEvent, targets)
            RawEventSupport.publishGuard(ack, commit.signedEvent.id)?.let { return it }

            val after = ctx.marmot.groupView(gid)
            Output.emit(
                mapOf(
                    "group_id" to gid,
                    "name" to (after?.name ?: view.name),
                    "admins" to (after?.adminPubkeys ?: view.adminPubkeys),
                    "epoch" to ctx.marmot.groupEpoch(gid),
                    "commit_event_id" to commit.signedEvent.id,
                ) + RawEventSupport.ackFields(ack) + extra,
            )
            return 0
        }
    }
}
