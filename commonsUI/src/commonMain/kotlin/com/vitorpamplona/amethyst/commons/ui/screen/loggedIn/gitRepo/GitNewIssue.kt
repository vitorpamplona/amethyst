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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_body
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_create
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_labels
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_labels_hint
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_subject
import com.vitorpamplona.amethyst.commons.resources.git_new_issue_title
import com.vitorpamplona.amethyst.commons.service.upload.ui.StrippingFailureDialog
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.commons.ui.components.SpellCheckedOutlinedTextField
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.layouts.DisappearingScaffold
import com.vitorpamplona.amethyst.commons.ui.layouts.LocalDisappearingScaffoldPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TitleIconModifier
import com.vitorpamplona.amethyst.commons.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.ImageVideoDescription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Address

@Composable
fun GitNewIssueScreen(
    address: Address,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadAddressableNote(address) { note ->
        note?.let { GitNewIssueForm(it, accountViewModel, nav) }
    }
}

/**
 * Full-screen "New issue" composer: a subject, a markdown body with attachable pictures and
 * videos, and labels. On submit it builds a NIP-34 issue addressed to [repoNote], signs it with
 * the account signer and broadcasts it, then returns to the issues list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GitNewIssueForm(
    repoNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: GitNewIssueViewModel = rememberViewModel { GitNewIssueViewModel() }
    viewModel.init(accountViewModel.account)

    DisappearingScaffold(
        isInvertedLayout = false,
        topBar = {
            ShorterTopAppBar(
                title = { Text(stringRes(Res.string.git_new_issue_title)) },
                navigationIcon = {
                    Row(TitleIconModifier, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = nav::popBack) { ArrowBackIcon() }
                    }
                },
                actions = {
                    TextButton(
                        enabled = viewModel.canSend(),
                        onClick = {
                            accountViewModel.launchSigner { viewModel.send(repoNote) }
                            nav.popBack()
                        },
                    ) {
                        Text(stringRes(Res.string.git_new_issue_create))
                    }
                },
            )
        },
        accountViewModel = accountViewModel,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(LocalDisappearingScaffoldPadding.current)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SpellCheckedOutlinedTextField(
                value = viewModel.subject,
                onValueChange = { viewModel.subject = it },
                label = { Text(stringRes(Res.string.git_new_issue_subject)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SpellCheckedOutlinedTextField(
                value = viewModel.body,
                onValueChange = { viewModel.body = it },
                label = { Text(stringRes(Res.string.git_new_issue_body)) },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            // Pictures and videos of the problem: uploaded, linked in the body, and described by
            // an imeta tag on the issue, as in a comment.
            SelectFromGallery(
                isUploading = viewModel.mediaUploadTracker.isUploadingImage,
                enabled = !viewModel.mediaUploadTracker.isUploadingFile,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier,
            ) {
                viewModel.selectMedia(it)
            }
            viewModel.multiOrchestrator?.let {
                ImageVideoDescription(
                    it,
                    accountViewModel.account.settings.defaultFileServer,
                    isUploading = viewModel.mediaUploadTracker.isUploading,
                    onAdd = { alt, server, sensitiveContent, mediaQuality, useH265, stripMetadata, convertGifToMp4 ->
                        viewModel.upload(alt, if (sensitiveContent) "" else null, mediaQuality, server, accountViewModel.toastManager::toast, accountViewModel.host.mediaUploader, useH265, stripMetadata, convertGifToMp4)
                        accountViewModel.account.settings.changeDefaultFileServer(server)
                    },
                    onDelete = viewModel::deleteMediaToUpload,
                    onCancel = { viewModel.multiOrchestrator = null },
                    accountViewModel = accountViewModel,
                )
            }
            OutlinedTextField(
                value = viewModel.labels,
                onValueChange = { viewModel.labels = it },
                label = { Text(stringRes(Res.string.git_new_issue_labels)) },
                placeholder = { Text(stringRes(Res.string.git_new_issue_labels_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    StrippingFailureDialog(viewModel.strippingFailureConfirmation)
}
