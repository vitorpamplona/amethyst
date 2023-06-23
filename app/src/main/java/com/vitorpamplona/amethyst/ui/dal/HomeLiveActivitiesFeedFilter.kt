package com.vitorpamplona.amethyst.ui.dal

import android.util.Log
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.HttpClient
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.model.LiveActivitiesEvent
import okhttp3.Request
import java.util.Date

class HomeLiveActivitiesFeedFilter(val account: Account) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String {
        val followingKeySet = account.selectedUsersFollowList(account.defaultHomeFollowList)?.size ?: 0
        val followingTagSet = account.selectedTagsFollowList(account.defaultHomeFollowList)?.size ?: 0

        return account.userProfile().pubkeyHex + "-" + account.defaultHomeFollowList + "-" + followingKeySet + "-" + followingTagSet
    }

    override fun feed(): List<Note> {
        val longFormNotes = innerApplyFilter(LocalCache.addressables.values)

        return sort(longFormNotes)
    }

    override fun applyFilter(collection: Set<Note>): Set<Note> {
        return innerApplyFilter(collection)
    }

    private fun innerApplyFilter(collection: Collection<Note>): Set<Note> {
        checkNotInMainThread()

        val followingKeySet = account.selectedUsersFollowList(account.defaultHomeFollowList) ?: emptySet()
        val followingTagSet = account.selectedTagsFollowList(account.defaultHomeFollowList) ?: emptySet()

        val fortyEightHrs = (Date().time / 1000) - 60 * 60 * 48 // hrs

        return collection
            .asSequence()
            .filter { it ->
                val noteEvent = it.event
                (noteEvent is LiveActivitiesEvent && noteEvent.createdAt > fortyEightHrs && noteEvent.status() == "live" && checkIfOnline(noteEvent.streaming())) &&
                    (it.author?.pubkeyHex in followingKeySet || (noteEvent.isTaggedHashes(followingTagSet))) &&
                    // && account.isAcceptable(it)  // This filter follows only. No need to check if acceptable
                    it.author?.let { !account.isHidden(it.pubkeyHex) } ?: true
            }
            .toSet()
    }

    override fun limit() = 2

    override fun sort(collection: Set<Note>): List<Note> {
        return collection.sortedWith(compareBy({ it.createdAt() }, { it.idHex })).reversed()
    }
}

fun checkIfOnline(url: String?): Boolean {
    if (url.isNullOrBlank()) return false

    val request = Request.Builder()
        .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
        .url(url)
        .get()
        .build()

    return try {
        HttpClient.getHttpClient().newCall(request).execute().code == 200
    } catch (e: Exception) {
        Log.e("LiveActivities", "Failed to check streaming url $url", e)
        false
    }
}
