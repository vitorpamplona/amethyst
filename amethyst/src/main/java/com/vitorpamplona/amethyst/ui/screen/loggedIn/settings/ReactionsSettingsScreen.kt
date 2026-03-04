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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.ReactionRowAction
import com.vitorpamplona.amethyst.model.ReactionRowItem
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size20dp

@Composable
fun ReactionsSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.reactions_settings), nav::popBack)
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            ReactionsSettingsContent(accountViewModel)
        }
    }
}

@Composable
fun ReactionsSettingsContent(accountViewModel: AccountViewModel) {
    val reactionRowItems by accountViewModel.reactionRowItemsFlow().collectAsStateWithLifecycle()
    var items by remember(reactionRowItems) { mutableStateOf(reactionRowItems.toMutableList()) }

    fun save(newItems: List<ReactionRowItem>) {
        items = newItems.toMutableList()
        accountViewModel.changeReactionRowItems(newItems)
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Size20dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringRes(R.string.reactions_settings_description),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        items.forEachIndexed { index, item ->
            ReactionRowItemCard(
                item = item,
                canMoveUp = index > 0,
                canMoveDown = index < items.lastIndex,
                onToggleEnabled = {
                    val newItems = items.toMutableList()
                    newItems[index] = item.copy(enabled = !item.enabled)
                    save(newItems)
                },
                onToggleCounter = {
                    val newItems = items.toMutableList()
                    newItems[index] = item.copy(showCounter = !item.showCounter)
                    save(newItems)
                },
                onMoveUp = {
                    if (index > 0) {
                        val newItems = items.toMutableList()
                        val temp = newItems[index - 1]
                        newItems[index - 1] = newItems[index]
                        newItems[index] = temp
                        save(newItems)
                    }
                },
                onMoveDown = {
                    if (index < items.lastIndex) {
                        val newItems = items.toMutableList()
                        val temp = newItems[index + 1]
                        newItems[index + 1] = newItems[index]
                        newItems[index] = temp
                        save(newItems)
                    }
                },
            )
            if (index < items.lastIndex) {
                HorizontalDivider()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ReactionRowItemCard(
    item: ReactionRowItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleEnabled: () -> Unit,
    onToggleCounter: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    val actionName = reactionActionName(item.action)
    val actionDescription = reactionActionDescription(item.action)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = actionName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = actionDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringRes(R.string.reactions_settings_move_up),
                        modifier = Modifier.size(20.dp),
                        tint = if (canMoveUp) MaterialTheme.colorScheme.onSurface else Color.Gray,
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringRes(R.string.reactions_settings_move_down),
                        modifier = Modifier.size(20.dp),
                        tint = if (canMoveDown) MaterialTheme.colorScheme.onSurface else Color.Gray,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Switch(
                    checked = item.enabled,
                    onCheckedChange = { onToggleEnabled() },
                )
                Text(
                    text = stringRes(R.string.reactions_settings_enabled),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (item.action != ReactionRowAction.Share) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Switch(
                        checked = item.showCounter,
                        onCheckedChange = { onToggleCounter() },
                        enabled = item.enabled,
                    )
                    Text(
                        text = stringRes(R.string.reactions_settings_show_counter),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.enabled) MaterialTheme.colorScheme.onSurface else Color.Gray,
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun reactionActionName(action: ReactionRowAction): String =
    when (action) {
        ReactionRowAction.Reply -> stringRes(R.string.reactions_settings_reply)
        ReactionRowAction.Boost -> stringRes(R.string.reactions_settings_boost)
        ReactionRowAction.Like -> stringRes(R.string.reactions_settings_like)
        ReactionRowAction.Zap -> stringRes(R.string.reactions_settings_zap)
        ReactionRowAction.Share -> stringRes(R.string.reactions_settings_share)
    }

@Composable
fun reactionActionDescription(action: ReactionRowAction): String =
    when (action) {
        ReactionRowAction.Reply -> stringRes(R.string.reactions_settings_reply_description)
        ReactionRowAction.Boost -> stringRes(R.string.reactions_settings_boost_description)
        ReactionRowAction.Like -> stringRes(R.string.reactions_settings_like_description)
        ReactionRowAction.Zap -> stringRes(R.string.reactions_settings_zap_description)
        ReactionRowAction.Share -> stringRes(R.string.reactions_settings_share_description)
    }
