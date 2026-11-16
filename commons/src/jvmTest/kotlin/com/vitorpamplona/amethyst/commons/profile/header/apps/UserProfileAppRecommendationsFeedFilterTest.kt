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
package com.vitorpamplona.amethyst.commons.profile.header.apps

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip89AppHandlers.recommendation.AppRecommendationEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The profile's app recommendations read only the user's own kind-31989 lists, not every cached
 * addressable event: another user's list, and other addressable kinds, never show up.
 */
class UserProfileAppRecommendationsFeedFilterTest {
    private val me = NostrSignerSync(KeyPair())
    private val someoneElse = NostrSignerSync(KeyPair())
    private val appAuthor = KeyPair().pubKey.joinToString("") { "%02x".format(it) }

    private fun recommending(
        signer: NostrSignerSync,
        app: String,
    ): AppRecommendationEvent =
        signer.sign(
            1_000,
            AppRecommendationEvent.KIND,
            arrayOf(arrayOf("d", "1"), arrayOf("a", "31990:$appAuthor:$app", "wss://relay.example.com/", "web")),
            "",
        )

    @Test
    fun onlyTheUsersOwnRecommendationsAreRead() {
        LocalCache.justConsume(recommending(me, "mine"), null, true)
        LocalCache.justConsume(recommending(someoneElse, "theirs"), null, true)

        val user = LocalCache.getOrCreateUser(me.pubKey)
        val feed = UserProfileAppRecommendationsFeedFilter(user).feed()

        assertEquals(listOf("31990:$appAuthor:mine"), feed.map { (it as AddressableNote).address.toValue() })
    }
}
