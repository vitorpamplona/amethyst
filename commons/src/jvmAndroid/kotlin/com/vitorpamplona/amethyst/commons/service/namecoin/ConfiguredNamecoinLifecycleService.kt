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
package com.vitorpamplona.amethyst.commons.service.namecoin

import com.vitorpamplona.amethyst.commons.model.accountLifecycle.AccountLifecycleService
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.AccountLifecycleTransport
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.LifecycleAssociationStore
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.LifecycleProvenance
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.NamecoinRawRecord
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinSettings
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.IElectrumXClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Lifecycle lookups over the exact configured backend used by ordinary Namecoin NIP-05. */
class ConfiguredNamecoinLifecycleService internal constructor(
    private val backendProvider: () -> IElectrumXClient,
    private val serverListProvider: () -> List<ElectrumxServer>,
    private val settings: StateFlow<NamecoinSettings>,
    store: LifecycleAssociationStore,
    private val scope: CoroutineScope,
) : NamecoinLifecycleService {
    override val accountLifecycle =
        AccountLifecycleService(
            transport = AccountLifecycleTransport(::lookup),
            store = store,
            scope = scope,
        )

    init {
        scope.launch {
            settings.drop(1).distinctUntilChanged().collect {
                // Current authority and reciprocal confirmation must be rechecked after any
                // backend, server, fallback, or Core RPC settings change.
                accountLifecycle.invalidateAndWait()
            }
        }
    }

    private suspend fun lookup(canonicalName: String): NamecoinRawRecord? {
        val configured = settings.value
        val result = backendProvider().nameShowWithFallback(canonicalName, serverListProvider()) ?: return null
        return NamecoinRawRecord(
            value = result.value,
            provenance =
                LifecycleProvenance(
                    selectedName = canonicalName,
                    dependencies = listOf(canonicalName),
                    txid = result.txid,
                    height = result.height,
                    expiresIn = result.expiresIn,
                    configuredBackend = configured.backend.name,
                ),
        )
    }
}
