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
package com.vitorpamplona.amethyst.commons.mavenReleases

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nipFCMavenRepositories.MavenCoordinate
import com.vitorpamplona.quartz.nipFCMavenRepositories.MavenModule
import com.vitorpamplona.quartz.nipFCMavenRepositories.MavenPublication
import com.vitorpamplona.quartz.nipFCMavenRepositories.snapshot.MavenSnapshotEvent

/** A Kotlin Multiplatform target family, read from a per-target module's artifactId suffix. */
enum class KmpPlatform {
    ANDROID,
    JVM,
    IOS,
    MACOS,
    TVOS,
    WATCHOS,
    LINUX,
    WINDOWS,
    JS,
    WASM,
    ;

    companion object {
        /**
         * The family of a per-target suffix as the Kotlin Gradle plugin names them (`jvm`,
         * `android`, `iosarm64`, `iossimulatorarm64`, `linuxx64`, `mingwx64`, `wasm-js`, …), or
         * null for a suffix that names no target (`bom`, `test`, `annotations`).
         */
        fun ofSuffix(suffix: String): KmpPlatform? =
            when {
                // Compose Multiplatform names its JVM target `desktop` and its iOS ones `uikit…`.
                suffix == "jvm" || suffix == "desktop" -> JVM
                suffix == "android" || suffix.startsWith("android-") || suffix.startsWith("androidnative") -> ANDROID
                suffix.startsWith("ios") || suffix.startsWith("uikit") -> IOS
                suffix.startsWith("macos") -> MACOS
                suffix.startsWith("tvos") -> TVOS
                suffix.startsWith("watchos") -> WATCHOS
                suffix.startsWith("linux") -> LINUX
                suffix.startsWith("mingw") -> WINDOWS
                suffix == "js" -> JS
                suffix.startsWith("wasm") -> WASM
                else -> null
            }
    }
}

/**
 * What a NIP-FC release or snapshot card shows, read once per event: the library, its version,
 * the platforms its modules cover and how to depend on it.
 */
@Immutable
class MavenReleaseSummary(
    event: MavenPublication,
) {
    val isSnapshot = event is MavenSnapshotEvent
    val isWellFormed = event.isWellFormed()
    val version: String? = event.version()
    val root: MavenModule? = event.rootModule()
    val coordinate: MavenCoordinate? = event.coordinate()

    /** The root `j` as written, for a card whose modules don't parse. */
    val rootKey: String? = root?.key ?: event.moduleKeys().firstOrNull()

    /** The target families the per-target modules cover, in [KmpPlatform] order. */
    val platforms: List<KmpPlatform>

    /** Modules that are neither the root nor a target: a BOM, test helpers, another artifact. */
    val otherModules: List<String>

    init {
        val found = HashSet<KmpPlatform>()
        val others = ArrayList<String>()
        val prefix = root?.artifactId?.let { "$it-" }
        event.modules().drop(1).forEach { module ->
            val suffix =
                if (prefix != null && module.groupId == root.groupId && module.artifactId.startsWith(prefix)) {
                    module.artifactId.substring(prefix.length)
                } else {
                    null
                }
            val platform = suffix?.let(KmpPlatform::ofSuffix)
            if (platform != null) {
                found.add(platform)
            } else {
                others.add(if (module.groupId == root?.groupId) module.artifactId else module.key)
            }
        }
        platforms = KmpPlatform.entries.filter { it in found }
        otherModules = others
    }

    private val manifest = event.manifest()

    /** The manifest's size in bytes, or null when the event gives none. */
    val manifestSize: Long? = manifest?.size?.takeIf { it >= 0 }

    /** Primary artifacts the event hashes, besides the manifest. Publishers may trim them. */
    val artifactCount: Int = event.hashes().count { !it.equals(manifest?.sha256, ignoreCase = true) }

    val servers: List<String> = event.servers()

    /** `build.gradle.kts`: the dependency line. */
    val dependency: String? = coordinate?.let { MavenSetup.dependency(it) }

    /** `settings.gradle.kts`: what lets Gradle resolve the group from Nostr. */
    val settings: String? = coordinate?.let { MavenSetup.settings(it.groupId) }
}

/** The lines a Gradle build needs to use a NIP-FC library through Magpie. */
object MavenSetup {
    /** The Magpie plugin release these snippets name. Bump it with Magpie's releases. */
    const val MAGPIE_VERSION = "0.2.0"

    fun dependency(coordinate: MavenCoordinate) = "implementation(\"$coordinate\")"

    /**
     * Magpie trusts the key the group's domain names as `releases@`, for the group and its
     * subgroups, so the block names the group itself.
     */
    fun settings(groupId: String) =
        """
        // settings.gradle.kts
        plugins {
            id("com.vitorpamplona.magpie") version "$MAGPIE_VERSION"
        }

        magpie {
            group("$groupId")
        }
        """.trimIndent()
}
