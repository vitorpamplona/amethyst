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
package com.vitorpamplona.amethyst.ui.note.creators.notify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size24dp
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Notifying(
    baseMentions: ImmutableList<User>?,
    accountViewModel: AccountViewModel,
    label: String? = null,
    showWhenEmpty: Boolean = false,
    mutedNotifies: ImmutableSet<HexKey> = persistentSetOf(),
    onAddUser: (() -> Unit)? = null,
    onToggleNotify: (User) -> Unit,
) {
    val mentions = baseMentions?.toSet()

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!mentions.isNullOrEmpty() || showWhenEmpty) {
            Text(
                label ?: stringRes(R.string.reply_notify),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.placeholderText,
                modifier = Modifier.align(CenterVertically),
            )

            // The chips render through a selectable Surface that enforces a 48dp minimum
            // touch target, inflating each chip's measured height well above its visible
            // 32dp pill. That invisible padding would dominate the gap between wrapped
            // rows and make verticalArrangement barely noticeable. Disabling the minimum
            // interactive size lets the chips measure at their visible height so the row
            // spacing matches the horizontal spacing between chips.
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                mentions?.forEach { user ->
                    NotifyUserChip(user, user.pubkeyHex in mutedNotifies, accountViewModel) { onToggleNotify(user) }
                }

                if (onAddUser != null) {
                    AddUserChip(onAddUser)
                }
            }
        }
    }
}

@Composable
private fun NotifyUserChip(
    user: User,
    isMuted: Boolean,
    accountViewModel: AccountViewModel,
    onToggleNotify: () -> Unit,
) {
    InputChip(
        selected = false,
        onClick = onToggleNotify,
        // The bell-off icon alone is easy to miss at chip size, so a muted member
        // also fades as a second cue while staying in the list for easy re-adding.
        modifier = if (isMuted) Modifier.alpha(0.4f) else Modifier,
        label = {
            UsernameDisplay(
                user,
                weight = Modifier.widthIn(max = 180.dp),
                fontWeight = FontWeight.SemiBold,
                accountViewModel = accountViewModel,
            )
        },
        // Use the leadingIcon slot instead of avatar: InputChip clips the avatar slot
        // to a circle, which would cut off the following badge that BaseUserPicture
        // intentionally draws slightly outside the picture's circle.
        leadingIcon = {
            BaseUserPicture(user, Size24dp, accountViewModel)
        },
        trailingIcon = {
            Icon(
                symbol = if (isMuted) MaterialSymbols.NotificationsOff else MaterialSymbols.Notifications,
                contentDescription = stringRes(if (isMuted) R.string.notify_unmute_user else R.string.notify_mute_user),
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
    )
}

@Composable
private fun AddUserChip(onAddUser: () -> Unit) {
    AssistChip(
        onClick = onAddUser,
        label = { Text(text = stringRes(R.string.notify_add_user)) },
        leadingIcon = {
            Icon(
                symbol = MaterialSymbols.PersonAdd,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
    )
}
