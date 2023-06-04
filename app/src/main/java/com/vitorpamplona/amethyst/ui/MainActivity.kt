package com.vitorpamplona.amethyst.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.PrivateDmEvent
import com.vitorpamplona.amethyst.service.nip19.Nip19
import com.vitorpamplona.amethyst.service.notifications.PushNotificationUtils
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.ui.components.DefaultMutedSetting
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.note.Nip47
import com.vitorpamplona.amethyst.ui.screen.AccountScreen
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent?.data?.toString()

        val startingPage = uriToRoute(uri)

        LocalPreferences.migrateSingleUserPrefs()

        setContent {
            AmethystTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    val accountStateViewModel: AccountStateViewModel = viewModel {
                        AccountStateViewModel(this@MainActivity)
                    }

                    AccountScreen(accountStateViewModel, startingPage)
                }
            }
        }

        Client.lenient = true
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onResume() {
        super.onResume()
        // starts muted every time
        DefaultMutedSetting.value = true

        // Only starts after login
        GlobalScope.launch(Dispatchers.IO) {
            ServiceManager.start(this@MainActivity)
        }

        PushNotificationUtils().init(LocalPreferences.allSavedAccounts())
    }

    override fun onPause() {
        ServiceManager.pause()

        super.onPause()
    }

    /**
     * Release memory when the UI becomes hidden or when system resources become low.
     * @param level the memory-related event that was raised.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        println("Trim Memory $level")
        GlobalScope.launch(Dispatchers.Default) {
            ServiceManager.cleanUp()
        }
    }
}

class GetMediaActivityResultContract : ActivityResultContracts.GetContent() {

    override fun createIntent(context: Context, input: String): Intent {
        return super.createIntent(context, input).apply {
            // Force only images and videos to be selectable
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
                        "Room/${nip19.author}"
                    } else if (nip19.kind == ChannelMessageEvent.kind || nip19.kind == ChannelCreateEvent.kind || nip19.kind == ChannelMetadataEvent.kind) {
                        "Channel/${nip19.hex}"
                    } else {
                        "Event/${nip19.hex}"
                    }
                }

                Nip19.Type.ADDRESS -> "Note/${nip19.hex}"
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
