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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.newUser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarExtensibleWithBackButton
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.UserSuggestionState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

@Composable
fun ImportFollowListSelectUserScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopBarExtensibleWithBackButton(
                title = {},
                popBack = nav::popBack,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            InputSelectUserBody(
                onLookup = {
                    nav.nav(Route.ImportFollowsPickFollows(it.pubkeyHex))
                },
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun InputSelectUserBody(
    onLookup: (User) -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var identifier by rememberSaveable { mutableStateOf("") }
    val userSuggestions = remember { UserSuggestionState(accountViewModel.account, accountViewModel.nip05Client) }

    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        ImportHeader()
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = identifier,
            onValueChange = {
                identifier = it
                userSuggestions.processCurrentWord(it)
            },
            label = { Text(stringRes(R.string.profile_to_import_from)) },
            placeholder = { Text(stringRes(R.string.name_search_npub1_alice_example_com)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            supportingText = { Text(stringRes(R.string.supports_npub_nip_05_hex_and_namecoin_bit_d_id)) },
        )

        Spacer(Modifier.height(8.dp))

        ShowUserSuggestionList(
            userSuggestions = userSuggestions,
            onSelect = { user ->
                onLookup(user)
                userSuggestions.reset()
            },
            modifier = Modifier.weight(1f),
            accountViewModel = accountViewModel,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f).padding(24.dp),
            ) {
                Text(
                    stringRes(R.string.tip),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    stringRes(R.string.import_follows_tips),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = nav::popBack) { Text(stringRes(R.string.skip_for_now)) }
        }
    }
}

@Composable
private fun ImportHeader() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.PersonAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stringRes(R.string.import_follow_list),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringRes(R.string.start_with_a_great_feed_by_following_the_same_people_as_someone_you_trust),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
