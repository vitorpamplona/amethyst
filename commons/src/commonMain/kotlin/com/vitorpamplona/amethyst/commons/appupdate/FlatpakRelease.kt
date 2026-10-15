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
package com.vitorpamplona.amethyst.commons.appupdate

/**
 * Amethyst's Flatpak as Orchard (https://github.com/Letdown2491/orchard) lists it: an OCI image
 * per architecture on ghcr, which the release workflow builds and pushes with Orchard's own
 * builder, and NIP-82 events signed by Amethyst's key that pin each image by digest. Orchard
 * installs only an image whose digest matches its signed asset.
 */
object FlatpakRelease {
    /** The Flatpak app id, Orchard's listing id (the APK's and the desktop's ids are others). */
    const val APP_ID = "com.vitorpamplona.amethyst.Amethyst"

    /** Where the release workflow pushes the images. */
    const val REGISTRY_REPOSITORY = "ghcr.io/vitorpamplona/amethyst-flatpak"

    /** The Flatpak architectures, as NIP-82 platforms. */
    val PLATFORMS = listOf("linux-x86_64", "linux-aarch64")

    /** The image tag the release workflow pushes for a version and platform: `1.20.0-linux-x86_64`. */
    fun tagFor(
        version: String,
        platform: String,
    ) = "$version-$platform"

    /** The Flatpak arch of a NIP-82 platform: `linux-x86_64` to `x86_64`. */
    fun refArch(platform: String) = platform.removePrefix("linux-")

    /** Reads the release bundles' names, `amethyst-desktop-<version>-linux-<x64|arm64>.flatpak`, as platforms. */
    fun platformOfBundle(
        filename: String,
        version: String,
    ): String? =
        when (filename) {
            "amethyst-desktop-$version-linux-x64.flatpak" -> "linux-x86_64"
            "amethyst-desktop-$version-linux-arm64.flatpak" -> "linux-aarch64"
            else -> null
        }

    /** Orchard's channel names: `stable`, as orchard-publish signs it, or `beta` for a prerelease. */
    fun channelFor(version: String) = if (DesktopReleaseEvents.channelFor(version) == "main") "stable" else "beta"
}

/** One pushed image, checked against the registry by `OciRegistry`. */
data class FlatpakImage(
    val platform: String,
    val version: String,
    /** `sha256:<hex>` of the image manifest. */
    val digest: String,
    /** The registry repository it was read from, e.g. `ghcr.io/vitorpamplona/amethyst-flatpak`. */
    val repository: String,
    /** The runtime ref from the image's Flatpak metadata, e.g. `org.freedesktop.Platform/x86_64/26.08`. */
    val runtime: String?,
) {
    /** The `oci` tag Orchard installs from: the digest form, never a tag. */
    val location get() = "docker://$repository@$digest"
}

/**
 * The listing half of Orchard's `*.publish.toml` (the file orchard-publish reads), as a plain map
 * from the caller's TOML reader: `[app]` for the text, `[release]` for the platforms and notes,
 * `[publish]` for the relays.
 */
data class OrchardConfig(
    val name: String,
    val summary: String,
    val description: String?,
    val icon: String?,
    val categories: List<String>,
    val license: String?,
    val repository: String?,
    val homepage: String?,
    val platforms: List<String>,
    val notes: String?,
    val relays: List<String>,
) {
    companion object {
        /** [descriptionFile] reads the `description-file` the TOML names, relative to it. */
        fun fromMap(
            map: Map<String, Any?>,
            descriptionFile: (String) -> String?,
        ): OrchardConfig {
            val app = map.section("app")
            val release = map.section("release")
            val publish = map.section("publish")
            return OrchardConfig(
                name = app.string("name") ?: DesktopRelease.APP_NAME,
                summary = app.string("summary").orEmpty(),
                description = app.string("description") ?: app.string("description-file")?.let(descriptionFile),
                icon = app.string("icon"),
                categories = app.strings("categories"),
                license = app.string("license"),
                repository = app.string("repository"),
                homepage = app.string("homepage"),
                platforms = release.strings("platforms").ifEmpty { FlatpakRelease.PLATFORMS },
                notes = release.string("notes"),
                relays = publish.strings("relays"),
            )
        }

        @Suppress("UNCHECKED_CAST")
        private fun Map<String, Any?>.section(key: String) = (this[key] as? Map<String, Any?>).orEmpty()

        private fun Map<String, Any?>.string(key: String) = this[key]?.toString()?.takeIf { it.isNotBlank() }

        private fun Map<String, Any?>.strings(key: String): List<String> = (this[key] as? List<*>)?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }.orEmpty()
    }
}
