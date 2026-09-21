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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.cordn.spec00Coordinator.CoordinatorServerInfo
import com.vitorpamplona.quartz.cordn.spec00Coordinator.ICoordinator
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import java.io.File

/** A live connection to one coordinator, and the way to close it. */
interface CordnCoordinatorLink {
    val coordinator: ICoordinator

    /**
     * What the coordinator says about itself, or null.
     *
     * Deliberately **not** on `ICoordinator`. That interface is "the eleven
     * coordinator tools, as a contract" and its KDoc says it adds nothing to
     * them; `initialize` is the MCP handshake, not a twelfth tool. It belongs
     * to whoever owns the transport, which is this.
     *
     * The default is null so a substitute link — a fixture, a test double —
     * does not have to invent a handshake it never performed.
     */
    suspend fun serverInfo(): CoordinatorServerInfo? = null

    suspend fun close()
}

/**
 * Opens the ContextVM transport to a coordinator.
 *
 * The one piece [FileBackedCordnScopeFactory] cannot supply: a `CvmTransport`
 * needs a relay pool and the account's signers, which belong to whichever front
 * end is running. Everything else about a scope — where the bytes go and how
 * they are encrypted — is the same on every platform, so it is here.
 */
fun interface CordnCoordinatorLinkFactory {
    suspend fun connect(
        accountPubKey: HexKey,
        config: CoordinatorConfig,
    ): CordnCoordinatorLink
}

/**
 * The production [CordnCoordinatorScopeFactory]: encrypted files on disk, plus
 * whatever transport [links] opens.
 *
 * Splitting it this way is what lets the storage half be tested. The transport
 * half needs a relay and an account; the storage half needs a directory, and
 * everything that can go wrong with it — the wrong account reading another's
 * groups, two coordinators colliding on one `gid`, a `gid` that walks out of
 * the directory — goes wrong silently and is worth a test. See
 * `FileCordnStoresTest`.
 *
 * @param root the app's private files directory. Everything lands under
 *   `<root>/cordn/<account>/<coordinator>`; see [CordnStorageLayout].
 */
class FileBackedCordnScopeFactory(
    private val root: File,
    private val cipher: CordnBlobCipher,
    private val links: CordnCoordinatorLinkFactory,
) : CordnCoordinatorScopeFactory {
    override suspend fun open(
        accountPubKey: HexKey,
        config: CoordinatorConfig,
    ): CordnCoordinatorScope {
        val dir = CordnStorageLayout.directoryFor(root, accountPubKey, config.pubKey)
        val link = links.connect(accountPubKey, config)

        return object : CordnCoordinatorScope {
            override val coordinator = link.coordinator

            override suspend fun serverInfo() = link.serverInfo()

            override val groupStore = FileCordnGroupStore(dir, cipher)
            override val keyPackageStore = FileCordnKeyPackageStore(dir, cipher)

            // Only the transport closes. The files outlive the session by
            // design — closing a coordinator is not leaving its groups, and
            // the registry's `forget` says the same thing.
            override suspend fun close() = link.close()
        }
    }
}
