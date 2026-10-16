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
package com.vitorpamplona.amethyst.desktop.update

import com.vitorpamplona.amethyst.commons.appupdate.DesktopPackage
import com.vitorpamplona.amethyst.commons.appupdate.UpdateMethod
import com.vitorpamplona.amethyst.commons.appupdate.UpdateTarget
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.shared.Platform
import java.io.File
import java.nio.file.Path

/**
 * How this copy of the app was installed, which decides how it updates. Read once at startup from
 * where the launcher sits and what the environment says (see [InstallChannel.detect]).
 */
sealed interface InstallChannel {
    /** `./gradlew run` or any JVM without jpackage's launcher: never updated. */
    data object Development : InstallChannel

    /** `Amethyst.app`, from the DMG. [homebrew] when the Homebrew cask owns it. */
    data class MacApp(
        val bundle: File,
        val platform: String,
        val homebrew: Boolean,
        val translocated: Boolean,
        val parentWritable: Boolean,
    ) : InstallChannel

    /** The MSI (directly or through winget), in Program Files. */
    data class WindowsMsi(
        val appDir: File,
        val launcher: File,
        val platform: String,
    ) : InstallChannel

    /** A jpackage app folder the user unpacked: the Windows zip or the Linux tarball. */
    data class PortableFolder(
        val appDir: File,
        val launcher: File,
        val platform: String,
        val packageType: DesktopPackage,
        val parentWritable: Boolean,
    ) : InstallChannel

    /** A single-file AppImage at [file] (the `APPIMAGE` path its runtime sets). */
    data class AppImage(
        val file: File,
        val platform: String,
        val dirWritable: Boolean,
    ) : InstallChannel

    /** The .deb or .rpm, owned by the system's package database. */
    data class SystemPackage(
        val launcher: File,
        val platform: String,
        val packageType: DesktopPackage,
    ) : InstallChannel

    /** Inside a Flatpak sandbox: Flatpak (the GitHub bundle, Orchard, Flathub) updates it. */
    data class Flatpak(
        val appId: String,
        val platform: String,
    ) : InstallChannel

    /** What the shared UI says and does for this channel. */
    val method: UpdateMethod
        get() =
            when (this) {
                Development -> UpdateMethod.DownloadPage
                is MacApp ->
                    when {
                        homebrew -> UpdateMethod.PackageManager("Homebrew", "brew upgrade --cask amethyst-nostr")
                        translocated -> UpdateMethod.MoveToApplications
                        parentWritable -> UpdateMethod.SelfInstall
                        else -> UpdateMethod.DownloadPage
                    }
                is WindowsMsi -> UpdateMethod.SystemInstaller
                is PortableFolder -> if (parentWritable) UpdateMethod.SelfInstall else UpdateMethod.DownloadPage
                is AppImage -> if (dirWritable) UpdateMethod.SelfInstall else UpdateMethod.DownloadPage
                is SystemPackage -> UpdateMethod.SystemInstaller
                is Flatpak -> UpdateMethod.PackageManager("Flatpak", "flatpak update $appId")
            }

    /** The release files this channel can apply; none when it only tells about a new version. */
    val target: UpdateTarget?
        get() =
            when (this) {
                Development -> null
                is MacApp -> UpdateTarget(platform, if (method == UpdateMethod.SelfInstall) listOf(DesktopPackage.DMG) else emptyList())
                is WindowsMsi -> UpdateTarget(platform, listOf(DesktopPackage.MSI))
                is PortableFolder -> UpdateTarget(platform, if (parentWritable) listOf(packageType) else emptyList())
                is AppImage -> UpdateTarget(platform, if (dirWritable) listOf(DesktopPackage.APPIMAGE) else emptyList())
                is SystemPackage -> UpdateTarget(platform, listOf(packageType))
                is Flatpak -> UpdateTarget(platform, emptyList())
            }

    /** The facts [detect] reads, gathered by [Probe.system] and faked in tests. */
    class Probe(
        val osName: String,
        val osArch: String,
        val appPath: String?,
        val env: Map<String, String>,
        val exists: (String) -> Boolean,
        val canWrite: (File) -> Boolean,
        /** DEB or RPM when that package database owns [File]; null otherwise. */
        val systemPackageOwning: (File) -> DesktopPackage?,
        /** Whether the Homebrew cask installed this app bundle. */
        val homebrewCaskOwns: (File) -> Boolean,
    ) {
        companion object {
            fun system() =
                Probe(
                    osName = System.getProperty("os.name", ""),
                    osArch = System.getProperty("os.arch", ""),
                    appPath = System.getProperty("jpackage.app-path"),
                    env = System.getenv(),
                    exists = { File(it).exists() },
                    canWrite = { it.canWrite() },
                    systemPackageOwning = ::queryPackageDatabases,
                    homebrewCaskOwns = { homebrewCaskOwns(it) },
                )
        }
    }

