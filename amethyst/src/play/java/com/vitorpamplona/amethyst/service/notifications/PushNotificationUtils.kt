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
import com.google.firebase.messaging.FirebaseMessaging
import com.vitorpamplona.amethyst.AccountInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient

object PushNotificationUtils {
    var lastToken: String? = null
    var hasInit: List<AccountInfo>? = null

    suspend fun checkAndInit(
        accounts: List<AccountInfo>,
        okHttpClient: OkHttpClient,
    ) = with(Dispatchers.IO) {
        val token = FirebaseMessaging.getInstance().token.await()
        if (hasInit?.equals(accounts) == true && lastToken == token) {
            return@with
        }

        registerToken(token, accounts, okHttpClient)
    }

    suspend fun checkAndInit(
        token: String,
        accounts: List<AccountInfo>,
        okHttpClient: OkHttpClient,
    ) = with(Dispatchers.IO) {
        // initializes if the accounts are different or if the token has changed
        if (hasInit?.equals(accounts) == true && lastToken == token) {
            return@with
        }
        registerToken(token, accounts, okHttpClient)
    }

    private suspend fun registerToken(
        token: String,
        accounts: List<AccountInfo>,
        okHttpClient: OkHttpClient,
    ) = retryIfException("RegisterAccounts") {
        RegisterAccounts(accounts, okHttpClient).go(token)
        lastToken = token
        hasInit = accounts.toList()
    }
}

suspend fun <T> retryIfException(
    debugTag: String = "RetryIfException",
    maxRetries: Int = 10,
    delayMs: Long = 1000,
    func: suspend () -> T,
) {
    var tentative = 0
    var currentDelay = delayMs
    while (tentative < maxRetries) {
        try {
            func()

            // if it works, finishes.
            return
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(debugTag, "Tentative $tentative failed", e)

            delay(currentDelay)
            tentative++
            currentDelay = currentDelay * 2
        }
    }
    // gives up
}
