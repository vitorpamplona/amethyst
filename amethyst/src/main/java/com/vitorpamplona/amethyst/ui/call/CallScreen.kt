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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.call.CallManager
import com.vitorpamplona.amethyst.commons.call.CallState
import com.vitorpamplona.amethyst.service.call.CallController
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms.LoadUser
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CallScreen(
    callManager: CallManager,
    callController: CallController?,
    accountViewModel: AccountViewModel,
    onCallEnded: () -> Unit,
) {
    val callState by callManager.state.collectAsState()
    val scope = rememberCoroutineScope()

    when (val state = callState) {
        is CallState.Idle -> {
            LaunchedEffect(Unit) { onCallEnded() }
        }

        is CallState.Offering -> {
            CallInProgressUI(
                peerPubKey = state.peerPubKey,
                statusText = "Calling...",
                accountViewModel = accountViewModel,
                onHangup = { scope.launch { callManager.hangup() } },
            )
        }

        is CallState.IncomingCall -> {
            IncomingCallUI(
                callerPubKey = state.callerPubKey,
                callType = state.callType,
                accountViewModel = accountViewModel,
                onAccept = { callController?.acceptIncomingCall(state.sdpOffer) },
                onReject = { scope.launch { callManager.rejectCall() } },
            )
        }

        is CallState.Connecting -> {
            CallInProgressUI(
                peerPubKey = state.peerPubKey,
                statusText = "Connecting...",
                accountViewModel = accountViewModel,
                onHangup = { scope.launch { callManager.hangup() } },
            )
        }

        is CallState.Connected -> {
            ConnectedCallUI(
                state = state,
                accountViewModel = accountViewModel,
                onHangup = { scope.launch { callManager.hangup() } },
                onToggleMute = { callManager.toggleAudioMute() },
                onToggleVideo = { callManager.toggleVideo() },
                onToggleSpeaker = { callManager.toggleSpeaker() },
            )
        }

        is CallState.Ended -> {
            LaunchedEffect(Unit) {
                delay(2000)
                callManager.reset()
                onCallEnded()
            }
            CallInProgressUI(
                peerPubKey = state.peerPubKey,
                statusText = "Call ended",
                accountViewModel = accountViewModel,
                onHangup = { onCallEnded() },
            )
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
                    contentDescription = "Hang up",
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
                text = "Incoming ${callType.value} call...",
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
                        contentDescription = "Reject",
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
                        contentDescription = "Accept",
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
    accountViewModel: AccountViewModel,
    onHangup: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onToggleSpeaker: () -> Unit,
) {
    var elapsed by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state.startedAtEpoch) {
        while (true) {
            elapsed = TimeUtils.now() - state.startedAtEpoch
            delay(1000)
        }
    }

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
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatDuration(elapsed),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.height(48.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = onToggleMute) {
                    Text(if (state.isAudioMuted) "Unmute" else "Mute")
                }
                IconButton(onClick = onToggleVideo) {
                    Text(if (state.isVideoEnabled) "Cam Off" else "Cam On")
                }
                IconButton(onClick = onToggleSpeaker) {
                    Text(if (state.isSpeakerOn) "Earpiece" else "Speaker")
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
                    contentDescription = "Hang up",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
}
