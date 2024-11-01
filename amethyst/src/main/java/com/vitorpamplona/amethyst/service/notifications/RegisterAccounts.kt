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
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.BuildConfig
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.launchAndWaitAll
import com.vitorpamplona.amethyst.model.AccountSettings
import com.vitorpamplona.amethyst.tryAndWait
import com.vitorpamplona.ammolite.service.HttpClientManager
import com.vitorpamplona.quartz.events.RelayAuthEvent
import com.vitorpamplona.quartz.signers.NostrSignerExternal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume

class RegisterAccounts(
    private val accounts: List<AccountInfo>,
) {
    val tag =
        if (BuildConfig.FLAVOR == "play") {
            "RegisterAccounts FirebaseMsgService"
        } else {
            "RegisterAccounts UnifiedPushService"
        }

    private suspend fun signAllAuths(
        notificationToken: String,
        remainingTos: List<Pair<AccountSettings, List<String>>>,
        output: MutableList<RelayAuthEvent>,
        onReady: (List<RelayAuthEvent>) -> Unit,
    ) {
        if (remainingTos.isEmpty()) {
            onReady(output)
            return
        }

        launchAndWaitAll(remainingTos) { accountRelayPair ->
            val result =
                tryAndWait { continuation ->
                    val signer = accountRelayPair.first.createSigner()
                    // TODO: Modify the external launcher to launch as different users.
                    // Right now it only registers if Amber has already approved this signature
                    if (signer is NostrSignerExternal) {
                        signer.launcher.registerLauncher(
                            launcher = { },
                            contentResolver = Amethyst.instance::contentResolverFn,
                        )
                    }

                    RelayAuthEvent.create(accountRelayPair.second, notificationToken, signer) { result ->
                        continuation.resume(result)
                    }
                }

            if (result != null) {
                output.add(result)
            }
        }

        onReady(output)
    }

    // creates proof that it controls all accounts
    private suspend fun signEventsToProveControlOfAccounts(
        accounts: List<AccountInfo>,
        notificationToken: String,
        onReady: (List<RelayAuthEvent>) -> Unit,
    ) {
        val readyToSend =
            accounts
                .mapNotNull {
                    Log.d(tag, "Register Account ${it.npub}")

                    val acc = LocalPreferences.loadCurrentAccountFromEncryptedStorage(it.npub)
                    if (acc != null && acc.isWriteable()) {
                        val nip65Read = acc.backupNIP65RelayList?.readRelays() ?: emptyList()

                        Log.d(tag, "Register Account ${it.npub} NIP65 Reads ${nip65Read.joinToString(", ")}")

                        val nip17Read = acc.backupDMRelayList?.relays() ?: emptyList<String>()

                        Log.d(tag, "Register Account ${it.npub} NIP17 Reads ${nip17Read.joinToString(", ")}")

                        val readKind3Relays = acc.backupContactList?.relays()?.mapNotNull { if (it.value.read) it.key else null } ?: emptyList<String>()

                        Log.d(tag, "Register Account ${it.npub} Kind3 Reads ${readKind3Relays.joinToString(", ")}")

                        val relays = (nip65Read + nip17Read + readKind3Relays)

                        if (relays.isNotEmpty()) {
                            Pair(acc, relays)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }

        val listOfAuthEvents = mutableListOf<RelayAuthEvent>()
        signAllAuths(
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
                Request
                    .Builder()
                    .header("User-Agent", "Amethyst/${BuildConfig.VERSION_NAME}")
                    .url("https://push.amethyst.social/register")
                    .post(body)
                    .build()

            // Always try via Tor for Amethyst.
            val client = HttpClientManager.getHttpClient(true)

            val isSucess = client.newCall(request).execute().use { it.isSuccessful }
            Log.i(tag, "Server registration $isSucess")
        } catch (e: java.lang.Exception) {
            if (e is CancellationException) throw e
            Log.e(tag, "Unable to register with push server", e)
        }
    }

    suspend fun go(notificationToken: String) {
        if (notificationToken.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                signEventsToProveControlOfAccounts(accounts, notificationToken) { postRegistrationEvent(it) }

                PushNotificationUtils.hasInit = true
            }
        }
    }
}
