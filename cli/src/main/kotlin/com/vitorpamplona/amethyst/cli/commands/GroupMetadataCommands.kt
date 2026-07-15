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
import com.vitorpamplona.amethyst.commons.service.upload.BlossomAuth
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupImageEncryption
import com.vitorpamplona.quartz.nip01Core.core.HexKey
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
        return edit(dataDir, rest[0]) { _, cur -> cur.copy(name = rest[1]) }
    }

    suspend fun promote(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Output.error("bad_args", "group promote <gid> <npub>")
        return edit(dataDir, rest[0]) { ctx, cur ->
            val newAdmin = ctx.requireUserHex(rest[1])
            val admins = cur.adminPubkeys.toMutableList()
            if (newAdmin !in admins) admins.add(newAdmin)
            cur.copy(adminPubkeys = admins)
        }
    }

    suspend fun demote(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Output.error("bad_args", "group demote <gid> <npub>")
        return edit(dataDir, rest[0]) { ctx, cur ->
            val target = ctx.requireUserHex(rest[1])
            val admins = cur.adminPubkeys.filter { it != target }
            cur.copy(adminPubkeys = admins)
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
                tmp.delete()
            }
        }

        return edit(dataDir, gid, mapOf("image_hash" to enc.imageHash, "image_url" to uploadedUrl)) { _, cur ->
            cur.withImage(enc.imageHash, enc.imageKey, enc.imageNonce, uploadKeySeed)
        }
    }

    /** Remove the group avatar. `group clear-image <gid>` */
    suspend fun clearImage(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Output.error("bad_args", "group clear-image <gid>")
        return edit(dataDir, rest[0]) { _, cur -> cur.withoutImage() }
    }

    private suspend fun edit(
        dataDir: DataDir,
        rawGid: HexKey,
        extra: Map<String, Any?> = emptyMap(),
        mutate: suspend (Context, MarmotGroupData) -> MarmotGroupData,
    ): Int {
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(rawGid)
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", gid)
            val outboxUrls = ctx.outboxRelays().map { it.url }
            val cur =
                ctx.marmot.groupMetadata(gid)
                    ?: MarmotGroupData.bootstrap(
                        nostrGroupId = gid,
                        creatorPubKey = ctx.identity.pubKeyHex,
                        outboxRelays = outboxUrls,
                    )
            val updated = mutate(ctx, cur).withMergedRelays(outboxUrls)

            val commit = ctx.marmot.updateGroupMetadata(gid, updated)
            val targets = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            val ack = ctx.publish(commit.signedEvent, targets)

            Output.emit(
                mapOf(
                    "group_id" to gid,
                    "name" to updated.name,
                    "admins" to updated.adminPubkeys,
                    "epoch" to ctx.marmot.groupEpoch(gid),
                    "commit_event_id" to commit.signedEvent.id,
                    "published_to" to ack.filterValues { it }.keys.map { it.url },
                ) + extra,
            )
            return 0
        }
    }
}
