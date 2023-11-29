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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.adaptive.calculateDisplayFeatures
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.service.HttpClient
import com.vitorpamplona.amethyst.service.lang.LanguageTranslatorService
import com.vitorpamplona.amethyst.service.notifications.PushNotificationUtils
import com.vitorpamplona.amethyst.ui.components.DefaultMutedSetting
import com.vitorpamplona.amethyst.ui.components.keepPlayingMutex
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.navigation.debugState
import com.vitorpamplona.amethyst.ui.note.Nip47
import com.vitorpamplona.amethyst.ui.screen.AccountScreen
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme
import com.vitorpamplona.quartz.encoders.Nip19
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.ChannelMessageEvent
import com.vitorpamplona.quartz.events.ChannelMetadataEvent
import com.vitorpamplona.quartz.events.CommunityDefinitionEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import com.vitorpamplona.quartz.events.PrivateDmEvent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Timer
import kotlin.concurrent.schedule

class MainActivity : AppCompatActivity() {
    private val isOnMobileDataState = mutableStateOf(false)
    private val isOnWifiDataState = mutableStateOf(false)

    // Service Manager is only active when the activity is active.
    val serviceManager = ServiceManager()
    private var shouldPauseService = true

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("Lifetime Event", "MainActivity.onCreate")

        setContent {
            val sharedPreferencesViewModel: SharedPreferencesViewModel = viewModel()

            val displayFeatures = calculateDisplayFeatures(this)
            val windowSizeClass = calculateWindowSizeClass(this)

            LaunchedEffect(key1 = sharedPreferencesViewModel) {
                sharedPreferencesViewModel.init()
                sharedPreferencesViewModel.updateDisplaySettings(windowSizeClass, displayFeatures)
            }

            LaunchedEffect(isOnMobileDataState) {
                sharedPreferencesViewModel.updateConnectivityStatusState(isOnMobileDataState)
            }

            AmethystTheme(sharedPreferencesViewModel) {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val accountStateViewModel: AccountStateViewModel = viewModel()
                    accountStateViewModel.serviceManager = serviceManager

                    LaunchedEffect(key1 = Unit) {
                        accountStateViewModel.tryLoginExistingAccountAsync()
                    }

                    AccountScreen(accountStateViewModel, sharedPreferencesViewModel)
                }
            }
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
        DefaultMutedSetting.value = true

        // Keep connection alive if it's calling the signer app
        Log.d("shouldPauseService", "shouldPauseService onResume: $shouldPauseService")
        if (shouldPauseService) {
            GlobalScope.launch(Dispatchers.IO) {
                serviceManager.justStart()
            }
        }

        GlobalScope.launch(Dispatchers.IO) {
            PushNotificationUtils.init(LocalPreferences.allSavedAccounts())
        }

        val connectivityManager = (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            ?.let { updateNetworkCapabilities(it) }

        // resets state until next External Signer Call
        Timer().schedule(350) {
            shouldPauseService = true
        }
    }

    override fun onPause() {
        Log.d("Lifetime Event", "MainActivity.onPause")

        LanguageTranslatorService.clear()
        serviceManager.cleanObservers()

        // if (BuildConfig.DEBUG) {
        GlobalScope.launch(Dispatchers.IO) {
            debugState(this@MainActivity)
        }
        // }

        Log.d("shouldPauseService", "shouldPauseService onPause: $shouldPauseService")
        if (shouldPauseService) {
            GlobalScope.launch(Dispatchers.IO) {
                serviceManager.pauseForGood()
            }
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

        GlobalScope.launch(Dispatchers.IO) {
            keepPlayingMutex?.stop()
            keepPlayingMutex?.release()
            keepPlayingMutex = null
        }

        super.onDestroy()
    }

    /**
     * Release memory when the UI becomes hidden or when system resources become low.
     * @param level the memory-related event that was raised.
     */
    @OptIn(DelicateCoroutinesApi::class)
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        println("Trim Memory $level")
        GlobalScope.launch(Dispatchers.Default) {
            serviceManager.trimMemory()
        }
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
                HttpClient.changeTimeouts(HttpClient.DEFAULT_TIMEOUT_ON_MOBILE)
            } else {
                HttpClient.changeTimeouts(HttpClient.DEFAULT_TIMEOUT_ON_WIFI)
            }
        }

        return changedNetwork
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        var lastNetwork: Network? = null

        override fun onAvailable(network: Network) {
            super.onAvailable(network)

            Log.d("ServiceManager NetworkCallback", "onAvailable: $shouldPauseService")
            if (shouldPauseService && lastNetwork != null && lastNetwork != network) {
                GlobalScope.launch(Dispatchers.IO) {
                    serviceManager.forceRestart()
                }
            }

            lastNetwork = network
        }

        // Network capabilities have changed for the network
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            super.onCapabilitiesChanged(network, networkCapabilities)

            GlobalScope.launch(Dispatchers.IO) {
                Log.d("ServiceManager NetworkCallback", "onCapabilitiesChanged: ${network.networkHandle} hasMobileData ${isOnMobileDataState.value} hasWifi ${isOnWifiDataState.value}")
                if (updateNetworkCapabilities(networkCapabilities) && shouldPauseService) {
                    GlobalScope.launch(Dispatchers.IO) {
                        serviceManager.forceRestart()
                    }
                }
            }
        }
    }
}

class GetMediaActivityResultContract : ActivityResultContracts.GetContent() {

    @SuppressLint("MissingSuperCall")
    override fun createIntent(context: Context, input: String): Intent {
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
            val nip19 = Nip19.uriToRoute(uri)
            when (nip19?.type) {
                Nip19.Type.USER -> "User/${nip19.hex}"
                Nip19.Type.NOTE -> "Note/${nip19.hex}"
                Nip19.Type.EVENT -> {
                    if (nip19.kind == PrivateDmEvent.kind) {
                        nip19.author?.let {
                            "RoomByAuthor/$it"
                        }
                    } else if (nip19.kind == ChannelMessageEvent.kind || nip19.kind == ChannelCreateEvent.kind || nip19.kind == ChannelMetadataEvent.kind) {
                        "Channel/${nip19.hex}"
                    } else {
                        "Event/${nip19.hex}"
                    }
                }

                Nip19.Type.ADDRESS ->
                    if (nip19.kind == CommunityDefinitionEvent.kind) {
                        "Community/${nip19.hex}"
                    } else if (nip19.kind == LiveActivitiesEvent.kind) {
                        "Channel/${nip19.hex}"
                    } else {
                        "Event/${nip19.hex}"
                    }
                else -> null
            }
        } ?: try {
            uri?.let {
                Nip47.parse(it)
                val encodedUri = URLEncoder.encode(it, StandardCharsets.UTF_8.toString())
                Route.Home.base + "?nip47=" + encodedUri
            }
        } catch (e: Exception) {
            null
        }
    }
}
