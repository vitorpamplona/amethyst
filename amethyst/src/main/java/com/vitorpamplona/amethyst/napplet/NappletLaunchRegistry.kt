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

import androidx.collection.LruCache
import com.vitorpamplona.amethyst.commons.napplet.NappletCapability
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.RandomInstance

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
        /**
         * The account this surface was launched as. Requests resolve their signer through THIS, not
         * through whichever account happens to be active when they arrive.
         *
         * A full-screen host is a separate activity that an account switch does not tear down, so
         * resolving live meant its WebView kept account A's cookies while the broker signed as B —
         * a page showing one identity while another signed, and B's session written into A's
         * storage jar. Binding here gives both halves of the rule for free: embedded surfaces are
         * rebuilt on a switch, so they re-mint and follow the active account, while a full-screen
         * surface keeps the account it was opened with.
         */
        val accountPubKey: HexKey,
    )

    // Access-ordered + capped so tokens from long-closed napplets can't accumulate without bound. The
    // active napplet always re-touches its token, so only stale sessions are ever evicted.
    private const val MAX_SESSIONS = 128

    // LruCache is internally synchronized and access-ordered — the same
    // touch-on-resolve + evict-eldest-beyond-cap semantics the old access-ordered
    // LinkedHashMap + @Synchronized pair provided, without JVM-only APIs.
    private val sessions = LruCache<String, Session>(MAX_SESSIONS)

    fun register(
        identity: NappletIdentity,
        declared: Set<NappletCapability>,
        accountPubKey: HexKey,
    ): String {
        val token = RandomInstance.bytes(32).toHexKey()
        sessions.put(token, Session(identity.copy(instanceId = token), declared, accountPubKey))
        return token
    }

    fun resolve(token: String?): Session? = token?.let { sessions[it] }

    fun unregister(token: String?) {
        token?.let { sessions.remove(it) }
    }
}
