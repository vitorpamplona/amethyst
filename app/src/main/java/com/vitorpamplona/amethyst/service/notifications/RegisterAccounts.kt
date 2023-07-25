package com.vitorpamplona.amethyst.service.notifications

import android.util.Log
import com.vitorpamplona.amethyst.AccountInfo
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.service.HttpClient
import com.vitorpamplona.amethyst.service.model.RelayAuthEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RegisterAccounts(
    private val accounts: List<AccountInfo>
) {

    // creates proof that it controls all accounts
    private fun signEventsToProveControlOfAccounts(
        accounts: List<AccountInfo>,
        notificationToken: String
    ): List<RelayAuthEvent> {
        return accounts.mapNotNull {
            val acc = LocalPreferences.loadFromEncryptedStorage(it.npub)
            if (acc != null) {
                val relayToUse = acc.activeRelays()?.firstOrNull { it.read }
                if (relayToUse != null) {
                    acc.createAuthEvent(relayToUse, notificationToken)
                } else {
                    null
                }
            } else {
                null
            }
        }
    }

    private fun postRegistrationEvent(events: List<RelayAuthEvent>) {
        try {
            val jsonObject = """{
                "events": [ ${events.joinToString(", ") { it.toJson() }} ]
            }
            """

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = jsonObject.toRequestBody(mediaType)

            val request = Request.Builder()
                .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
                .url("https://push.amethyst.social/register")
                .post(body)
                .build()

            val client = HttpClient.getHttpClient()

            val isSucess = client.newCall(request).execute().use {
                it.isSuccessful
            }
        } catch (e: java.lang.Exception) {
            Log.e("FirebaseMsgService", "Unable to register with push server", e)
        }
    }

    fun go(notificationToken: String) {
        val scope = CoroutineScope(Job() + Dispatchers.IO)
        scope.launch {
            postRegistrationEvent(
                signEventsToProveControlOfAccounts(accounts, notificationToken)
            )
        }
    }
}
