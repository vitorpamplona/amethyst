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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.uploads.ShowImageUploadGallery
import com.vitorpamplona.amethyst.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.ui.components.TextSpinner
import com.vitorpamplona.amethyst.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.ui.navigation.topbars.TitleIconModifier
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.note.creators.contentWarning.SettingSwitchItem
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SettingsRow
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatFileUploadDialog(
    state: ChatFileUploadState,
    title: @Composable () -> Unit,
    upload: () -> Unit,
    onCancel: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = { onCancel() },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        SetDialogToEdgeToEdge()
        Scaffold(
            topBar = {
                ShorterTopAppBar(
                    title = title,
                    navigationIcon = {
                        IconButton(
                            modifier = TitleIconModifier,
                            onClick = {
                                state.reset()
                                onCancel()
                            },
                        ) {
                            ArrowBackIcon()
                        }
                    },
                    actions = {
                        SendButton(
                            modifier = Modifier.padding(end = 5.dp),
                            onPost = upload,
                            isActive = state.canPost(),
                        )
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
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
                Column(Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
                    Column(Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                        ImageVideoPostChat(state, accountViewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageVideoPostChat(
    fileUploadState: ChatFileUploadState,
    accountViewModel: AccountViewModel,
) {
    val fileServers by accountViewModel.account.blossomServers.hostNameFlow
        .collectAsState()

    val fileServerOptions =
        remember(fileServers) {
            fileServers
                .map {
                    TitleExplainer(it.name, it.baseUrl)
                }.toImmutableList()
        }

    fileUploadState.multiOrchestrator?.let {
        ShowImageUploadGallery(
            it,
            fileUploadState::deleteMediaToUpload,
            accountViewModel,
        )
    }

    OutlinedTextField(
        label = { Text(text = stringRes(R.string.content_description)) },
        modifier = Modifier.fillMaxWidth().padding(top = 3.dp).height(150.dp),
        maxLines = 10,
        value = fileUploadState.caption,
        onValueChange = { fileUploadState.caption = it },
        placeholder = {
            Text(
                text = stringRes(R.string.content_description_example),
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        keyboardOptions =
            KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences,
            ),
    )

    SettingSwitchItem(
        title = R.string.add_sensitive_content_label,
        description = R.string.add_sensitive_content_description,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        checked = fileUploadState.contentWarning,
        onCheckedChange = fileUploadState::updateContentWarning,
    )

    SettingsRow(R.string.file_server, R.string.file_server_description) {
        TextSpinner(
            label = "",
            placeholder =
                fileServers
                    .firstOrNull { it == accountViewModel.account.settings.defaultFileServer }
                    ?.name
                    ?: fileServers[0].name,
            options = fileServerOptions,
            onSelect = { fileUploadState.selectedServer = fileServers[it] },
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(Size5dp),
    ) {
        Text(
            text = stringRes(R.string.media_compression_quality_label),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringRes(R.string.media_compression_quality_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text =
                    when (fileUploadState.mediaQualitySlider) {
                        0 -> stringRes(R.string.media_compression_quality_low)
                        1 -> stringRes(R.string.media_compression_quality_medium)
                        2 -> stringRes(R.string.media_compression_quality_high)
                        3 -> stringRes(R.string.media_compression_quality_uncompressed)
                        else -> stringRes(R.string.media_compression_quality_medium)
                    },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Slider(
            value = fileUploadState.mediaQualitySlider.toFloat(),
            onValueChange = { fileUploadState.mediaQualitySlider = it.toInt() },
            valueRange = 0f..3f,
            steps = 2,
        )
    }
}

@Composable
fun SendButton(
    onPost: () -> Unit = {},
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        modifier = modifier,
        enabled = isActive,
        onClick = onPost,
    ) {
        Text(text = stringRes(R.string.accessibility_send))
    }
}
