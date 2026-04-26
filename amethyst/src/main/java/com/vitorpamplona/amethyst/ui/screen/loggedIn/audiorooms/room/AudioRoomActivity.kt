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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.audiorooms.room

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
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Standalone activity that owns the lifetime of an audio-room session. The
 * lobby ([AudioRoomJoinCard]) launches this activity when the user taps
 * "Join audio room"; finishing it tears down the MoQ session, the
 * broadcaster (if any), and the foreground notification.
 *
 * Picture-in-Picture: pressing Home or the Recents gesture while connected
 * collapses the activity into a small floating window so the user can
 * keep the speakers visible while doing other things. Mute / leave are
 * exposed via the system PIP overlay as [RemoteAction]s.
 *
 * AccountViewModel arrives via [AudioRoomBridge] — same pattern as
 * [com.vitorpamplona.amethyst.ui.call.CallActivity] uses for
 * `CallSessionBridge`.
 */
class AudioRoomActivity : AppCompatActivity() {
    private val isInPipMode = mutableStateOf(false)
    private val isMuted = mutableStateOf(false)
    private val isConnected = mutableStateOf(false)

    private val pipReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    ACTION_PIP_TOGGLE_MUTE -> {
                        // Per-Activity SharedFlow — observed by the composable
                        // and forwarded to the VM. Replaces an earlier
                        // process-wide singleton that could leak edges across
                        // activity instances.
                        toggleMuteSignal.tryEmit(Unit)
                    }

                    ACTION_PIP_LEAVE -> {
                        finish()
                    }
                }
            }
        }

    private val _toggleMuteSignal =
        MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emitted when the PIP-overlay mute action is tapped. */
    val toggleMuteSignal: MutableSharedFlow<Unit> get() = _toggleMuteSignal

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val accountViewModel = AudioRoomBridge.accountViewModel
        val addressValue = intent.getStringExtra(EXTRA_ADDRESS)
        val serviceBase = intent.getStringExtra(EXTRA_SERVICE_BASE)
        val roomId = intent.getStringExtra(EXTRA_ROOM_ID)
        if (accountViewModel == null || addressValue == null || serviceBase == null || roomId == null) {
            finish()
            return
        }

        val filter =
            IntentFilter().apply {
                addAction(ACTION_PIP_TOGGLE_MUTE)
                addAction(ACTION_PIP_LEAVE)
            }
        // RECEIVER_NOT_EXPORTED requires API 33+. The PendingIntents that
        // fire these actions all use setPackage(packageName) so the
        // visibility risk on older devices is bounded; pre-33 we register
        // without the flag.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(pipReceiver, filter)
        }

        setContent {
            AmethystTheme {
                AudioRoomActivityContent(
                    addressValue = addressValue,
                    serviceBase = serviceBase,
                    roomId = roomId,
                    accountViewModel = accountViewModel,
                    isInPipMode = isInPipMode.value,
                    onMuteState = { muted ->
                        if (isMuted.value != muted) {
                            isMuted.value = muted
                            updatePipParams()
                        }
                    },
                    onConnectedChange = { isConnected.value = it },
                    pipMuteSignal = toggleMuteSignal.asSharedFlow(),
                    onLeave = { finish() },
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // PIP only makes sense once the audio session is live; entering
        // PIP from the lobby/Connecting state would freeze a half-rendered
        // card in Recents. Also gates on the system supporting PIP.
        if (!isConnected.value) return
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        runCatching { enterPictureInPictureMode(buildPipParams()) }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        isInPipMode.value = isInPictureInPictureMode
    }

    private fun updatePipParams() {
        if (!isInPipMode.value) return
        runCatching { setPictureInPictureParams(buildPipParams()) }
    }

    private fun buildPipParams(): PictureInPictureParams =
        PictureInPictureParams
            .Builder()
            .setAspectRatio(Rational(9, 16))
            .setActions(buildPipActions())
            .build()

    private fun buildPipActions(): List<RemoteAction> {
        val muteIntent =
            PendingIntent.getBroadcast(
                this,
                PIP_MUTE_REQ,
                Intent(ACTION_PIP_TOGGLE_MUTE).setPackage(packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val leaveIntent =
            PendingIntent.getBroadcast(
                this,
                PIP_LEAVE_REQ,
                Intent(ACTION_PIP_LEAVE).setPackage(packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val muteIconRes = if (isMuted.value) R.drawable.ic_mic_off else R.drawable.ic_mic_on
        val muteLabel = getString(if (isMuted.value) R.string.audio_room_unmute else R.string.audio_room_mute)
        return listOf(
            RemoteAction(Icon.createWithResource(this, muteIconRes), muteLabel, muteLabel, muteIntent),
            RemoteAction(
                Icon.createWithResource(this, R.drawable.ic_call_end),
                getString(R.string.audio_room_leave),
                getString(R.string.audio_room_leave),
                leaveIntent,
            ),
        )
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipReceiver) }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ADDRESS = "com.vitorpamplona.amethyst.AUDIO_ROOM_ADDRESS"
        const val EXTRA_SERVICE_BASE = "com.vitorpamplona.amethyst.AUDIO_ROOM_SERVICE_BASE"
        const val EXTRA_ROOM_ID = "com.vitorpamplona.amethyst.AUDIO_ROOM_ROOM_ID"
        private const val ACTION_PIP_TOGGLE_MUTE = "com.vitorpamplona.amethyst.AUDIO_ROOM_PIP_MUTE"
        private const val ACTION_PIP_LEAVE = "com.vitorpamplona.amethyst.AUDIO_ROOM_PIP_LEAVE"
        private const val PIP_MUTE_REQ = 0x6A001
        private const val PIP_LEAVE_REQ = 0x6A002

        fun launch(
            context: Context,
            addressValue: String,
            serviceBase: String,
            roomId: String,
        ) {
            context.startActivity(
                Intent(context, AudioRoomActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(EXTRA_ADDRESS, addressValue)
                    putExtra(EXTRA_SERVICE_BASE, serviceBase)
                    putExtra(EXTRA_ROOM_ID, roomId)
                },
            )
        }
    }
}
