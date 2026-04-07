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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.call.CallState
import com.vitorpamplona.amethyst.service.call.CallSessionBridge
import com.vitorpamplona.amethyst.service.relayClient.authCommand.compose.RelayAuthSubscription
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.account.AccountFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.StringResSetup
import com.vitorpamplona.amethyst.ui.screen.ManageRelayServices
import com.vitorpamplona.amethyst.ui.screen.ManageWebOkHttp
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class CallActivity : AppCompatActivity() {
    val isInPipMode = mutableStateOf(false)

    // Tracks whether we entered PiP at least once, so we can distinguish
    // "user swiped PiP away" from "user pressed Home from full-screen call".
    private var wasInPipMode = false

    // Launcher for requesting call permissions when accepting from notification
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (hasCallPermissions(this, isVideo = pendingAcceptIsVideo)) {
                acceptCall()
            }
        }

    private var pendingAcceptIsVideo = false

    private val pipActionReceiver =
        object : BroadcastReceiver() {
            @OptIn(DelicateCoroutinesApi::class)
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    ACTION_PIP_HANGUP -> {
                        GlobalScope.launch { CallSessionBridge.callManager?.hangup() }
                    }

                    ACTION_PIP_TOGGLE_MUTE -> {
                        CallSessionBridge.callController?.toggleAudioMute()
                        updatePipParams()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Show over lock screen and turn screen on for incoming calls
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }

        val callManager = CallSessionBridge.callManager
        val callController = CallSessionBridge.callController
        val accountViewModel = CallSessionBridge.accountViewModel

        if (callManager == null || accountViewModel == null) {
            finish()
            return
        }

        registerPipReceiver()
        observeVideoStateForPip(callController)
        handleAcceptCallIntent(intent)

        setContent {
            AmethystTheme {
                StringResSetup()

                // Pauses relay services when the app pauses
                ManageRelayServices()
                ManageWebOkHttp()

                // Adds this account to the authentication procedures for relays.
                RelayAuthSubscription(accountViewModel)

                // Loads account information + DMs and Notifications from Relays.
                AccountFilterAssemblerSubscription(accountViewModel)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAcceptCallIntent(intent)
    }

    private fun handleAcceptCallIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ACCEPT_CALL, false) != true) return
        // Clear the extra so it doesn't re-trigger on config changes
        intent.removeExtra(EXTRA_ACCEPT_CALL)

        com.vitorpamplona.amethyst.service.notifications.NotificationUtils
            .cancelCallNotification(this)

        val callManager = CallSessionBridge.callManager ?: return
        val state = callManager.state.value
        if (state !is CallState.IncomingCall) return

        val isVideo = state.callType == com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType.VIDEO
        pendingAcceptIsVideo = isVideo

        if (hasCallPermissions(this, isVideo)) {
            acceptCall()
        } else {
            permissionLauncher.launch(buildCallPermissions(isVideo))
        }
    }

    private fun acceptCall() {
        val callController = CallSessionBridge.callController ?: return
        val callManager = CallSessionBridge.callManager ?: return
        val state = callManager.state.value
        if (state is CallState.IncomingCall) {
            callController.acceptIncomingCall(state.sdpOffer)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipIfActive()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        isInPipMode.value = isInPictureInPictureMode

        if (isInPictureInPictureMode) {
            wasInPipMode = true
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onStop() {
        super.onStop()
        // Only hang up if the user dismissed PiP (swiped it away).
        // When PiP is dismissed, Android calls onStop with isInPictureInPictureMode == false
        // after the activity was previously in PiP mode.
        // We must NOT hang up when the user simply presses Home from the full-screen
        // call UI (that enters PiP via onUserLeaveHint instead).
        if (wasInPipMode && !isInPictureInPictureMode) {
            val state = CallSessionBridge.callManager?.state?.value
            if (state is CallState.Connected || state is CallState.Connecting || state is CallState.Offering) {
                GlobalScope.launch { CallSessionBridge.callManager?.hangup() }
            }
            finishAndRemoveTask()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onDestroy() {
        unregisterPipReceiver()

        // Safety net: if the Activity is destroyed while a call is still
        // ringing/offering, ensure the call is hung up so audio stops.
        val manager = CallSessionBridge.callManager
        when (manager?.state?.value) {
            is CallState.IncomingCall -> {
                GlobalScope.launch { manager.rejectCall() }
            }

            is CallState.Offering,
            is CallState.Connecting,
            is CallState.Connected,
            -> {
                GlobalScope.launch { manager.hangup() }
            }

            else -> {}
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
        val callManager = CallSessionBridge.callManager ?: return
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
        val controller = CallSessionBridge.callController ?: return Rational(9, 16)
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
        val isMuted = CallSessionBridge.callController?.isAudioMuted?.value == true
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
        const val EXTRA_ACCEPT_CALL = "com.vitorpamplona.amethyst.EXTRA_ACCEPT_CALL"
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
