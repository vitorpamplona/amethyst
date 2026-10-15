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

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.appupdate.AndroidRelease
import com.vitorpamplona.amethyst.commons.appupdate.DesktopRelease
import com.vitorpamplona.amethyst.commons.appupdate.OrchardConfig
import com.vitorpamplona.amethyst.commons.appupdate.ReleaseBundles
import com.vitorpamplona.amethyst.commons.appupdate.ReleaseFilesException
import com.vitorpamplona.amethyst.commons.appupdate.SignedRelease
import com.vitorpamplona.amethyst.commons.appupdate.ZapstoreConfig
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import java.io.File

/**
 * `amy releases publish` — announce a release on Nostr (NIP-82), signed with Amethyst's key:
 * the desktop files for the desktop app's updater, the APKs for Zapstore as zsp
 * (https://github.com/zapstore/zsp) did, and the Flatpak images for Orchard as orchard-publish
 * (https://github.com/Letdown2491/orchard) did. Run after the GitHub Release assets exist
 * (RELEASE_OPS.md § Nostr releases).
 *
 * Thin assembly: file naming, APK inspection, registry checks, event shapes and Blossom mirroring
 * are commons' `appupdate` ([ReleaseBundles]); this reads the flags, `zapstore.yaml` and Orchard's
 * TOML, and publishes.
 */
