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
package com.vitorpamplona.amethyst.commons.wot.nudge

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent

/** When Home invites the user to turn on web of trust. */
object WebOfTrustNudgeRules {
    /**
     * Follows, not counting the user, a provider needs to map a circle from. One is enough: the
     * provider walks out from there, so following one well-connected person already places the
     * user in the network.
     */
    const val MIN_FOLLOWS = 1

    /** How long "Not now" hides the card. */
    const val SNOOZE_SECONDS = 30L * 24 * 60 * 60

    /** "Hidden until" for a card the user closed: for good. */
    const val FOREVER = Long.MAX_VALUE

    fun shouldOffer(
        followCount: Int,
        hasProvider: Boolean,
        hiddenUntil: Long,
        now: Long,
    ): Boolean = !hasProvider && followCount >= MIN_FOLLOWS && now >= hiddenUntil
}

/**
 * How many people the user's follows follow, leaving out the user and their own follows, from
 * the follow lists already in the cache. A lower bound: a follow whose list has not loaded adds
 * nobody. Zero when none has loaded.
 */
fun Account.followsOfFollowsCount(follows: Set<HexKey> = kind3FollowList.flow.value.authors): Int {
    val me = signer.pubKey
    val seen = HashSet<HexKey>()
    follows.forEach { follow ->
        val list = cache.getAddressableNoteIfExists(ContactListEvent.createAddress(follow))?.event as? ContactListEvent
        list?.unverifiedFollowKeySet()?.forEach { if (it != me && it !in follows) seen.add(it) }
    }
    return seen.size
}
