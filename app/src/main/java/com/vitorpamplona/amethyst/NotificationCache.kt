package com.vitorpamplona.amethyst

import android.content.Context
import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.model.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object NotificationCache {
  val lastReadByRoute = mutableMapOf<String, Long>()

  fun markAsRead(route: String, timestampInSecs: Long, context: Context) {
    val lastTime = lastReadByRoute[route]
    if (lastTime == null || timestampInSecs > lastTime) {
      lastReadByRoute.put(route, timestampInSecs)

      val scope = CoroutineScope(Job() + Dispatchers.IO)
      scope.launch {
        LocalPreferences(context).saveLastRead(route, timestampInSecs)
        invalidateData()
      }
    }
  }

  fun load(route: String, context: Context): Long {
    var lastTime = lastReadByRoute[route]
    if (lastTime == null) {
      lastTime = LocalPreferences(context).loadLastRead(route)
      lastReadByRoute[route] = lastTime
    }
    return lastTime
  }

  // Observers line up here.
  val live: NotificationLiveData = NotificationLiveData(this)

  // Refreshes observers in batches.
  var handlerWaiting = false
  @Synchronized
  fun invalidateData() {
    if (handlerWaiting) return

    handlerWaiting = true
    val scope = CoroutineScope(Job() + Dispatchers.Default)
    scope.launch {
      delay(100)
      live.refresh()
      handlerWaiting = false
    }
  }
}

class NotificationLiveData(val cache: NotificationCache): LiveData<NotificationState>(NotificationState(cache)) {
  fun refresh() {
    postValue(NotificationState(cache))
  }
}

class NotificationState(val cache: NotificationCache)