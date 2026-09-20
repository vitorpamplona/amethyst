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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.cordnGroup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.cordn.GroupExposure
import com.vitorpamplona.amethyst.commons.cordn.ui.CordnExposureCard
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.cordnGroups.CordnGroupChatroom
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * What this room is, and what the coordinator can see of it.
 *
 * The **exposure card's real home**. The link screen shows the same disclosure
 * before joining, when it is a decision; this shows it after, when it is a
 * fact worth being able to check. §8 is only meaningful if it is available at
 * both moments — a privacy property nobody can look up again is a claim, not a
 * property.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CordnGroupInfoScreen(
    coordinatorPubKey: HexKey,
    gid: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val runtime = accountViewModel.account.cordnRuntime
    val room = remember(coordinatorPubKey, gid) { runtime?.groups?.get(coordinatorPubKey, gid) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        Icon(MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringRes(R.string.back))
                    }
                },
                title = { Text(stringRes(R.string.cordn_group_info)) },
            )
        },
    ) { padding ->
        if (room == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text(stringRes(R.string.cordn_group_unavailable))
            }
            return@Scaffold
        }

        CordnGroupInfo(room, coordinatorPubKey, Modifier.padding(padding))
    }
}

@Composable
private fun CordnGroupInfo(
    room: CordnGroupChatroom,
    coordinatorPubKey: HexKey,
    modifier: Modifier = Modifier,
) {
    val name by room.name.collectAsStateWithLifecycle()
    val description by room.description.collectAsStateWithLifecycle()
    val members by room.members.collectAsStateWithLifecycle()
    val admins by room.adminPubkeys.collectAsStateWithLifecycle()
    val epoch by room.epoch.collectAsStateWithLifecycle()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = name?.takeIf { it.isNotBlank() } ?: stringRes(R.string.cordn_group_untitled, room.gid.take(8)),
            style = MaterialTheme.typography.headlineSmall,
        )
        description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        InfoRow(stringRes(R.string.cordn_info_coordinator), coordinatorPubKey)
        InfoRow(stringRes(R.string.cordn_info_gid), room.gid)
        InfoRow(stringRes(R.string.cordn_info_epoch), epoch.toString())
        InfoRow(stringRes(R.string.cordn_info_members), members.size.toString())
        InfoRow(
            label = stringRes(R.string.cordn_info_admins),
            // An empty admin set is not "none configured" — spec/01.md makes it
            // permanently egalitarian, so saying "0" would read as a group
            // waiting to be set up rather than one that decided.
            value =
                if (admins.isEmpty()) {
                    stringRes(R.string.cordn_info_admins_egalitarian)
                } else {
                    admins.size.toString()
                },
        )

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        CordnExposureCard(
            GroupExposure(
                coordinator = coordinatorPubKey,
                linkedGroupCount = 1,
                joinedFromShareLink = false,
                publishedKeyPackage = true,
                encryptionPinned = true,
            ),
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
