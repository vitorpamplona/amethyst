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

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.call.CallManager
import com.vitorpamplona.amethyst.commons.call.CallState
import com.vitorpamplona.amethyst.service.call.AudioRoute
import com.vitorpamplona.amethyst.service.call.CallController
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun CallScreen(
    callManager: CallManager,
    callController: CallController?,
    accountViewModel: AccountViewModel,
    onCallEnded: () -> Unit,
) {
    val callState by callManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val emptyStringFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }
    val errorMessage by (callController?.errorMessage ?: emptyStringFlow).collectAsState()

    BackHandler(enabled = callState !is CallState.Idle && callState !is CallState.Ended) {
        scope.launch { callManager.hangup() }
    }

    KeepScreenOn()
    EnterPipOnLeave(callState)

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = callState) {
            is CallState.Idle -> {
                // Wait briefly — initiateCall runs async and state may not have
                // transitioned yet when navigating to this screen
                LaunchedEffect(Unit) {
                    delay(500)
                    if (callManager.state.value is CallState.Idle) {
                        onCallEnded()
                    }
                }
            }

            is CallState.Offering -> {
                CallInProgressUI(
                    peerPubKey = state.peerPubKey,
                    statusText = stringRes(R.string.call_calling),
                    accountViewModel = accountViewModel,
                    onHangup = { scope.launch { callManager.hangup() } },
                )
            }

            is CallState.IncomingCall -> {
                val isVideoCall = state.callType == com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType.VIDEO
                val acceptWithPermission =
                    rememberCallWithPermission(context, isVideo = isVideoCall) {
                        callController?.acceptIncomingCall(state.sdpOffer)
                    }
                IncomingCallUI(
                    callerPubKey = state.callerPubKey,
                    callType = state.callType,
                    accountViewModel = accountViewModel,
                    onAccept = acceptWithPermission,
                    onReject = { scope.launch { callManager.rejectCall() } },
                )
            }

            is CallState.Connecting -> {
                CallInProgressUI(
                    peerPubKey = state.peerPubKey,
                    statusText = stringRes(R.string.call_connecting),
                    accountViewModel = accountViewModel,
                    onHangup = { scope.launch { callManager.hangup() } },
                )
            }

            is CallState.Connected -> {
                ConnectedCallUI(
                    state = state,
                    callController = callController,
                    accountViewModel = accountViewModel,
                    onHangup = { scope.launch { callManager.hangup() } },
                    onToggleMute = { callController?.toggleAudioMute() },
                    onToggleVideo = { callController?.toggleVideo() },
                    onCycleAudioRoute = { callController?.cycleAudioRoute() },
                )
            }

            is CallState.Ended -> {
                CallInProgressUI(
                    peerPubKey = state.peerPubKey,
                    statusText = stringRes(R.string.call_ended),
                    accountViewModel = accountViewModel,
                    onHangup = { onCallEnded() },
                )
            }
        }

        errorMessage?.let { error ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = {
                    Text(
                        stringRes(R.string.call_dismiss),
                        modifier =
                            Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.inversePrimary,
                    )
                },
            ) {
                Text(error)
            }
        }
    }
}

