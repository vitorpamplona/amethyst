package com.vitorpamplona.amethyst

import android.content.Context
import androidx.lifecycle.LiveData
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object NotificationCache {
  val lastReadByRoute = mutableMapOf<String, Long>()

  fun markAsRead(route: String, timestampInSecs: Long, context: Context) {
    val lastTime = lastReadByRoute[route]
    if (lastTime == null || timestampInSecs > lastTime) {
      lastReadByRoute.put(route, timestampInSecs)

      val scope = CoroutineScope(Job() + Dispatchers.IO)
      scope.launch {
        LocalPreferences(context).saveLastRead(route, timestampInSecs)
        live.invalidateData()
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
}

class NotificationLiveData(val cache: NotificationCache): LiveData<NotificationState>(NotificationState(cache)) {
  // Refreshes observers in batches.
  var handlerWaiting = AtomicBoolean()

  fun invalidateData() {
    if (!hasActiveObservers()) return
    if (handlerWaiting.getAndSet(true)) return

    val scope = CoroutineScope(Job() + Dispatchers.Main)
    scope.launch {
      try {
        delay(100)
        refresh()
      } finally {
        withContext(NonCancellable) {
          handlerWaiting.set(false)
        }
      }
    }
  }

  fun refresh() {
    postValue(NotificationState(cache))
  }
}

class NotificationState(val cache: NotificationCache)