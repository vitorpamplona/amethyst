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
package com.vitorpamplona.amethyst.service.notifications

import android.util.Log
import com.vitorpamplona.amethyst.AccountInfo
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.ammolite.service.HttpClientManager
import com.vitorpamplona.quartz.events.RelayAuthEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RegisterAccounts(
    private val accounts: List<AccountInfo>,
) {
    private fun recursiveAuthCreation(
        notificationToken: String,
        remainingTos: List<Pair<Account, String>>,
        output: MutableList<RelayAuthEvent>,
        onReady: (List<RelayAuthEvent>) -> Unit,
    ) {
        if (remainingTos.isEmpty()) {
            onReady(output)
            return
        }

        val next = remainingTos.first()

        next.first.createAuthEvent(next.second, notificationToken) {
            output.add(it)
            recursiveAuthCreation(notificationToken, remainingTos.filter { next != it }, output, onReady)
        }
    }

    // creates proof that it controls all accounts
    private suspend fun signEventsToProveControlOfAccounts(
        accounts: List<AccountInfo>,
        notificationToken: String,
        onReady: (List<RelayAuthEvent>) -> Unit,
    ) {
        val readyToSend =
            accounts.mapNotNull {
                val acc = LocalPreferences.loadCurrentAccountFromEncryptedStorage(it.npub)
                if (acc != null && acc.isWriteable()) {
                    val readRelays =
                        acc.userProfile().latestContactList?.relays() ?: acc.backupContactList?.relays()

                    val relayToUse = readRelays?.firstNotNullOfOrNull { if (it.value.read) it.key else null }
                    if (relayToUse != null) {
                        Pair(acc, relayToUse)
                    } else {
                        null
                    }
                } else {
                    null
                }
            }

        val listOfAuthEvents = mutableListOf<RelayAuthEvent>()
        recursiveAuthCreation(
            notificationToken,
            readyToSend,
            listOfAuthEvents,
            onReady,
        )
    }

    fun postRegistrationEvent(events: List<RelayAuthEvent>) {
        try {
            val jsonObject =
                """{
                "events": [ ${events.joinToString(", ") { it.toJson() }} ]
            }
            """

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = jsonObject.toRequestBody(mediaType)

            val request =
                Request.Builder()
                    .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
                    .url("https://push.amethyst.social/register")
                    .post(body)
                    .build()

            val client = HttpClientManager.getHttpClient()

            val isSucess = client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: java.lang.Exception) {
            if (e is CancellationException) throw e
            val tag =
                if (BuildConfig.FLAVOR == "play") {
                    "FirebaseMsgService"
                } else {
                    "UnifiedPushService"
                }
            Log.e(tag, "Unable to register with push server", e)
        }
    }

    suspend fun go(notificationToken: String) =
        withContext(Dispatchers.IO) {
            signEventsToProveControlOfAccounts(accounts, notificationToken) { postRegistrationEvent(it) }

            PushNotificationUtils.hasInit = true
        }
}
