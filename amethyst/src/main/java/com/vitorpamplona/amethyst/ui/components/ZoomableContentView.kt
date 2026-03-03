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
package com.vitorpamplona.amethyst.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaLocalImage
import com.vitorpamplona.amethyst.commons.richtext.MediaLocalVideo
import com.vitorpamplona.amethyst.commons.richtext.MediaPreloadedContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.service.images.BlurhashWrapper
import com.vitorpamplona.amethyst.service.playback.composable.VideoView
import com.vitorpamplona.amethyst.ui.actions.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.ui.actions.InformationDialog
import com.vitorpamplona.amethyst.ui.note.BlankNote
import com.vitorpamplona.amethyst.ui.note.DownloadForOfflineIcon
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.Size24dp
import com.vitorpamplona.amethyst.ui.theme.Size30Modifier
import com.vitorpamplona.amethyst.ui.theme.Size40dp
import com.vitorpamplona.amethyst.ui.theme.Size6dp
import com.vitorpamplona.amethyst.ui.theme.Size75dp
import com.vitorpamplona.amethyst.ui.theme.hashVerifierMark
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import okio.sink
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

// Delay before cleaning up shared video temp files.
// Allows time for receiving app to copy the file after user confirms share.
private const val SHARED_VIDEO_CLEANUP_DELAY_MS = 120_000L

@Composable
fun ZoomableContentView(
    content: BaseMediaContent,
    images: ImmutableList<BaseMediaContent> = remember(content) { persistentListOf(content) },
    roundedCorner: Boolean,
    contentScale: ContentScale,
    accountViewModel: AccountViewModel,
) {
    var dialogOpen by remember(content) { mutableStateOf(false) }

    when (content) {
        is MediaUrlImage -> {
            SensitivityWarning(content.contentWarning != null, accountViewModel) {
                TwoSecondController(content) { controllerVisible ->
                    val mainImageModifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { dialogOpen = true }
                    val loadedImageModifier = if (roundedCorner) MaterialTheme.colorScheme.imageModifier else Modifier.fillMaxWidth()
                    UrlImageView(content, contentScale, mainImageModifier, loadedImageModifier, controllerVisible, accountViewModel = accountViewModel)
                }
            }
        }

        is MediaUrlVideo -> {
            SensitivityWarning(content.contentWarning != null, accountViewModel) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    VideoView(
                        videoUri = content.url,
                        mimeType = content.mimeType,
                        title = content.description,
                        artworkUri = content.artworkUri,
                        authorName = content.authorName,
                        dimensions = content.dim,
                        blurhash = content.blurhash,
                        roundedCorner = roundedCorner,
                        contentScale = contentScale,
                        nostrUriCallback = content.uri,
                        onDialog = { dialogOpen = true },
                        accountViewModel = accountViewModel,
                    )
                }
            }
        }

        is MediaLocalImage -> {
            TwoSecondController(content) { controllerVisible ->
                val mainImageModifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { dialogOpen = true }
                val loadedImageModifier = if (roundedCorner) MaterialTheme.colorScheme.imageModifier else Modifier.fillMaxWidth()

                LocalImageView(content, contentScale, mainImageModifier, loadedImageModifier, controllerVisible, accountViewModel = accountViewModel)
            }
        }

        is MediaLocalVideo -> {
            content.localFile?.let {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    VideoView(
                        videoUri = it.toUri().toString(),
                        mimeType = content.mimeType,
                        title = content.description,
                        artworkUri = content.artworkUri,
                        authorName = content.authorName,
                        roundedCorner = roundedCorner,
                        contentScale = contentScale,
                        nostrUriCallback = content.uri,
                        onDialog = { dialogOpen = true },
                        accountViewModel = accountViewModel,
                    )
                }
            }
        }
    }

    if (dialogOpen) {
        ZoomableImageDialog(
            content,
            images,
            onDismiss = {
                dialogOpen = false
            },
            accountViewModel,
        )
    }
}

@Composable
fun TwoSecondController(
    content: BaseMediaContent,
    inner: @Composable (controllerVisible: MutableState<Boolean>) -> Unit,
) {
    val controllerVisible = remember(content) { mutableStateOf(true) }

    LaunchedEffect(content) {
        delay(2.seconds)
        controllerVisible.value = false
    }

    inner(controllerVisible)
}

