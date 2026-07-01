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
package com.vitorpamplona.amethyst.napplet

import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import java.security.SecureRandom

/**
 * Main-process registry that binds a sandbox launch to its **trusted** identity + declared capability
 * set, addressed by an unguessable launch token.
 *
 * Why: the broker (main process) must know *which* napplet a brokered request belongs to, but the
 * request travels up from the sandboxed `:napplet` process, which is the very thing we don't fully
 * trust (a WebView/renderer escape runs code there). If the identity rode along inside each IPC
 * message, a compromised sandbox could simply claim another napplet's coordinate and read/act as it.
 *
 * Instead, [NappletLauncher] (main process) mints a random token here at launch time, hands only that
 * token to the sandbox, and the broker resolves it back to the identity it was registered with. The
 * sandbox only ever holds *its own* token, so even a fully compromised `:napplet` process can act as
 * nothing but the napplet it was launched as.
 *
 * Both the launcher and [NappletBrokerService] run in the main process, so they share this singleton.
 */
object NappletLaunchRegistry {
    data class Session(
        val identity: NappletIdentity,
        val declared: Set<NappletCapability>,
    )

    // Access-ordered + capped so tokens from long-closed napplets can't accumulate without bound. The
    // active napplet always re-touches its token, so only stale sessions are ever evicted.
    private const val MAX_SESSIONS = 128
    private val sessions =
        object : LinkedHashMap<String, Session>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Session>) = size > MAX_SESSIONS
        }
    private val secureRandom = SecureRandom()

    @Synchronized
    fun register(
        identity: NappletIdentity,
        declared: Set<NappletCapability>,
    ): String {
        val token = ByteArray(32).also(secureRandom::nextBytes).toHexKey()
        sessions[token] = Session(identity, declared)
        return token
    }

    @Synchronized
    fun resolve(token: String?): Session? = token?.let { sessions[it] }

    @Synchronized
    fun unregister(token: String?) {
        token?.let { sessions.remove(it) }
    }
}
