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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarmotGroupChatScreen(
    nostrGroupId: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val chatroom =
        remember(nostrGroupId) {
            accountViewModel.account.marmotGroupList.getOrCreateGroup(nostrGroupId)
        }
    val displayName by chatroom.displayName.collectAsStateWithLifecycle()
    val memberCount by chatroom.memberCount.collectAsStateWithLifecycle()

    DisappearingScaffold(
        isInvertedLayout = true,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                title = {
                    Column(
                        modifier =
                            Modifier.clickable {
                                nav.nav(Route.MarmotGroupInfo(nostrGroupId))
                            },
                    ) {
                        Text(displayName ?: "Marmot Group")
                        if (memberCount > 0) {
                            Text(
                                text = "$memberCount members",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { nav.nav(Route.MarmotGroupAddMember(nostrGroupId)) }) {
                        Icon(
                            imageVector = Icons.Default.GroupAdd,
                            contentDescription = "Add Member",
                        )
                    }
                },
            )
        },
        accountViewModel = accountViewModel,
        allowBarHide = false,
    ) {
        Column(Modifier.padding(it)) {
            MarmotGroupChatView(
                nostrGroupId = nostrGroupId,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}
