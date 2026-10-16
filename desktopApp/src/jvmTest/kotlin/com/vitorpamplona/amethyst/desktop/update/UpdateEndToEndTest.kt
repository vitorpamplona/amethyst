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

import com.sun.net.httpserver.HttpServer
import com.vitorpamplona.amethyst.commons.appupdate.AppUpdateManager
import com.vitorpamplona.amethyst.commons.appupdate.AppUpdateSettings
import com.vitorpamplona.amethyst.commons.appupdate.AppUpdateSettingsPersistence
import com.vitorpamplona.amethyst.commons.appupdate.AppUpdateState
import com.vitorpamplona.amethyst.commons.appupdate.DesktopAssetNames
import com.vitorpamplona.amethyst.commons.appupdate.DesktopRelease
import com.vitorpamplona.amethyst.commons.appupdate.DesktopReleaseEvents
import com.vitorpamplona.amethyst.commons.appupdate.HashedDesktopAsset
import com.vitorpamplona.amethyst.commons.appupdate.Nip82ReleaseEvents
import com.vitorpamplona.amethyst.commons.appupdate.Nip82UpdateFinder
import com.vitorpamplona.amethyst.commons.appupdate.Nip82UpdateSource
import com.vitorpamplona.amethyst.desktop.testrelay.LaunchFixtureRelay
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The whole path for an AppImage install: signed release events on a relay, the updater finding
 * them, downloading the file, checking its hash, staging it and swapping it in on restart.
 */
class UpdateEndToEndTest {
    private class MemoryPersistence : AppUpdateSettingsPersistence {
        override suspend fun load() = AppUpdateSettings()

        override suspend fun save(settings: AppUpdateSettings) = Unit
    }

    @Test
    fun findsDownloadsVerifiesAndSwapsAnAppImage() =
        runBlocking {
            val dir = Files.createTempDirectory("update-e2e").toFile()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            // A launcher that exits at once, since the helper starts it once this JVM is gone.
            val newBytes = "#!/bin/sh\nexit 0\n# version 9.0.0\n".toByteArray()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                exchange.sendResponseHeaders(200, newBytes.size.toLong())
                exchange.responseBody.use { it.write(newBytes) }
            }
            server.start()

            try {
                val publisher = NostrSignerSync()
                val version = "9.0.0"
                val filename = "amethyst-desktop-$version-x86_64.AppImage"
                val sha256 = MessageDigest.getInstance("SHA-256").digest(newBytes).joinToString("") { "%02x".format(it) }
                val asset =
                    publisher.sign(
                        DesktopReleaseEvents.asset(
                            HashedDesktopAsset(DesktopAssetNames.classify(filename, version)!!, sha256, newBytes.size.toLong(), "https://updates.test/$filename"),
                            version,
                        ),
                    )
                val release = publisher.sign(Nip82ReleaseEvents.release(DesktopRelease.APP_ID, publisher.pubKey, version, "notes", listOf(asset), null))

                // The release names an https URL, as the finder requires; the test client sends it to the local server.
                val client =
                    OkHttpClient
                        .Builder()
                        .addInterceptor { chain ->
                            val local =
                                chain
                                    .request()
                                    .url
                                    .newBuilder()
                                    .scheme("http")
                                    .host("127.0.0.1")
                                    .port(server.address.port)
                                    .build()
                            chain.proceed(
                                chain
                                    .request()
                                    .newBuilder()
                                    .url(local)
                                    .build(),
                            )
                        }.build()

                val appImage = File(dir, "Amethyst.AppImage").apply { writeText("#!/bin/sh\nexit 0\n# version 1.0.0\n") }
                val channel = InstallChannel.AppImage(appImage, "linux-x86_64", dirWritable = true)
                var quit = false

                LaunchFixtureRelay.open(listOf(asset, release)).use { relay ->
                    val nostr = NostrClient(relay.builder, parentScope = scope)
                    val manager =
                        AppUpdateManager(
                            currentVersion = "1.0.0",
                            method = channel.method,
                            target = channel.target!!,
                            sources =
                                listOf(
                                    Nip82UpdateSource(
                                        client = nostr,
                                        relays = listOf(LaunchFixtureRelay.LAUNCH_TEST_RELAY_URL.url),
                                        finder = Nip82UpdateFinder(publisher = publisher.pubKey),
                                        publisher = publisher.pubKey,
                                        idleTimeoutMs = 3_000,
                                    ),
                                ),
                            installer = DesktopUpdateInstaller(channel, UpdateDownloader { client }, File(dir, "updates")) { quit = true },
                            persistence = MemoryPersistence(),
                            scope = scope,
                        )

                    manager.check()
                    val available = assertIs<AppUpdateState.Available>(manager.state.value)
                    assertEquals(version, available.update.version)
                    assertEquals(sha256, available.update.asset?.sha256)

                    manager.install()
                    withTimeout(20_000) {
                        while (manager.state.value !is AppUpdateState.ReadyToRestart) {
                            check(manager.state.value !is AppUpdateState.Failed) { "failed: ${manager.state.value}" }
                            delay(50)
                        }
                    }
                    assertTrue(File(dir, ".Amethyst.AppImage.update").exists(), "staged next to the AppImage")

                    manager.restartToUpdate()
                    assertTrue(quit, "the app quits for the helper to relaunch it")
                    assertContentEquals(newBytes, appImage.readBytes())
                    assertTrue(appImage.canExecute())
                }
            } finally {
                server.stop(0)
                scope.cancel()
                dir.deleteRecursively()
            }
        }
}
