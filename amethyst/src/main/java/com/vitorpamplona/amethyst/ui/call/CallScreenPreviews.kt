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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn

@Composable
private fun PreviewCallInProgress(statusText: String) {
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
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Alice",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.height(48.dp))
            FloatingActionButton(
                onClick = {},
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

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewCallingScreen() {
    ThemeComparisonColumn {
        PreviewCallInProgress("Calling...")
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewConnectingScreen() {
    ThemeComparisonColumn {
        PreviewCallInProgress("Connecting...")
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewCallEndedScreen() {
    ThemeComparisonColumn {
        PreviewCallInProgress("Call ended")
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewIncomingCallScreen() {
    ThemeComparisonColumn {
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
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Bob",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Incoming voice call...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp,
                )
                Spacer(modifier = Modifier.height(48.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                ) {
                    FloatingActionButton(
                        onClick = {},
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
                        onClick = {},
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
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewConnectedCallScreen() {
    ThemeComparisonColumn {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = Color.White.copy(alpha = 0.5f),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Alice",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "02:45",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                )
            }

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
                        onClick = {},
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    IconButton(
                        onClick = {},
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = "Camera off",
                            tint = Color.Red,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    IconButton(
                        onClick = {},
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = "Speaker",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                FloatingActionButton(
                    onClick = {},
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
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun PreviewConnectedCallMuted() {
    ThemeComparisonColumn {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    tint = Color.White.copy(alpha = 0.5f),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Alice", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text("05:12", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
            }

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
                    IconButton(onClick = {}, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.MicOff, "Unmute", tint = Color.Red, modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = {}, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.Videocam, "Camera on", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = {}, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.VolumeUp, "Earpiece", tint = Color.Cyan, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                FloatingActionButton(
                    onClick = {},
                    containerColor = Color.Red,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(Icons.Default.CallEnd, "Hang up", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}
