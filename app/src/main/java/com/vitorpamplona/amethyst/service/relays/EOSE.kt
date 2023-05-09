package com.vitorpamplona.amethyst.service.relays

import com.vitorpamplona.amethyst.model.User

class EOSETime(var time: Long) {
    override fun toString(): String {
        return time.toString()
    }
}

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

class EOSEFollowList(var followList: Map<String, EOSERelayList> = emptyMap()) {
    fun addOrUpdate(listCode: String, relayUrl: String, time: Long) {
        val relayList = followList[listCode]
        if (relayList == null) {
            val newList = EOSERelayList()
            newList.addOrUpdate(relayUrl, time)
            followList = followList + mapOf(listCode to newList)
        } else {
            relayList.addOrUpdate(relayUrl, time)
        }
    }
}

class EOSEAccount(var users: Map<User, EOSEFollowList> = emptyMap()) {
    fun addOrUpdate(user: User, listCode: String, relayUrl: String, time: Long) {
        val followList = users[user]
        if (followList == null) {
            val newList = EOSEFollowList()
            newList.addOrUpdate(listCode, relayUrl, time)
            users = users + mapOf(user to newList)
        } else {
            followList.addOrUpdate(listCode, relayUrl, time)
        }
    }
}
