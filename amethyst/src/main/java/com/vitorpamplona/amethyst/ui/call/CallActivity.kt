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

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.call.CallState
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class CallActivity : AppCompatActivity() {
    val isInPipMode = mutableStateOf(false)

    private val pipActionReceiver =
        object : BroadcastReceiver() {
            @OptIn(DelicateCoroutinesApi::class)
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    ACTION_PIP_HANGUP -> {
                        GlobalScope.launch { ActiveCallHolder.callManager?.hangup() }
                    }

                    ACTION_PIP_TOGGLE_MUTE -> {
                        ActiveCallHolder.callController?.toggleAudioMute()
                        updatePipParams()
                    }
                }
            }
        }

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

        registerPipReceiver()
        observeVideoStateForPip(callController)

        setContent {
            AmethystTheme {
                CallScreen(
                    callManager = callManager,
                    callController = callController,
                    accountViewModel = accountViewModel,
                    onCallEnded = { finish() },
                    isInPipMode = isInPipMode.value,
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipIfActive()
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        isInPipMode.value = isInPictureInPictureMode

        if (!isInPictureInPictureMode) {
            // User pressed the X button on PiP or expanded it.
            // If the activity is finishing (user dismissed PiP), hang up.
            if (isFinishing) {
                GlobalScope.launch { ActiveCallHolder.callManager?.hangup() }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onStop() {
        super.onStop()
        // When PiP is dismissed (X button), Android stops the activity.
        // If we're not in PiP anymore and the activity is stopping, hang up and finish.
        if (!isInPictureInPictureMode) {
            val state = ActiveCallHolder.callManager?.state?.value
            if (state is CallState.Connected || state is CallState.Connecting || state is CallState.Offering) {
                GlobalScope.launch { ActiveCallHolder.callManager?.hangup() }
            }
            finishAndRemoveTask()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onDestroy() {
        unregisterPipReceiver()

        // If the activity is being destroyed while still in an active call
        // (e.g. user swiped PiP away), hang up the call.
        val state = ActiveCallHolder.callManager?.state?.value
        if (state is CallState.Connected || state is CallState.Connecting || state is CallState.Offering) {
            GlobalScope.launch { ActiveCallHolder.callManager?.hangup() }
        }

        super.onDestroy()
    }

    private fun observeVideoStateForPip(controller: com.vitorpamplona.amethyst.service.call.CallController?) {
        controller ?: return
        lifecycleScope.launch {
            controller.isRemoteVideoActive.collect {
                updatePipParams()
            }
        }
    }

    private fun enterPipIfActive() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val callManager = ActiveCallHolder.callManager ?: return
        val state = callManager.state.value
        val isActive =
            state is CallState.Connected ||
                state is CallState.Connecting ||
                state is CallState.Offering

        if (isActive) {
            try {
                enterPictureInPictureMode(buildPipParams())
            } catch (_: Exception) {
                // PiP not supported or activity not in correct state
            }
        }
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!isInPipMode.value) return
        try {
            setPictureInPictureParams(buildPipParams())
        } catch (_: Exception) {
        }
    }

    private fun buildPipParams(): PictureInPictureParams {
        val aspectRatio = computePipAspectRatio()
        val builder =
            PictureInPictureParams
                .Builder()
                .setAspectRatio(aspectRatio)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setActions(buildPipActions())
        }

        return builder.build()
    }

    private fun computePipAspectRatio(): Rational {
        val controller = ActiveCallHolder.callController ?: return Rational(9, 16)
        val isVideoActive = controller.isRemoteVideoActive.value
        val videoRatio = controller.remoteVideoAspectRatio.value

        if (isVideoActive && videoRatio != null && videoRatio > 0f) {
            // Clamp to Android's allowed PiP range (roughly 1:2.39 to 2.39:1)
            val clamped = videoRatio.coerceIn(0.42f, 2.39f)
            val num = (clamped * 1000).toInt()
            return Rational(num, 1000)
        }

        // No active video — use portrait ratio
        return Rational(9, 16)
    }

    private fun buildPipActions(): List<RemoteAction> {
        val actions = mutableListOf<RemoteAction>()

        // Mute / Unmute toggle
        val isMuted = ActiveCallHolder.callController?.isAudioMuted?.value == true
        val muteIntent =
            PendingIntent.getBroadcast(
                this,
                PIP_MUTE_REQUEST_CODE,
                Intent(ACTION_PIP_TOGGLE_MUTE).setPackage(packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val muteAction =
            RemoteAction(
                Icon.createWithResource(
                    this,
                    if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic_on,
                ),
                getString(if (isMuted) R.string.call_unmute else R.string.call_mute),
                getString(if (isMuted) R.string.call_unmute else R.string.call_mute),
                muteIntent,
            )
        actions.add(muteAction)

        // Hangup
        val hangupIntent =
            PendingIntent.getBroadcast(
                this,
                PIP_HANGUP_REQUEST_CODE,
                Intent(ACTION_PIP_HANGUP).setPackage(packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val hangupAction =
            RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_call_end),
                getString(R.string.call_hangup),
                getString(R.string.call_hangup),
                hangupIntent,
            )
        actions.add(hangupAction)

        return actions
    }

    private fun registerPipReceiver() {
        val filter =
            IntentFilter().apply {
                addAction(ACTION_PIP_HANGUP)
                addAction(ACTION_PIP_TOGGLE_MUTE)
            }
        registerReceiver(pipActionReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    private fun unregisterPipReceiver() {
        try {
            unregisterReceiver(pipActionReceiver)
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val ACTION_PIP_HANGUP = "com.vitorpamplona.amethyst.PIP_HANGUP"
        private const val ACTION_PIP_TOGGLE_MUTE = "com.vitorpamplona.amethyst.PIP_TOGGLE_MUTE"
        private const val PIP_HANGUP_REQUEST_CODE = 0x60001
        private const val PIP_MUTE_REQUEST_CODE = 0x60002

        fun launch(context: Context) {
            context.startActivity(
                Intent(context, CallActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }
}
