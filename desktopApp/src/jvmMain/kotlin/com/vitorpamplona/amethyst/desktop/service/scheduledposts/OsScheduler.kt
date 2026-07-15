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
package com.vitorpamplona.amethyst.desktop.service.scheduledposts

import com.vitorpamplona.quartz.utils.Log
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * Registers/unregisters a native OS timer job that relaunches this app in the
 * headless `--publish-scheduled` mode roughly every 5 minutes, so scheduled posts
 * fire even when the desktop app is closed.
 *
 * Per-OS backend:
 *  - macOS: a launchd LaunchAgent plist (`StartInterval` 300).
 *  - Windows: a `schtasks` per-minute (interval 5) task that runs a hidden `.vbs`
 *    launcher (so no console window flashes).
 *  - Linux: a systemd `--user` service+timer (`OnUnitActiveSec=5min`); falls back
 *    to a marker-block crontab entry when systemd is unavailable.
 *
 * [appLaunchCommand] is the ABSOLUTE argv that invokes this app in headless mode
 * (see [resolveAppLaunchCommand]). In dev (`./gradlew run`) there is no stable app
 * binary, so callers pass `null` there and construct the scheduler with an empty
 * command; every method then becomes a logged no-op.
 *
 * All process invocations use argv arrays (never `sh -c` with interpolation), and
 * every file we write (plist / vbs / unit) is owner-only (0600) with a
 * parent-directory writability check.
 */
