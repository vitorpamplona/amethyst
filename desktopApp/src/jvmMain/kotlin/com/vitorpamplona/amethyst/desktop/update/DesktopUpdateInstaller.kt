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

import com.vitorpamplona.amethyst.commons.appupdate.AppUpdateInstaller
import com.vitorpamplona.amethyst.commons.appupdate.AvailableUpdate
import com.vitorpamplona.amethyst.commons.appupdate.DesktopPackage
import com.vitorpamplona.amethyst.commons.appupdate.UpdateFailure
import com.vitorpamplona.amethyst.commons.appupdate.UpdateInstallException
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipInputStream

/**
 * Applies a verified release file the way [channel] needs:
 *
 * - AppImage: the new file is renamed over the old one (safe while it runs: the running copy keeps
 *   its inode), then the app restarts.
 * - Windows zip, Linux tarball: unpacked next to the app folder; after the app quits, a helper
 *   swaps the folders and relaunches.
 * - DMG: mounted, its `Amethyst.app` checked with `codesign` (and against the running app's Team
 *   ID), copied next to the bundle; after the app quits, a helper swaps the bundles.
 * - MSI: after the app quits, a helper runs `msiexec` with UAC, then relaunches.
 * - .deb / .rpm: after the app quits, a helper installs it with PackageKit, then relaunches.
 *
 * The downloads go to [updatesDir], except where the new copy must sit on the install's filesystem
 * for the final rename.
 */
