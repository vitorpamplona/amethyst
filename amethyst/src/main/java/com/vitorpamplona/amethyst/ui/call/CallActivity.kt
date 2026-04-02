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

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.vitorpamplona.amethyst.commons.call.CallState
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme

class CallActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val callManager = ActiveCallHolder.callManager
        val callController = ActiveCallHolder.callController
        val accountViewModel = ActiveCallHolder.accountViewModel

        if (callManager == null || accountViewModel == null) {
            finish()
            return
        }

        setContent {
            AmethystTheme {
                CallScreen(
                    callManager = callManager,
                    callController = callController,
                    accountViewModel = accountViewModel,
                    onCallEnded = { finish() },
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipIfActive()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        if (!isInPictureInPictureMode) {
            // User expanded from PiP — bring the full call UI back
        }
    }

    private fun enterPipIfActive() {
        val callManager = ActiveCallHolder.callManager ?: return
        val state = callManager.state.value
        val isActive =
            state is CallState.Connected ||
                state is CallState.Connecting ||
                state is CallState.Offering

        if (isActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params =
                    PictureInPictureParams
                        .Builder()
                        .setAspectRatio(Rational(9, 16))
                        .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {
                // PiP not supported or activity not in correct state
            }
        }
    }

    companion object {
        fun launch(context: Context) {
            context.startActivity(
                Intent(context, CallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }
}