class OsScheduler(
    private val appLaunchCommand: List<String>,
) {
    private val os: HostOs = detectOs()

    /** True when there is no packaged binary to point the OS job at (dev mode). */
    private val isNoOp: Boolean = appLaunchCommand.isEmpty()

    fun ensureRegistered() {
        if (isNoOp) {
            Log.w(TAG) { "scheduled-post OS job not registered: no packaged app binary in dev mode" }
            return
        }
        val bad = appLaunchCommand.firstOrNull { hasControlChars(it) }
        if (bad != null) {
            Log.e(TAG) { "Refusing to register: launch command contains control characters" }
            return
        }
        try {
            when (os) {
                HostOs.MAC -> registerMac()
                HostOs.WINDOWS -> registerWindows()
                HostOs.LINUX -> registerLinux()
                HostOs.UNKNOWN -> Log.w(TAG) { "Unsupported OS; scheduled-post OS job not registered" }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register scheduled-post OS job", e)
        }
    }

    fun unregister() {
        if (isNoOp) return
        try {
            when (os) {
                HostOs.MAC -> unregisterMac()
                HostOs.WINDOWS -> unregisterWindows()
                HostOs.LINUX -> unregisterLinux()
                HostOs.UNKNOWN -> {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister scheduled-post OS job", e)
        }
    }

    fun isRegistered(): Boolean {
        if (isNoOp) return false
        return try {
            when (os) {
                HostOs.MAC -> isRegisteredMac()
                HostOs.WINDOWS -> isRegisteredWindows()
                HostOs.LINUX -> isRegisteredLinux()
                HostOs.UNKNOWN -> false
            }
        } catch (e: Exception) {
            Log.w(TAG) { "Failed to query scheduled-post OS job: ${e.message}" }
            false
        }
    }

    // ---------------------------------------------------------------------------
    // macOS — launchd LaunchAgent
    // ---------------------------------------------------------------------------

    private fun uid(): String = run(listOf("id", "-u")).stdout.trim().ifEmpty { "0" }

    private fun macPlistFile(): File = File(System.getProperty("user.home"), "Library/LaunchAgents/$LABEL.plist")

    private fun registerMac() {
        val plist = macPlistFile()
        if (!parentDirSafe(plist)) {
            Log.e(TAG) { "Refusing to write plist: ${plist.parentFile} is group/world-writable" }
            return
        }
        writeOwnerOnly(plist, buildPlist())

        val uid = uid()
        // Best-effort teardown of any prior registration; ignore failure.
        run(listOf("launchctl", "bootout", "gui/$uid/$LABEL"))
        val res = run(listOf("launchctl", "bootstrap", "gui/$uid", plist.absolutePath))
        if (res.exit != 0) {
            Log.w(TAG) { "launchctl bootstrap exited ${res.exit}: ${res.stderr.trim()}" }
        } else {
            Log.i(TAG) { "Registered launchd agent $LABEL" }
        }
    }

    private fun unregisterMac() {
        val uid = uid()
        run(listOf("launchctl", "bootout", "gui/$uid/$LABEL"))
        val plist = macPlistFile()
        if (plist.exists() && !plist.delete()) {
            Log.w(TAG) { "Failed to delete plist $plist" }
        }
    }

    private fun isRegisteredMac(): Boolean {
        val uid = uid()
        return run(listOf("launchctl", "print", "gui/$uid/$LABEL")).exit == 0
    }

    private fun buildPlist(): String {
        val argsXml =
            appLaunchCommand.joinToString(separator = "\n") { "        <string>${xmlEscape(it)}</string>" }
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>${xmlEscape(LABEL)}</string>
    <key>ProgramArguments</key>
    <array>
$argsXml
    </array>
    <key>StartInterval</key>
    <integer>$INTERVAL_SECONDS</integer>
    <key>RunAtLoad</key>
    <false/>
    <key>ProcessType</key>
    <string>Background</string>
    <key>EnvironmentVariables</key>
    <dict>
        <key>LANG</key>
        <string>en_US.UTF-8</string>
        <key>LC_ALL</key>
        <string>en_US.UTF-8</string>
    </dict>
</dict>
</plist>
"""
    }

    // ---------------------------------------------------------------------------
    // Windows — schtasks + hidden VBS launcher
    // ---------------------------------------------------------------------------

    private fun windowsVbsFile(): File {
        val base = System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
        return File(File(base, "Amethyst"), "amethyst-scheduler-launch.vbs")
    }

    private fun registerWindows() {
        val vbs = windowsVbsFile()
        vbs.parentFile?.mkdirs()
        if (!parentDirSafe(vbs)) {
            Log.e(TAG) { "Refusing to write vbs: ${vbs.parentFile} is group/world-writable" }
            return
        }
        writeOwnerOnly(vbs, buildVbs())

        // /tr must be a single string; wscript runs the .vbs which in turn spawns
        // the app hidden (window style 0), avoiding a console flash.
        val tr = "wscript.exe \"${vbs.absolutePath}\""
        val res =
            run(
                listOf(
                    "schtasks",
                    "/create",
                    "/sc",
                    "minute",
                    "/mo",
                    "5",
                    "/tn",
                    TASK_NAME,
                    "/tr",
                    tr,
                    "/f",
                ),
            )
        if (res.exit != 0) {
            Log.w(TAG) { "schtasks /create exited ${res.exit}: ${res.stderr.trim()}" }
        } else {
            Log.i(TAG) { "Registered scheduled task $TASK_NAME" }
        }
    }

    private fun unregisterWindows() {
        run(listOf("schtasks", "/delete", "/tn", TASK_NAME, "/f"))
        val vbs = windowsVbsFile()
        if (vbs.exists() && !vbs.delete()) {
            Log.w(TAG) { "Failed to delete vbs $vbs" }
        }
    }

    private fun isRegisteredWindows(): Boolean = run(listOf("schtasks", "/query", "/tn", TASK_NAME)).exit == 0

    private fun buildVbs(): String {
        // WScript.Shell.Run(command, windowStyle=0 (hidden), waitOnReturn=False)
        val cmd = appLaunchCommand.joinToString(separator = " ") { "\"\"${it.replace("\"", "")}\"\"" }
        return """Set WshShell = CreateObject("WScript.Shell")
WshShell.Run "$cmd", 0, False
"""
    }

    // ---------------------------------------------------------------------------
    // Linux — systemd --user (service + timer), cron fallback
    // ---------------------------------------------------------------------------

    private fun systemdAvailable(): Boolean = File("/run/systemd/system").exists()

    private fun systemdDir(): File = File(System.getProperty("user.home"), ".config/systemd/user")

    private fun serviceFile(): File = File(systemdDir(), "$LINUX_UNIT.service")

    private fun timerFile(): File = File(systemdDir(), "$LINUX_UNIT.timer")

    private fun registerLinux() {
        if (systemdAvailable()) {
            registerSystemd()
        } else {
            registerCron()
        }
    }

    private fun unregisterLinux() {
        if (systemdAvailable()) {
            unregisterSystemd()
        }
        // Always attempt cron cleanup so a fallback entry from a prior boot is removed.
        unregisterCron()
    }

    private fun isRegisteredLinux(): Boolean =
        if (systemdAvailable()) {
            run(listOf("systemctl", "--user", "is-enabled", "$LINUX_UNIT.timer")).exit == 0
        } else {
            currentCrontab().contains(CRON_MARKER_BEGIN)
        }

    private fun registerSystemd() {
        val dir = systemdDir()
        dir.mkdirs()
        if (!parentDirSafe(serviceFile())) {
            Log.e(TAG) { "Refusing to write systemd unit: $dir is group/world-writable" }
            return
        }
        writeOwnerOnly(serviceFile(), buildService())
        writeOwnerOnly(timerFile(), buildTimer())

        run(listOf("systemctl", "--user", "daemon-reload"))
        val res = run(listOf("systemctl", "--user", "enable", "--now", "$LINUX_UNIT.timer"))
        if (res.exit != 0) {
            Log.w(TAG) { "systemctl enable --now exited ${res.exit}: ${res.stderr.trim()}" }
        }

        // App-closed firing needs the user to have lingering enabled; tolerate failure.
        val user = System.getProperty("user.name") ?: ""
        if (user.isNotEmpty()) {
            val linger = run(listOf("loginctl", "enable-linger", user))
            if (linger.exit != 0) {
                Log.w(TAG) {
                    "Could not enable-linger for $user (exit ${linger.exit}); scheduled posts will " +
                        "only fire while you are logged in. Run: loginctl enable-linger $user"
                }
            }
        }
        Log.i(TAG) { "Registered systemd --user timer $LINUX_UNIT.timer" }
    }

    private fun unregisterSystemd() {
        run(listOf("systemctl", "--user", "disable", "--now", "$LINUX_UNIT.timer"))
        if (serviceFile().exists() && !serviceFile().delete()) {
            Log.w(TAG) { "Failed to delete ${serviceFile()}" }
        }
        if (timerFile().exists() && !timerFile().delete()) {
            Log.w(TAG) { "Failed to delete ${timerFile()}" }
        }
        run(listOf("systemctl", "--user", "daemon-reload"))
    }

    private fun buildService(): String {
        val execStart = appLaunchCommand.joinToString(separator = " ") { systemdEscape(it) }
        return """[Unit]
Description=Amethyst scheduled-post publisher

[Service]
Type=oneshot
ExecStart=$execStart
"""
    }

    private fun buildTimer(): String =
        """[Unit]
Description=Amethyst scheduled-post publisher timer

[Timer]
OnBootSec=2min
OnUnitActiveSec=5min
Persistent=true

[Install]
WantedBy=timers.target
"""

    // --- cron fallback ---

    private fun currentCrontab(): String {
        val res = run(listOf("crontab", "-l"))
        // Exit 1 with "no crontab" is normal for a user without one yet.
        return if (res.exit == 0) res.stdout else ""
    }

    private fun registerCron() {
        val existing = currentCrontab()
        val stripped = stripCronBlock(existing)
        val cmd = appLaunchCommand.joinToString(separator = " ") { shellQuote(it) }
        val block =
            buildString {
                append(CRON_MARKER_BEGIN).append('\n')
                append("*/5 * * * * ").append(cmd).append('\n')
                append(CRON_MARKER_END).append('\n')
            }
        val next = (if (stripped.isBlank()) "" else stripped.trimEnd() + "\n") + block
        val res = runWithStdin(listOf("crontab", "-"), next)
        if (res.exit != 0) {
            Log.w(TAG) { "crontab install exited ${res.exit}: ${res.stderr.trim()}" }
        } else {
            Log.i(TAG) { "Registered cron fallback entry" }
        }
    }

    private fun unregisterCron() {
        val existing = currentCrontab()
        if (!existing.contains(CRON_MARKER_BEGIN)) return
        val stripped = stripCronBlock(existing)
        val res =
            if (stripped.isBlank()) {
                run(listOf("crontab", "-r"))
            } else {
                runWithStdin(listOf("crontab", "-"), stripped.trimEnd() + "\n")
            }
        if (res.exit != 0) {
            Log.w(TAG) { "crontab uninstall exited ${res.exit}: ${res.stderr.trim()}" }
        }
    }

    private fun stripCronBlock(crontab: String): String {
        if (!crontab.contains(CRON_MARKER_BEGIN)) return crontab
        val lines = crontab.lines()
        val out = StringBuilder()
        var inside = false
        for (line in lines) {
            when {
                line.trim() == CRON_MARKER_BEGIN -> inside = true
                line.trim() == CRON_MARKER_END -> inside = false
                !inside -> out.append(line).append('\n')
            }
        }
        return out.toString()
    }

    // ---------------------------------------------------------------------------
    // Shared helpers
    // ---------------------------------------------------------------------------

    private data class ProcResult(
        val exit: Int,
        val stdout: String,
        val stderr: String,
    )

    private fun run(argv: List<String>): ProcResult = runWithStdin(argv, null)

    private fun runWithStdin(
        argv: List<String>,
        stdin: String?,
    ): ProcResult =
        try {
            val proc = ProcessBuilder(argv).redirectErrorStream(false).start()
            if (stdin != null) {
                proc.outputStream.use { it.write(stdin.toByteArray(Charsets.UTF_8)) }
            } else {
                proc.outputStream.close()
            }
            val out = proc.inputStream.readBytes().toString(Charsets.UTF_8)
            val err = proc.errorStream.readBytes().toString(Charsets.UTF_8)
            proc.waitFor()
            ProcResult(proc.exitValue(), out, err)
        } catch (e: Exception) {
            Log.w(TAG) { "Command failed to run (${argv.firstOrNull()}): ${e.message}" }
            ProcResult(-1, "", e.message ?: "")
        }

    /** Writes [content] to [file] with owner read/write only (best-effort on Windows). */
    private fun writeOwnerOnly(
        file: File,
        content: String,
    ) {
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
        try {
            Files.setPosixFilePermissions(
                file.toPath(),
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        } catch (_: UnsupportedOperationException) {
            // Windows — POSIX perms not supported; ACLs default to owner.
        } catch (e: Exception) {
            Log.w(TAG) { "Failed to set 0600 on $file: ${e.message}" }
        }
    }

    /**
     * Refuses to write into a directory that is group- or world-writable (a
     * classic privilege-escalation vector: an attacker could swap our plist/unit
     * for one that runs their command). Best-effort — always safe on Windows
     * (POSIX perms unsupported → treated as safe).
     */
    private fun parentDirSafe(file: File): Boolean {
        val dir = file.parentFile ?: return true
        if (!dir.exists()) return true
        return try {
            val perms = Files.getPosixFilePermissions(dir.toPath())
            !perms.contains(PosixFilePermission.GROUP_WRITE) &&
                !perms.contains(PosixFilePermission.OTHERS_WRITE)
        } catch (_: UnsupportedOperationException) {
            true
        } catch (e: Exception) {
            Log.w(TAG) { "Could not check permissions on $dir: ${e.message}" }
            true
        }
    }

    private fun hasControlChars(s: String): Boolean = s.any { it.isISOControl() }

    private fun xmlEscape(s: String): String =
        buildString(s.length) {
            for (c in s) {
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    else -> append(c)
                }
            }
        }

    /** Escapes a token for a systemd ExecStart line (spaces/backslashes). */
    private fun systemdEscape(s: String): String =
        if (s.any { it == ' ' || it == '\\' || it == '"' }) {
            "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        } else {
            s
        }

    /** Single-quotes a token for a POSIX shell (cron reads /bin/sh). */
    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    private enum class HostOs { MAC, WINDOWS, LINUX, UNKNOWN }

    private fun detectOs(): HostOs {
        val name = System.getProperty("os.name")?.lowercase() ?: ""
        return when {
            name.contains("mac") || name.contains("darwin") -> HostOs.MAC
            name.contains("win") -> HostOs.WINDOWS
            name.contains("nux") || name.contains("nix") -> HostOs.LINUX
            else -> HostOs.UNKNOWN
        }
    }

    companion object {
        private const val TAG = "OsScheduler"

        /** Canonical label (macOS launchd / Windows schtasks folder+name). */
        const val LABEL = "com.vitorpamplona.amethyst.scheduler"
        private const val TASK_NAME = "Amethyst\\Scheduler"
        private const val LINUX_UNIT = "amethyst-scheduler"
        private const val INTERVAL_SECONDS = 300

        private const val CRON_MARKER_BEGIN = "# BEGIN amethyst-scheduler"
        private const val CRON_MARKER_END = "# END amethyst-scheduler"

        /**
         * The absolute argv that invokes this app in headless publish mode, or
         * `null` when running in dev (`./gradlew run`) where no stable app binary
         * exists. Callers pass `null` through as an empty command so the scheduler
         * no-ops rather than registering a job that points at gradle/java.
         *
         * `jpackage.app-path` is set by the JVM launcher inside a jpackage bundle
         * to the absolute path of the app executable.
         */
        fun resolveAppLaunchCommand(): List<String>? {
            val appPath = System.getProperty("jpackage.app-path")
            if (appPath.isNullOrBlank()) return null
            val f = File(appPath)
            if (!f.isAbsolute || !f.exists()) return null
            return listOf(f.absolutePath, "--publish-scheduled")
        }
    }
}
