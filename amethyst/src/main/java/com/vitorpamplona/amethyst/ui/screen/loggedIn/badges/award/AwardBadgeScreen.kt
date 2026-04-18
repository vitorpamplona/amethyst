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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.badges.award

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.HexKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AwardBadgeScreen(
    kind: Int,
    pubKeyHex: HexKey,
    dTag: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val vm: AwardBadgeViewModel = viewModel()

    LaunchedEffect(accountViewModel, kind, pubKeyHex, dTag) {
        vm.init(accountViewModel, kind, pubKeyHex, dTag)
    }

    BackHandler {
        vm.cancel()
        nav.popBack()
    }

    Scaffold(
        topBar = {
            SavingTopBar(
                titleRes = R.string.award_badge,
                isActive = vm::canPost,
                onCancel = {
                    vm.cancel()
                    nav.popBack()
                },
                onPost = {
                    accountViewModel.launchSigner {
                        vm.sendPost()
                        nav.popBack()
                    }
                },
            )
        },
    ) { pad ->
        Surface(
            modifier =
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .imePadding(),
        ) {
            AwardBadgeBody(vm)
        }
    }
}

@Composable
private fun AwardBadgeBody(vm: AwardBadgeViewModel) {
    val scrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {
        val def = vm.definition
        if (def == null) {
            Text(
                text = stringRes(R.string.award_badge_loading),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                text = def.name() ?: def.dTag(),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            def.description()?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = vm.awardeesText,
            onValueChange = { vm.awardeesText = it },
            label = { Text(stringRes(R.string.award_badge_recipients_label)) },
            placeholder = { Text(stringRes(R.string.award_badge_recipients_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 10,
        )

        Spacer(modifier = Modifier.height(8.dp))

        val parsed = vm.parsedPubKeys()
        Text(
            text = stringRes(R.string.award_badge_recipient_count, parsed.size),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
