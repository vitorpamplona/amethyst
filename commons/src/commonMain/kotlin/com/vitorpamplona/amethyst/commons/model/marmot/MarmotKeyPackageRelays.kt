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
package com.vitorpamplona.amethyst.commons.model.marmot

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Which relays this account publishes its kind:30443 KeyPackages to.
 *
 * Inviters look for them on the relays the account chose: its NIP-65 write set (what the Marmot
 * transport names) and, for clients that still read it, its kind:10051 KeyPackage relay list.
 * Until the account's kind:10002 loads, the outbox is the app's defaults; a
 * KeyPackage published only there is one a White Noise inviter never finds. So the defaults are
 * used only while no list says otherwise, and that choice is [Choice.provisional]: it must be
 * redone once the lists arrive.
 */
object MarmotKeyPackageRelays {
    data class Choice(
        val relays: Set<NormalizedRelayUrl>,
        val provisional: Boolean,
    )

    /**
     * [nip65Write] is the write set of the account's kind:10002, or null while none is known;
     * [otherChosen] the other outbox relays the user picked (private storage, local, broadcast);
     * [defaults] what the app falls back to without a kind:10002; [keyPackageList] the account's
     * kind:10051 relays (empty when it has none or none loaded). Every input is read from the same
     * snapshot of the lists: the derived outbox flows update one after another, and a choice made
     * between two of their updates once published to the defaults although the kind:10002 had
     * already arrived.
     */
    fun choose(
        nip65Write: Set<NormalizedRelayUrl>?,
        otherChosen: Set<NormalizedRelayUrl>,
        defaults: Set<NormalizedRelayUrl>,
        keyPackageList: Set<NormalizedRelayUrl>,
    ): Choice =
        when {
            // Relays the user named for KeyPackages: never add ones they did not choose.
            keyPackageList.isNotEmpty() -> Choice(keyPackageList + nip65Write.orEmpty() + otherChosen, provisional = false)
            nip65Write != null -> Choice(nip65Write + otherChosen, provisional = false)
            else -> Choice(defaults + otherChosen, provisional = true)
        }

    /** The relays of [target] an event already sent to [reached] has yet to be published on. */
    fun missing(
        target: Set<NormalizedRelayUrl>,
        reached: Set<NormalizedRelayUrl>,
    ): Set<NormalizedRelayUrl> = target - reached
}
