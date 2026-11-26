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
package com.vitorpamplona.amethyst.commons.napplet.session

import com.vitorpamplona.amethyst.commons.napplet.HostProfile
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.NappletLaunch
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletBlobStore
import com.vitorpamplona.amethyst.commons.napplet.blobs.NappletFiles
import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** A browser engine with no browser: it records what the core asks of it. */
class FakeNappletWebEngine(
    private val supported: Set<NappletGuard> = NappletGuard.entries.toSet(),
) : NappletWebEngine {
    private val lock = KmpLock()
    private val scripts = ArrayList<String>()
    val installed = ArrayList<NappletGuard>()
    val loaded = ArrayList<String>()
    var closed = false

    override fun installGuard(guard: NappletGuard): Boolean = (guard in supported).also { if (it) installed += guard }

    override fun bridgeHead(): String = BRIDGE_HEAD

    override suspend fun loadShell(shellUrl: String): Boolean {
        loaded += shellUrl
        return true
    }

    override fun evaluateInShell(js: String) {
        lock.withLock { scripts += js }
    }

    override fun close() {
        closed = true
    }

    /** Each payload the page was handed, in order, unpacked from the delivery scripts. */
    fun delivered(): List<String> = lock.withLock { scripts.toList() }.flatMap(::payloadsOf)

    fun scriptCount(): Int = lock.withLock { scripts.size }

    companion object {
        const val BRIDGE_HEAD = "<script>/*bridge*/</script>"

        fun payloadsOf(script: String): List<String> {
            val array = script.substringAfter("})(").substringBeforeLast(");")
            return Json.parseToJsonElement(array).jsonArray.map { it.jsonPrimitive.content }
        }
    }
}

/** A route that is up, or not, as the test says. */
class FakeRoute(
    var torRunning0: Boolean = true,
    var claimResult: Boolean = true,
    var awaitResult: Boolean = true,
) : NappletRoutePort {
    var claimed: Boolean? = null
    var released = 0

    override val torRunning: Boolean get() = torRunning0

    override suspend fun claim(useTor: Boolean): Boolean {
        claimed = useTor
        return claimResult
    }

    override suspend fun awaitTor(): Boolean = awaitResult

    override fun release() {
        released++
    }
}

class FakeLinks : NappletLinkOpener {
    val external = ArrayList<String>()
    val nostr = ArrayList<String>()

    override fun openExternal(url: String) {
        external += url
    }

    override fun openNostr(uri: String) {
        nostr += uri
    }
}

/** Files that hold one page at `/index.html` (and `/`), or nothing when [available] is false. */
class FakeFiles(
    private val available: Boolean = true,
    private val html: String = "<p>app</p>",
) : NappletFiles {
    var closed = false

    private fun blob(path: String) = NappletBlobStore.Blob(path, "00".repeat(32), "text/html", file = null, bytes = html.encodeToByteArray())

    override suspend fun isAvailable(requestPath: String): Boolean = available

    override suspend fun find(requestPath: String): NappletBlobStore.Blob? = if (available && (requestPath == "/" || requestPath == "/index.html")) blob(requestPath) else null

    override fun isListed(requestPath: String): Boolean = requestPath == "/index.html"

    override fun close() {
        closed = true
    }
}

object TestLaunches {
    fun napplet(
        author: String,
        declared: Set<NappletCapability> = NappletCapability.entries.toSet(),
    ) = NappletLaunch(NappletIdentity(author, "demo"), HostProfile.NAPPLET, declared, emptyList(), "Demo", emptyList(), emptyList())

    fun site(author: String) = NappletLaunch(NappletIdentity(author, "blog"), HostProfile.WEBSITE, HostProfile.WEBSITE.declaredCapabilities(emptyList()), emptyList(), "Blog", emptyList(), emptyList())

    fun paper(author: String) =
        NappletLaunch(
            NappletIdentity(author, "paper"),
            HostProfile.PAPER,
            HostProfile.PAPER.declaredCapabilities(emptyList()),
            emptyList(),
            "Paper",
            emptyList(),
            emptyList(),
            paper = "<p>news</p>".encodeToByteArray(),
        )
}

/** Waits, on real time, until [condition] holds. */
suspend fun eventually(
    timeoutMs: Long = 5_000,
    condition: () -> Boolean,
) = withContext(Dispatchers.Default) {
    withTimeout(timeoutMs) {
        while (!condition()) delay(5)
    }
}