@Composable
private fun CallInProgressUI(
    peerPubKey: String,
    statusText: String,
    accountViewModel: AccountViewModel,
    onHangup: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LoadUser(baseUserHex = peerPubKey, accountViewModel = accountViewModel) { user ->
                if (user != null) {
                    ClickableUserPicture(
                        baseUser = user,
                        size = 120.dp,
                        accountViewModel = accountViewModel,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    UsernameDisplay(
                        baseUser = user,
                        accountViewModel = accountViewModel,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.height(48.dp))
            FloatingActionButton(
                onClick = onHangup,
                containerColor = Color.Red,
                shape = CircleShape,
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    Icons.Default.CallEnd,
                    contentDescription = stringRes(R.string.call_hangup),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

@Composable
private fun IncomingCallUI(
    callerPubKey: String,
    callType: com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType,
    accountViewModel: AccountViewModel,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LoadUser(baseUserHex = callerPubKey, accountViewModel = accountViewModel) { user ->
                if (user != null) {
                    ClickableUserPicture(
                        baseUser = user,
                        size = 120.dp,
                        accountViewModel = accountViewModel,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    UsernameDisplay(
                        baseUser = user,
                        accountViewModel = accountViewModel,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    stringRes(
                        if (callType == com.vitorpamplona.quartz.nipACWebRtcCalls.tags.CallType.VIDEO) {
                            R.string.call_incoming_video
                        } else {
                            R.string.call_incoming_voice
                        },
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.height(48.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(48.dp),
            ) {
                FloatingActionButton(
                    onClick = onReject,
                    containerColor = Color.Red,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        Icons.Default.CallEnd,
                        contentDescription = stringRes(R.string.call_reject),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                FloatingActionButton(
                    onClick = onAccept,
                    containerColor = Color(0xFF4CAF50),
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = stringRes(R.string.call_accept),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectedCallUI(
    state: CallState.Connected,
    callController: CallController?,
    accountViewModel: AccountViewModel,
    onHangup: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onCycleAudioRoute: () -> Unit,
) {
    var elapsed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state.startedAtEpoch) {
        while (true) {
            elapsed = TimeUtils.now() - state.startedAtEpoch
            delay(1000)
        }
    }

    val emptyVideoFlow = remember { kotlinx.coroutines.flow.MutableStateFlow<VideoTrack?>(null) }
    val remoteVideoTrack by (callController?.remoteVideoTrack ?: emptyVideoFlow).collectAsState()
    val localVideoTrack by (callController?.localVideoTrack ?: emptyVideoFlow).collectAsState()
    val defaultFalse = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
    val defaultTrue = remember { kotlinx.coroutines.flow.MutableStateFlow(true) }
    val defaultRoute = remember { kotlinx.coroutines.flow.MutableStateFlow(AudioRoute.EARPIECE) }
    val isAudioMuted by (callController?.isAudioMuted ?: defaultFalse).collectAsState()
    val isVideoEnabled by (callController?.isVideoEnabled ?: defaultTrue).collectAsState()
    val currentAudioRoute by (callController?.audioRoute ?: defaultRoute).collectAsState()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        // Remote video (full screen background)
        remoteVideoTrack?.let { track ->
            VideoRenderer(
                videoTrack = track,
                eglBase = callController?.getEglBase(),
                modifier = Modifier.fillMaxSize(),
                mirror = false,
            )
        }

        // Local video (small pip in corner)
        localVideoTrack?.let { track ->
            VideoRenderer(
                videoTrack = track,
                eglBase = callController?.getEglBase(),
                modifier =
                    Modifier
                        .size(120.dp, 160.dp)
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                mirror = true,
            )
        }

        // If no video, show avatar
        if (remoteVideoTrack == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LoadUser(baseUserHex = state.peerPubKey, accountViewModel = accountViewModel) { user ->
                    if (user != null) {
                        ClickableUserPicture(
                            baseUser = user,
                            size = 120.dp,
                            accountViewModel = accountViewModel,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        UsernameDisplay(
                            baseUser = user,
                            accountViewModel = accountViewModel,
                            fontWeight = FontWeight.Bold,
                            textColor = Color.White,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatDuration(elapsed),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                )
            }
        } else {
            // Timer overlay
            Text(
                text = formatDuration(elapsed),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp),
            )
        }

        // Controls at bottom
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = if (isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = stringRes(if (isAudioMuted) R.string.call_unmute else R.string.call_mute),
                        tint = if (isAudioMuted) Color.Red else Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
                IconButton(
                    onClick = onToggleVideo,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = stringRes(if (isVideoEnabled) R.string.call_camera_off else R.string.call_camera_on),
                        tint = if (!isVideoEnabled) Color.Red else Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
                IconButton(
                    onClick = onCycleAudioRoute,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        imageVector =
                            when (currentAudioRoute) {
                                AudioRoute.EARPIECE -> Icons.Default.VolumeOff
                                AudioRoute.SPEAKER -> Icons.Default.VolumeUp
                                AudioRoute.BLUETOOTH -> Icons.Default.BluetoothAudio
                            },
                        contentDescription =
                            stringRes(
                                when (currentAudioRoute) {
                                    AudioRoute.EARPIECE -> R.string.call_earpiece
                                    AudioRoute.SPEAKER -> R.string.call_speaker
                                    AudioRoute.BLUETOOTH -> R.string.call_bluetooth
                                },
                            ),
                        tint =
                            when (currentAudioRoute) {
                                AudioRoute.EARPIECE -> Color.White
                                AudioRoute.SPEAKER -> Color.Cyan
                                AudioRoute.BLUETOOTH -> Color(0xFF2196F3)
                            },
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            FloatingActionButton(
                onClick = onHangup,
                containerColor = Color.Red,
                shape = CircleShape,
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    Icons.Default.CallEnd,
                    contentDescription = stringRes(R.string.call_hangup),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

@Composable
private fun VideoRenderer(
    videoTrack: VideoTrack,
    eglBase: org.webrtc.EglBase?,
    modifier: Modifier = Modifier,
    mirror: Boolean = false,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                setMirror(mirror)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                eglBase?.eglBaseContext?.let { init(it, null) }
                videoTrack.addSink(this)
            }
        },
        onRelease = { renderer ->
            videoTrack.removeSink(renderer)
            renderer.release()
        },
    )
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
}

@Composable
private fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
private fun EnterPipOnLeave(callState: CallState) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity ?: return
    val isActiveCall =
        callState is CallState.Connected ||
            callState is CallState.Connecting ||
            callState is CallState.Offering

    if (isActiveCall && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer =
                object : androidx.lifecycle.DefaultLifecycleObserver {
                    override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                        try {
                            val params =
                                android.app.PictureInPictureParams
                                    .Builder()
                                    .setAspectRatio(android.util.Rational(9, 16))
                                    .build()
                            activity.enterPictureInPictureMode(params)
                        } catch (_: Exception) {
                            // PiP not supported or activity not in correct state
                        }
                    }
                }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }
}