@Composable
fun LocalImageView(
    content: MediaLocalImage,
    contentScale: ContentScale,
    mainImageModifier: Modifier,
    loadedImageModifier: Modifier,
    controllerVisible: MutableState<Boolean>,
    accountViewModel: AccountViewModel,
    alwayShowImage: Boolean = false,
) {
    if (content.localFileExists()) {
        val showImage =
            remember {
                mutableStateOf(
                    if (alwayShowImage) true else accountViewModel.settings.showImages(),
                )
            }

        val ratio = remember(content) { content.dim?.aspectRatio() ?: MediaAspectRatioCache.get(content.localFile.toString()) }
        CrossfadeIfEnabled(targetState = showImage.value, contentAlignment = Alignment.Center, accountViewModel = accountViewModel) { imageVisible ->
            if (imageVisible) {
                SubcomposeAsyncImage(
                    model = content.localFile,
                    contentDescription = content.description,
                    contentScale = contentScale,
                    modifier = mainImageModifier,
                ) {
                    val state by painter.state.collectAsState()
                    when (state) {
                        is AsyncImagePainter.State.Loading,
                        -> {
                            if (content.blurhash != null) {
                                if (ratio != null) {
                                    DisplayBlurHash(
                                        content.blurhash,
                                        content.description,
                                        contentScale,
                                        loadedImageModifier.aspectRatio(ratio),
                                    )
                                } else {
                                    DisplayBlurHash(
                                        content.blurhash,
                                        content.description,
                                        contentScale,
                                        loadedImageModifier,
                                    )
                                }
                            } else {
                                if (ratio != null) {
                                    Box(loadedImageModifier.aspectRatio(ratio), contentAlignment = Alignment.Center) {
                                        LoadingAnimation(Size40dp, Size6dp)
                                    }
                                } else {
                                    WaitAndDisplay {
                                        DisplayUrlWithLoadingSymbol(content)
                                    }
                                }
                            }
                        }

                        is AsyncImagePainter.State.Error -> {
                            BlankNote(loadedImageModifier)
                        }

                        is AsyncImagePainter.State.Success -> {
                            SubcomposeAsyncImageContent(loadedImageModifier)

                            SideEffect {
                                val drawable = (state as AsyncImagePainter.State.Success).result.image
                                MediaAspectRatioCache.add(content.localFile.toString(), drawable.width, drawable.height)
                            }

                            content.isVerified?.let {
                                AnimatedVisibility(
                                    visible = controllerVisible.value,
                                    modifier = Modifier.align(Alignment.TopEnd),
                                    enter = remember { fadeIn() },
                                    exit = remember { fadeOut() },
                                ) {
                                    Box(Modifier.align(Alignment.TopEnd), contentAlignment = Alignment.TopEnd) {
                                        HashVerificationSymbol(it)
                                    }
                                }
                            }
                        }

                        else -> {}
                    }
                }
            } else {
                if (content.blurhash != null && ratio != null) {
                    DisplayBlurHash(
                        content.blurhash,
                        content.description,
                        ContentScale.Crop,
                        loadedImageModifier
                            .aspectRatio(ratio)
                            .clickable { showImage.value = true },
                    )
                    IconButton(
                        modifier = Modifier.size(Size75dp),
                        onClick = { showImage.value = true },
                    ) {
                        DownloadForOfflineIcon(Size75dp, Color.White)
                    }
                } else {
                    ImageUrlWithDownloadButton(content.uri, showImage)
                }
            }
        }
    } else {
        BlankNote(loadedImageModifier)
    }
}

