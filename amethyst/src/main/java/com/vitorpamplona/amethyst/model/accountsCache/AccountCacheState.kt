/**
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
package com.vitorpamplona.amethyst.model.accountsCache

import android.content.ContentResolver
import android.util.Log
import androidx.collection.LruCache
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.nwc.NWCPaymentFilterAssembler
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip55AndroidSigner.client.NostrSignerExternal
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow

class AccountCacheState(
    val geolocationFlow: StateFlow<LocationState.LocationResult>,
    val nwcFilterAssembler: NWCPaymentFilterAssembler,
    val contentResolver: ContentResolver,
    val cache: LocalCache,
    val client: INostrClient,
) {
    val accounts =
        object : LruCache<HexKey, Account>(20) {
            override fun entryRemoved(
                evicted: Boolean,
                key: HexKey,
                oldValue: Account,
                newValue: Account?,
            ) {
                super.entryRemoved(evicted, key, oldValue, newValue)
                oldValue.scope.cancel()
            }
        }

    fun removeAccount(pubkey: HexKey) = accounts.remove(pubkey)

    fun loadAccount(accountSettings: AccountSettings): Account =
        loadAccount(
            signer =
                if (accountSettings.keyPair.privKey != null) {
                    NostrSignerInternal(accountSettings.keyPair)
                } else {
                    when (val packageName = accountSettings.externalSignerPackageName) {
                        null -> NostrSignerInternal(accountSettings.keyPair)
                        else ->
                            NostrSignerExternal(
                                pubKey = accountSettings.keyPair.pubKey.toHexKey(),
                                packageName = packageName,
                                contentResolver = contentResolver,
                            )
                    }
                },
            accountSettings = accountSettings,
        )

    fun loadAccount(
        signer: NostrSigner,
        accountSettings: AccountSettings,
    ): Account {
        val cached = accounts[signer.pubKey]
        if (cached != null) return cached

        return Account(
            settings = accountSettings,
            signer = signer,
            geolocationFlow = geolocationFlow,
            nwcFilterAssembler = nwcFilterAssembler,
            cache = cache,
            client = client,
            scope =
                CoroutineScope(
                    Dispatchers.Default +
                        SupervisorJob() +
                        CoroutineExceptionHandler { _, throwable ->
                            Log.e("AccountCacheState", "Account ${signer.pubKey} caught exception: ${throwable.message}", throwable)
                        },
                ),
        ).also {
            accounts.put(signer.pubKey, it)
        }
    }
}
