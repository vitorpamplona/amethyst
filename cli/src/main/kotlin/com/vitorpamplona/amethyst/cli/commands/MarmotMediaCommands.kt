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
import com.vitorpamplona.quartz.marmot.appComponents.BlobStoreEndpointV2
import com.vitorpamplona.quartz.marmot.appComponents.EncryptedMediaPolicyV2
import com.vitorpamplona.quartz.marmot.appComponents.EncryptedMediaReferenceV2
import com.vitorpamplona.quartz.marmot.appComponents.EncryptedMediaV2
import com.vitorpamplona.quartz.marmot.appComponents.MarmotMediaType
import com.vitorpamplona.quartz.marmot.appComponents.MediaLocatorV2
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.sha256.sha256
import java.io.File

/**
 * `amy marmot media` — `encrypted-media-v2` attachments (`0x800b`).
 *
 * The server only ever sees ciphertext and its hash. The key is derived from
 * the group's own MLS exporter and never leaves the group, so the blob store
 * is storage and not a party to the conversation.
 */
object MarmotMediaCommands {
    val USAGE: String =
        """
        |amy marmot media — encrypted-media-v2 attachments
        |
        |  marmot media policy GID                    print the group's media policy
        |  marmot media set-policy GID URL[,URL…]     commit a policy naming these blob stores,
        |                                              in upload/fetch fallback order
        |  marmot media send GID FILE [--caption TXT] encrypt, upload, and post the kind:9
        |    [--server URL] [--mime TYPE]              (--server overrides the policy's first endpoint)
        |  marmot media get GID EVENT_ID --out PATH   fetch, decrypt and verify an attachment
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int =
        route(
            "media",
            tail,
            "media <policy|set-policy|send|get> …",
            mapOf(
                "policy" to { rest -> policy(dataDir, rest) },
                "set-policy" to { rest -> setPolicy(dataDir, rest) },
                "send" to { rest -> send(dataDir, rest) },
                "get" to { rest -> get(dataDir, rest) },
            ),
            help = USAGE,
        )

    private suspend fun policy(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.isEmpty()) return Output.error("bad_args", "media policy <gid>")
        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", "not a member of group $gid")

            val policy = ctx.marmot.encryptedMediaPolicy(gid)
            Output.emit(
                mapOf(
                    "group_id" to gid,
                    "media_format" to policy?.mediaFormat,
                    "allowed_locator_kinds" to policy?.allowedLocatorKinds,
                    "default_blob_endpoints" to
                        policy?.defaultBlobEndpoints?.map {
                            mapOf("locator_kind" to it.locatorKind, "base_url" to it.baseUrl)
                        },
                ),
            )
            return 0
        }
    }

    private suspend fun setPolicy(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        if (rest.size < 2) return Output.error("bad_args", "media set-policy <gid> <base-url>[,<base-url>…]")
        val urls = rest[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (urls.isEmpty()) return Output.error("bad_args", "media set-policy needs at least one base URL")

        val policy =
            try {
                EncryptedMediaPolicyV2(
                    allowedLocatorKinds = listOf(EncryptedMediaPolicyV2.INITIAL_LOCATOR_KIND),
                    // Order is preserved deliberately: it IS the upload/fetch
                    // fallback priority, so sorting it would change where the
                    // group uploads.
                    defaultBlobEndpoints =
                        urls.map { BlobStoreEndpointV2(EncryptedMediaPolicyV2.INITIAL_LOCATOR_KIND, it) },
                )
            } catch (e: IllegalArgumentException) {
                return Output.error("bad_args", e.message ?: "invalid media policy")
            }

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val gid = ctx.resolveGroupId(rest[0])
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(gid)) return Output.error("not_member", "not a member of group $gid")

            val commit = ctx.marmot.setEncryptedMediaPolicy(gid, policy)
            val targets = ctx.marmotGroupRelays(gid).ifEmpty { ctx.outboxRelays() }
            val ack = ctx.publish(commit.signedEvent, targets)
            RawEventSupport.publishGuard(ack, commit.signedEvent.id)?.let { return it }

            Output.emit(
                mapOf(
                    "group_id" to gid,
                    "default_blob_endpoints" to policy.defaultBlobEndpoints.map { it.baseUrl },
                    "epoch" to ctx.marmot.groupEpoch(gid),
                    "commit_event_id" to commit.signedEvent.id,
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }

    private suspend fun send(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val gid = args.positional(0, "gid")
        val path = args.positional(1, "file")
        val serverFlag = args.flag("server")
        val caption = args.flag("caption") ?: ""
        val mime = args.flag("mime")
        args.rejectUnknown()

        val file = File(path)
        if (!file.isFile) return Output.error("bad_args", "no such file: $path")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val resolved = ctx.resolveGroupId(gid)
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(resolved)) return Output.error("not_member", "not a member of group $resolved")

            val endpoint =
                serverFlag
                    ?: ctx.marmot
                        .encryptedMediaPolicy(resolved)
                        ?.defaultBlobEndpoints
                        ?.firstOrNull { it.locatorKind == EncryptedMediaPolicyV2.INITIAL_LOCATOR_KIND }
                        ?.baseUrl
                    ?: return Output.error(
                        "no_endpoint",
                        "group $resolved has no encrypted-media policy; pass --server or commit one with media set-policy",
                    )
            val store = BlobStoreEndpointV2(EncryptedMediaPolicyV2.INITIAL_LOCATOR_KIND, endpoint)

            // `m` has to be byte-for-byte canonical: it feeds both the key
            // derivation and the AEAD associated data, so "image/JPEG" and
            // "image/jpeg" would be different keys for the same file.
            val rawMediaType = mime ?: guessMediaType(file.name)
            // A media type that will not canonicalize is refused rather than
            // guessed at: `m` is inside both the key derivation and the AEAD
            // associated data, so a sender and a receiver that canonicalized it
            // differently would not agree on the key at all.
            val mediaType =
                MarmotMediaType.canonicalize(rawMediaType)
                    ?: return Output.error("bad_args", "'$rawMediaType' is not a usable media type")
            val encrypted =
                try {
                    ctx.marmot.encryptMedia(resolved, file.readBytes(), mediaType, file.name)
                } catch (e: IllegalArgumentException) {
                    return Output.error("bad_args", e.message ?: "cannot encrypt this attachment")
                }

            val ciphertextHash = encrypted.ciphertextSha256.toHexKey()
            val uploadedUrl: String
            try {
                val auth =
                    BlossomAuth.createUploadAuth(
                        ciphertextHash,
                        encrypted.ciphertext.size.toLong(),
                        "Encrypted attachment",
                        ctx.signer,
                    )
                val result =
                    BlossomClient().upload(encrypted.ciphertext, "application/octet-stream", store.serverRoot, auth)
                if (result.sha256 != null && result.sha256 != ciphertextHash) {
                    return Output.error("hash_mismatch", "blossom returned ${result.sha256}, expected $ciphertextHash")
                }
                // The locator is the canonical BUD-01 URL for the ciphertext
                // hash, not whatever the server echoed: the hash is what a
                // receiver verifies, and a server-chosen URL could name
                // something else entirely.
                uploadedUrl = store.blossomFetchUrl(ciphertextHash)
            } catch (e: Exception) {
                return Output.error("upload_failed", "${e.message}")
            }

            val reference =
                EncryptedMediaReferenceV2(
                    locators = listOf(MediaLocatorV2(EncryptedMediaPolicyV2.INITIAL_LOCATOR_KIND, uploadedUrl)),
                    ciphertextSha256 = encrypted.ciphertextSha256,
                    plaintextSha256 = encrypted.plaintextSha256,
                    nonce = encrypted.nonce,
                    mediaType = mediaType,
                    filename = file.name,
                )

            val bundle = ctx.marmot.buildMediaMessage(resolved, reference, caption)
            val targets = ctx.marmotGroupRelays(resolved).ifEmpty { ctx.outboxRelays() }
            val ack = ctx.publish(bundle.outbound.signedEvent, targets)
            RawEventSupport.publishGuard(ack, bundle.outbound.signedEvent.id)?.let { return it }

            Output.emit(
                mapOf(
                    "group_id" to resolved,
                    "inner_event_id" to bundle.innerEvent.id,
                    "outer_event_id" to bundle.outbound.signedEvent.id,
                    "locator" to uploadedUrl,
                    "ciphertext_sha256" to ciphertextHash,
                    "plaintext_sha256" to encrypted.plaintextSha256.toHexKey(),
                    "m" to mediaType,
                    "filename" to file.name,
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }

    private suspend fun get(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val gid = args.positional(0, "gid")
        val eventId = args.positional(1, "event-id")
        val out = args.flag("out") ?: return Output.error("bad_args", "media get <gid> <event_id> --out PATH")
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val resolved = ctx.resolveGroupId(gid)
            ctx.syncIncoming()
            if (!ctx.marmot.isMember(resolved)) return Output.error("not_member", "not a member of group $resolved")

            val message =
                ctx.marmot
                    .loadStoredMessages(resolved)
                    .mapNotNull { Event.fromJsonOrNull(it) }
                    .firstOrNull { it.id == eventId }
                    ?: return Output.error("not_found", "no stored message $eventId in group $resolved")

            val reference =
                message.tags
                    .firstOrNull { it.isNotEmpty() && it[0] == "imeta" }
                    ?.let {
                        try {
                            EncryptedMediaV2.parseImetaTag(it)
                        } catch (e: IllegalArgumentException) {
                            return Output.error("bad_reference", e.message ?: "invalid imeta tag")
                        }
                    }
                    ?: return Output.error("no_media", "message $eventId carries no encrypted-media reference")

            val locator =
                reference.locators.firstOrNull { it.kind == EncryptedMediaPolicyV2.INITIAL_LOCATOR_KIND }
                    ?: return Output.error("no_locator", "no blossom-v1 locator in message $eventId")

            val ciphertext =
                try {
                    BlossomClient().download(locator.value)
                } catch (e: Exception) {
                    return Output.error("download_failed", "${e.message}")
                } ?: return Output.error("download_failed", "blob ${locator.value} not available")

            // The ciphertext hash is checked BEFORE decryption: it is what the
            // locator names, so a server that served something else is caught
            // here rather than as a confusing AEAD failure.
            if (!sha256(ciphertext).contentEquals(reference.ciphertextSha256)) {
                return Output.error("hash_mismatch", "the blob at ${locator.value} is not the one the message names")
            }

            val plaintext =
                try {
                    ctx.marmot.decryptMedia(resolved, reference, ciphertext)
                } catch (e: Exception) {
                    // A failure here is not "the file is corrupt": the media
                    // secret is per-epoch, so an attachment from an older epoch
                    // simply does not open under the current one.
                    return Output.error("decrypt_failed", "${e.message}")
                }

            File(out).writeBytes(plaintext)
            Output.emit(
                mapOf(
                    "group_id" to resolved,
                    "event_id" to eventId,
                    "locator" to locator.value,
                    "out" to out,
                    "bytes" to plaintext.size,
                    "m" to reference.mediaType,
                    "filename" to reference.filename,
                ),
            )
            return 0
        }
    }

    /** Extension-based guess, only as a default for `--mime`. */
    private fun guessMediaType(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
}