@Composable
fun UrlImageView(
    content: MediaUrlImage,
    contentScale: ContentScale,
    mainImageModifier: Modifier,
    loadedImageModifier: Modifier,
    controllerVisible: MutableState<Boolean>,
    accountViewModel: AccountViewModel,
    alwayShowImage: Boolean = false,
) {
    val ratio = content.dim?.aspectRatio() ?: MediaAspectRatioCache.get(content.url)

    val showImage =
        remember {
            mutableStateOf(
                if (alwayShowImage) true else accountViewModel.settings.showImages(),
            )
        }

    CrossfadeIfEnabled(targetState = showImage.value, contentAlignment = Alignment.Center, accountViewModel = accountViewModel) {
        if (it) {
            SubcomposeAsyncImage(
                model = content.url,
                contentDescription = content.description,
                contentScale = contentScale,
                modifier = mainImageModifier,
            ) {
                val state by painter.state.collectAsState()
                when (state) {
                    is AsyncImagePainter.State.Loading,
                    -> {
                        if (content.blurhash != null) {
                            if (ratio != null) {
                                val modifier =
                                    if (contentScale == ContentScale.Crop) {
                                        loadedImageModifier.clickable { showImage.value = true }
                                    } else {
                                        loadedImageModifier.aspectRatio(ratio).clickable { showImage.value = true }
                                    }

                                DisplayBlurHash(
                                    content.blurhash,
                                    content.description,
                                    ContentScale.Crop,
                                    modifier,
                                )
                            } else {
                                DisplayBlurHash(
                                    content.blurhash,
                                    content.description,
                                    ContentScale.Crop,
                                    loadedImageModifier,
                                )
                            }
                        } else {
                            if (ratio != null) {
                                Box(loadedImageModifier.aspectRatio(ratio), contentAlignment = Alignment.Center) {
                                    LoadingAnimation(Size40dp, Size6dp)
                                }
                            } else {
                                WaitAndDisplay {
                                    DisplayUrlWithLoadingSymbol(content)
                                }
                            }
                        }
                    }

                    is AsyncImagePainter.State.Error -> {
                        ClickableUrl(urlText = "${content.url} ", url = content.url)
                    }

                    is AsyncImagePainter.State.Success -> {
                        SubcomposeAsyncImageContent(loadedImageModifier)

                        ShowHashAnimated(content, controllerVisible, Modifier.align(Alignment.TopEnd))

                        SideEffect {
                            val drawable = (state as AsyncImagePainter.State.Success).result.image
                            MediaAspectRatioCache.add(content.url, drawable.width, drawable.height)
                        }
                    }

                    else -> {}
                }
            }
        } else {
            if (content.blurhash != null && ratio != null) {
                val modifier =
                    if (contentScale == ContentScale.Crop) {
                        loadedImageModifier.clickable { showImage.value = true }
                    } else {
                        loadedImageModifier.aspectRatio(ratio).clickable { showImage.value = true }
                    }

                DisplayBlurHash(
                    content.blurhash,
                    content.description,
                    contentScale,
                    modifier,
                )
                IconButton(
                    modifier = Modifier.size(Size75dp),
                    onClick = { showImage.value = true },
                ) {
                    DownloadForOfflineIcon(Size75dp, Color.White)
                }
            } else {
                ImageUrlWithDownloadButton(content.url, showImage)
            }
        }
    }
}

@Composable
fun ImageUrlWithDownloadButton(
    url: String,
    showImage: MutableState<Boolean>,
) {
    val uri = LocalUriHandler.current

    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.onBackground

    val regularText = remember { SpanStyle(color = background) }
    val clickableTextStyle = remember { SpanStyle(color = primary) }

    val annotatedTermsString =
        remember {
            buildAnnotatedString {
                withStyle(clickableTextStyle) {
                    pushStringAnnotation("routeToImage", "")
                    append("$url ")
                    pop()
                }

                withStyle(clickableTextStyle) {
                    pushStringAnnotation("routeToImage", "")
                    pop()
                }

                withStyle(regularText) { append(" ") }
            }
        }

    val pressIndicator =
        remember {
            Modifier
                .fillMaxWidth()
                .clickable { runCatching { uri.openUri(url) } }
        }

    Row(
        modifier =
            Modifier
                .width(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = annotatedTermsString,
            modifier =
                pressIndicator
                    .weight(1f, fill = false),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
        InlineDownloadIcon(showImage)
    }
}

@Composable
private fun InlineDownloadIcon(showImage: MutableState<Boolean>) =
    IconButton(
        modifier = Modifier.size(Size20dp),
        onClick = { showImage.value = true },
    ) {
        DownloadForOfflineIcon(Size24dp)
    }

@Composable
fun ShowHashAnimated(
    content: MediaUrlImage,
    controllerVisible: MutableState<Boolean>,
    modifier: Modifier,
) {
    AnimatedVisibility(
        visible = controllerVisible.value,
        modifier = modifier,
        enter = remember { fadeIn() },
        exit = remember { fadeOut() },
    ) {
        Box(modifier, contentAlignment = Alignment.TopEnd) {
            ShowHash(content)
        }
    }
}

@Composable
fun ShowHash(content: MediaUrlContent) {
    var verifiedHash by remember(content.url) { mutableStateOf<Boolean?>(null) }

    if (content.hash != null) {
        LaunchedEffect(key1 = content.url) {
            val newVerifiedHash =
                withContext(Dispatchers.IO) {
                    verifyHash(content)
                }
            if (newVerifiedHash != verifiedHash) {
                verifiedHash = newVerifiedHash
            }
        }
    }

    verifiedHash?.let { HashVerificationSymbol(it) }
}

@Composable
fun WaitAndDisplay(content: @Composable (AnimatedVisibilityScope.() -> Unit)) {
    val visible = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        visible.value = true
    }

    AnimatedVisibility(
        visible = visible.value,
        enter = fadeIn(),
        exit = fadeOut(),
        content = content,
    )
}

