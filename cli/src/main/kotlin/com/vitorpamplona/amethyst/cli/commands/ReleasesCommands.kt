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
import com.vitorpamplona.amethyst.commons.appupdate.DesktopAssetNames
import com.vitorpamplona.amethyst.commons.appupdate.DesktopRelease
import com.vitorpamplona.amethyst.commons.appupdate.DesktopReleaseEvents
import com.vitorpamplona.amethyst.commons.appupdate.HashedDesktopAsset
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import java.io.File
import java.security.MessageDigest

/**
 * `amy releases publish` — announce a desktop release to the app's updater: hashes the
 * release files in a folder and signs the NIP-82 events the desktop app installs from (one kind
 * 3063 asset per file, the kind 30063 release naming them, and with `--listing` the kind 32267
 * app). Run it with Amethyst's key after the GitHub Release assets exist (RELEASE_OPS.md).
 *
 * Thin assembly: file naming, event shapes and the app id are commons' `appupdate`.
 */
object ReleasesCommands {
    val USAGE: String =
        """
        |Desktop releases (the desktop app's self-update, NIP-82):
        |  releases publish DIR --version X.Y.Z          hash the amethyst-desktop-* files in DIR and
        |        [--notes-file F] [--listing]            publish their signed asset events and the
        |        [--url-base URL] [--relay URL[,URL]]    release that names them (--listing also
        |        [--dry-run] [--any-key]                 publishes the app listing). URLs default to
        |                                                the GitHub Release of vX.Y.Z; relays to the
        |                                                ones the app reads. --dry-run signs and
        |                                                prints without publishing. Refuses a key
        |                                                other than Amethyst's unless --any-key.
        """.trimMargin()

    suspend fun dispatch(
        dataDir: DataDir,
        tail: Array<String>,
    ): Int = route("releases", tail, "releases publish DIR --version X.Y.Z …", mapOf("publish" to { publish(dataDir, it) }), USAGE)

    private suspend fun publish(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val dir = File(args.positional(0, "DIR"))
        val version = args.requireFlag("version").removePrefix("v")
        val notes = args.flag("notes-file")?.let { File(it).readText() } ?: ""
        val withListing = args.bool("listing")
        val urlBase = args.flag("url-base")?.trimEnd('/')
        val relayFlag = args.flag("relay")
        val dryRun = args.bool("dry-run")
        val anyKey = args.bool("any-key")
        args.rejectUnknown()

        if (!dir.isDirectory) return Output.error("bad_args", "not a directory: $dir")
        val files =
            dir
                .listFiles()
                .orEmpty()
                .sortedBy { it.name }
                .mapNotNull { file -> DesktopAssetNames.classify(file.name, version)?.let { file to it } }
        if (files.isEmpty()) return Output.error("bad_args", "no amethyst-desktop-$version-* release files in $dir")

        val relays: Set<NormalizedRelayUrl> =
            (relayFlag?.split(',')?.map(String::trim) ?: DesktopRelease.RELAYS)
                .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
                .toSet()
        if (relays.isEmpty()) return Output.error("bad_args", "no valid relay")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            if (ctx.signer.pubKey != DesktopRelease.PUBLISHER && !anyKey) {
                return Output.error("wrong_key", "the app installs only releases signed by ${DesktopRelease.PUBLISHER}; this account is ${ctx.signer.pubKey} (pass --any-key to sign anyway)")
            }

            val hashed =
                files.map { (file, classified) ->
                    HashedDesktopAsset(
                        file = classified,
                        sha256 = sha256(file),
                        sizeInBytes = file.length(),
                        url = urlBase?.let { "$it/${file.name}" } ?: DesktopRelease.downloadUrl(version, file.name),
                    )
                }
            val assets = hashed.map { ctx.signer.sign(DesktopReleaseEvents.asset(it, version)) }
            val release = ctx.signer.sign(DesktopReleaseEvents.release(ctx.signer.pubKey, version, notes, assets, relays.first()))
            val listing = if (withListing) ctx.signer.sign(DesktopReleaseEvents.listing()) else null

            val events: List<Event> = assets + release + listOfNotNull(listing)
            val published = mutableMapOf<String, List<String>>()
            if (!dryRun) {
                for (event in events) {
                    val ack = ctx.publish(event, relays)
                    RawEventSupport.publishGuard(ack, event.id)?.let { return it }
                    published[event.id] = ack.filterValues { it.accepted }.keys.map { it.url }
                }
            }

            Output.emit(
                mapOf(
                    "app_id" to DesktopRelease.APP_ID,
                    "version" to version,
                    "release_id" to release.id,
                    "listing_id" to listing?.id,
                    "assets" to
                        hashed.zip(assets).map { (h, event) ->
                            mapOf(
                                "file" to h.file.filename,
                                "platform" to h.file.platform,
                                "mime_type" to h.file.packageType.mimeType,
                                "sha256" to h.sha256,
                                "size_bytes" to h.sizeInBytes,
                                "url" to h.url,
                                "event_id" to event.id,
                            )
                        },
                    "dry_run" to dryRun,
                    "events" to if (dryRun) events.map { it.toJson() } else null,
                    "published_to" to published,
                ),
            )
            return 0
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
