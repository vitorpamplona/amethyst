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
package com.vitorpamplona.amethyst.commons.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.accessibility_user_avatar
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.commons.ui.theme.ChatSize34dp
import com.vitorpamplona.amethyst.commons.ui.theme.ChatStdPadding
import org.jetbrains.compose.resources.stringResource

/**
 * Shared chatroom header for a single-user conversation.
 * Displays the user's avatar and display name.
 *
 * Uses the shared UserAvatar component from commons for image loading,
 * avoiding Android-specific dependencies.
 *
 * @param user The chat partner
 * @param modifier Layout modifier (defaults to standard padding)
 * @param onClick Called when the header is tapped (e.g., navigate to profile)
 */
@Composable
fun ChatroomHeader(
    user: User,
    modifier: Modifier = ChatStdPadding,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier, Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(
                    userHex = user.pubkeyHex,
                    pictureUrl = user.profilePicture(),
                    size = ChatSize34dp,
                    contentDescription = stringResource(Res.string.accessibility_user_avatar),
                )

                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        text = user.toBestDisplayName(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Shared chatroom header for a group conversation.
 * Displays multiple user avatars and a combined room name.
 *
 * @param users List of users in the group conversation
 * @param modifier Layout modifier (defaults to standard padding)
 * @param onClick Called when the header is tapped
 */
@Composable
fun GroupChatroomHeader(
    users: List<User>,
    modifier: Modifier = ChatStdPadding,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            modifier = modifier,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Show first user's avatar as the group icon
                users.firstOrNull()?.let { firstUser ->
                    UserAvatar(
                        userHex = firstUser.pubkeyHex,
                        pictureUrl = firstUser.profilePicture(),
                        size = ChatSize34dp,
                        contentDescription = stringResource(Res.string.accessibility_user_avatar),
                    )
                }

                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        text = users.joinToString(", ") { it.toBestDisplayName() },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
