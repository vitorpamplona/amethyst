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
package com.vitorpamplona.amethyst.service.relays

import androidx.collection.LruCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.ammolite.relays.filters.MutableTime
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.collections.plus

typealias SincePerRelayMap = Map<NormalizedRelayUrl, MutableTime>

class EOSERelayList {
    var relayList: SincePerRelayMap = emptyMap()

    fun addOrUpdate(
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) {
        val eose = relayList[relayUrl]
        if (eose == null) {
            relayList = relayList + Pair(relayUrl, MutableTime(time))
        } else {
            eose.updateIfNewer(time)
        }
    }

    fun clear() {
        relayList = emptyMap()
    }

    fun since() = relayList

    fun newEose(
        relay: NormalizedRelayUrl,
        time: Long,
    ) = addOrUpdate(relay, time)
}

class EOSEFollowList(
    cacheSize: Int = 200,
) {
    var followList: LruCache<String, EOSERelayList> = LruCache<String, EOSERelayList>(cacheSize)

    fun addOrUpdate(
        listCode: String,
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) {
        val relayList = followList[listCode]
        if (relayList == null) {
            val newList = EOSERelayList()
            newList.addOrUpdate(relayUrl, time)
            followList.put(listCode, newList)
        } else {
            relayList.addOrUpdate(relayUrl, time)
        }
    }

    fun since(listCode: String) = followList[listCode]?.relayList

    fun newEose(
        listCode: String,
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) = addOrUpdate(listCode, relayUrl, time)
}

class EOSEAccount(
    cacheSize: Int = 20,
) {
    var users: LruCache<User, EOSEFollowList> = LruCache<User, EOSEFollowList>(cacheSize)

    fun addOrUpdate(
        user: User,
        listCode: String,
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) {
        val followList = users[user]
        if (followList == null) {
            val newList = EOSEFollowList()
            users.put(user, newList)
            newList.addOrUpdate(listCode, relayUrl, time)
        } else {
            followList.addOrUpdate(listCode, relayUrl, time)
        }
    }

    fun removeDataFor(user: User) {
        users.remove(user)
    }

    fun since(
        key: User,
        listCode: String,
    ) = users[key]?.followList?.get(listCode)?.relayList

    fun newEose(
        user: User,
        listCode: String,
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) = addOrUpdate(user, listCode, relayUrl, time)
}

class EOSEAccountFast<T : Any>(
    cacheSize: Int = 20,
) {
    private val users: LruCache<T, EOSERelayList> = LruCache<T, EOSERelayList>(cacheSize)

    fun addOrUpdate(
        user: T,
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) {
        val relayList = users[user]
        if (relayList == null) {
            val newList = EOSERelayList()
            users.put(user, newList)

            newList.addOrUpdate(relayUrl, time)
        } else {
            relayList.addOrUpdate(relayUrl, time)
        }
    }

    fun removeDataFor(user: T) {
        users.remove(user)
    }

    fun since(key: T) = users[key]?.relayList

    fun newEose(
        user: T,
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) = addOrUpdate(user, relayUrl, time)
}
