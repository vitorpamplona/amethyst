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
import com.vitorpamplona.amethyst.service.relays.MutableTime
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

typealias SincePerRelayMap = MutableMap<NormalizedRelayUrl, MutableTime>

class EOSERelayList {
    var relayList: SincePerRelayMap = mutableMapOf()

    fun addOrUpdate(
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) {
        val eose = relayList[relayUrl]
        if (eose == null) {
            relayList[relayUrl] = MutableTime(time)
        } else {
            eose.updateIfNewer(time)
        }
    }

    fun clear() {
        relayList = mutableMapOf()
    }

    fun since() = relayList

    fun newEose(
        relay: NormalizedRelayUrl,
        time: Long,
    ) = addOrUpdate(relay, time)
}

open class EOSEByKey<U : Any>(
    cacheSize: Int = 200,
) {
    var followList: LruCache<U, EOSERelayList> = LruCache<U, EOSERelayList>(cacheSize)

    fun addOrUpdate(
        listCode: U,
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

    fun since(listCode: U) = followList[listCode]?.relayList

    fun newEose(
        listCode: U,
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) = addOrUpdate(listCode, relayUrl, time)
}

open class EOSEAccountKey<U : Any>(
    cacheSize: Int = 20,
) {
    var users: LruCache<User, EOSEByKey<U>> = LruCache<User, EOSEByKey<U>>(cacheSize)

    fun addOrUpdate(
        user: User,
        listCode: U,
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) {
        val followList = users[user]
        if (followList == null) {
            val newList = EOSEByKey<U>()
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
        listCode: U,
    ) = users[key]?.followList?.get(listCode)?.relayList

    fun newEose(
        user: User,
        listCode: U,
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) = addOrUpdate(user, listCode, relayUrl, time)
}

class EOSEAccountFast<T : Any>(
    cacheSize: Int = 20,
) {
    private val users: LruCache<T, EOSERelayList> = LruCache<T, EOSERelayList>(cacheSize)
    private val lock = Any()

    fun addOrUpdate(
        user: T,
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) {
        synchronized(lock) {
            val relayList = users[user]
            if (relayList == null) {
                val newList = EOSERelayList()
                users.put(user, newList)

                newList.addOrUpdate(relayUrl, time)
            } else {
                relayList.addOrUpdate(relayUrl, time)
            }
        }
    }

    fun removeEveryoneBut(list: Set<T>) {
        synchronized(lock) {
            users.snapshot().forEach {
                if (it.key !in list) {
                    users.remove(it.key)
                }
            }
        }
    }

    fun removeDataFor(user: T) {
        synchronized(lock) {
            users.remove(user)
        }
    }

    fun since(key: T): SincePerRelayMap? =
        synchronized(lock) {
            users[key]?.relayList?.toMutableMap()
        }

    fun sinceRelaySet(key: T): Set<NormalizedRelayUrl>? =
        synchronized(lock) {
            users[key]?.relayList?.keys?.toSet()
        }

    fun newEose(
        user: T,
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) = addOrUpdate(user, relayUrl, time)
}