class DesktopUpdateInstaller(
    private val channel: InstallChannel,
    private val downloader: UpdateDownloader,
    private val updatesDir: File,
    /** Quits the app the way its Quit menu does (the windows close, then the JVM exits), so the helper can take over. */
    private val quit: () -> Unit,
) : AppUpdateInstaller {
    private sealed interface Staged {
        val version: String

        class ReplaceFile(
            override val version: String,
            val staged: File,
            val target: File,
        ) : Staged

        class SwapFolder(
            override val version: String,
            val current: File,
            val replacement: File,
            val relaunch: List<String>,
        ) : Staged

        class Msi(
            override val version: String,
            val msi: File,
            val launcher: File,
        ) : Staged

        class SystemPackage(
            override val version: String,
            val file: File,
            val launcher: File,
        ) : Staged
    }

    @Volatile
    private var staged: Staged? = null

    override suspend fun prepare(
        update: AvailableUpdate,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit,
    ) {
        val asset = update.asset ?: throw UpdateInstallException(UpdateFailure.INSTALL_FAILED, "Nothing to install")
        val filename = safeName(asset.filename ?: "amethyst-${update.version}.${asset.packageType?.extension ?: "bin"}")

        staged =
            withContext(Dispatchers.IO) {
                when (val c = channel) {
                    is InstallChannel.AppImage -> {
                        val dir = c.file.parentFile ?: throw notWritable(c.file)
                        if (!dir.canWrite()) throw notWritable(dir)
                        val file = downloader.download(asset, File(dir, ".${c.file.name}.update"), onProgress)
                        file.setExecutable(true, false)
                        Staged.ReplaceFile(update.version, file, c.file)
                    }

                    is InstallChannel.PortableFolder -> {
                        val parent = c.appDir.parentFile ?: throw notWritable(c.appDir)
                        if (!parent.canWrite()) throw notWritable(parent)
                        val work = File(parent, ".amethyst-update")
                        work.deleteRecursively()
                        val archive = downloader.download(asset, File(work, filename), onProgress)
                        val extracted = File(work, "extract")
                        when (c.packageType) {
                            DesktopPackage.WINDOWS_ZIP -> unzip(archive, extracted)
                            else -> untar(archive, extracted)
                        }
                        archive.delete()
                        val launcherPath = c.launcher.relativeTo(c.appDir).path
                        val replacement =
                            extracted.listFiles()?.firstOrNull { File(it, launcherPath).isFile }
                                ?: throw UpdateInstallException(UpdateFailure.INSTALL_FAILED, "No $launcherPath in the archive")
                        File(replacement, launcherPath).setExecutable(true, false)
                        Staged.SwapFolder(update.version, c.appDir, replacement, listOf(c.launcher.absolutePath))
                    }

                    is InstallChannel.MacApp -> {
                        val parent = c.bundle.parentFile ?: throw notWritable(c.bundle)
                        if (!parent.canWrite()) throw notWritable(parent)
                        val dmg = downloader.download(asset, File(updatesDir, filename), onProgress)
                        val replacement = File(parent, ".${c.bundle.name}.update")
                        stageFromDmg(dmg, c.bundle, replacement)
                        dmg.delete()
                        Staged.SwapFolder(update.version, c.bundle, replacement, listOf("open", c.bundle.absolutePath))
                    }

                    is InstallChannel.WindowsMsi ->
                        Staged.Msi(update.version, downloader.download(asset, File(updatesDir, filename), onProgress), c.launcher)

                    is InstallChannel.SystemPackage ->
                        Staged.SystemPackage(update.version, downloader.download(asset, File(updatesDir, filename), onProgress), c.launcher)

                    is InstallChannel.Flatpak, InstallChannel.Development ->
                        throw UpdateInstallException(UpdateFailure.INSTALL_FAILED, "$c is not updated by the app")
                }
            }
    }

    override fun applyAndRestart(update: AvailableUpdate) {
        val ready = staged?.takeIf { it.version == update.version } ?: throw UpdateInstallException(UpdateFailure.INSTALL_FAILED, "Update ${update.version} is not staged")
        val pid = ProcessHandle.current().pid()
        val log = File(updatesDir, "update.log").also { it.parentFile.mkdirs() }

        try {
            when (ready) {
                is Staged.ReplaceFile -> {
                    Files.move(ready.staged.toPath(), ready.target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                    val script = UpdateHelperScripts.writeUnix(updatesDir)
                    spawnDetached(UpdateHelperScripts.unixCommand(script, pid, "launch", emptyList(), listOf(ready.target.absolutePath)), log)
                }

                is Staged.SwapFolder ->
                    if (isWindows()) {
                        val script = UpdateHelperScripts.writeWindows(updatesDir)
                        spawnDetached(
                            UpdateHelperScripts.windowsCommand(
                                script,
                                pid,
                                "swap",
                                mapOf("Current" to ready.current.absolutePath, "Replacement" to ready.replacement.absolutePath),
                                File(ready.relaunch.single()),
                            ),
                            log,
                        )
                    } else {
                        val script = UpdateHelperScripts.writeUnix(updatesDir)
                        spawnDetached(
                            UpdateHelperScripts.unixCommand(script, pid, "swap", listOf(ready.current.absolutePath, ready.replacement.absolutePath), ready.relaunch),
                            log,
                        )
                    }

                is Staged.Msi -> {
                    val script = UpdateHelperScripts.writeWindows(updatesDir)
                    spawnDetached(UpdateHelperScripts.windowsCommand(script, pid, "msi", mapOf("Package" to ready.msi.absolutePath), ready.launcher), log)
                }

                is Staged.SystemPackage -> {
                    val script = UpdateHelperScripts.writeUnix(updatesDir)
                    spawnDetached(
                        UpdateHelperScripts.unixCommand(script, pid, "package", listOf(ready.file.absolutePath), listOf(ready.launcher.absolutePath)),
                        log,
                    )
                }
            }
        } catch (e: IOException) {
            throw UpdateInstallException(UpdateFailure.INSTALL_FAILED, e.message ?: "could not start the update", e)
        }

        Log.i(TAG) { "Quitting to finish the update to ${update.version}" }
        quit()
    }

    /**
     * Removes what an earlier update left: downloads never applied, the emptied unpack folder. Run
     * at startup, when no update is in flight.
     */
    fun cleanUp() {
        try {
            updatesDir.listFiles()?.filter { it.name != "update.log" }?.forEach { it.deleteRecursively() }
            when (val c = channel) {
                is InstallChannel.AppImage -> File(c.file.parentFile, ".${c.file.name}.update").delete()
                is InstallChannel.PortableFolder -> c.appDir.parentFile?.let { File(it, ".amethyst-update").deleteRecursively() }
                is InstallChannel.MacApp -> c.bundle.parentFile?.let { File(it, ".${c.bundle.name}.update").deleteRecursively() }
                else -> Unit
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not clean up an earlier update", e)
        }
    }

    /** Mounts [dmg], checks the app inside, and copies it to [replacement]. */
    private fun stageFromDmg(
        dmg: File,
        current: File,
        replacement: File,
    ) {
        val mountPoint = Files.createTempDirectory("amethyst-update-mnt").toFile()
        val (attached, attachOut) = runCapturing("hdiutil", "attach", "-nobrowse", "-readonly", "-noautoopen", "-mountpoint", mountPoint.absolutePath, dmg.absolutePath)
        if (attached != 0) throw UpdateInstallException(UpdateFailure.INSTALL_FAILED, "hdiutil attach failed: $attachOut")
        try {
            val newApp =
                mountPoint.listFiles()?.firstOrNull { it.name.endsWith(".app") }
                    ?: throw UpdateInstallException(UpdateFailure.INSTALL_FAILED, "No app in the disk image")

            // A signed install takes only an intact app signed by the same team, so it never turns
            // into an unsigned one. An unsigned install (a release built without the signing
            // secrets) has no team to compare: the signed release's SHA-256 already vouched for the
            // file, and codesign checks the new app only if it is signed at all.
            val currentTeam = teamIdentifier(current)
            val newTeam = teamIdentifier(newApp)
            if (currentTeam != null && currentTeam != newTeam) {
                throw UpdateInstallException(UpdateFailure.VERIFICATION_FAILED, "The new app is not signed by team $currentTeam")
            }
            if (newTeam != null) {
                val (verified, verifyOut) = runCapturing("codesign", "--verify", "--deep", "--strict", newApp.absolutePath)
                if (verified != 0) throw UpdateInstallException(UpdateFailure.VERIFICATION_FAILED, "codesign rejects the new app: $verifyOut")
            }

            replacement.deleteRecursively()
            val (copied, copyOut) = runCapturing("ditto", newApp.absolutePath, replacement.absolutePath, timeoutSec = 600)
            if (copied != 0) throw UpdateInstallException(UpdateFailure.INSTALL_FAILED, "ditto failed: $copyOut")
        } finally {
            runQuietly("hdiutil", "detach", mountPoint.absolutePath, "-force", timeoutSec = 60)
            mountPoint.delete()
        }
    }

    private fun teamIdentifier(app: File): String? {
        val (_, out) = runCapturing("codesign", "-dv", "--verbose=2", app.absolutePath)
        return out
            .lineSequence()
            .firstOrNull { it.startsWith("TeamIdentifier=") }
            ?.substringAfter('=')
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != "not set" }
    }

    private fun untar(
        archive: File,
        into: File,
    ) {
        into.mkdirs()
        val (code, out) = runCapturing("tar", "-xzf", archive.absolutePath, "-C", into.absolutePath, timeoutSec = 600)
        if (code != 0) throw UpdateInstallException(UpdateFailure.INSTALL_FAILED, "tar failed: $out")
        if (into.walkTopDown().any { Files.isSymbolicLink(it.toPath()) && !it.canonicalPath.startsWith(into.canonicalPath) }) {
            throw UpdateInstallException(UpdateFailure.VERIFICATION_FAILED, "The archive links outside its folder")
        }
    }

    companion object {
        private const val TAG = "DesktopUpdateInstaller"

        private fun isWindows() = System.getProperty("os.name", "").lowercase().startsWith("windows")

        private fun notWritable(file: File) = UpdateInstallException(UpdateFailure.NOT_WRITABLE, "Cannot write to $file")

        /** A file name from a release event, stripped of anything that could leave the folder. */
        internal fun safeName(name: String) =
            name
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .replace("..", "_")
                .ifBlank { "update" }

        /** Unzips [archive] into [into], refusing entries that would land outside it. */
        internal fun unzip(
            archive: File,
            into: File,
        ) {
            val root = into.canonicalFile
            root.mkdirs()
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val out = File(root, entry.name).canonicalFile
                    if (!out.toPath().startsWith(root.toPath())) {
                        throw UpdateInstallException(UpdateFailure.VERIFICATION_FAILED, "Archive entry ${entry.name} leaves the folder")
                    }
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
    }
}
