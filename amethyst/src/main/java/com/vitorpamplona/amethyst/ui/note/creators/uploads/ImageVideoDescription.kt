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
package com.vitorpamplona.amethyst.ui.note.creators.uploads

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.ui.actions.mediaServers.ServerName
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMediaProcessing
import com.vitorpamplona.amethyst.ui.actions.uploads.ShowImageUploadGallery
import com.vitorpamplona.amethyst.ui.components.TextSpinner
import com.vitorpamplona.amethyst.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.ui.note.CancelIcon
import com.vitorpamplona.amethyst.ui.note.creators.contentWarning.SettingSwitchItem
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import kotlinx.collections.immutable.toImmutableList

@Composable
fun ImageVideoDescription(
    uris: MultiOrchestrator,
    defaultServer: ServerName,
    onAdd: (String, ServerName, Boolean, Int, Boolean) -> Unit,
    onDelete: (SelectedMediaProcessing) -> Unit,
    onCancel: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    val fileServers by accountViewModel.account.blossomServers.hostNameFlow
        .collectAsState()

    val fileServerOptions =
        remember(fileServers) {
            fileServers.map { TitleExplainer(it.name, it.baseUrl) }.toImmutableList()
        }

    var selectedServer by remember {
        mutableStateOf(
            fileServers.firstOrNull { it == defaultServer } ?: fileServers[0],
        )
    }
    var message by remember { mutableStateOf("") }
    var sensitiveContent by remember { mutableStateOf(false) }

    // 0 = Low, 1 = Medium, 2 = High, 3=UNCOMPRESSED
    var mediaQualitySlider by remember { mutableIntStateOf(1) }

    // Codec selection: false = H264, true = H265
    var useH265Codec by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 30.dp, end = 30.dp)
                .clip(shape = QuoteBorder)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.subtleBorder,
                    QuoteBorder,
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(30.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
            ) {
                val text =
                    if (uris.size() == 1) {
                        if (uris.first().media.isImage() == true) {
                            R.string.content_description_add_image
                        } else {
                            if (uris.first().media.isVideo() == true) {
                                R.string.content_description_add_video
                            } else {
                                R.string.content_description_add_document
                            }
                        }
                    } else {
                        R.string.content_description_add_media
                    }

                Text(
                    text = stringRes(text),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.W500,
                    modifier =
                        Modifier
                            .padding(start = 10.dp)
                            .weight(1.0f)
                            .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
                )

                IconButton(
                    modifier =
                        Modifier
                            .size(30.dp)
                            .padding(end = 5.dp),
                    onClick = onCancel,
                ) {
                    CancelIcon()
                }
            }

            HorizontalDivider(thickness = DividerThickness)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
            ) {
                ShowImageUploadGallery(uris, onDelete, accountViewModel)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextSpinner(
                    label = stringRes(id = R.string.file_server),
                    placeholder =
                        fileServers
                            .firstOrNull { it == defaultServer }
                            ?.name
                            ?: fileServers[0].name,
                    options = fileServerOptions,
                    onSelect = { selectedServer = fileServers[it] },
                    modifier =
                        Modifier
                            .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
                            .weight(1f),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SettingSwitchItem(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    checked = sensitiveContent,
                    onCheckedChange = { sensitiveContent = it },
                    title = R.string.add_sensitive_content_label,
                    description = R.string.add_sensitive_content_description,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
            ) {
                OutlinedTextField(
                    label = { Text(text = stringRes(R.string.content_description)) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
                    value = message,
                    onValueChange = { message = it },
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
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
                        .padding(vertical = 8.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1.0f),
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
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text =
                            when (mediaQualitySlider) {
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
                    value = mediaQualitySlider.toFloat(),
                    onValueChange = { mediaQualitySlider = it.toInt() },
                    valueRange = 0f..3f,
                    steps = 2,
                )
            }

            if (uris.first().media.isVideo() == true) {
                SettingSwitchItem(
                    title = R.string.video_codec_h265_label,
                    description = R.string.video_codec_h265_description,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    checked = useH265Codec,
                    onCheckedChange = { useH265Codec = it },
                )
            }

            Button(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                onClick = { onAdd(message, selectedServer, sensitiveContent, mediaQualitySlider, useH265Codec) },
                shape = QuoteBorder,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
            ) {
                Text(text = stringRes(R.string.add_content), color = Color.White, fontSize = 20.sp)
            }
        }
    }
}
