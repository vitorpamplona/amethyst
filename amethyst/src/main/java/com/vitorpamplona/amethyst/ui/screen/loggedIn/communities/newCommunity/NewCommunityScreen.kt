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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.communities.newCommunity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewCommunityScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val model: NewCommunityModel = viewModel()

    LaunchedEffect(accountViewModel.account) {
        model.init(accountViewModel.account)
    }

    val context = LocalContext.current
    var errorTitle by remember { mutableStateOf<String?>(null) }
    var errorBody by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopBarWithBackButton(
                caption = stringRes(R.string.new_community),
                popBack = { nav.popBack() },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = model.name,
                onValueChange = { model.name = it },
                label = { Text(stringRes(R.string.new_community_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = model.description,
                onValueChange = { model.description = it },
                label = { Text(stringRes(R.string.new_community_description)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = model.imageUrl,
                onValueChange = { model.imageUrl = it },
                label = { Text(stringRes(R.string.new_community_image_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = model.rules,
                onValueChange = { model.rules = it },
                label = { Text(stringRes(R.string.new_community_rules)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = model.moderatorsText,
                onValueChange = { model.moderatorsText = it },
                label = { Text(stringRes(R.string.new_community_moderators)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = model.relayRequestsUrl,
                onValueChange = { model.relayRequestsUrl = it },
                label = { Text(stringRes(R.string.new_community_relay_requests)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = model.relayApprovalsUrl,
                onValueChange = { model.relayApprovalsUrl = it },
                label = { Text(stringRes(R.string.new_community_relay_approvals)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            errorTitle?.let { title ->
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                )
                errorBody?.let { body ->
                    Text(text = body)
                }
            }

            Button(
                onClick = {
                    errorTitle = null
                    errorBody = null
                    model.publish(
                        context = context,
                        onSuccess = { nav.popBack() },
                        onError = { title, body ->
                            errorTitle = title
                            errorBody = body
                        },
                    )
                },
                enabled = model.canPost(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringRes(R.string.new_community_publish))
            }
        }
    }
}
