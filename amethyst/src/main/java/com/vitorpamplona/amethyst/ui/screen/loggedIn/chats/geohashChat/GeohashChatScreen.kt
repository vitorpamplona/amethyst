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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.geohashChat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts.ChatBubbleLayout
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.layouts.ChatGroupPosition
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * A Bitchat-interoperable public geohash location chat: everyone physically (or
 * "teleported") in the same geohash cell shares an ephemeral, relay-broadcast
 * room. Messages are signed with a per-geohash throwaway identity, so posting
 * here does not reveal the account's npub.
 *
 * Renders through the shared [ChatBubbleLayout] so it matches the rest of
 * Amethyst's chat surfaces (grouped bubbles, own-vs-other alignment, footer),
 * while keeping its own ephemeral subscription instead of the LocalCache-backed
 * chat feed.
 */
@Composable
fun GeohashChatScreen(
    geohash: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: GeohashChatViewModel = viewModel(key = "GeohashChat/$geohash")
    viewModel.init(geohash, accountViewModel)

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val participants by viewModel.participants.collectAsStateWithLifecycle()
    val relays by viewModel.relays.collectAsStateWithLifecycle()
    val myPubKey by viewModel.myPubKey.collectAsStateWithLifecycle()

    var nickname by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("#$geohash", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "$participants here · ${relays.size} relays",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()

        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
        ) {
            itemsIndexed(messages.asReversed(), key = { _, it -> it.id }) { revIndex, message ->
                val index = messages.size - 1 - revIndex
                GeohashBubble(
                    message = message,
                    position = groupPositionFor(messages, index),
                    isMine = message.pubKey == myPubKey,
                )
            }
        }

        HorizontalDivider()
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            singleLine = true,
            label = { Text("Nickname (optional)") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message #$geohash") },
            )
            IconButton(
                enabled = draft.isNotBlank() && relays.isNotEmpty(),
                onClick = {
                    viewModel.sendMessage(draft, nickname)
                    draft = ""
                },
            ) {
                SymbolIcon(symbol = MaterialSymbols.AutoMirrored.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun GeohashBubble(
    message: GeohashChatEvent,
    position: ChatGroupPosition,
    isMine: Boolean,
) {
    val clipboard = LocalClipboardManager.current
    ChatBubbleLayout(
        isLoggedInUser = isMine,
        isDraft = false,
        innerQuote = false,
        drawAuthorInfo = position.isFirstOfGroup && !isMine,
        groupPosition = position,
        onClick = { false },
        onAuthorClick = {},
        actionMenu = { onDismiss ->
            DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
                DropdownMenuItem(
                    text = { Text("Copy") },
                    onClick = {
                        clipboard.setText(AnnotatedString(message.content))
                        onDismiss()
                    },
                )
            }
        },
        footerRow =
            if (position.isLastOfGroup) {
                { GeohashBubbleFooter(message) }
            } else {
                null
            },
        drawAuthorLine = { GeohashAuthorLine(message) },
    ) { _ ->
        Text(message.content, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun GeohashAuthorLine(message: GeohashChatEvent) {
    val name = message.nickname()?.takeIf { it.isNotBlank() } ?: message.pubKey.take(8)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        if (message.isTeleported()) {
            Text(
                "  ✈",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun GeohashBubbleFooter(message: GeohashChatEvent) {
    val time = remember(message.createdAt) { timeFormat.format(Date(message.createdAt * 1000)) }
    Text(
        time,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private const val GROUP_WINDOW_SECONDS = 10 * 60L

/** Same author within the grouping window continues the bubble run. */
private fun groups(
    newer: GeohashChatEvent,
    older: GeohashChatEvent,
): Boolean = newer.pubKey == older.pubKey && abs(newer.createdAt - older.createdAt) <= GROUP_WINDOW_SECONDS

/** Bubble position from time-ordered neighbors (older = earlier, newer = later). */
private fun groupPositionFor(
    messages: List<GeohashChatEvent>,
    index: Int,
): ChatGroupPosition {
    val note = messages[index]
    val older = messages.getOrNull(index - 1)
    val newer = messages.getOrNull(index + 1)
    val connectedAbove = older != null && groups(note, older)
    val connectedBelow = newer != null && groups(newer, note)
    return when {
        connectedAbove && connectedBelow -> ChatGroupPosition.MIDDLE
        connectedAbove -> ChatGroupPosition.BOTTOM
        connectedBelow -> ChatGroupPosition.TOP
        else -> ChatGroupPosition.SINGLE
    }
}
