/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.HttpClientManager
import com.vitorpamplona.amethyst.service.lang.LanguageTranslatorService
import com.vitorpamplona.amethyst.service.notifications.PushNotificationUtils
import com.vitorpamplona.amethyst.ui.components.DEFAULT_MUTED_SETTING
import com.vitorpamplona.amethyst.ui.components.keepPlayingMutex
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.navigation.debugState
import com.vitorpamplona.quartz.encoders.Nip19Bech32
import com.vitorpamplona.quartz.encoders.Nip47WalletConnect
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChannelMessageEvent
import com.vitorpamplona.quartz.events.ChannelMetadataEvent
import com.vitorpamplona.quartz.events.CommunityDefinitionEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import com.vitorpamplona.quartz.events.PrivateDmEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Timer
import kotlin.concurrent.schedule

class MainActivity : AppCompatActivity() {
    val isOnMobileDataState = mutableStateOf(false)
    private val isOnWifiDataState = mutableStateOf(false)

    // Service Manager is only active when the activity is active.
    val serviceManager = ServiceManager()
    private var shouldPauseService = true

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("Lifetime Event", "MainActivity.onCreate")

        setContent {
            val sharedPreferencesViewModel = prepareSharedViewModel(act = this)
            AppScreen(sharedPreferencesViewModel = sharedPreferencesViewModel, serviceManager = serviceManager)
        }
    }

    fun prepareToLaunchSigner() {
        shouldPauseService = false
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onResume() {
        super.onResume()

        Log.d("Lifetime Event", "MainActivity.onResume")

        // starts muted every time
        DEFAULT_MUTED_SETTING.value = true

        // Keep connection alive if it's calling the signer app
        Log.d("shouldPauseService", "shouldPauseService onResume: $shouldPauseService")
        if (shouldPauseService) {
            GlobalScope.launch(Dispatchers.IO) { serviceManager.justStart() }
        }

        GlobalScope.launch(Dispatchers.IO) {
            PushNotificationUtils.init(LocalPreferences.allSavedAccounts())
        }

        val connectivityManager =
            (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)?.let {
            updateNetworkCapabilities(it)
        }

        // resets state until next External Signer Call
        Timer().schedule(350) { shouldPauseService = true }
    }

    override fun onPause() {
        Log.d("Lifetime Event", "MainActivity.onPause")

        GlobalScope.launch(Dispatchers.IO) {
            LanguageTranslatorService.clear()
        }
        serviceManager.cleanObservers()

        // if (BuildConfig.DEBUG) {
        GlobalScope.launch(Dispatchers.IO) { debugState(this@MainActivity) }
        // }

        Log.d("shouldPauseService", "shouldPauseService onPause: $shouldPauseService")
        if (shouldPauseService) {
            GlobalScope.launch(Dispatchers.IO) { serviceManager.pauseForGood() }
        }

        (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
            .unregisterNetworkCallback(networkCallback)

        super.onPause()
    }

    override fun onStart() {
        super.onStart()

        Log.d("Lifetime Event", "MainActivity.onStart")
    }

    override fun onStop() {
        super.onStop()

        // Graph doesn't completely clear.
        // GlobalScope.launch(Dispatchers.Default) {
        //    serviceManager.trimMemory()
        // }

        Log.d("Lifetime Event", "MainActivity.onStop")
    }

    override fun onDestroy() {
        Log.d("Lifetime Event", "MainActivity.onDestroy")

        GlobalScope.launch(Dispatchers.Main) {
            keepPlayingMutex?.stop()
            keepPlayingMutex?.release()
            keepPlayingMutex = null
        }

        super.onDestroy()
    }

    /**
     * Release memory when the UI becomes hidden or when system resources become low.
     *
     * @param level the memory-related event that was raised.
     */
    @OptIn(DelicateCoroutinesApi::class)
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        println("Trim Memory $level")
        GlobalScope.launch(Dispatchers.Default) { serviceManager.trimMemory() }
    }

    fun updateNetworkCapabilities(networkCapabilities: NetworkCapabilities): Boolean {
        val isOnMobileData = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val isOnWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

        var changedNetwork = false

        if (isOnMobileDataState.value != isOnMobileData) {
            isOnMobileDataState.value = isOnMobileData

            changedNetwork = true
        }

        if (isOnWifiDataState.value != isOnWifi) {
            isOnWifiDataState.value = isOnWifi

            changedNetwork = true
        }

        if (changedNetwork) {
            if (isOnMobileData) {
                HttpClientManager.setDefaultTimeout(HttpClientManager.DEFAULT_TIMEOUT_ON_MOBILE)
            } else {
                HttpClientManager.setDefaultTimeout(HttpClientManager.DEFAULT_TIMEOUT_ON_WIFI)
            }
        }

        return changedNetwork
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val networkCallback =
        object : ConnectivityManager.NetworkCallback() {
            var lastNetwork: Network? = null

            override fun onAvailable(network: Network) {
                super.onAvailable(network)

                Log.d("ServiceManager NetworkCallback", "onAvailable: $shouldPauseService")
                if (shouldPauseService && lastNetwork != null && lastNetwork != network) {
                    GlobalScope.launch(Dispatchers.IO) { serviceManager.forceRestart() }
                }

                lastNetwork = network
            }

            // Network capabilities have changed for the network
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)

                GlobalScope.launch(Dispatchers.IO) {
                    Log.d(
                        "ServiceManager NetworkCallback",
                        "onCapabilitiesChanged: ${network.networkHandle} hasMobileData ${isOnMobileDataState.value} hasWifi ${isOnWifiDataState.value}",
                    )
                    if (updateNetworkCapabilities(networkCapabilities) && shouldPauseService) {
                        serviceManager.forceRestart()
                    }
                }
            }
        }
}

