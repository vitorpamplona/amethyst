package com.vitorpamplona.amethyst.service.notifications

import android.util.Log
import com.vitorpamplona.amethyst.AccountInfo
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.service.HttpClient
import com.vitorpamplona.amethyst.service.IntentUtils
import com.vitorpamplona.amethyst.service.PackageUtils
import com.vitorpamplona.amethyst.ui.actions.SignerType
import com.vitorpamplona.amethyst.ui.actions.openAmber
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
    private fun signEventsToProveControlOfAccounts(
        accounts: List<AccountInfo>,
        notificationToken: String,
        isAmberInstalled: Boolean
    ): List<RelayAuthEvent> {
        return accounts.mapNotNull {
            val acc = LocalPreferences.loadFromEncryptedStorage(it.npub)
            if (acc != null) {
                val relayToUse = acc.activeRelays()?.firstOrNull { it.read }
                if (relayToUse != null) {
                    val event = acc.createAuthEvent(relayToUse, notificationToken, isAmberInstalled)
                    event
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
            Log.e("FirebaseMsgService", "Unable to register with push server", e)
        }
    }

    suspend fun go(notificationToken: String) = withContext(Dispatchers.IO) {
        val isAmberInstalled = PackageUtils.isAmberInstalled(Amethyst.instance)
        val accountsWithoutPrivKey = accounts.filter { !it.hasPrivKey }
        val accountsWithPrivKey = accounts.filter { it.hasPrivKey }
        accountsWithoutPrivKey.forEach { account ->
            val events = signEventsToProveControlOfAccounts(listOf(account), notificationToken, isAmberInstalled)
            if (events.isNotEmpty()) {
                openAmber(
                    events.first().toJson(),
                    SignerType.SIGN_EVENT,
                    IntentUtils.activityResultLauncher,
                    ""
                )
            }
        }

        if (accountsWithPrivKey.isNotEmpty()) {
            postRegistrationEvent(
                signEventsToProveControlOfAccounts(accountsWithPrivKey, notificationToken, isAmberInstalled)
            )
        }
    }
}
