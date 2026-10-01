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
package com.vitorpamplona.amethyst.desktop.nowPlaying

import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlaying
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingSource
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.Base64

/**
 * Windows: reads the system media transport controls (the media flyout every player feeds:
 * Spotify, the Media Player app, browsers, ...) through the WinRT
 * `GlobalSystemMediaTransportControlsSessionManager`, called from the built-in Windows PowerShell
 * since the JVM has no WinRT bridge.
 */
class WindowsNowPlayingReader(
    private val run: suspend (List<String>) -> String? = { runCommand(it, timeoutSeconds = 10) },
    private val nowSeconds: () -> Long = TimeUtils::now,
) : OsNowPlayingReader {
    override suspend fun read(): NowPlaying? {
        val output = run(listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", ENCODED_SCRIPT)) ?: return null
        return parse(output, nowSeconds())
    }

    companion object {
        private val SCRIPT =
            """
            ${'$'}ErrorActionPreference = 'Stop'
            [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            ${'$'}asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { ${'$'}_.Name -eq 'AsTask' -and ${'$'}_.GetParameters().Count -eq 1 -and ${'$'}_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
            function Await(${'$'}op, [Type]${'$'}type) { ${'$'}task = ${'$'}asTask.MakeGenericMethod(${'$'}type).Invoke(${'$'}null, @(${'$'}op)); ${'$'}task.Wait(-1) | Out-Null; ${'$'}task.Result }
            [void][Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
            ${'$'}manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
            foreach (${'$'}session in ${'$'}manager.GetSessions()) {
                if (${'$'}session.GetPlaybackInfo().PlaybackStatus -ne 'Playing') { continue }
                ${'$'}props = Await (${'$'}session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                ${'$'}timeline = ${'$'}session.GetTimelineProperties()
                ${'$'}length = [long](${'$'}timeline.EndTime - ${'$'}timeline.StartTime).TotalMilliseconds
                ${'$'}position = [long]${'$'}timeline.Position.TotalMilliseconds
                [Console]::Out.WriteLine((${'$'}session.SourceAppUserModelId, ${'$'}props.Title, ${'$'}props.Artist, ${'$'}length, ${'$'}position) -join "`t")
                break
            }
            """.trimIndent()

        // -EncodedCommand takes UTF-16LE in Base64, which sidesteps every quoting rule of the command line.
        private val ENCODED_SCRIPT: String = Base64.getEncoder().encodeToString(SCRIPT.toByteArray(Charsets.UTF_16LE))

        /** Parses `appId<TAB>title<TAB>artist<TAB>lengthMs<TAB>positionMs`. */
        fun parse(
            output: String,
            nowSeconds: Long,
        ): NowPlaying? {
            val fields =
                output
                    .lineSequence()
                    .map { it.trimEnd('\r') }
                    .firstOrNull { it.isNotBlank() }
                    ?.split('\t') ?: return null
            if (fields.size < 5) return null

            val appId = fields[0].ifBlank { null } ?: return null
            val title = fields[1].ifBlank { null } ?: return null
            val durationMs = fields[3].toLongOrNull()?.takeIf { it > 0 }
            val positionMs = fields[4].toLongOrNull() ?: 0L

            return NowPlaying(
                title = title,
                artist = fields[2].ifBlank { null },
                source = NowPlayingSource.OtherApp(appId, appLabel(appId)),
                endsAt = NowPlaying.endsAt(nowSeconds, durationMs, positionMs),
            )
        }

        /**
         * A readable name for an app user model id: `Spotify.exe` is `Spotify`,
         * `Microsoft.ZuneMusic_8wekyb3d8bbwe!Microsoft.ZuneMusic` is `ZuneMusic`.
         */
        fun appLabel(appId: String): String {
            val app = appId.substringAfterLast('!').substringBefore('_')
            val name =
                app
                    .removeSuffix(".exe")
                    .removeSuffix(".EXE")
                    .substringAfterLast('\\')
                    .substringAfterLast('.')
            return name.ifBlank { appId }
        }
    }
}
