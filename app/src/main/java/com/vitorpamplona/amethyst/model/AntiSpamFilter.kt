package com.vitorpamplona.amethyst.model

import android.util.Log
import android.util.LruCache
import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.model.Event
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import kotlinx.coroutines.Dispatchers

data class Spammer(val pubkeyHex: HexKey, var duplicatedMessages: Set<HexKey>)

class AntiSpamFilter {
    val recentMessages = LruCache<Int, String>(1000)
    val spamMessages = LruCache<Int, Spammer>(1000)

    @Synchronized
    fun isSpam(event: Event, relay: Relay?): Boolean {
        return false
    }

    val liveSpam: AntiSpamLiveData = AntiSpamLiveData(this)
}

class AntiSpamLiveData(val cache: AntiSpamFilter) : LiveData<AntiSpamState>(AntiSpamState(cache)) {

    // Refreshes observers in batches.
    private val bundler = BundledUpdate(300, Dispatchers.IO)

    fun invalidateData() {
        bundler.invalidate() {
            if (hasActiveObservers()) {
                postValue(AntiSpamState(cache))
            }
        }
    }
}

class AntiSpamState(val cache: AntiSpamFilter)
