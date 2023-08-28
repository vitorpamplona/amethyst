package com.vitorpamplona.amethyst.service

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.service.notifications.PushNotificationUtils
import com.vitorpamplona.amethyst.service.notifications.RegisterAccounts
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.RelayAuthEvent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object IntentUtils {
    lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
    lateinit var authActivityResultLauncher: ActivityResultLauncher<Intent>
    lateinit var decryptActivityResultLauncher: ActivityResultLauncher<Intent>

    @OptIn(DelicateCoroutinesApi::class)
    fun start(activity: MainActivity) {
        activityResultLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (it.resultCode != Activity.RESULT_OK) {
                GlobalScope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        Amethyst.instance,
                        "Sign request rejected",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@registerForActivityResult
            }

            val event = it.data?.getStringExtra("event") ?: ""

            val signedEvent = Event.fromJson(event)
            val authEvent = RelayAuthEvent(signedEvent.id, signedEvent.pubKey, signedEvent.createdAt, signedEvent.tags, signedEvent.content, signedEvent.sig)

            RegisterAccounts(LocalPreferences.allSavedAccounts()).postRegistrationEvent(
                listOf(authEvent)
            )
            PushNotificationUtils.hasInit = true
            ServiceManager.shouldPauseService = true
        }

        authActivityResultLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (it.resultCode != Activity.RESULT_OK) {
                GlobalScope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        Amethyst.instance,
                        "Sign request rejected",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@registerForActivityResult
            }

            val event = it.data?.getStringExtra("event") ?: ""

            val signedEvent = Event.fromJson(event)
            val authEvent = RelayAuthEvent(signedEvent.id, signedEvent.pubKey, signedEvent.createdAt, signedEvent.tags, signedEvent.content, signedEvent.sig)

            GlobalScope.launch(Dispatchers.IO) {
                Client.send(authEvent, authEvent.relay())
            }
            ServiceManager.shouldPauseService = true
        }

        decryptActivityResultLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (it.resultCode != Activity.RESULT_OK) {
                GlobalScope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        Amethyst.instance,
                        "Sign request rejected",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                AmberUtils.isActivityRunning = false
                return@registerForActivityResult
            }

            val event = it.data?.getStringExtra("signature") ?: ""
            AmberUtils.content = event
            AmberUtils.isActivityRunning = false
        }
    }
}
