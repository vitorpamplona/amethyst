/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.ui.theme.LeftHalfCircleButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ZeroPadding

@Composable
fun FollowButton(
    text: Int = R.string.follow,
    // Needed for when browsing a user's profile, for list functionality.
    isInProfileActions: Boolean = false,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = onClick,
        shape = if (isInProfileActions) LeftHalfCircleButtonBorder else ButtonBorder,
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringRes(text), textAlign = TextAlign.Center)
    }
}

@Composable
fun UnfollowButton(
    isInProfileActions: Boolean = false,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = onClick,
        shape = if (isInProfileActions) LeftHalfCircleButtonBorder else ButtonBorder,
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringRes(R.string.unfollow))
    }
}

@Composable
fun ListButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        shape = ButtonBorder.copy(topStart = CornerSize(0f), bottomStart = CornerSize(0f)),
        colors = ButtonDefaults.filledTonalButtonColors(),
        contentPadding = ZeroPadding,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.List,
            contentDescription = stringRes(R.string.follow_set_profile_actions_menu_description),
        )
    }
}
