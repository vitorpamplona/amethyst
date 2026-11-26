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
package com.vitorpamplona.amethyst.napplethost

import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobCache
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobHttp
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobStore
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletFiles
import com.vitorpamplona.amethyst.commons.napplet.blobs.OkHttpNappletHttp
import com.vitorpamplona.amethyst.commons.napplet.blobs.PaperFiles
import com.vitorpamplona.amethyst.commons.napplet.session.NappletLaunchPlan
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcBundles
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcFiles
import com.vitorpamplona.amethyst.commons.napplet.webxdc.WebxdcRuntime
import com.vitorpamplona.amethyst.commons.napplet.webxdc.downloadClient
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * The files one sandbox launch serves, for the shared core's site server: the sha256-verified blobs from
 * the shared content-addressed cache (warmed by the prefetcher while the card was on screen, so opening is
 * instant), a webxdc app's unpacked archive, or a front page's one checked document. A miss downloads
 * through Tor when [NappletLaunchPlan.blobsUseTor] says so, on a client of the launch's own that goes when
 * the files are closed (a new one is built per embedded tab, so it would otherwise leak its threads).
 */
internal fun androidNappletFiles(
    config: NappletSandboxLaunch,
    plan: NappletLaunchPlan,
    cacheDir: File,
): NappletFiles {
    val client = NappletBlobHttp.client(if (plan.blobsUseTor) config.proxyPort else -1)
    val launch = config.launch
    val paper = launch.paper
    val webxdc = launch.webxdc
    val files: NappletFiles =
        when {
            paper != null -> PaperFiles(paper)
            webxdc != null ->
                WebxdcFiles(
                    WebxdcBundles(WebxdcBundles.dirFor(cacheDir)),
                    WebxdcFiles.downloadClient(client),
                    webxdc.url,
                    webxdc.hash,
                    launch.servers,
                    WebxdcRuntime.script(webxdc.selfAddr, webxdc.selfName, webxdc.isAppSender),
                )
            else -> {
                // This site's cache partition: files cached for other sites or accounts are fetched again, so
                // the site can't tell what else this device opened.
                val partition = NappletBlobCache.partitionOf(config.webViewProfile.orEmpty(), config.author, config.identifier)
                NappletBlobStore(NappletBlobCache(NappletBlobCache.dirFor(cacheDir.toOkioPath())).forSite(partition), OkHttpNappletHttp(client), launch.paths, launch.servers)
            }
        }
    return object : NappletFiles by files {
        override fun close() {
            // First, so a webxdc download stops at its next read instead of moving to the next server.
            files.close()
            runCatching {
                client.dispatcher.cancelAll()
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
        }
    }
}