class GetMediaActivityResultContract : ActivityResultContracts.GetContent() {
    @SuppressLint("MissingSuperCall")
    override fun createIntent(
        context: Context,
        input: String,
    ): Intent {
        // Force only images and videos to be selectable
        // Force OPEN Document because of the resulting URI must be passed to the
        // Playback service and the picker's permissions only allow the activity to read the URI
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // Force only images and videos to be selectable
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
        }
    }
}

fun uriToRoute(uri: String?): String? {
    return if (uri.equals("nostr:Notifications", true)) {
        Route.Notification.route.replace("{scrollToTop}", "true")
    } else {
        if (uri?.startsWith("nostr:Hashtag?id=") == true) {
            Route.Hashtag.route.replace("{id}", uri.removePrefix("nostr:Hashtag?id="))
        } else {
            val nip19 = Nip19Bech32.uriToRoute(uri)?.entity
            when (nip19) {
                is Nip19Bech32.NPub -> "User/${nip19.hex}"
                is Nip19Bech32.NProfile -> "User/${nip19.hex}"
                is Nip19Bech32.Note -> "Note/${nip19.hex}"
                is Nip19Bech32.NEvent -> {
                    if (nip19.kind == PrivateDmEvent.KIND) {
                        nip19.author?.let { "RoomByAuthor/$it" }
                    } else if (
                        nip19.kind == ChannelMessageEvent.KIND ||
                        nip19.kind == ChannelCreateEvent.KIND ||
                        nip19.kind == ChannelMetadataEvent.KIND
                    ) {
                        "Channel/${nip19.hex}"
                    } else {
                        "Event/${nip19.hex}"
                    }
                }
                is Nip19Bech32.NAddress -> {
                    if (nip19.kind == CommunityDefinitionEvent.KIND) {
                        "Community/${nip19.atag}"
                    } else if (nip19.kind == LiveActivitiesEvent.KIND) {
                        "Channel/${nip19.atag}"
                    } else {
                        "Event/${nip19.atag}"
                    }
                }
                is Nip19Bech32.NEmbed -> {
                    if (LocalCache.getNoteIfExists(nip19.event.id) == null) {
                        LocalCache.verifyAndConsume(nip19.event, null)
                    }
                    "Event/${nip19.event.id}"
                }
                else -> null
            }
        }
            ?: try {
                uri?.let {
                    Nip47WalletConnect.parse(it)
                    val encodedUri = URLEncoder.encode(it, StandardCharsets.UTF_8.toString())
                    Route.Home.base + "?nip47=" + encodedUri
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
    }
}
