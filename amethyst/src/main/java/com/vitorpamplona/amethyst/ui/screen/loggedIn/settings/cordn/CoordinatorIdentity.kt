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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.cordn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types.observeUserNameByHex
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * What to call a coordinator, and the face to put next to it.
 *
 * A coordinator is a Nostr identity -- ContextVM addresses it with ordinary
 * p-tags, and CEP-23 says outright that a server MAY publish NIP-01 profile
 * metadata (`CvmKinds.PROFILE_METADATA` is kind 0 for exactly this reason). So
 * the app can name one the same way it names anybody else, and showing 64 hex
 * characters instead was throwing that away.
 *
 * ## The order, and why
 *
 * 1. `CoordinatorConfig.label` -- documented as "what the user calls it. Never
 *    a claim -- a coordinator cannot prove a name." The only name here that
 *    means anything, so it wins.
 * 2. The CEP-6 announcement's name, when one was heard. Before the profile
 *    because a coordinator added from discovery was *picked* by this name, and
 *    having the settings screen rename it afterwards would be its own small
 *    confusion.
 * 3. The kind 0's display name (CEP-23).
 * 4. The key's first characters.
 *
 * Both 2 and 3 are the coordinator's own word for itself, and two servers may
 * publish the same one, which is why neither outranks a label.
 *
 * The profile is observed whatever the outcome, so the avatar still loads for a
 * coordinator that is named by 1 or 2.
 */
@Composable
fun coordinatorDisplayName(
    pubKey: HexKey,
    label: String?,
    announced: String?,
    accountViewModel: AccountViewModel,
): String {
    // Unconditional: this is what subscribes for the kind 0 (observeUserName
    // registers a UserFinder subscription), and it must not come and go with
    // whether a label or an announcement happens to be present.
    val fromProfile = observeUserNameByHex(pubKey, accountViewModel)

    return label?.takeIf { it.isNotBlank() }
        ?: announced?.takeIf { it.isNotBlank() }
        ?: fromProfile
}

/**
 * A coordinator rendered as the user it is: avatar, then name.
 *
 * The avatar navigates to the profile, because [UserPicture] already does and
 * a coordinator's profile is as worth reading as anyone's -- more, given it is
 * the party whose metadata exposure the group info screen is about.
 */
@Composable
fun CoordinatorIdentityRow(
    pubKey: HexKey,
    label: String?,
    announced: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    name: @Composable (String) -> Unit,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UserPicture(userHex = pubKey, size = size, accountViewModel = accountViewModel, nav = nav)
        name(coordinatorDisplayName(pubKey, label, announced, accountViewModel))
    }
}
