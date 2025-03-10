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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.navigation.buildNewPostRoute
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.metadata.ChannelMetadataDialog
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Font12SP
import com.vitorpamplona.amethyst.ui.theme.Size55Modifier

@Composable
fun ChannelFabColumn(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var isOpen by remember { mutableStateOf(false) }

    var wantsToCreateChannel by remember { mutableStateOf(false) }

    if (wantsToCreateChannel) {
        ChannelMetadataDialog({ wantsToCreateChannel = false }, accountViewModel = accountViewModel)
    }

    Column {
        AnimatedVisibility(
            visible = isOpen,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        ) {
            Column {
                FloatingActionButton(
                    onClick = {
                        val route =
                            buildNewPostRoute(
                                enableMessageInterface = true,
                            )
                        nav.nav(route)
                        isOpen = false
                    },
                    modifier = Size55Modifier,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = stringRes(R.string.messages_new_message),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontSize = Font12SP,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                FloatingActionButton(
                    onClick = {
                        wantsToCreateChannel = true
                        isOpen = false
                    },
                    modifier = Size55Modifier,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = stringRes(R.string.messages_create_public_chat),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontSize = Font12SP,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        val rotationDegree by animateFloatAsState(
            targetValue = if (isOpen) 45f else 0f,
        )

        FloatingActionButton(
            onClick = { isOpen = !isOpen },
            modifier = Size55Modifier,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = stringRes(R.string.messages_create_public_private_chat_description),
                modifier =
                    Modifier.size(26.dp).graphicsLayer {
                        rotationZ = rotationDegree
                    },
                tint = Color.White,
            )
        }
    }
}
