package com.vitorpamplona.amethyst.service.relays

import com.vitorpamplona.amethyst.model.User

class EOSETime(var time: Long)

class EOSERelayList(var relayList: Map<String, EOSETime> = emptyMap()) {
    fun addOrUpdate(relayUrl: String, time: Long) {
        val eose = relayList[relayUrl]
        if (eose == null) {
            relayList = relayList + Pair(relayUrl, EOSETime(time))
        } else {
            eose.time = time
        }
    }
}

class EOSEAccount(var users: Map<User, EOSERelayList> = emptyMap()) {
    fun addOrUpdate(user: User, relayUrl: String, time: Long) {
        val relayList = users[user]
        if (relayList == null) {
            users = users + mapOf(user to EOSERelayList(mapOf(relayUrl to EOSETime(time))))
        } else {
            relayList.addOrUpdate(relayUrl, time)
        }
    }
}