object ReleasesCommands {
    val USAGE: String =
        """
        |Releases (NIP-82: the desktop app's updater, Zapstore and Orchard):
        |  releases publish DIR --version X.Y.Z          sign and publish the release of every app
        |        [--only desktop|android|flatpak]        whose files are in DIR: one kind:3063 asset
        |        [--notes-file F] [--listing]            per file and the kind:30063 release naming
        |        [--zapstore-yaml F] [--blossom URL]     them (--listing adds the kind:32267 app).
        |        [--android-flavor googleplay|fdroid]    Desktop: the amethyst-desktop-* files, under
        |        [--android-abis A[,A]|all]              com.vitorpamplona.amethyst.desktop. Android:
        |        [--orchard-toml F]                      the APKs of one flavor (googleplay) and ABI
        |        [--url-base URL] [--relay URL[,URL]]    list (arm64-v8a), checked with apksig and
        |        [--dry-run] [--any-key]                 tagged as zsp does (version_code, SDKs,
        |                                                apk_certificate_hash, supported_nip from
        |                                                --zapstore-yaml, default ./zapstore.yaml).
        |                                                --blossom mirrors the APKs (and the listing's
        |                                                icon and screenshots) there, as zsp does with
        |                                                https://cdn.zapstore.dev. Flatpak: for each
        |                                                linux-*.flatpak bundle in DIR, the image the
        |                                                release workflow pushed (tag X.Y.Z-linux-ARCH
        |                                                on ghcr), checked against the registry and
        |                                                signed as orchard-publish does, with the
        |                                                listing from --orchard-toml. URLs default to
        |                                                the GitHub Release of vX.Y.Z; relays to each
        |                                                app's. --dry-run signs and prints, uploading
        |                                                nothing. Refuses a key other than Amethyst's
        |                                                unless --any-key.
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
        val only = args.flag("only")
        val notes = args.flag("notes-file")?.let { File(it).readText() } ?: ""
        val withListing = args.bool("listing")
        val yaml = File(args.flag("zapstore-yaml") ?: "zapstore.yaml")
        val orchardToml = File(args.flag("orchard-toml") ?: ORCHARD_TOML)
        val blossom = args.flag("blossom")?.trimEnd('/')
        val flavorName = args.flag("android-flavor") ?: AndroidRelease.Flavor.GOOGLE_PLAY.fileTag
        val abisFlag = args.flag("android-abis") ?: "arm64-v8a"
        val urlBase = args.flag("url-base")
        val relayOverride =
            args
                .flag("relay")
                ?.split(',')
                ?.map(String::trim)
                ?.filter { RelayUrlNormalizer.normalizeOrNull(it) != null }
        val dryRun = args.bool("dry-run")
        val anyKey = args.bool("any-key")
        args.rejectUnknown()

        if (!dir.isDirectory) return Output.error("bad_args", "not a directory: $dir")
        if (only != null && only !in APPS) return Output.error("bad_args", "--only takes ${APPS.joinToString(", ")}")
        val flavor = AndroidRelease.Flavor.parse(flavorName) ?: return Output.error("bad_args", "--android-flavor takes googleplay or fdroid")
        val abis = if (abisFlag == "all") AndroidRelease.ABIS else abisFlag.split(',').map(String::trim)
        abis.firstOrNull { it !in AndroidRelease.ABIS }?.let { return Output.error("bad_args", "unknown ABI $it (expected ${AndroidRelease.ABIS.joinToString(",")})") }
        if (relayOverride?.isEmpty() == true) return Output.error("bad_args", "no valid relay")

        Context.open(dataDir).use { ctx ->
            ctx.prepare()
            val signer = ctx.signer
            if (signer.pubKey != DesktopRelease.PUBLISHER && !anyKey) {
                return Output.error("wrong_key", "releases must be signed by ${DesktopRelease.PUBLISHER}; this account is ${signer.pubKey} (pass --any-key to sign anyway)")
            }

            val releases = mutableListOf<SignedRelease>()
            try {
                if (only == null || only == "desktop") {
                    val assets = ReleaseBundles.desktopAssets(dir, version, urlBase)
                    if (assets.isNotEmpty()) {
                        releases += ReleaseBundles.signDesktop(signer, version, notes, assets, relayOverride ?: DesktopRelease.RELAYS, withListing)
                    }
                }
                if (only == null || only == "android") {
                    val apks = ReleaseBundles.androidApks(dir, version, flavor, abis, urlBase)
                    if (apks.isNotEmpty()) {
                        val config = if (yaml.isFile) ZapstoreConfig.fromMap(readYaml(yaml)) else ZapstoreConfig()
                        if (withListing && !yaml.isFile) return Output.error("bad_args", "--listing for the APK needs --zapstore-yaml (no $yaml)")
                        val mirrored =
                            apks.map { (file, apk) ->
                                if (blossom == null) apk else apk.copy(urls = apk.urls + ReleaseBundles.mirrorToBlossom(file, AndroidRelease.APK_MIME_TYPE, blossom, signer, dryRun = dryRun))
                            }
                        val media =
                            if (withListing) {
                                ReleaseBundles.listingMedia(config, yaml.absoluteFile.parentFile, blossom, signer, dryRun)
                            } else {
                                null
                            }
                        releases += ReleaseBundles.signAndroid(signer, version, notes, mirrored, config.supportedNips, relayOverride ?: AndroidRelease.RELAYS, media, config)
                    }
                }
                if (only == null || only == "flatpak") {
                    val images = ReleaseBundles.flatpakImages(dir, version)
                    if (images.isNotEmpty()) {
                        if (!orchardToml.isFile) return Output.error("bad_args", "the Flatpak needs --orchard-toml (no $orchardToml)")
                        val config = OrchardConfig.fromMap(readToml(orchardToml)) { File(orchardToml.absoluteFile.parentFile, it).takeIf(File::isFile)?.readText() }
                        releases += ReleaseBundles.signFlatpak(signer, notes, images, config, withListing, relayOverride ?: config.relays)
                    }
                }
            } catch (e: ReleaseFilesException) {
                return Output.error("bad_release_files", e.message ?: "bad release files")
            }
            if (releases.isEmpty()) return Output.error("bad_args", "no release files for $version in $dir")

            val published = mutableMapOf<String, Map<String, List<String>>>()
            if (!dryRun) {
                for (release in releases) {
                    val relays = release.relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()
                    published[release.appId] =
                        release.events.associate { event ->
                            val ack = ctx.publish(event, relays)
                            RawEventSupport.publishGuard(ack, event.id)?.let { return it }
                            event.id to ack.filterValues { it.accepted }.keys.map { it.url }
                        }
                }
            }

            Output.emit(
                mapOf(
                    "version" to version,
                    "dry_run" to dryRun,
                    "apps" to
                        releases.map { r ->
                            mapOf(
                                "app_id" to r.appId,
                                "release_ids" to r.releases.map { it.id },
                                "channel" to r.releases.first().channel(),
                                "listing_id" to r.listing?.id,
                                "assets" to r.files,
                                "relays" to r.relays,
                                "published_to" to published[r.appId],
                                "events" to if (dryRun) r.events.map { it.toJson() } else null,
                            )
                        },
                ),
            )
            return 0
        }
    }

    private const val ORCHARD_TOML = "desktopApp/packaging/orchard/com.vitorpamplona.amethyst.Amethyst.publish.toml"
    private val APPS = listOf("desktop", "android", "flatpak")

    @Suppress("UNCHECKED_CAST")
    internal fun readToml(file: File): Map<String, Any?> = TomlMapper().readValue(file, Map::class.java) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun readYaml(file: File): Map<String, Any?> = ObjectMapper(YAMLFactory()).readValue(file, Map::class.java) as Map<String, Any?>
}
