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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.LikeReaction
import com.vitorpamplona.amethyst.ui.note.NoteQuickActionMenu
import com.vitorpamplona.amethyst.ui.note.ZapReaction
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@Composable
fun ChatQuickActionBar(
    note: Note,
    onDismiss: () -> Unit,
    onWantsToReply: (Note) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val showFullMenu = remember { mutableStateOf(false) }

    if (showFullMenu.value) {
        NoteQuickActionMenu(
            note = note,
            onDismiss = {
                showFullMenu.value = false
                onDismiss()
            },
            onWantsToEditDraft = {},
            accountViewModel = accountViewModel,
            nav = nav,
        )
        return
    }

    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Reply button
                Column(
                    modifier = Modifier
                        .clickable {
                            onWantsToReply(note)
                            onDismiss()
                        }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = stringRes(R.string.reply),
                        modifier = Modifier.size(Size20dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Like/React button
                LikeReaction(
                    baseNote = note,
                    grayTint = MaterialTheme.colorScheme.placeholderText,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    iconSize = Size20dp,
                )

                // Zap button
                ZapReaction(
                    baseNote = note,
                    grayTint = MaterialTheme.colorScheme.placeholderText,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    iconSize = Size20dp,
                )

                // Copy button
                val clipboardManager = LocalClipboardManager.current
                val context = LocalContext.current
                Column(
                    modifier = Modifier
                        .clickable {
                            val content = note.event?.content() ?: ""
                            clipboardManager.setText(AnnotatedString(content))
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.copied_note_text_to_clipboard),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            onDismiss()
                        }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringRes(R.string.copy_text),
                        modifier = Modifier.size(Size20dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // More options
                Column(
                    modifier = Modifier
                        .clickable {
                            showFullMenu.value = true
                        }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = stringRes(R.string.more_options),
                        modifier = Modifier.size(Size20dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
