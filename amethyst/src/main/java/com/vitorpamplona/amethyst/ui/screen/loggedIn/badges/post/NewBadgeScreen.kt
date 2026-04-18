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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.badges.post

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
import com.vitorpamplona.amethyst.ui.navigation.topbars.PostingTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewBadgeScreen(
    editDTag: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val vm: NewBadgeViewModel = viewModel()

    LaunchedEffect(accountViewModel, editDTag) {
        vm.init(accountViewModel, editDTag)
    }

    BackHandler {
        vm.cancel()
        nav.popBack()
    }

    Scaffold(
        topBar = {
            PostingTopBar(
                titleRes = if (vm.isEdit) R.string.edit_badge else R.string.new_badge,
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
            NewBadgeBody(vm)
        }
    }
}

@Composable
private fun NewBadgeBody(vm: NewBadgeViewModel) {
    val scrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = vm.badgeId,
            onValueChange = { vm.badgeId = it },
            label = { Text(stringRes(R.string.badge_id_label)) },
            placeholder = { Text(stringRes(R.string.badge_id_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !vm.isEdit,
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = vm.name,
            onValueChange = { vm.name = it },
            label = { Text(stringRes(R.string.badge_name_label)) },
            placeholder = { Text(stringRes(R.string.badge_name_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = vm.description,
            onValueChange = { vm.description = it },
            label = { Text(stringRes(R.string.badge_description_label)) },
            placeholder = { Text(stringRes(R.string.badge_description_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 6,
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = vm.imageUrl,
            onValueChange = { vm.imageUrl = it },
            label = { Text(stringRes(R.string.badge_image_label)) },
            placeholder = { Text(stringRes(R.string.badge_image_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = vm.thumbUrl,
            onValueChange = { vm.thumbUrl = it },
            label = { Text(stringRes(R.string.badge_thumb_label)) },
            placeholder = { Text(stringRes(R.string.badge_thumb_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}
