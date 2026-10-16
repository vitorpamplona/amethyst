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
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstallChannelTest {
    private fun probe(
        os: String,
        appPath: String?,
        arch: String = "amd64",
        env: Map<String, String> = emptyMap(),
        existing: Set<String> = emptySet(),
        writable: Boolean = true,
        owner: DesktopPackage? = null,
        caskApps: Set<String> = emptySet(),
    ) = InstallChannel.Probe(
        osName = os,
        osArch = arch,
        appPath = appPath,
        env = env,
        exists = { it in existing },
        canWrite = { writable },
        systemPackageOwning = { owner },
        homebrewCaskOwns = { it.invariantSeparatorsPath in caskApps },
    )

    @Test
    fun aGradleRunHasNothingToUpdate() {
        val channel = InstallChannel.detect(probe("Linux", appPath = null))
        assertEquals(InstallChannel.Development, channel)
        assertNull(channel.target)
    }

    @Test
    fun macAppFromTheDmg() {
        val channel = InstallChannel.detect(probe("Mac OS X", "/Applications/Amethyst.app/Contents/MacOS/Amethyst", arch = "aarch64"))
        val mac = assertIs<InstallChannel.MacApp>(channel)
        assertEquals(File("/Applications/Amethyst.app"), mac.bundle)
        assertEquals(UpdateMethod.SelfInstall, channel.method)
        assertEquals("darwin-arm64", channel.target?.platform)
        assertEquals(listOf(DesktopPackage.DMG), channel.target?.packages)
    }

    @Test
    fun macAppOwnedByHomebrewOnlyTellsTheCommand() {
        val channel =
            InstallChannel.detect(
                probe("Mac OS X", "/Applications/Amethyst.app/Contents/MacOS/Amethyst", arch = "aarch64", caskApps = setOf("/Applications/Amethyst.app")),
            )
        assertEquals(UpdateMethod.PackageManager("Homebrew", "brew upgrade --cask amethyst-nostr"), channel.method)
        assertEquals(emptyList(), channel.target?.packages)
    }

    /** A DMG copy on a Mac that also has the cask: the cask's app is elsewhere, so this one updates itself. */
    @Test
    fun macAppBesideTheHomebrewOneUpdatesItself() {
        val channel =
            InstallChannel.detect(
                probe("Mac OS X", "/Users/me/Applications/Amethyst.app/Contents/MacOS/Amethyst", arch = "aarch64", caskApps = setOf("/Applications/Amethyst.app")),
            )
        assertEquals(UpdateMethod.SelfInstall, channel.method)
        assertEquals(listOf(DesktopPackage.DMG), channel.target?.packages)
    }

    /** Homebrew's record of the cask is a link from `Caskroom/<cask>/<version>/` to the app it installed. */
    @Test
    fun theCaskOwnsOnlyTheAppItsLinkLeadsTo() {
        val root = Files.createTempDirectory("brew").toFile()
        try {
            val installed = File(root, "Applications/Amethyst.app").apply { mkdirs() }
            val other = File(root, "Elsewhere/Amethyst.app").apply { mkdirs() }
            val version = File(root, "prefix/Caskroom/amethyst-nostr/1.18.0").apply { mkdirs() }
            Files.createSymbolicLink(File(version, "Amethyst.app").toPath(), installed.toPath())
            val prefixes = listOf(File(root, "prefix").path, File(root, "missing").path)

            assertTrue(InstallChannel.homebrewCaskOwns(installed, prefixes))
            assertFalse(InstallChannel.homebrewCaskOwns(other, prefixes))
            assertFalse(InstallChannel.homebrewCaskOwns(installed, listOf(File(root, "missing").path)))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun translocatedMacAppAsksToBeMoved() {
        val channel = InstallChannel.detect(probe("Mac OS X", "/private/var/folders/x/AppTranslocation/ABC/d/Amethyst.app/Contents/MacOS/Amethyst"))
        assertEquals(UpdateMethod.MoveToApplications, channel.method)
        val fromDmg = InstallChannel.detect(probe("Mac OS X", "/Volumes/Amethyst/Amethyst.app/Contents/MacOS/Amethyst", writable = false))
        assertEquals(UpdateMethod.MoveToApplications, fromDmg.method)
    }

    /** /Volumes is also where other drives mount: an app kept on a writable one updates in place. */
    @Test
    fun macAppOnAnotherDriveUpdatesInPlace() {
        val channel = InstallChannel.detect(probe("Mac OS X", "/Volumes/External/Applications/Amethyst.app/Contents/MacOS/Amethyst"))
        assertEquals(UpdateMethod.SelfInstall, channel.method)
    }

    @Test
    fun macAppInAFolderTheUserCannotWriteOnlyLinks() {
        val channel = InstallChannel.detect(probe("Mac OS X", "/Applications/Amethyst.app/Contents/MacOS/Amethyst", writable = false))
        assertEquals(UpdateMethod.DownloadPage, channel.method)
    }

    // Windows paths with forward slashes, which Windows takes too: java.io.File splits on the host's
    // separator, and these tests run on Linux and macOS as well.
    @Test
    fun windowsMsiInProgramFiles() {
        val channel =
            InstallChannel.detect(probe("Windows 11", "C:/Program Files/Amethyst/Amethyst.exe", env = mapOf("ProgramFiles" to "C:/Program Files")))
        assertIs<InstallChannel.WindowsMsi>(channel)
        assertEquals(UpdateMethod.SystemInstaller, channel.method)
        assertEquals("windows-x86_64", channel.target?.platform)
        assertEquals(listOf(DesktopPackage.MSI), channel.target?.packages)
    }

    @Test
    fun windowsZipAnywhereElse() {
        val channel =
            InstallChannel.detect(
                probe("Windows 11", "C:/Users/me/Apps/Amethyst/Amethyst.exe", arch = "aarch64", env = mapOf("ProgramFiles" to "C:/Program Files")),
            )
        val portable = assertIs<InstallChannel.PortableFolder>(channel)
        assertEquals(DesktopPackage.WINDOWS_ZIP, portable.packageType)
        assertEquals("windows-aarch64", channel.target?.platform)
        assertEquals(UpdateMethod.SelfInstall, channel.method)
    }

    @Test
    fun linuxAppImage() {
        val channel =
            InstallChannel.detect(
                probe("Linux", "/tmp/.mount_Amethyx/usr/bin/Amethyst", env = mapOf("APPIMAGE" to "/home/me/Apps/amethyst.AppImage", "APPDIR" to "/tmp/.mount_Amethyx")),
            )
        val appImage = assertIs<InstallChannel.AppImage>(channel)
        assertEquals(File("/home/me/Apps/amethyst.AppImage"), appImage.file)
        assertEquals(listOf(DesktopPackage.APPIMAGE), channel.target?.packages)
    }

    /**
     * Started from a terminal or IDE that is itself an AppImage, the app inherits that one's APPIMAGE
     * and APPDIR. It is not that file, and must never replace it.
     */
    @Test
    fun anInheritedAppImageIsNotThisApp() {
        val channel =
            InstallChannel.detect(
                probe(
                    "Linux",
                    "/home/me/Apps/Amethyst/bin/Amethyst",
                    env = mapOf("APPIMAGE" to "/home/me/Apps/VSCode.AppImage", "APPDIR" to "/tmp/.mount_VSCodeX"),
                ),
            )
        val portable = assertIs<InstallChannel.PortableFolder>(channel)
        assertEquals(DesktopPackage.LINUX_TARBALL, portable.packageType)
    }

    @Test
    fun linuxFlatpakIsUpdatedByFlatpak() {
        val channel = InstallChannel.detect(probe("Linux", "/app/bin/Amethyst", env = mapOf("FLATPAK_ID" to "com.vitorpamplona.amethyst.Amethyst")))
        assertEquals(UpdateMethod.PackageManager("Flatpak", "flatpak update com.vitorpamplona.amethyst.Amethyst"), channel.method)
    }

    @Test
    fun linuxDebAndRpm() {
        val deb = InstallChannel.detect(probe("Linux", "/opt/amethyst/bin/Amethyst", arch = "aarch64", owner = DesktopPackage.DEB))
        assertEquals(UpdateMethod.SystemInstaller, deb.method)
        assertEquals("linux-aarch64", deb.target?.platform)
        assertEquals(listOf(DesktopPackage.DEB), deb.target?.packages)

        val rpm = InstallChannel.detect(probe("Linux", "/opt/amethyst/bin/Amethyst", owner = DesktopPackage.RPM))
        assertEquals(listOf(DesktopPackage.RPM), rpm.target?.packages)
    }

    @Test
    fun linuxTarball() {
        val channel = InstallChannel.detect(probe("Linux", "/home/me/Amethyst/bin/Amethyst"))
        val portable = assertIs<InstallChannel.PortableFolder>(channel)
        assertEquals(File("/home/me/Amethyst"), portable.appDir)
        assertEquals(listOf(DesktopPackage.LINUX_TARBALL), channel.target?.packages)
    }
}