@Composable
fun DisplayUrlWithLoadingSymbol(content: BaseMediaContent) {
    val uri = LocalUriHandler.current

    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.onBackground

    val regularText = remember { SpanStyle(color = background) }
    val clickableTextStyle = remember { SpanStyle(color = primary) }

    val annotatedTermsString =
        remember {
            buildAnnotatedString {
                if (content is MediaUrlContent) {
                    withStyle(clickableTextStyle) {
                        pushStringAnnotation("routeToImage", "")
                        append(content.url + " ")
                        pop()
                    }
                } else {
                    withStyle(regularText) { append("Loading content...") }
                }

                withStyle(clickableTextStyle) {
                    pushStringAnnotation("routeToImage", "")
                    pop()
                }

                withStyle(regularText) { append(" ") }
            }
        }

    val pressIndicator =
        remember {
            if (content is MediaUrlContent) {
                Modifier.clickable { runCatching { uri.openUri(content.url) } }
            } else {
                Modifier
            }
        }

    Row(
        modifier =
            Modifier
                .width(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = annotatedTermsString,
            modifier =
                pressIndicator
                    .weight(1f, fill = false),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
        InlineLoadingIcon()
    }
}

@Composable
fun DisplayUrlWithLoadingSymbol(url: String) {
    val uri = LocalUriHandler.current

    val primary = MaterialTheme.colorScheme.primary
    val annotatedTermsString =
        remember {
            buildAnnotatedString {
                withStyle(SpanStyle(color = primary)) {
                    pushStringAnnotation("routeToImage", "")
                    append("$url ")
                    pop()
                }
            }
        }

    val pressIndicator = remember { Modifier.clickable { runCatching { uri.openUri(url) } } }

    Row(
        modifier = Modifier.width(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = annotatedTermsString,
            modifier = pressIndicator.weight(1f, fill = false),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
        InlineLoadingIcon()
    }
}

@Composable
private fun InlineLoadingIcon() = LoadingAnimation()

@Composable
fun DisplayBlurHash(
    blurhash: String?,
    description: String?,
    contentScale: ContentScale,
    modifier: Modifier,
) {
    if (blurhash == null) return

    AsyncImage(
        model = BlurhashWrapper(blurhash),
        contentDescription = description,
        contentScale = contentScale,
        modifier = modifier,
    )
}

@Composable
fun ShareMediaAction(
    accountViewModel: AccountViewModel,
    popupExpanded: MutableState<Boolean>,
    content: BaseMediaContent,
    onDismiss: () -> Unit,
) {
    if (content is MediaUrlContent) {
        ShareMediaAction(
            popupExpanded = popupExpanded,
            videoUri = content.url,
            postNostrUri = content.uri,
            blurhash = content.blurhash,
            dim = content.dim,
            hash = content.hash,
            mimeType = content.mimeType,
            onDismiss = onDismiss,
            content = content,
            accountViewModel = accountViewModel,
        )
    } else if (content is MediaPreloadedContent) {
        ShareMediaAction(
            popupExpanded = popupExpanded,
            videoUri = content.localFile?.toUri().toString(),
            postNostrUri = content.uri,
            blurhash = content.blurhash,
            dim = content.dim,
            hash = null,
            mimeType = content.mimeType,
            onDismiss = onDismiss,
            content = content,
            accountViewModel = accountViewModel,
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ShareMediaAction(
    popupExpanded: MutableState<Boolean>,
    videoUri: String?,
    postNostrUri: String?,
    blurhash: String?,
    dim: DimensionTag?,
    hash: String?,
    mimeType: String?,
    onDismiss: () -> Unit,
    content: BaseMediaContent? = null,
    accountViewModel: AccountViewModel,
) {
    val scope = accountViewModel.viewModelScope

    // Track if video is downloading - hoisted here to block menu dismiss during download
    val isDownloadingVideo = remember { mutableStateOf(false) }

    DropdownMenu(
        expanded = popupExpanded.value,
        onDismissRequest = { if (!isDownloadingVideo.value) onDismiss() },
    ) {
        val clipboardManager = LocalClipboardManager.current

        if (videoUri != null && !videoUri.startsWith("file")) {
            DropdownMenuItem(
                text = { Text(stringRes(R.string.copy_url_to_clipboard)) },
                onClick = {
                    clipboardManager.setText(AnnotatedString(videoUri))
                    onDismiss()
                },
            )
        }

        postNostrUri?.let {
            DropdownMenuItem(
                text = { Text(stringRes(R.string.copy_the_note_id_to_the_clipboard)) },
                onClick = {
                    clipboardManager.setText(AnnotatedString(it))
                    onDismiss()
                },
            )
        }

        postNostrUri?.let {
            DropdownMenuItem(
                text = { Text(stringRes(R.string.add_media_to_gallery)) },
                onClick = {
                    if (videoUri != null) {
                        val n19 = Nip19Parser.uriToRoute(postNostrUri)?.entity as? NEvent
                        if (n19 != null) {
                            accountViewModel.addMediaToGallery(n19.hex, videoUri, n19.relay.getOrNull(0), blurhash, dim, hash, mimeType) // TODO Whole list or first?
                            accountViewModel.toastManager.toast(R.string.media_added, R.string.media_added_to_profile_gallery)
                        }
                    }

                    onDismiss()
                },
            )
        }

        content?.let {
            val context = LocalContext.current

            when (content) {
                is MediaUrlImage -> {
                    videoUri?.let {
                        if (videoUri.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringRes(R.string.share_image)) },
                                onClick = {
                                    scope.launch { shareImageFile(context, videoUri, mimeType) }
                                    onDismiss()
                                },
                            )
                        }
                    }
                }

                is MediaUrlVideo -> {
                    videoUri?.let {
                        if (videoUri.isNotEmpty()) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringRes(R.string.share_video))
                                        if (isDownloadingVideo.value) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            LoadingAnimation(indicatorSize = 16.dp, circleWidth = 2.dp)
                                        }
                                    }
                                },
                                enabled = !isDownloadingVideo.value,
                                onClick = {
                                    isDownloadingVideo.value = true
                                    scope.launch {
                                        shareVideoFile(
                                            context = context,
                                            videoUrl = videoUri,
                                            mimeType = mimeType,
                                            okHttpClient = { url ->
                                                accountViewModel.httpClientBuilder.okHttpClientForVideo(url)
                                            },
                                            onComplete = {
                                                isDownloadingVideo.value = false
                                                onDismiss()
                                            },
                                            onError = {
                                                isDownloadingVideo.value = false
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    }
                }

                is MediaLocalVideo -> {
                    content.localFile?.let { localFile ->
                        DropdownMenuItem(
                            text = { Text(stringRes(R.string.share_video)) },
                            onClick = {
                                scope.launch { shareLocalVideoFile(context, localFile, mimeType) }
                                onDismiss()
                            },
                        )
                    }
                }

                else -> { /* No share option for other types */ }
            }
        }
    }
}

private suspend fun shareImageFile(
    context: Context,
    videoUri: String,
    mimeType: String?,
) {
    try {
        // Get sharable URI and file extension
        val (uri, fileExtension) = ShareHelper.getSharableUriFromUrl(context, videoUri)

        // Determine mime type, use provided or derive from extension
        val determinedMimeType = mimeType ?: "image/$fileExtension"

        // Create share intent
        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = determinedMimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        context.startActivity(Intent.createChooser(shareIntent, null))
    } catch (e: Exception) {
        Log.w("ZoomableContentView", "Failed to share image: $videoUri", e)
        Toast.makeText(context, context.getString(R.string.unable_to_share_image), Toast.LENGTH_SHORT).show()
    }
}

@OptIn(DelicateCoroutinesApi::class)
private suspend fun shareVideoFile(
    context: Context,
    videoUrl: String,
    mimeType: String?,
    okHttpClient: (String) -> OkHttpClient,
    onComplete: () -> Unit,
    onError: () -> Unit,
) {
    val tempFile = ShareHelper.createTempVideoFile(context)
    var sharedFile: File? = null
    try {
        withContext(Dispatchers.IO) {
            // Download video using streaming
            val client = okHttpClient(videoUrl)
            val request =
                Request
                    .Builder()
                    .get()
                    .url(videoUrl)
                    .build()

            client.newCall(request).executeAsync().use { response ->
                check(response.isSuccessful) { "Download failed: ${response.code}" }
                val responseBody = response.body

                // Stream the response to the temp file
                tempFile.outputStream().use { outputStream ->
                    val bytesCopied = responseBody.source().readAll(outputStream.sink())
                    if (bytesCopied == 0L) {
                        throw IOException("Download failed: empty response body")
                    }
                }
            }

            // Prepare the temp file for sharing (determines extension and creates sharable URI)
            val (uri, extension, sharableFile) = ShareHelper.prepareTempVideoForSharing(context, tempFile)
            sharedFile = sharableFile

            // Determine mime type
            val determinedMimeType = mimeType ?: "video/$extension"

            // Create share intent
            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = determinedMimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(shareIntent, null))
            }
        }

        // Schedule cleanup to allow the receiving app time to copy the file.
        // GlobalScope is intentional: cleanup must survive after share UI is dismissed.
        GlobalScope.launch(Dispatchers.IO) {
            delay(SHARED_VIDEO_CLEANUP_DELAY_MS)
            sharedFile?.let { file ->
                if (!file.delete()) {
                    Log.w("ZoomableContentView", "Failed to delete shared file: ${file.path}")
                }
            }
        }

        withContext(Dispatchers.Main) {
            onComplete()
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w("ZoomableContentView", "Failed to share video: $videoUrl", e)

        // Clean up temp file on error
        if (!tempFile.delete()) {
            Log.w("ZoomableContentView", "Failed to delete temp file: ${tempFile.path}")
        }
        sharedFile?.let { file ->
            if (!file.delete()) {
                Log.w("ZoomableContentView", "Failed to delete shared file: ${file.path}")
            }
        }

        withContext(Dispatchers.Main) {
            Toast
                .makeText(
                    context,
                    context.getString(R.string.unable_to_share_video),
                    Toast.LENGTH_SHORT,
                ).show()
            onError()
        }
    }
}

private suspend fun shareLocalVideoFile(
    context: Context,
    localFile: File,
    mimeType: String?,
) {
    try {
        withContext(Dispatchers.IO) {
            // Get sharable URI for the local file
            val (uri, extension) = ShareHelper.getSharableUriForLocalVideo(context, localFile)

            // Determine mime type
            val determinedMimeType = mimeType ?: "video/$extension"

            // Create share intent
            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = determinedMimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(shareIntent, null))
            }
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.w("ZoomableContentView", "Failed to share local video: ${localFile.path}", e)
        Toast
            .makeText(
                context,
                context.getString(R.string.unable_to_share_video),
                Toast.LENGTH_SHORT,
            ).show()
    }
}

