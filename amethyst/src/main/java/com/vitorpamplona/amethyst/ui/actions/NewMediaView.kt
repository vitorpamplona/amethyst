/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.actions

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.Nip96MediaServers
import com.vitorpamplona.amethyst.ui.components.VideoView
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TextSpinner
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TitleExplainer
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.events.FileServersEvent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun NewMediaView(
    uri: Uri,
    onClose: () -> Unit,
    postViewModel: NewMediaModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val account = accountViewModel.account
    val resolver = LocalContext.current.contentResolver
    val context = LocalContext.current

    val scrollState = rememberScrollState()

    LaunchedEffect(uri) {
        val mediaType = resolver.getType(uri) ?: ""
        postViewModel.load(account, uri, mediaType)
    }

    var showRelaysDialog by remember { mutableStateOf(false) }
    var relayList = remember { accountViewModel.account.activeWriteRelays().toImmutableList() }

    // 0 = Low, 1 = Medium, 2 = High, 3=UNCOMPRESSED
    var mediaQualitySlider by remember { mutableIntStateOf(1) }

    Dialog(
        onDismissRequest = { onClose() },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (showRelaysDialog) {
                RelaySelectionDialog(
                    preSelectedList = relayList,
                    onClose = { showRelaysDialog = false },
                    onPost = { relayList = it },
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            Column(
                modifier =
                    Modifier
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp)
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .imePadding(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CloseButton(
                        onPress = {
                            postViewModel.cancel()
                            onClose()
                        },
                    )

                    Box {
                        IconButton(
                            modifier = Modifier.align(Alignment.Center),
                            onClick = { showRelaysDialog = true },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.relays),
                                contentDescription = null,
                                modifier = Modifier.height(25.dp),
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }

                    PostButton(
                        onPost = {
                            onClose()
                            postViewModel.upload(context, relayList, mediaQualitySlider) {
                                accountViewModel.toast(stringRes(context, R.string.failed_to_upload_media_no_details), it)
                            }
                            postViewModel.selectedServer?.let {
                                if (!it.isNip95) {
                                    account.settings.changeDefaultFileServer(it.server)
                                }
                            }
                        },
                        isActive = postViewModel.canPost(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
                    ) {
                        ImageVideoPost(postViewModel, accountViewModel)

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
                                    text = stringRes(context, R.string.media_compression_quality_label),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringRes(context, R.string.media_compression_quality_explainer),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
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
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageVideoPost(
    postViewModel: NewMediaModel,
    accountViewModel: AccountViewModel,
) {
    val listOfNip96ServersNote =
        accountViewModel.account
            .getFileServersNote()
            .live()
            .metadata
            .observeAsState()

    val fileServers =
        (
            (listOfNip96ServersNote.value?.note?.event as? FileServersEvent)?.servers()?.map {
                ServerOption(
                    Nip96MediaServers.ServerName(
                        it,
                        it,
                    ),
                    false,
                )
            } ?: Nip96MediaServers.DEFAULT.map { ServerOption(it, false) }
        ) +
            listOf(
                ServerOption(
                    Nip96MediaServers.ServerName(
                        "NIP95",
                        stringRes(id = R.string.upload_server_relays_nip95),
                    ),
                    true,
                ),
            )

    val fileServerOptions =
        remember {
            fileServers.map { TitleExplainer(it.server.name, it.server.baseUrl) }.toImmutableList()
        }
    val resolver = LocalContext.current.contentResolver

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
    ) {
        if (postViewModel.isImage() == true) {
            AsyncImage(
                model = postViewModel.galleryUri.toString(),
                contentDescription = postViewModel.galleryUri.toString(),
                contentScale = ContentScale.FillWidth,
                modifier =
                    Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
            )
        } else if (postViewModel.isVideo() == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var bitmap by remember { mutableStateOf<Bitmap?>(null) }

            LaunchedEffect(key1 = postViewModel.galleryUri) {
                launch(Dispatchers.IO) {
                    postViewModel.galleryUri?.let {
                        try {
                            bitmap = resolver.loadThumbnail(it, Size(1200, 1000), null)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.w("NewPostView", "Couldn't create thumbnail, but the video can be uploaded", e)
                        }
                    }
                }
            }

            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "some useful description",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                )
            }
        } else {
            postViewModel.galleryUri?.let {
                VideoView(
                    videoUri = it.toString(),
                    mimeType = postViewModel.mediaType,
                    roundedCorner = false,
                    isFiniteHeight = false,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextSpinner(
            label = stringRes(id = R.string.file_server),
            placeholder =
                fileServers
                    .firstOrNull { it.server == accountViewModel.account.settings.defaultFileServer }
                    ?.server
                    ?.name
                    ?: fileServers[0].server.name,
            options = fileServerOptions,
            onSelect = { postViewModel.selectedServer = fileServers[it] },
            modifier = Modifier.windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)).weight(1f),
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        SettingSwitchItem(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            checked = postViewModel.sensitiveContent,
            onCheckedChange = { postViewModel.sensitiveContent = it },
            title = R.string.add_sensitive_content_label,
            description = R.string.add_sensitive_content_description,
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
    ) {
        OutlinedTextField(
            label = { Text(text = stringRes(R.string.content_description)) },
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
            value = postViewModel.alt,
            onValueChange = { postViewModel.alt = it },
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
}
