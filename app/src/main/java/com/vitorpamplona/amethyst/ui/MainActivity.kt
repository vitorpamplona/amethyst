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
import com.vitorpamplona.amethyst.service.ExternalSignerUtils
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

class MainActivity : AppCompatActivity() {
    private val isOnMobileDataState = mutableStateOf(false)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        ExternalSignerUtils.start(this)

        super.onCreate(savedInstanceState)

        setContent {
            val sharedPreferencesViewModel: SharedPreferencesViewModel = viewModel()

            val displayFeatures = calculateDisplayFeatures(this)
            val windowSizeClass = calculateWindowSizeClass(this)

            LaunchedEffect(key1 = sharedPreferencesViewModel) {
                sharedPreferencesViewModel.init()
            }

            LaunchedEffect(isOnMobileDataState) {
                sharedPreferencesViewModel.updateConnectivityStatusState(isOnMobileDataState)
                sharedPreferencesViewModel.updateDisplaySettings(windowSizeClass, displayFeatures)
            }

            AmethystTheme(sharedPreferencesViewModel) {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val accountStateViewModel: AccountStateViewModel = viewModel()

                    LaunchedEffect(key1 = Unit) {
                        accountStateViewModel.tryLoginExistingAccountAsync()
                    }

                    AccountScreen(accountStateViewModel, sharedPreferencesViewModel)
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onResume() {
        super.onResume()

        // starts muted every time
        DefaultMutedSetting.value = true

        // Only starts after login
        if (ServiceManager.shouldPauseService) {
            GlobalScope.launch(Dispatchers.IO) {
                ServiceManager.start()
            }
        }

        GlobalScope.launch(Dispatchers.IO) {
            PushNotificationUtils.init(LocalPreferences.allSavedAccounts())
        }

        (getSystemService(ConnectivityManager::class.java) as ConnectivityManager).registerDefaultNetworkCallback(networkCallback)
    }

    override fun onPause() {
        LanguageTranslatorService.clear()
        ServiceManager.cleanObservers()

        // if (BuildConfig.DEBUG) {
        GlobalScope.launch(Dispatchers.IO) {
            debugState(this@MainActivity)
        }
        // }

        if (ServiceManager.shouldPauseService) {
            GlobalScope.launch(Dispatchers.IO) {
                ServiceManager.pause()
            }
        }

        (getSystemService(ConnectivityManager::class.java) as ConnectivityManager)
            .unregisterNetworkCallback(networkCallback)

        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        keepPlayingMutex?.stop()
        keepPlayingMutex?.release()
        keepPlayingMutex = null
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
            ServiceManager.trimMemory()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        // Network capabilities have changed for the network
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            super.onCapabilitiesChanged(network, networkCapabilities)

            GlobalScope.launch(Dispatchers.IO) {
                val isOnMobileData = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                val isOnWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                Log.d("ServiceManager NetworkCallback", "onCapabilitiesChanged: ${network.networkHandle} hasMobileData $isOnMobileData hasWifi $isOnWifi")

                if (isOnMobileDataState.value != isOnMobileData) {
                    isOnMobileDataState.value = isOnMobileData

                    ServiceManager.forceRestartIfItShould()
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
