package com.vitorpamplona.amethyst.service.notifications

import android.util.Log
import com.vitorpamplona.amethyst.AccountInfo
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.service.HttpClient
import com.vitorpamplona.quartz.events.RelayAuthEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RegisterAccounts(
    private val accounts: List<AccountInfo>
) {

    // creates proof that it controls all accounts
    private suspend fun signEventsToProveControlOfAccounts(
        accounts: List<AccountInfo>,
        notificationToken: String
    ): List<RelayAuthEvent> {
        return accounts.mapNotNull {
            val acc = LocalPreferences.loadCurrentAccountFromEncryptedStorage(it.npub)
            if (acc != null && (acc.isWriteable() || acc.loginWithExternalSigner)) {
                val readRelays = acc.userProfile().latestContactList?.relays() ?: acc.backupContactList?.relays()

                val relayToUse = readRelays?.firstNotNullOfOrNull { if (it.value.read) it.key else null }
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

    fun postRegistrationEvent(events: List<RelayAuthEvent>) {
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
            val tag = if (BuildConfig.FLAVOR == "play") {
                "FirebaseMsgService"
            } else {
                "UnifiedPushService"
            }
            Log.e(tag, "Unable to register with push server", e)
        }
    }

    suspend fun go(notificationToken: String) = withContext(Dispatchers.IO) {
        postRegistrationEvent(
            signEventsToProveControlOfAccounts(accounts, notificationToken)
        )
        PushNotificationUtils.hasInit = true
    }
}