private fun verifyHash(content: MediaUrlContent): Boolean? {
    if (content.hash == null) return null

    Amethyst.instance.diskCache.openSnapshot(content.url)?.use { snapshot ->
        val hash = sha256(snapshot.data.toFile().readBytes()).toHexKey()
        return hash == content.hash
    }

    return null
}

@Composable
private fun HashVerificationSymbol(verifiedHash: Boolean) {
    val localContext = LocalContext.current
    val openDialogMsg = remember { mutableStateOf<String?>(null) }

    openDialogMsg.value?.let {
        InformationDialog(
            title = stringRes(localContext, R.string.hash_verification_info_title),
            textContent = it,
        ) {
            openDialogMsg.value = null
        }
    }

    if (verifiedHash) {
        IconButton(
            modifier = hashVerifierMark,
            onClick = {
                openDialogMsg.value = stringRes(localContext, R.string.hash_verification_passed)
            },
        ) {
            Icon(
                painter = painterRes(R.drawable.original, 1),
                contentDescription = stringRes(id = R.string.hash_verification_passed),
                modifier = Size30Modifier,
                tint = Color.Unspecified,
            )
        }
    } else {
        IconButton(
            modifier = hashVerifierMark,
            onClick = {
                openDialogMsg.value = stringRes(localContext, R.string.hash_verification_failed)
            },
        ) {
            Icon(
                imageVector = Icons.Default.Report,
                contentDescription = stringRes(id = R.string.hash_verification_failed),
                modifier = Size30Modifier,
                tint = Color.Red,
            )
        }
    }
}
