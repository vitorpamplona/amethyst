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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.vitorpamplona.amethyst.commons.appupdate.AppUpdateManager
import com.vitorpamplona.amethyst.commons.appupdate.AppUpdateSettingsStore
import com.vitorpamplona.amethyst.commons.appupdate.Nip82UpdateSource
import com.vitorpamplona.amethyst.commons.appupdate.UpdateMethod
import com.vitorpamplona.amethyst.commons.appupdate.UpdateTarget
import com.vitorpamplona.amethyst.commons.appupdate.canInstall
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.shared.Platform
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import java.io.File

/**
 * Builds the desktop's updater for this install, or null for a development run (`./gradlew run`),
 * which has nothing to update.
 *
 * To see the prompt and the settings section from a development run: `-Damethyst.update.devCheck=true`
 * gives it a check-only updater that checks five seconds after start, and
 * `-Damethyst.update.pretendVersion=1.0.0` makes it compare from an older version.
 */
object DesktopAppUpdates {
    private const val TAG = "DesktopAppUpdates"

    fun create(
        appVersion: String,
        channel: InstallChannel,
        client: INostrClient,
        downloadClient: (String) -> OkHttpClient,
        apiClient: (String) -> OkHttpClient,
        settings: DataStore<Preferences>,
        updatesDir: File,
        scope: CoroutineScope,
        quit: () -> Unit,
    ): AppUpdateManager? {
        val devCheck = channel == InstallChannel.Development && System.getProperty("amethyst.update.devCheck") == "true"
        val target = channel.target ?: if (devCheck) UpdateTarget(hostPlatform(), emptyList()) else return null
        val method = if (devCheck) UpdateMethod.DownloadPage else channel.method

        Log.i(TAG) { "Install channel: $channel, updates by $method" }

        val installer =
            if (method.canInstall) {
                DesktopUpdateInstaller(channel, UpdateDownloader(downloadClient), updatesDir, quit).also { it.cleanUp() }
            } else {
                null
            }

        return AppUpdateManager(
            currentVersion = if (devCheck) System.getProperty("amethyst.update.pretendVersion") ?: appVersion else appVersion,
            method = method,
            target = target,
            sources = listOf(Nip82UpdateSource(client), GitHubReleaseSource(apiClient)),
            installer = installer,
            persistence = AppUpdateSettingsStore(settings),
            scope = scope,
            startupDelaySec = if (devCheck) 5 else 60,
        )
    }

    /** The platform this JVM runs on, for a development run that has no install to read it from. */
    private fun hostPlatform(): String {
        val os = System.getProperty("os.name", "").lowercase()
        val arm = System.getProperty("os.arch", "").lowercase().let { it == "aarch64" || it == "arm64" }
        return when {
            os.contains("mac") || os.contains("darwin") -> if (arm) Platform.DARWIN_ARM64 else Platform.DARWIN_X86_64
            os.contains("win") -> if (arm) Platform.WINDOWS_AARCH64 else Platform.WINDOWS_X86_64
            else -> if (arm) Platform.LINUX_AARCH64 else Platform.LINUX_X86_64
        }
    }
}
