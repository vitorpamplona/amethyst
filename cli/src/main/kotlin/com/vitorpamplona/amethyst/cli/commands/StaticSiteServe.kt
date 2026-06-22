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
package com.vitorpamplona.amethyst.cli.commands

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.service.upload.BlossomClient
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolution
import com.vitorpamplona.quartz.nip5aStaticWebsites.resolver.StaticSiteResolver
import com.vitorpamplona.quartz.nip5aStaticWebsites.tags.PathTag
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch

/**
 * Serves a resolved NIP-5A/5D manifest over a local HTTP server so you can open it in a browser. Each
 * request is resolved through quartz's [StaticSiteResolver] — the blob is downloaded from the
 * manifest's Blossom servers and **sha256-verified** against the pin before it is served, exactly as
 * the on-device host does. SPA fallback to `index.html` mirrors the host's behavior.
 *
 * Thin assembly over the resolver + commons [BlossomClient] + the JDK HTTP server. NOTE: this serves
 * the **static content** only — a napplet's `window.napplet.*` runtime needs the Android/desktop shell
 * + broker, which this does not provide; use it to inspect that the files load and route correctly.
 */
internal object StaticSiteServe {
    suspend fun serve(
        paths: List<PathTag>,
        servers: List<String>,
        port: Int,
    ): Int {
        if (servers.isEmpty()) return Output.error("no_servers", "manifest lists no Blossom servers; pass --server URL")

        val blossom = BlossomClient()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        server.createContext("/") { exchange -> handle(exchange, paths, servers, blossom) }
        server.executor = null // default per-request executor — fine for a single-user dev server
        server.start()

        val actualPort = server.address.port
        Output.emit(
            mapOf(
                "serving" to "http://127.0.0.1:$actualPort/",
                "port" to actualPort,
                "paths" to paths.size,
                "servers" to servers,
                "note" to "static content only; napplet runtime APIs need the Amethyst host. Ctrl+C to stop.",
            ),
        )

        // Block until the process is interrupted (Ctrl+C), then stop cleanly.
        val done = CountDownLatch(1)
        Runtime.getRuntime().addShutdownHook(
            Thread {
                server.stop(0)
                done.countDown()
            },
        )
        runCatching { done.await() }
        return 0
    }

    private fun handle(
        exchange: HttpExchange,
        paths: List<PathTag>,
        servers: List<String>,
        blossom: BlossomClient,
    ) {
        exchange.use {
            if (!exchange.requestMethod.equals("GET", ignoreCase = true)) {
                exchange.sendResponseHeaders(405, -1)
                return@use
            }
            val requestPath = exchange.requestURI.path
            val fetch: suspend (String) -> ByteArray? = { url -> blossom.download(url) }

            var resolution = runBlocking { StaticSiteResolver.resolve(requestPath, paths, servers, fetch) }
            // SPA fallback: an unknown route falls back to the verified index.html (browsers send Accept: text/html).
            if (resolution !is StaticSiteResolution.Resolved && requestPath != "/" && acceptsHtml(exchange)) {
                resolution = runBlocking { StaticSiteResolver.resolve("/", paths, servers, fetch) }
            }

            if (resolution is StaticSiteResolution.Resolved) {
                exchange.responseHeaders.set("Content-Type", resolution.contentType)
                exchange.sendResponseHeaders(200, resolution.bytes.size.toLong())
                exchange.responseBody.use { it.write(resolution.bytes) }
            } else {
                val body = "Not found: $requestPath".encodeToByteArray()
                exchange.responseHeaders.set("Content-Type", "text/plain")
                exchange.sendResponseHeaders(404, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
        }
    }

    private fun acceptsHtml(exchange: HttpExchange): Boolean = exchange.requestHeaders.getFirst("Accept")?.contains("text/html", ignoreCase = true) == true
}
