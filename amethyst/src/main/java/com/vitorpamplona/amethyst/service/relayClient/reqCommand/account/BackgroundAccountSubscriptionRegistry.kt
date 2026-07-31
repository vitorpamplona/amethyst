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
package com.vitorpamplona.amethyst.service.relayClient.reqCommand.account

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log

/**
 * Mounts the always-on account loaders — notifications, DMs, gift wraps, drafts,
 * metadata — for accounts that have no screen.
 *
 * Every other mount of [AccountFilterAssembler] comes from a composable holding an
 * `AccountViewModel`, which means it only ever covers the account the user is
 * looking at. Accounts the user asked to "keep active in the background" had no
 * such mount: they were merely loaded into memory so pushed gift wraps could be
 * decrypted by their owner, and everything else about them depended on a push
 * message arriving. On a device with no push (no Play Services, no UnifiedPush
 * distributor, Pokey not installed) those accounts pulled nothing at all.
 *
 * This registry is the pull side of that promise. It holds one
 * [AccountQueryState] per participating account and drives
 * [AccountFilterAssembler.subscribe] / [AccountFilterAssembler.unsubscribe]
 * directly — those are plain functions, not composables, so no UI has to exist.
 *
 * The keys carry no feed states (see [AccountQueryState.feedContentStates]) and no
 * `otherAccounts` — populating the account switcher's avatars is a screen's job,
 * and these accounts have no screen.
 *
 * Ownership of the participating set lives in
 * [com.vitorpamplona.amethyst.service.notifications.AlwaysOnNotificationServiceManager],
 * which already watches the two flags that define it, and calls [sync] on every
 * change.
 */
class BackgroundAccountSubscriptionRegistry(
    private val assembler: AccountFilterAssembler,
) {
    companion object {
        private const val TAG = "BackgroundAccountSubs"
    }

    private val mounted = mutableMapOf<HexKey, AccountQueryState>()

    /**
     * Makes the mounted set match [participants] exactly: subscribes accounts that
     * just opted in, unsubscribes accounts that opted out or were unloaded, and
     * leaves untouched the ones already mounted.
     *
     * Idempotent, so callers can hand it the same set on every emission of the
     * flags flow without churning subscriptions.
     */
    @Synchronized
    fun sync(participants: Collection<Account>) {
        val wanted = participants.associateBy { it.userProfile().pubkeyHex }

        // Unmount accounts that dropped out, and accounts whose Account object was
        // replaced (re-login rebuilds it) — the stale instance holds the old
        // signer and relay lists, so its filters would be built from dead state.
        val stale = mounted.filter { (pubkey, state) -> wanted[pubkey] !== state.account }
        stale.forEach { (pubkey, state) ->
            mounted.remove(pubkey)
            assembler.unsubscribe(state)
        }

        var added = 0
        wanted.forEach { (pubkey, account) ->
            if (pubkey !in mounted) {
                val state = AccountQueryState(account, emptySet())
                mounted[pubkey] = state
                assembler.subscribe(state)
                added++
            }
        }

        if (added > 0 || stale.isNotEmpty()) {
            Log.d(TAG) { "Background account subscriptions: ${mounted.size} mounted (+$added, -${stale.size})" }
        }
    }

    /** Unmounts everything. Used when the master switch goes off, and on logout. */
    @Synchronized
    fun clear() = sync(emptyList())
}
