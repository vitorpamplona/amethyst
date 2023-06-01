package com.vitorpamplona.amethyst

import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object NotificationCache {
    // TODO: This must be account-based
    val lastReadByRoute = mutableMapOf<String, Long>()

    fun markAsRead(route: String, timestampInSecs: Long) {
        val lastTime = lastReadByRoute[route]
        if (lastTime == null || timestampInSecs > lastTime) {
            lastReadByRoute.put(route, timestampInSecs)

            val scope = CoroutineScope(Job() + Dispatchers.IO)
            scope.launch {
                LocalPreferences.saveLastRead(route, timestampInSecs)
                live.invalidateData()
            }
        }
    }

    fun load(route: String): Long {
        var lastTime = lastReadByRoute[route]
        if (lastTime == null) {
            lastTime = LocalPreferences.loadLastRead(route)
            lastReadByRoute[route] = lastTime
        }
        return lastTime
    }

    // Observers line up here.
    val live: NotificationLiveData = NotificationLiveData(this)
}

class NotificationLiveData(val cache: NotificationCache) : LiveData<NotificationState>(NotificationState(cache)) {
    // Refreshes observers in batches.
    private val bundler = BundledUpdate(300, Dispatchers.IO)

    fun invalidateData() {
        bundler.invalidate() {
            if (hasActiveObservers()) {
                postValue(NotificationState(cache))
            }
        }
    }
}

class NotificationState(val cache: NotificationCache)
