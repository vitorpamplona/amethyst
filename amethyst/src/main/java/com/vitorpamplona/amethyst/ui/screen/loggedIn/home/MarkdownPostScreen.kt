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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectFromFiles
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectFromGallery
import com.vitorpamplona.amethyst.ui.actions.uploads.TakePictureButton
import com.vitorpamplona.amethyst.ui.components.markdown.RenderContentAsMarkdown
import com.vitorpamplona.amethyst.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.ui.navigation.topbars.PostingTopBar
import com.vitorpamplona.amethyst.ui.note.creators.contentWarning.ContentSensitivityExplainer
import com.vitorpamplona.amethyst.ui.note.creators.contentWarning.MarkAsSensitiveButton
import com.vitorpamplona.amethyst.ui.note.creators.emojiSuggestions.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.emojiSuggestions.WatchAndLoadMyEmojiList
import com.vitorpamplona.amethyst.ui.note.creators.uploads.ImageVideoDescription
import com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.ui.note.creators.zapraiser.AddZapraiserButton
import com.vitorpamplona.amethyst.ui.note.creators.zapraiser.ZapRaiserRequest
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.ForwardZapTo
import com.vitorpamplona.amethyst.ui.note.creators.zapsplits.ForwardZapToButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.SuggestionListDefaultHeightPage
import com.vitorpamplona.amethyst.ui.theme.placeholderText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownPostScreen(
    draft: Note? = null,
    version: Note? = null,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    val postViewModel: MarkdownPostViewModel = viewModel()
    postViewModel.init(accountViewModel)

    LaunchedEffect(postViewModel, accountViewModel) {
        postViewModel.load(draft, version)
    }

    WatchAndLoadMyEmojiList(accountViewModel)

    BackHandler {
        accountViewModel.launchSigner {
            postViewModel.sendDraftSync()
            postViewModel.cancel()
        }
        nav.popBack()
    }

    Scaffold(
        topBar = {
            PostingTopBar(
                titleRes = R.string.new_long_form_post,
                isActive = postViewModel::canPost,
                onPost = {
                    accountViewModel.launchSigner {
                        postViewModel.sendPostSync()
                        nav.popBack()
                    }
                },
                onCancel = {
                    accountViewModel.launchSigner {
                        postViewModel.sendDraftSync()
                        postViewModel.cancel()
                    }
                    nav.popBack()
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
            MarkdownPostScreenBody(postViewModel, accountViewModel, nav)
        }
    }
}

@Composable
private fun MarkdownPostScreenBody(
    postViewModel: MarkdownPostViewModel,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        // Title field
        OutlinedTextField(
            value = postViewModel.title,
            onValueChange = {
                postViewModel.title = it
                postViewModel.draftTag.newVersion()
            },
            label = { Text(stringRes(R.string.article_title)) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Size10dp, vertical = Size5dp),
            textStyle =
                LocalTextStyle.current.copy(
                    fontSize = 20.sp,
                    textDirection = TextDirection.Content,
                ),
            singleLine = true,
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    capitalization = KeyboardCapitalization.Words,
                ),
        )

        // Summary field
        OutlinedTextField(
            value = postViewModel.summary,
            onValueChange = {
                postViewModel.summary = it
                postViewModel.draftTag.newVersion()
            },
            label = { Text(stringRes(R.string.article_summary)) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Size10dp, vertical = Size5dp),
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
            maxLines = 3,
            keyboardOptions =
                KeyboardOptions.Default.copy(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
        )

        // Cover image URL field
        OutlinedTextField(
            value = postViewModel.coverImageUrl,
            onValueChange = {
                postViewModel.coverImageUrl = it
                postViewModel.draftTag.newVersion()
            },
            label = { Text(stringRes(R.string.article_cover_image_url)) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Size10dp, vertical = Size5dp),
            singleLine = true,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = Size5dp))

        // Edit / Preview tabs
        TabRow(
            selectedTabIndex = if (postViewModel.showPreview) 1 else 0,
        ) {
            Tab(
                selected = !postViewModel.showPreview,
                onClick = { postViewModel.showPreview = false },
                text = { Text(stringRes(R.string.markdown_edit)) },
            )
            Tab(
                selected = postViewModel.showPreview,
                onClick = { postViewModel.showPreview = true },
                text = { Text(stringRes(R.string.markdown_preview)) },
            )
        }

        if (postViewModel.showPreview) {
            // Markdown preview (scrollable)
            val previewScrollState = rememberScrollState()
            val backgroundColor = remember { mutableStateOf(Color.Transparent) }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(previewScrollState)
                        .padding(Size10dp),
            ) {
                RenderContentAsMarkdown(
                    content = postViewModel.message.text,
                    tags = EmptyTagList,
                    canPreview = true,
                    quotesLeft = 1,
                    backgroundColor = backgroundColor,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        } else {
            // Markdown editor
            OutlinedTextField(
                value = postViewModel.message,
                onValueChange = postViewModel::updateMessage,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = Size10dp, vertical = Size5dp),
                placeholder = {
                    Text(
                        text = stringRes(R.string.write_your_article_in_markdown),
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                },
                textStyle =
                    LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        textDirection = TextDirection.Content,
                    ),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                keyboardOptions =
                    KeyboardOptions.Default.copy(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
            )
        }

        // Content warning section
        if (postViewModel.wantsToMarkAsSensitive) {
            Row(
                verticalAlignment = CenterVertically,
                modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
            ) {
                ContentSensitivityExplainer(
                    description = postViewModel.contentWarningDescription,
                    onDescriptionChange = { postViewModel.contentWarningDescription = it },
                )
            }
        }

        // Forward zap section
        if (postViewModel.wantsForwardZapTo) {
            Row(
                verticalAlignment = CenterVertically,
                modifier = Modifier.padding(top = Size5dp, bottom = Size5dp, start = Size10dp),
            ) {
                ForwardZapTo(postViewModel, accountViewModel)
            }
        }

        // Image upload section
        postViewModel.multiOrchestrator?.let {
            Row(
                verticalAlignment = CenterVertically,
                modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
            ) {
                val context = LocalContext.current
                ImageVideoDescription(
                    it,
                    accountViewModel.account.settings.defaultFileServer,
                    onAdd = { alt, server, sensitiveContent, mediaQuality, useH265 ->
                        postViewModel.upload(alt, if (sensitiveContent) "" else null, mediaQuality, server, accountViewModel.toastManager::toast, context, useH265)
                        accountViewModel.account.settings.changeDefaultFileServer(server)
                    },
                    onDelete = postViewModel::deleteMediaToUpload,
                    onCancel = { postViewModel.multiOrchestrator = null },
                    accountViewModel = accountViewModel,
                )
            }
        }

        // Zap raiser section
        if (postViewModel.wantsZapRaiser && postViewModel.hasLnAddress()) {
            Row(
                verticalAlignment = CenterVertically,
                modifier = Modifier.padding(vertical = Size5dp, horizontal = Size10dp),
            ) {
                ZapRaiserRequest(
                    stringRes(id = R.string.zapraiser),
                    postViewModel,
                )
            }
        }

        // User suggestions
        postViewModel.userSuggestions?.let {
            ShowUserSuggestionList(
                it,
                postViewModel::autocompleteWithUser,
                accountViewModel,
                modifier = SuggestionListDefaultHeightPage,
            )
        }

        // Emoji suggestions
        postViewModel.emojiSuggestions?.let {
            ShowEmojiSuggestionList(
                it,
                postViewModel::autocompleteWithEmoji,
                postViewModel::autocompleteWithEmojiUrl,
                modifier = SuggestionListDefaultHeightPage,
            )
        }

        // Bottom action bar
        MarkdownBottomRowActions(postViewModel)
    }
}

@Composable
private fun MarkdownBottomRowActions(postViewModel: MarkdownPostViewModel) {
    val scrollState = rememberScrollState()
    Row(
        modifier =
            Modifier
                .horizontalScroll(scrollState)
                .fillMaxWidth()
                .height(50.dp),
        verticalAlignment = CenterVertically,
    ) {
        SelectFromGallery(
            isUploading = postViewModel.isUploadingImage,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier,
        ) {
            postViewModel.selectImage(it)
        }

        SelectFromFiles(
            isUploading = postViewModel.isUploadingImage,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier,
        ) {
            postViewModel.selectImage(it)
        }

        TakePictureButton(
            onPictureTaken = {
                postViewModel.selectImage(it)
            },
        )

        ForwardZapToButton(postViewModel.wantsForwardZapTo) {
            postViewModel.wantsForwardZapTo = !postViewModel.wantsForwardZapTo
        }

        if (postViewModel.canAddZapRaiser) {
            AddZapraiserButton(postViewModel.wantsZapRaiser) {
                postViewModel.wantsZapRaiser = !postViewModel.wantsZapRaiser
            }
        }

        MarkAsSensitiveButton(postViewModel.wantsToMarkAsSensitive) {
            postViewModel.wantsToMarkAsSensitive = !postViewModel.wantsToMarkAsSensitive
        }
    }
}
