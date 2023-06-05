package com.vitorpamplona.amethyst.model

import android.util.Log
import android.util.LruCache
import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.model.Event
import com.vitorpamplona.amethyst.service.nip19.Nip19
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import kotlinx.coroutines.Dispatchers

data class Spammer(val pubkeyHex: HexKey, var duplicatedMessages: Set<HexKey>)

class AntiSpamFilter {
    val recentMessages = LruCache<Int, String>(1000)
    val spamMessages = LruCache<Int, Spammer>(1000)

    @Synchronized
    fun isSpam(event: Event, relay: Relay?): Boolean {
        checkNotInMainThread()

        val idHex = event.id

        // if short message, ok
        if (event.content.length < 50) return false

        // double list strategy:
        // if duplicated, it goes into spam. 1000 spam messages are saved into the spam list.

        // Considers tags so that same replies to different people don't count.
        val hash = (event.content + event.tags.flatten().joinToString(",")).hashCode()

        if ((recentMessages[hash] != null && recentMessages[hash] != idHex) || spamMessages[hash] != null) {
            Log.w("Potential SPAM Message for sharing", "${Nip19.createNEvent(event.id, event.pubKey, event.kind, null)}")
            Log.w("Potential SPAM Message", "${event.id} ${recentMessages[hash]} ${spamMessages[hash] != null} ${relay?.url} ${event.content.replace("\n", " | ")}")

            // Log down offenders
            if (spamMessages.get(hash) == null) {
                spamMessages.put(hash, Spammer(event.pubKey, setOf(recentMessages[hash], event.id)))
                liveSpam.invalidateData()
            } else {
                val spammer = spamMessages.get(hash)
                spammer.duplicatedMessages = spammer.duplicatedMessages + event.id

                liveSpam.invalidateData()
            }

            return true
        }

        recentMessages.put(hash, idHex)

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
