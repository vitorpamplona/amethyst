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
package com.vitorpamplona.amethyst.desktop.app

import com.vitorpamplona.amethyst.commons.account.AccountCacheState
import com.vitorpamplona.amethyst.commons.account.AccountInfo
import com.vitorpamplona.amethyst.commons.account.AccountSessionStore
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.notifications.NotificationAccountLabel
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.account.AccountSubscriptionRegistry
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Every saved account that can sign is notified, whichever one is on screen, as on Android: each is
 * kept loaded, pulls its own notifications, DMs and gift wraps ([AccountSubscriptionRegistry]), and
 * raises its own OS notifications ([DesktopNotifications.watch]), labelled with its name once there
 * is more than one. The account on screen holds its toasts back while the window has the focus,
 * since the app already shows them; the others do not, since nothing on screen does.
 */
class DesktopAccountsNotified(
    private val scope: CoroutineScope,
    private val accountsCache: AccountCacheState,
    private val sessionStore: AccountSessionStore,
    private val subscriptions: AccountSubscriptionRegistry,
    private val notifications: DesktopNotifications,
    private val cache: LocalCache,
    /** Whether the main window has the focus. */
    private val windowFocused: StateFlow<Boolean>,
    /** The account on screen, if any. */
    private val onScreen: StateFlow<HexKey?>,
    /**
     * True once keys can be read without asking: the OS keyring answered, or the password file is
     * already unlocked. Until then a background load would raise the unlock prompt by itself, at
     * launch, for an account the user did not open; the accounts wait instead.
     */
    private val keysReadableSilently: Flow<Boolean>,
) {
    private class Watch(
        val account: Account,
        /** The watch and what feeds it; cancelled together. */
        val job: Job,
    )

    private val watches = mutableMapOf<HexKey, Watch>()

    fun start() {
        // Loads the saved accounts that can sign, again whenever the saved logins change.
        scope.launch {
            combine(sessionStore.accountsFlow().filterNotNull(), keysReadableSilently) { saved, readable -> saved.takeIf { readable } }
                .filterNotNull()
                .collect { saved ->
                    val signing = saved.filter { it.signsWithoutAsking() }
                    Log.d(TAG) { "${saved.size} saved account(s), ${signing.size} load in the background" }
                    signing.forEach { info -> load(info) }
                }
        }

        // Subscribes and watches exactly the loaded accounts, the one on screen included.
        scope.launch {
            accountsCache.accounts.collect { loaded ->
                subscriptions.sync(loaded.values)
                syncWatches(loaded)
            }
        }
    }

    private suspend fun load(info: AccountInfo) {
        val pubkey = decodePublicKeyAsHexOrNull(info.npub) ?: return
        if (accountsCache.loadedAccount(pubkey) != null) return
        try {
            val settings = sessionStore.loadAccountConfigFromEncryptedStorage(info.npub) ?: return
            accountsCache.loadAccount(settings)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Could not load ${info.npub}", e)
        }
    }

    @Synchronized
    private fun syncWatches(loaded: Map<HexKey, Account>) {
        // Gone, or replaced by a new Account object (a re-login rebuilds it).
        watches.entries.removeAll { (pubkey, watch) ->
            val stale = loaded[pubkey] !== watch.account
            if (stale) watch.job.cancel()
            stale
        }
        loaded.forEach { (pubkey, account) ->
            if (pubkey in watches) return@forEach
            val job = SupervisorJob(scope.coroutineContext[Job])
            val watchScope = CoroutineScope(scope.coroutineContext + job)
            val holdBack =
                combine(windowFocused, onScreen) { focused, shown -> focused && shown == pubkey }
                    .stateIn(watchScope, SharingStarted.Eagerly, false)
            val watch =
                notifications.watch(account, cache, holdBack) {
                    NotificationAccountLabel.of(account, accountsCache.accounts.value.size)
                }
            job.invokeOnCompletion { watch.cancel() }
            watches[pubkey] = Watch(account, job)
        }
        Log.d(TAG) { "Notifying ${watches.size} account(s)" }
    }

    companion object {
        private const val TAG = "DesktopAccountsNotified"

        /**
         * Can sign, and loads without a prompt: a key the vault keeps, or a remote signer. A key the
         * user chose not to keep is asked for each time it loads, so that account waits until the
         * user opens it; an Android signer app does not exist here.
         */
        internal fun AccountInfo.signsWithoutAsking(): Boolean = !isTransient && ((hasPrivKey && keyRemembered) || loggedInWithRemoteSigner)
    }
}
