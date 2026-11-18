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
package com.vitorpamplona.amethyst.commons.ui.components

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger

@Composable
actual fun rememberTextSharer(): TextSharer {
    val context = LocalContext.current
    return remember(context) {
        TextSharer { text, title, chooserTitle, onShared ->
            val sendIntent =
                Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    if (title != null) putExtra(Intent.EXTRA_TITLE, title)
                }
            val chosen = ShareChosenCallbacks.register(context.applicationContext, onShared)
            context.startActivity(Intent.createChooser(sendIntent, chooserTitle, chosen))
        }
    }
}

/**
 * Tells a share's caller that the user picked a target in the chooser: the chooser sends the
 * IntentSender it was given then, and never when it is dismissed. Each share gets its own
 * broadcast, received on the application context because the composable that started the share
 * (a menu) usually leaves composition before the user picks. A dismissed chooser never answers,
 * so only the latest few receivers are kept; older ones are unregistered.
 */
private object ShareChosenCallbacks {
    private const val MAX_PENDING = 4
    private val nextId = AtomicInteger()
    private val pending = ArrayDeque<Pair<Context, BroadcastReceiver>>()

    @Synchronized
    fun register(
        appContext: Context,
        onShared: () -> Unit,
    ): IntentSender {
        val id = nextId.incrementAndGet()
        val action = "${appContext.packageName}.SHARE_TARGET_CHOSEN.$id"
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    release(this)
                    onShared()
                }
            }
        ContextCompat.registerReceiver(appContext, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        pending.addLast(appContext to receiver)
        while (pending.size > MAX_PENDING) {
            val (oldContext, oldReceiver) = pending.removeFirst()
            unregister(oldContext, oldReceiver)
        }
        val callback = Intent(action).setPackage(appContext.packageName)
        return PendingIntent
            .getBroadcast(appContext, id, callback, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT)
            .intentSender
    }

    @Synchronized
    private fun release(receiver: BroadcastReceiver) {
        val entry = pending.firstOrNull { it.second === receiver } ?: return
        pending.remove(entry)
        unregister(entry.first, receiver)
    }

    private fun unregister(
        context: Context,
        receiver: BroadcastReceiver,
    ) {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // already unregistered
        }
    }
}
