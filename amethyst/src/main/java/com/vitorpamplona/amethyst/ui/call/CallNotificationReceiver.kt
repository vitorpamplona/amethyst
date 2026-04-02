/*
 * Copyright (c) 2025 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vitorpamplona.amethyst.service.notifications.NotificationUtils
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class CallNotificationReceiver : BroadcastReceiver() {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            ACTION_ACCEPT_CALL -> {
                val callController = ActiveCallHolder.callController
                val callManager = ActiveCallHolder.callManager
                val state = callManager?.state?.value
                if (state is com.vitorpamplona.amethyst.commons.call.CallState.IncomingCall) {
                    callController?.acceptIncomingCall(state.sdpOffer)
                }
                NotificationUtils.cancelCallNotification(context)
                CallActivity.launch(context)
            }

            ACTION_REJECT_CALL -> {
                val callManager = ActiveCallHolder.callManager
                GlobalScope.launch {
                    callManager?.rejectCall()
                }
                NotificationUtils.cancelCallNotification(context)
            }
        }
    }

    companion object {
        const val ACTION_ACCEPT_CALL = "com.vitorpamplona.amethyst.ACCEPT_CALL"
        const val ACTION_REJECT_CALL = "com.vitorpamplona.amethyst.REJECT_CALL"
    }
}
