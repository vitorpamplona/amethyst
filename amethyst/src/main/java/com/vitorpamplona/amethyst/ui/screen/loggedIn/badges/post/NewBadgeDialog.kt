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

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.actions.StrippingFailureDialog
import com.vitorpamplona.amethyst.ui.actions.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.ui.actions.uploads.GallerySelect
import com.vitorpamplona.amethyst.ui.actions.uploads.ShowImageUploadGallery
import com.vitorpamplona.amethyst.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.ui.components.TextSpinner
import com.vitorpamplona.amethyst.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.ui.navigation.topbars.CreatingTopBar
import com.vitorpamplona.amethyst.ui.note.creators.contentWarning.SettingSwitchItem
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SettingsRow
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewBadgeDialog(
    onClose: () -> Unit,
    postViewModel: NewBadgeModel,
    accountViewModel: AccountViewModel,
) {
    val account = accountViewModel.account
    val context = LocalContext.current

    val scrollState = rememberScrollState()

    LaunchedEffect(account) {
        postViewModel.init(account)
    }

    StrippingFailureDialog(postViewModel.strippingFailureConfirmation)

    var wantsToPickImage by remember { mutableStateOf(false) }

    if (wantsToPickImage) {
        GallerySelect(
            onImageUri = { uris ->
                wantsToPickImage = false
                postViewModel.setPickedMedia(
                    if (uris.isNotEmpty()) persistentListOf(uris.first()) else persistentListOf(),
                )
            },
        )
    }

    Dialog(
        onDismissRequest = onClose,
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
                CreatingTopBar(
                    titleRes = R.string.new_badge,
                    isActive = postViewModel::canPost,
                    onCancel = {
                        postViewModel.cancelModel()
                        onClose()
                    },
                    onPost = {
                        postViewModel.upload(
                            context,
                            onSuccess = onClose,
                            onError = accountViewModel.toastManager::toast,
                        )
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
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
                    ) {
                        BadgeImagePicker(
                            postViewModel = postViewModel,
                            accountViewModel = accountViewModel,
                            onPickImage = { wantsToPickImage = true },
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        BadgeFormFields(postViewModel, accountViewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeImagePicker(
    postViewModel: NewBadgeModel,
    accountViewModel: AccountViewModel,
    onPickImage: () -> Unit,
) {
    if (postViewModel.hasPickedImage()) {
        postViewModel.multiOrchestrator?.let {
            // Tap the preview to swap to a different image.
            Box(modifier = Modifier.clickable(onClick = onPickImage)) {
                ShowImageUploadGallery(
                    list = it,
                    onDelete = { postViewModel.setPickedMedia(persistentListOf()) },
                    accountViewModel = accountViewModel,
                )
            }
        }
    } else {
        UploadPlaceholder(onClick = onPickImage)
    }
}

@Composable
private fun UploadPlaceholder(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(12.dp),
                ).clickable(onClick = onClick)
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                symbol = MaterialSymbols.AddPhotoAlternate,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringRes(R.string.badge_upload_image_cta),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringRes(R.string.badge_upload_image_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BadgeFormFields(
    postViewModel: NewBadgeModel,
    accountViewModel: AccountViewModel,
) {
    val fileServers by accountViewModel.account.blossomServers.hostNameFlow
        .collectAsState()

    val fileServerOptions =
        remember(fileServers) {
            fileServers
                .map { TitleExplainer(it.name, it.baseUrl) }
                .toImmutableList()
        }

    OutlinedTextField(
        value = postViewModel.name,
        onValueChange = { postViewModel.name = it },
        label = { Text(stringRes(R.string.badge_name_label)) },
        placeholder = {
            Text(
                text = stringRes(R.string.badge_name_placeholder),
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions =
            KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences,
            ),
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = postViewModel.description,
        onValueChange = { postViewModel.description = it },
        label = { Text(stringRes(R.string.badge_description_label)) },
        placeholder = {
            Text(
                text = stringRes(R.string.badge_description_placeholder),
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .height(120.dp),
        minLines = 2,
        maxLines = 6,
        keyboardOptions =
            KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences,
            ),
    )

    Spacer(modifier = Modifier.height(12.dp))

    SettingsRow(R.string.file_server, R.string.file_server_description) {
        TextSpinner(
            label = "",
            placeholder =
                fileServers
                    .firstOrNull { it == accountViewModel.account.settings.defaultFileServer }
                    ?.name
                    ?: fileServers.firstOrNull()?.name
                    ?: DEFAULT_MEDIA_SERVERS[0].name,
            options = fileServerOptions,
            onSelect = { postViewModel.selectedServer = fileServers[it] },
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
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
                    when (postViewModel.mediaQualitySlider) {
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
            value = postViewModel.mediaQualitySlider.toFloat(),
            onValueChange = { postViewModel.mediaQualitySlider = it.toInt() },
            valueRange = 0f..3f,
            steps = 2,
        )
    }

    SettingSwitchItem(
        title = R.string.strip_metadata_label,
        description = R.string.strip_metadata_description,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        checked = postViewModel.stripMetadata,
        onCheckedChange = { postViewModel.stripMetadata = it },
    )
}
