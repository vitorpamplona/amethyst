package com.vitorpamplona.amethyst.model

import android.util.Log
import android.util.LruCache
import androidx.lifecycle.LiveData
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nostr.postr.events.Event
import nostr.postr.toHex

data class Spammer(val pubkeyHex: HexKey, var duplicatedMessages: Set<HexKey>)

class AntiSpamFilter {
  val recentMessages = LruCache<Int, String>(1000)
  val spamMessages = LruCache<Int, Spammer>(1000)

  @Synchronized
  fun isSpam(event: Event): Boolean {
    val idHex = event.id.toHexKey()

    // if already processed, ok
    if (LocalCache.notes[idHex] != null) return false

    // if short message, ok
    if (event.content.length < 50) return false

    // double list strategy:
    // if duplicated, it goes into spam. 1000 spam messages are saved into the spam list.

    // Considers tags so that same replies to different people don't count.
    val hash = (event.content + event.tags.flatten().joinToString(",")).hashCode()

    if ((recentMessages[hash] != null && recentMessages[hash] != idHex) || spamMessages[hash] != null) {
      Log.w("Potential SPAM Message", "${event.id.toHex()} ${recentMessages[hash]} ${spamMessages[hash] != null} ${event.content.replace("\n", " | ")}")

      // Log down offenders
      if (spamMessages.get(hash) == null) {
        spamMessages.put(hash, Spammer(event.pubKey.toHexKey(), setOf(recentMessages[hash], event.id.toHex())))
        liveSpam.invalidateData()
      } else {
        val spammer = spamMessages.get(hash)
        spammer.duplicatedMessages = spammer.duplicatedMessages + event.id.toHex()

        liveSpam.invalidateData()
      }

      return true
    }

    recentMessages.put(hash, idHex)

    return false
  }

  val liveSpam: AntiSpamLiveData = AntiSpamLiveData(this)
}


class AntiSpamLiveData(val cache: AntiSpamFilter): LiveData<AntiSpamState>(AntiSpamState(cache)) {

  // Refreshes observers in batches.
  var handlerWaiting = AtomicBoolean()

  @Synchronized
  fun invalidateData() {
    if (!hasActiveObservers()) return
    if (handlerWaiting.getAndSet(true)) return

    handlerWaiting.set(true)
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

  private fun refresh() {
    postValue(AntiSpamState(cache))
  }
}

class AntiSpamState(val cache: AntiSpamFilter) {

}