    companion object {
        const val HOMEBREW_CASK = "amethyst-nostr"

        fun detect(probe: Probe = Probe.system()): InstallChannel {
            val launcher = probe.appPath?.takeIf { it.isNotBlank() }?.let(::File) ?: return Development
            val os = probe.osName.lowercase()
            val arch = probe.osArch.lowercase()
            val is64BitArm = arch == "aarch64" || arch == "arm64"

            return when {
                os.contains("mac") || os.contains("darwin") -> {
                    // <bundle>/Contents/MacOS/Amethyst
                    val bundle = launcher.parentFile?.parentFile?.parentFile ?: return Development
                    // Compared with '/' whatever the host's separator, as the probe's paths may be.
                    val path = bundle.invariantSeparatorsPath
                    val parentWritable = bundle.parentFile?.let(probe.canWrite) == true
                    MacApp(
                        bundle = bundle,
                        platform = if (is64BitArm) Platform.DARWIN_ARM64 else Platform.DARWIN_X86_64,
                        // Only the copy the cask put there: a DMG copy elsewhere on the same Mac updates itself.
                        homebrew = probe.homebrewCaskOwns(bundle),
                        // Run from the quarantined copy, or straight off the mounted DMG (read-only).
                        // A writable /Volumes/… is an app on another drive, which updates in place.
                        translocated = path.contains("/AppTranslocation/") || (path.startsWith("/Volumes/") && !parentWritable),
                        parentWritable = parentWritable,
                    )
                }

                os.contains("win") -> {
                    val appDir = launcher.parentFile ?: return Development
                    val platform = if (is64BitArm) Platform.WINDOWS_AARCH64 else Platform.WINDOWS_X86_64
                    val programFiles =
                        listOf("ProgramFiles", "ProgramW6432", "ProgramFiles(x86)")
                            .mapNotNull { probe.env[it]?.takeIf(String::isNotBlank) }
                    if (programFiles.any { appDir.invariantSeparatorsPath.startsWith(File(it).invariantSeparatorsPath, ignoreCase = true) }) {
                        WindowsMsi(appDir, launcher, platform)
                    } else {
                        PortableFolder(appDir, launcher, platform, DesktopPackage.WINDOWS_ZIP, appDir.parentFile?.let(probe.canWrite) == true)
                    }
                }

                else -> {
                    val platform = if (is64BitArm) Platform.LINUX_AARCH64 else Platform.LINUX_X86_64
                    val flatpakId = probe.env["FLATPAK_ID"]?.takeIf(String::isNotBlank)
                    // The AppImage runtime sets APPIMAGE (the file) and APPDIR (its mount), and every
                    // process started from it inherits both: only a launcher inside APPDIR is that
                    // AppImage's, or a tarball started from another AppImage's terminal would replace it.
                    val appImage =
                        probe.env["APPIMAGE"]
                            ?.takeIf(String::isNotBlank)
                            ?.takeIf { probe.env["APPDIR"]?.takeIf(String::isNotBlank)?.let { mount -> launcher.isInside(File(mount)) } == true }
                            ?.let(::File)
                    when {
                        flatpakId != null || probe.exists("/.flatpak-info") ->
                            Flatpak(flatpakId ?: "com.vitorpamplona.amethyst.Amethyst", platform)
                        appImage != null ->
                            AppImage(appImage, platform, appImage.parentFile?.let(probe.canWrite) == true)
                        else -> {
                            // <appDir>/bin/Amethyst
                            val appDir = launcher.parentFile?.parentFile ?: return Development
                            when (val owner = probe.systemPackageOwning(launcher)) {
                                null -> PortableFolder(appDir, launcher, platform, DesktopPackage.LINUX_TARBALL, appDir.parentFile?.let(probe.canWrite) == true)
                                else -> SystemPackage(launcher, platform, owner)
                            }
                        }
                    }
                }
            }
        }

        private val HOMEBREW_PREFIXES = listOf("/opt/homebrew", "/usr/local")

        /**
         * Homebrew keeps a link to each app it installed under `Caskroom/<cask>/<version>/`, pointing
         * at where it put the app (`/Applications/Amethyst.app` unless `--appdir` said otherwise). The
         * cask owns [bundle] only when one of those links leads to it: a copy from the DMG elsewhere on
         * the same Mac is not Homebrew's.
         */
        internal fun homebrewCaskOwns(
            bundle: File,
            prefixes: List<String> = HOMEBREW_PREFIXES,
        ): Boolean {
            // toRealPath, not canonicalFile: on Windows the JDK's canonical path does not follow links.
            val target = bundle.realPathOrNull() ?: return false
            return prefixes.any { prefix ->
                File("$prefix/Caskroom/$HOMEBREW_CASK")
                    .listFiles()
                    .orEmpty()
                    .filter { it.isDirectory && !it.name.startsWith(".") }
                    .flatMap { version -> version.listFiles().orEmpty().filter { it.name.endsWith(".app") } }
                    .any { link -> link.realPathOrNull() == target }
            }
        }

        private fun File.realPathOrNull(): Path? = runCatching { toPath().toRealPath() }.getOrNull()

        private fun File.isInside(dir: File) = toPath().normalize().startsWith(dir.toPath().normalize())

        /** Asks dpkg, then rpm, whether a package owns [file]. */
        private fun queryPackageDatabases(file: File): DesktopPackage? {
            if (runQuietly("dpkg-query", "-S", file.absolutePath)) return DesktopPackage.DEB
            if (runQuietly("rpm", "-qf", file.absolutePath)) return DesktopPackage.RPM
            return null
        }
    }
}
