package com.vitorpamplona.amethyst.ui.actions

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Size
import android.widget.Toast
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.ServersAvailable
import com.vitorpamplona.amethyst.ui.components.VideoView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TextSpinner
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TitleExplainer
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NewMediaView(uri: Uri, onClose: () -> Unit, postViewModel: NewMediaModel, accountViewModel: AccountViewModel, nav: (String) -> Unit) {
    val account = accountViewModel.account
    val resolver = LocalContext.current.contentResolver
    val context = LocalContext.current

    val scroolState = rememberScrollState()

    LaunchedEffect(uri) {
        val mediaType = resolver.getType(uri) ?: ""
        postViewModel.load(account, uri, mediaType)

        launch(Dispatchers.IO) {
            postViewModel.imageUploadingError.collect { error ->
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var showRelaysDialog by remember {
        mutableStateOf(false)
    }
    var relayList = remember {
        accountViewModel.account.activeWriteRelays().toImmutableList()
    }

    Dialog(
        onDismissRequest = { onClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            if (showRelaysDialog) {
                RelaySelectionDialog(
                    preSelectedList = relayList,
                    onClose = {
                        showRelaysDialog = false
                    },
                    onPost = {
                        relayList = it
                    },
                    accountViewModel = accountViewModel,
                    nav = nav
                )
            }

            Column(
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp)
                    .fillMaxWidth()
                    .fillMaxHeight().imePadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloseButton(onPress = {
                        postViewModel.cancel()
                        onClose()
                    })

                    Box {
                        IconButton(
                            modifier = Modifier.align(Alignment.Center),
                            onClick = {
                                showRelaysDialog = true
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.relays),
                                contentDescription = null,
                                modifier = Modifier.height(25.dp),
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    PostButton(
                        onPost = {
                            onClose()
                            postViewModel.upload(context, relayList)
                            postViewModel.selectedServer?.let { account.changeDefaultFileServer(it) }
                        },
                        isActive = postViewModel.canPost()
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scroolState)
                    ) {
                        ImageVideoPost(postViewModel, accountViewModel)
                    }
                }
            }
        }
    }
}

fun isNIP94Server(selectedServer: ServersAvailable?): Boolean {
    return selectedServer == ServersAvailable.NOSTRIMG_NIP_94 ||
        // selectedServer == ServersAvailable.IMGUR_NIP_94 ||
        selectedServer == ServersAvailable.NOSTR_BUILD_NIP_94 ||
        selectedServer == ServersAvailable.NOSTRFILES_DEV_NIP_94 ||
        selectedServer == ServersAvailable.NOSTRCHECK_ME_NIP_94
}

@Composable
fun ImageVideoPost(postViewModel: NewMediaModel, accountViewModel: AccountViewModel) {
    val fileServers = listOf(
        // Triple(ServersAvailable.IMGUR_NIP_94, stringResource(id = R.string.upload_server_imgur_nip94), stringResource(id = R.string.upload_server_imgur_nip94_explainer)),
        Triple(ServersAvailable.NOSTRIMG_NIP_94, stringResource(id = R.string.upload_server_nostrimg_nip94), stringResource(id = R.string.upload_server_nostrimg_nip94_explainer)),
        Triple(ServersAvailable.NOSTR_BUILD_NIP_94, stringResource(id = R.string.upload_server_nostrbuild_nip94), stringResource(id = R.string.upload_server_nostrbuild_nip94_explainer)),
        Triple(ServersAvailable.NOSTRFILES_DEV_NIP_94, stringResource(id = R.string.upload_server_nostrfilesdev_nip94), stringResource(id = R.string.upload_server_nostrfilesdev_nip94_explainer)),
        Triple(ServersAvailable.NOSTRCHECK_ME_NIP_94, stringResource(id = R.string.upload_server_nostrcheckme_nip94), stringResource(id = R.string.upload_server_nostrcheckme_nip94_explainer)),
        Triple(ServersAvailable.NIP95, stringResource(id = R.string.upload_server_relays_nip95), stringResource(id = R.string.upload_server_relays_nip95_explainer))
    )

    val fileServerOptions = remember { fileServers.map { TitleExplainer(it.second, it.third) }.toImmutableList() }
    val resolver = LocalContext.current.contentResolver

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
    ) {
        if (postViewModel.isImage() == true) {
            AsyncImage(
                model = postViewModel.galleryUri.toString(),
                contentDescription = postViewModel.galleryUri.toString(),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
            )
        } else if (postViewModel.isVideo() == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var bitmap by remember { mutableStateOf<Bitmap?>(null) }

            LaunchedEffect(key1 = postViewModel.galleryUri) {
                launch(Dispatchers.IO) {
                    postViewModel.galleryUri?.let {
                        try {
                            bitmap = resolver.loadThumbnail(it, Size(1200, 1000), null)
                        } catch (e: Exception) {
                            postViewModel.imageUploadingError.emit("Unable to load thumbnail, but the video can be uploaded")
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
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                )
            }
        } else {
            postViewModel.galleryUri?.let {
                VideoView(
                    videoUri = it.toString(),
                    roundedCorner = false,
                    accountViewModel = accountViewModel
                )
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        TextSpinner(
            label = stringResource(id = R.string.file_server),
            placeholder = fileServers.firstOrNull { it.first == accountViewModel.account.defaultFileServer }?.second ?: fileServers[0].second,
            options = fileServerOptions,
            onSelect = {
                postViewModel.selectedServer = fileServers[it].first
            },
            modifier = Modifier
                .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
                .weight(1f)
        )
    }

    if (isNIP94Server(postViewModel.selectedServer) ||
        postViewModel.selectedServer == ServersAvailable.NIP95
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            SettingSwitchItem(
                checked = postViewModel.sensitiveContent,
                onCheckedChange = { postViewModel.sensitiveContent = it },
                title = R.string.add_sensitive_content_label,
                description = R.string.add_sensitive_content_description
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp))
        ) {
            OutlinedTextField(
                label = { Text(text = stringResource(R.string.content_description)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
                value = postViewModel.alt,
                onValueChange = { postViewModel.alt = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.content_description_example),
                        color = MaterialTheme.colorScheme.placeholderText
                    )
                },
                keyboardOptions = KeyboardOptions.Default.copy(
                    capitalization = KeyboardCapitalization.Sentences
                )
            )
        }
    }
}
