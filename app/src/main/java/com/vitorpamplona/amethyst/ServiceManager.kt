package com.vitorpamplona.amethyst

import android.os.Build
import android.util.Log
import androidx.compose.runtime.Stable
import coil.Coil
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.size.Precision
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.HttpClient
import com.vitorpamplona.amethyst.service.NostrAccountDataSource
import com.vitorpamplona.amethyst.service.NostrChannelDataSource
import com.vitorpamplona.amethyst.service.NostrChatroomDataSource
import com.vitorpamplona.amethyst.service.NostrChatroomListDataSource
import com.vitorpamplona.amethyst.service.NostrCommunityDataSource
import com.vitorpamplona.amethyst.service.NostrDiscoveryDataSource
import com.vitorpamplona.amethyst.service.NostrGeohashDataSource
import com.vitorpamplona.amethyst.service.NostrHashtagDataSource
import com.vitorpamplona.amethyst.service.NostrHomeDataSource
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.service.NostrSingleChannelDataSource
import com.vitorpamplona.amethyst.service.NostrSingleEventDataSource
import com.vitorpamplona.amethyst.service.NostrSingleUserDataSource
import com.vitorpamplona.amethyst.service.NostrThreadDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileDataSource
import com.vitorpamplona.amethyst.service.NostrVideoDataSource
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.quartz.encoders.bechToBytes
import com.vitorpamplona.quartz.encoders.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.encoders.toHexKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Stable
class ServiceManager {
    private var isStarted: Boolean = false // to not open amber in a loop trying to use auth relays and registering for notifications
    private var account: Account? = null

    private fun start(account: Account) {
        this.account = account
        start()
    }

    private fun start() {
        Log.d("ServiceManager", "Pre Starting Relay Services $isStarted $account")
        if (isStarted && account != null) {
            return
        }
        Log.d("ServiceManager", "Starting Relay Services")

        val myAccount = account

        // Resets Proxy Use
        HttpClient.start(account?.proxy)
        OptOutFromFilters.start(account?.warnAboutPostsWithReports ?: true, account?.filterSpamFromStrangers ?: true)
        Coil.setImageLoader {
            Amethyst.instance.imageLoaderBuilder().components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(SvgDecoder.Factory())
            } // .logger(DebugLogger())
                .okHttpClient { HttpClient.getHttpClient() }
                .precision(Precision.INEXACT)
                .respectCacheHeaders(false)
                .build()
        }

        if (myAccount != null) {
            val relaySet = myAccount.activeRelays() ?: myAccount.convertLocalRelays()
            Log.d("Relay", "Service Manager Connect Connecting ${relaySet.size}")
            Client.reconnect(relaySet)

            // start services
            NostrAccountDataSource.account = myAccount
            NostrAccountDataSource.otherAccounts = LocalPreferences.allSavedAccounts().mapNotNull {
                try {
                    it.npub.bechToBytes().toHexKey()
                } catch (e: Exception) {
                    null
                }
            }
            NostrHomeDataSource.account = myAccount
            NostrChatroomListDataSource.account = myAccount
            NostrVideoDataSource.account = myAccount
            NostrDiscoveryDataSource.account = myAccount

            // Notification Elements
            NostrHomeDataSource.start()
            NostrAccountDataSource.start()
            GlobalScope.launch(Dispatchers.IO) {
                delay(3000)
                NostrChatroomListDataSource.start()
                NostrDiscoveryDataSource.start()
                NostrVideoDataSource.start()
            }

            // More Info Data Sources
            NostrSingleEventDataSource.start()
            NostrSingleChannelDataSource.start()
            NostrSingleUserDataSource.start()
            isStarted = true
        }
    }

    private fun pause() {
        Log.d("ServiceManager", "Pausing Relay Services")

        NostrAccountDataSource.stopSync()
        NostrHomeDataSource.stopSync()
        NostrChannelDataSource.stopSync()
        NostrChatroomDataSource.stopSync()
        NostrChatroomListDataSource.stopSync()
        NostrDiscoveryDataSource.stopSync()

        NostrCommunityDataSource.stopSync()
        NostrHashtagDataSource.stopSync()
        NostrGeohashDataSource.stopSync()
        NostrSearchEventOrUserDataSource.stopSync()
        NostrSingleChannelDataSource.stopSync()
        NostrSingleEventDataSource.stopSync()
        NostrSingleUserDataSource.stopSync()
        NostrThreadDataSource.stopSync()
        NostrUserProfileDataSource.stopSync()
        NostrVideoDataSource.stopSync()

        Client.reconnect(null)
        isStarted = false
    }

    fun cleanObservers() {
        LocalCache.cleanObservers()
    }

    fun trimMemory() {
        LocalCache.cleanObservers()

        val accounts = LocalPreferences.allSavedAccounts().mapNotNull {
            decodePublicKeyAsHexOrNull(it.npub)
        }.toSet()

        account?.let {
            LocalCache.pruneOldAndHiddenMessages(it)
            NostrChatroomDataSource.clearEOSEs(it)

            LocalCache.pruneHiddenMessages(it)
            LocalCache.pruneContactLists(accounts)
            LocalCache.pruneRepliesAndReactions(accounts)
            LocalCache.prunePastVersionsOfReplaceables()
            LocalCache.pruneExpiredEvents()
        }
    }

    // This method keeps the pause/start in a Syncronized block to
    // avoid concurrent pauses and starts.
    @Synchronized
    fun forceRestart(account: Account? = null, start: Boolean = true, pause: Boolean = true) {
        if (pause) {
            pause()
        }

        if (start) {
            if (account != null) {
                start(account)
            } else {
                start()
            }
        }
    }

    fun restartIfDifferentAccount(account: Account) {
        if (this.account != account) {
            forceRestart(account, true, true)
        }
    }

    fun forceRestart() {
        forceRestart(null, true, true)
    }

    fun justStart() {
        forceRestart(null, true, false)
    }

    fun pauseForGood() {
        forceRestart(null, false, true)
    }
}
