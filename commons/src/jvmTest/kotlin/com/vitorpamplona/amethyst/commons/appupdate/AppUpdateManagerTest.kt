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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateManagerTest {
    private val target = UpdateTarget("linux-x86_64", listOf(DesktopPackage.APPIMAGE))
    private val asset = UpdateAsset("https://example.com/a.AppImage", "a".repeat(64), 10, DesktopPackage.APPIMAGE, "a.AppImage")

    private fun update(
        version: String,
        installable: Boolean,
    ) = AvailableUpdate(version, "", if (installable) asset else null, "https://example.com/$version", 0)

    private class FixedSource(
        val result: AvailableUpdate?,
        val fails: Boolean = false,
    ) : UpdateSource {
        var calls = 0

        override suspend fun findUpdate(
            currentVersion: String,
            target: UpdateTarget,
        ): AvailableUpdate? {
            calls++
            if (fails) error("unreachable")
            return result
        }
    }

    private class MemoryPersistence : AppUpdateSettingsPersistence {
        var saved = AppUpdateSettings()

        override suspend fun load() = saved

        override suspend fun save(settings: AppUpdateSettings) {
            saved = settings
        }
    }

    private class RecordingInstaller(
        val failWith: UpdateFailure? = null,
    ) : AppUpdateInstaller {
        var prepared: AvailableUpdate? = null
        var applied: AvailableUpdate? = null

        override suspend fun prepare(
            update: AvailableUpdate,
            onProgress: (Long, Long?) -> Unit,
        ) {
            onProgress(5, 10)
            failWith?.let { throw UpdateInstallException(it, "test") }
            prepared = update
        }

        override fun applyAndRestart(update: AvailableUpdate) {
            applied = update
        }
    }

    private fun TestScope.manager(
        vararg sources: UpdateSource,
        installer: AppUpdateInstaller? = RecordingInstaller(),
        persistence: MemoryPersistence = MemoryPersistence(),
    ) = AppUpdateManager("1.0.0", UpdateMethod.SelfInstall, target, sources.toList(), installer, persistence, backgroundScope, now = { 1000 })

    @Test
    fun aTrustedInstallableUpdateStopsTheSearch() =
        runTest {
            val github = FixedSource(update("3.0.0", installable = false))
            val m = manager(FixedSource(update("2.0.0", installable = true)), github)
            m.check()
            assertEquals("2.0.0", (m.state.value as AppUpdateState.Available).update.version)
            assertEquals(0, github.calls)
        }

    @Test
    fun theFallbackIsAskedWhenTheTrustedSourceHasNothingToInstall() =
        runTest {
            val m = manager(FixedSource(null), FixedSource(update("3.0.0", installable = false)))
            m.check()
            val offered = (m.state.value as AppUpdateState.Available).update
            assertEquals("3.0.0", offered.version)
            assertFalse(offered.canInstall)
        }

    @Test
    fun upToDateWhenEverySourceAnswersEmpty() =
        runTest {
            val m = manager(FixedSource(null), FixedSource(null))
            m.check()
            assertIs<AppUpdateState.UpToDate>(m.state.value)
        }

    @Test
    fun failedOnlyWhenNoSourceCouldBeReached() =
        runTest {
            val one = manager(FixedSource(null, fails = true), FixedSource(null))
            one.check()
            assertIs<AppUpdateState.UpToDate>(one.state.value)

            val none = manager(FixedSource(null, fails = true), FixedSource(null, fails = true))
            none.check()
            assertEquals(AppUpdateState.Failed(UpdateFailure.CHECK_FAILED, null), none.state.value)
        }

    @Test
    fun installsAndRestarts() =
        runTest {
            val installer = RecordingInstaller()
            val m = manager(FixedSource(update("2.0.0", installable = true)), installer = installer)
            m.check()
            m.install()
            runCurrent()
            assertIs<AppUpdateState.ReadyToRestart>(m.state.value)
            m.restartToUpdate()
            assertEquals("2.0.0", installer.applied?.version)
        }

    @Test
    fun neverInstallsANotifyOnlyUpdate() =
        runTest {
            val installer = RecordingInstaller()
            val m = manager(FixedSource(update("2.0.0", installable = false)), installer = installer)
            m.check()
            m.install()
            runCurrent()
            assertIs<AppUpdateState.Available>(m.state.value)
            assertEquals(null, installer.prepared)
        }

    @Test
    fun aFailedDownloadKeepsTheUpdateToRetry() =
        runTest {
            val m = manager(FixedSource(update("2.0.0", installable = true)), installer = RecordingInstaller(UpdateFailure.VERIFICATION_FAILED))
            m.check()
            m.install()
            runCurrent()
            val failed = m.state.value as AppUpdateState.Failed
            assertEquals(UpdateFailure.VERIFICATION_FAILED, failed.failure)
            assertEquals("2.0.0", failed.update?.version)
        }

    @Test
    fun aCheckDoesNotInterruptADownloadedUpdate() =
        runTest {
            val source = FixedSource(update("2.0.0", installable = true))
            val m = manager(source)
            m.check()
            m.install()
            runCurrent()
            m.check()
            assertIs<AppUpdateState.ReadyToRestart>(m.state.value)
            assertEquals(1, source.calls)
        }

    @Test
    fun keepsTheSettings() =
        runTest {
            val persistence = MemoryPersistence()
            val m = manager(FixedSource(null), persistence = persistence)
            m.setAutoCheck(false)
            m.skip("2.0.0")
            runCurrent()
            assertEquals(AppUpdateSettings(autoCheck = false, skippedVersion = "2.0.0", lastCheckAt = 0), persistence.saved)
            assertTrue(!m.autoCheck.value)
        }
}
