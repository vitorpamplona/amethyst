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

import java.io.File

/**
 * The scripts that finish an update once the app has quit: the running app holds its own files (on
 * Windows it locks them), so the swap happens after it exits. They take every path as an argument,
 * never pasted into the script, and they relaunch the app whether the update went in or not, so a
 * failed install leaves the user on the version they had.
 */
internal object UpdateHelperScripts {
    /**
     * `amethyst-update.sh PID MODE ARGS... -- CMD...`, for macOS and Linux:
     * - `launch`: only relaunch.
     * - `swap CURRENT REPLACEMENT`: move REPLACEMENT into CURRENT's place (CURRENT kept as `.old`
     *   until the relaunch, and put back if the move fails).
     * - `package FILE`: install a .deb or .rpm through PackageKit, or hand it to the desktop's
     *   software installer when PackageKit's `pkcon` is missing.
     */
    val UNIX =
        """
        |#!/bin/sh
        |# Finishes an Amethyst update once the app has quit. Written and started by the app.
        |pid="${'$'}1"; mode="${'$'}2"; shift 2
        |i=0
        |while kill -0 "${'$'}pid" 2>/dev/null; do
        |  i=${'$'}((i + 1)); [ "${'$'}i" -gt 600 ] && exit 1
        |  sleep 0.2
        |done
        |backup=""
        |case "${'$'}mode" in
        |  launch) ;;
        |  swap)
        |    current="${'$'}1"; replacement="${'$'}2"; shift 2
        |    backup="${'$'}current.old"
        |    rm -rf "${'$'}backup"
        |    if mv "${'$'}current" "${'$'}backup"; then
        |      if ! mv "${'$'}replacement" "${'$'}current"; then
        |        echo "could not move the new version in; keeping the old one"
        |        mv "${'$'}backup" "${'$'}current"
        |        backup=""
        |      fi
        |    else
        |      echo "could not move the old version aside"
        |      backup=""
        |    fi
        |    ;;
        |  package)
        |    file="${'$'}1"; shift
        |    if command -v pkcon >/dev/null 2>&1; then
        |      pkcon -y install-local "${'$'}file" || echo "pkcon could not install ${'$'}file"
        |    else
        |      exec xdg-open "${'$'}file"
        |    fi
        |    ;;
        |  *) echo "unknown mode ${'$'}mode"; exit 2 ;;
        |esac
        |[ "${'$'}1" = "--" ] && shift
        |"${'$'}@" >/dev/null 2>&1 &
        |if [ -n "${'$'}backup" ]; then
        |  sleep 10
        |  rm -rf "${'$'}backup"
        |fi
        |exit 0
        |
        """.trimMargin()

    /**
     * `amethyst-update.ps1 -ProcessId N -Mode msi|swap|launch ... -Launch EXE`, for Windows:
     * - `msi -Package FILE`: run the MSI with UAC (it is a per-machine install), then relaunch.
     * - `swap -Current DIR -Replacement DIR`: move the new folder in, as in the Unix script.
     */
    val WINDOWS =
        """
        |param(
        |  [Parameter(Mandatory = ${'$'}true)][int]${'$'}ProcessId,
        |  [Parameter(Mandatory = ${'$'}true)][string]${'$'}Mode,
        |  [string]${'$'}Current,
        |  [string]${'$'}Replacement,
        |  [string]${'$'}Package,
        |  [Parameter(Mandatory = ${'$'}true)][string]${'$'}Launch
        |)
        |# Finishes an Amethyst update once the app has quit. Written and started by the app.
        |try { Wait-Process -Id ${'$'}ProcessId -Timeout 120 -ErrorAction SilentlyContinue } catch {}
        |${'$'}backup = ${'$'}null
        |if (${'$'}Mode -eq 'msi') {
        |  try {
        |    ${'$'}msiArgs = @('/i', ('"{0}"' -f ${'$'}Package), '/passive', '/norestart')
        |    ${'$'}p = Start-Process -FilePath 'msiexec.exe' -ArgumentList ${'$'}msiArgs -Verb RunAs -Wait -PassThru
        |    Write-Output "msiexec exited with ${'$'}(${'$'}p.ExitCode)"
        |  } catch {
        |    Write-Output "msiexec did not run: ${'$'}_"
        |  }
        |} elseif (${'$'}Mode -eq 'swap') {
        |  ${'$'}backup = "${'$'}Current.old"
        |  if (Test-Path -LiteralPath ${'$'}backup) { Remove-Item -LiteralPath ${'$'}backup -Recurse -Force }
        |  ${'$'}moved = ${'$'}false
        |  for (${'$'}i = 0; ${'$'}i -lt 40 -and -not ${'$'}moved; ${'$'}i++) {
        |    try { Move-Item -LiteralPath ${'$'}Current -Destination ${'$'}backup -ErrorAction Stop; ${'$'}moved = ${'$'}true }
        |    catch { Start-Sleep -Milliseconds 500 }
        |  }
        |  if (${'$'}moved) {
        |    try { Move-Item -LiteralPath ${'$'}Replacement -Destination ${'$'}Current -ErrorAction Stop }
        |    catch {
        |      Write-Output "could not move the new version in; keeping the old one: ${'$'}_"
        |      Move-Item -LiteralPath ${'$'}backup -Destination ${'$'}Current
        |      ${'$'}backup = ${'$'}null
        |    }
        |  } else {
        |    Write-Output "could not move the old version aside"
        |    ${'$'}backup = ${'$'}null
        |  }
        |}
        |Start-Process -FilePath ${'$'}Launch
        |if (${'$'}backup) {
        |  Start-Sleep -Seconds 10
        |  Remove-Item -LiteralPath ${'$'}backup -Recurse -Force -ErrorAction SilentlyContinue
        |}
        |
        """.trimMargin()

    fun writeUnix(dir: File): File =
        File(dir, "amethyst-update.sh").apply {
            parentFile.mkdirs()
            writeText(UNIX)
            setExecutable(true, true)
        }

    fun writeWindows(dir: File): File =
        File(dir, "amethyst-update.ps1").apply {
            parentFile.mkdirs()
            writeText(WINDOWS)
        }

    /** The command that runs the Unix script in [mode]; [relaunch] goes after `--`. */
    fun unixCommand(
        script: File,
        pid: Long,
        mode: String,
        args: List<String>,
        relaunch: List<String>,
    ): List<String> = listOf("nohup", "/bin/sh", script.absolutePath, pid.toString(), mode) + args + "--" + relaunch

    fun windowsCommand(
        script: File,
        pid: Long,
        mode: String,
        named: Map<String, String>,
        launch: File,
    ): List<String> =
        listOf(
            "powershell.exe",
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy",
            "Bypass",
            "-WindowStyle",
            "Hidden",
            "-File",
            script.absolutePath,
            "-ProcessId",
            pid.toString(),
            "-Mode",
            mode,
        ) + named.flatMap { (key, value) -> listOf("-$key", value) } + listOf("-Launch", launch.absolutePath)
}
