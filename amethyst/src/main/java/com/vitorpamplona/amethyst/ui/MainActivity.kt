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

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.debugState
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.lang.LanguageTranslatorService
import com.vitorpamplona.amethyst.service.okhttp.HttpClientManager
import com.vitorpamplona.amethyst.service.playback.composable.DEFAULT_MUTED_SETTING
import com.vitorpamplona.amethyst.service.playback.pip.BackgroundMedia
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.screen.AccountScreen
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme
import com.vitorpamplona.quartz.nip04Dm.messages.PrivateDmEvent
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.Note
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
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

    private var shouldPauseService = true

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        Log.d("Lifetime Event", "MainActivity.onCreate")

        setContent {
            val sharedPreferencesViewModel = prepareSharedViewModel(act = this)

            AmethystTheme(sharedPreferencesViewModel) {
                val accountStateViewModel: AccountStateViewModel = viewModel()

                LaunchedEffect(key1 = Unit) {
                    accountStateViewModel.tryLoginExistingAccountAsync()
                }

                AccountScreen(accountStateViewModel, sharedPreferencesViewModel)
            }
        }
    }

    fun prepareToLaunchSigner() {
        shouldPauseService = false
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onResume() {
        super.onResume()

        val locales = this.applicationContext.resources.configuration.locales
        if (!locales.isEmpty) {
            checkLanguage(locales.get(0).language)
        }

        Log.d("Lifetime Event", "MainActivity.onResume")

        // starts muted every time
        DEFAULT_MUTED_SETTING.value = true

        // Keep connection alive if it's calling the signer app
        Log.d("shouldPauseService", "shouldPauseService onResume: $shouldPauseService")
        if (shouldPauseService) {
            GlobalScope.launch(Dispatchers.IO) { Amethyst.instance.serviceManager.justStart() }
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
        Amethyst.instance.serviceManager.cleanObservers()

        // if (BuildConfig.DEBUG) {
        GlobalScope.launch(Dispatchers.IO) { debugState(this@MainActivity) }
        // }

        Log.d("shouldPauseService", "shouldPauseService onPause: $shouldPauseService")
        if (shouldPauseService) {
            GlobalScope.launch(Dispatchers.IO) { Amethyst.instance.serviceManager.pauseForGood() }
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

        BackgroundMedia.removeBackgroundControllerAndReleaseIt()

        super.onDestroy()
    }

    fun updateNetworkCapabilities(networkCapabilities: NetworkCapabilities): Boolean {
        val unmetered = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

        val isOnMobileData = !unmetered || networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val isOnWifi = unmetered && networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

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
                    GlobalScope.launch(Dispatchers.IO) { Amethyst.instance.serviceManager.forceRestart() }
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
                        Amethyst.instance.serviceManager.forceRestart()
                    }
                }
            }
        }
}

fun uriToRoute(uri: String?): String? =
    if (uri?.startsWith("notifications", true) == true || uri?.startsWith("nostr:notifications", true) == true) {
        Route.Notification.route.replace("{scrollToTop}", "true")
    } else {
        if (uri?.startsWith("hashtag?id=") == true || uri?.startsWith("nostr:hashtag?id=") == true) {
            Route.Hashtag.route.replace("{id}", uri.removePrefix("nostr:").removePrefix("hashtag?id="))
        } else {
            val nip19 = Nip19Parser.uriToRoute(uri)?.entity
            when (nip19) {
                is NPub -> "User/${nip19.hex}"
                is NProfile -> "User/${nip19.hex}"
                is Note -> "Note/${nip19.hex}"
                is NEvent -> {
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

                is NAddress -> {
                    if (nip19.kind == CommunityDefinitionEvent.KIND) {
                        "Community/${nip19.aTag()}"
                    } else if (nip19.kind == LiveActivitiesEvent.KIND) {
                        "Channel/${nip19.aTag()}"
                    } else {
                        "Event/${nip19.aTag()}"
                    }
                }

                is NEmbed -> {
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
                    Route.NIP47Setup.base + "?nip47=" + encodedUri
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
    }
