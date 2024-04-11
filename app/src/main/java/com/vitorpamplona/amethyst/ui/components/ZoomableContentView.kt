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
package com.vitorpamplona.amethyst.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaLocalImage
import com.vitorpamplona.amethyst.commons.richtext.MediaLocalVideo
import com.vitorpamplona.amethyst.commons.richtext.MediaPreloadedContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.service.BlurHashRequester
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.actions.InformationDialog
import com.vitorpamplona.amethyst.ui.actions.LoadingAnimation
import com.vitorpamplona.amethyst.ui.actions.SaveToGallery
import com.vitorpamplona.amethyst.ui.note.BlankNote
import com.vitorpamplona.amethyst.ui.note.DownloadForOfflineIcon
import com.vitorpamplona.amethyst.ui.note.HashCheckFailedIcon
import com.vitorpamplona.amethyst.ui.note.HashCheckIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Font17SP
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.Size24dp
import com.vitorpamplona.amethyst.ui.theme.Size30dp
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.Size75dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ZoomableContentView(
    content: BaseMediaContent,
    images: ImmutableList<BaseMediaContent> = remember(content) { listOf(content).toImmutableList() },
    roundedCorner: Boolean,
    accountViewModel: AccountViewModel,
) {
    // store the dialog open or close state
    var dialogOpen by remember(content) { mutableStateOf(false) }

    // store the dialog open or close state
    val shareOpen = remember { mutableStateOf(false) }

    if (shareOpen.value) {
        ShareImageAction(shareOpen, content) { shareOpen.value = false }
    }

    var mainImageModifier =
        if (roundedCorner) {
            MaterialTheme.colorScheme.imageModifier
        } else {
            Modifier.fillMaxWidth()
        }

    if (content is MediaUrlContent) {
        mainImageModifier =
            mainImageModifier.combinedClickable(
                onClick = { dialogOpen = true },
                onLongClick = { shareOpen.value = true },
            )
    } else if (content is MediaPreloadedContent) {
        mainImageModifier =
            mainImageModifier.combinedClickable(
                onClick = { dialogOpen = true },
                onLongClick = { shareOpen.value = true },
            )
    } else {
        mainImageModifier = mainImageModifier.clickable { dialogOpen = true }
    }

    when (content) {
        is MediaUrlImage ->
            SensitivityWarning(content.contentWarning != null, accountViewModel) {
                UrlImageView(content, mainImageModifier, accountViewModel = accountViewModel)
            }
        is MediaUrlVideo ->
            SensitivityWarning(content.contentWarning != null, accountViewModel) {
                VideoView(
                    videoUri = content.url,
                    title = content.description,
                    artworkUri = content.artworkUri,
                    authorName = content.authorName,
                    dimensions = content.dim,
                    blurhash = content.blurhash,
                    roundedCorner = roundedCorner,
                    nostrUriCallback = content.uri,
                    onDialog = { dialogOpen = true },
                    accountViewModel = accountViewModel,
                )
            }
        is MediaLocalImage ->
            LocalImageView(content, mainImageModifier, accountViewModel = accountViewModel)
        is MediaLocalVideo ->
            content.localFile?.let {
                VideoView(
                    videoUri = it.toUri().toString(),
                    title = content.description,
                    artworkUri = content.artworkUri,
                    authorName = content.authorName,
                    roundedCorner = roundedCorner,
                    nostrUriCallback = content.uri,
                    onDialog = { dialogOpen = true },
                    accountViewModel = accountViewModel,
                )
            }
    }

    if (dialogOpen) {
        ZoomableImageDialog(content, images, onDismiss = { dialogOpen = false }, accountViewModel)
    }
}

@Composable
private fun LocalImageView(
    content: MediaLocalImage,
    mainImageModifier: Modifier,
    topPaddingForControllers: Dp = Dp.Unspecified,
    accountViewModel: AccountViewModel,
    alwayShowImage: Boolean = false,
) {
    if (content.localFileExists()) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val showImage =
                remember {
                    mutableStateOf(
                        if (alwayShowImage) true else accountViewModel.settings.showImages.value,
                    )
                }

            val myModifier =
                remember {
                    mainImageModifier.widthIn(max = maxWidth).heightIn(max = maxHeight)
        /*
        .run {
            aspectRatio(content.dim)?.let { ratio ->
                this.aspectRatio(ratio, false)
            } ?: this
        }
         */
                }

            val contentScale =
                remember {
                    if (maxHeight.isFinite) ContentScale.Fit else ContentScale.FillWidth
                }

            val verifierModifier =
                if (topPaddingForControllers.isSpecified) {
                    Modifier.padding(top = topPaddingForControllers).align(Alignment.TopEnd)
                } else {
                    Modifier.align(Alignment.TopEnd)
                }

            val painterState = remember { mutableStateOf<AsyncImagePainter.State?>(null) }

            if (showImage.value) {
                AsyncImage(
                    model = content.localFile,
                    contentDescription = content.description,
                    contentScale = contentScale,
                    modifier = myModifier,
                    onState = { painterState.value = it },
                )
            }

            AddedImageFeatures(
                painterState,
                content,
                contentScale,
                myModifier,
                verifierModifier,
                showImage,
            )
        }
    } else {
        BlankNote()
    }
}

@Composable
private fun UrlImageView(
    content: MediaUrlImage,
    mainImageModifier: Modifier,
    topPaddingForControllers: Dp = Dp.Unspecified,
    accountViewModel: AccountViewModel,
    alwayShowImage: Boolean = false,
) {
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val showImage =
            remember {
                mutableStateOf(
                    if (alwayShowImage) true else accountViewModel.settings.showImages.value,
                )
            }

        val myModifier =
            remember {
                mainImageModifier.widthIn(max = maxWidth).heightIn(max = maxHeight)
            }

        val contentScale = if (maxHeight.isFinite) ContentScale.Fit else ContentScale.FillWidth

        val verifierModifier =
            if (topPaddingForControllers.isSpecified) {
                Modifier.padding(top = topPaddingForControllers).align(Alignment.TopEnd)
            } else {
                Modifier.align(Alignment.TopEnd)
            }

        val painterState = remember { mutableStateOf<AsyncImagePainter.State?>(null) }

        if (showImage.value) {
            AsyncImage(
                model = content.url,
                contentDescription = content.description,
                contentScale = contentScale,
                modifier = myModifier,
                onState = {
                    painterState.value = it
                },
            )
        }

        AddedImageFeatures(
            painterState,
            content,
            contentScale,
            myModifier,
            verifierModifier,
            showImage,
        )
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
                    appendInlineContent("inlineContent", "[icon]")
                    pop()
                }

                withStyle(regularText) { append(" ") }
            }
        }

    val inlineContent = mapOf("inlineContent" to InlineDownloadIcon(showImage))

    val pressIndicator = remember { Modifier.clickable { runCatching { uri.openUri(url) } } }

    Text(
        text = annotatedTermsString,
        modifier = pressIndicator,
        inlineContent = inlineContent,
    )
}

@Composable
private fun InlineDownloadIcon(showImage: MutableState<Boolean>) =
    InlineTextContent(
        Placeholder(
            width = Font17SP,
            height = Font17SP,
            placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
        ),
    ) {
        IconButton(
            modifier = Modifier.size(Size20dp),
            onClick = { showImage.value = true },
        ) {
            DownloadForOfflineIcon(Size24dp)
        }
    }

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AddedImageFeatures(
    painter: MutableState<AsyncImagePainter.State?>,
    content: MediaLocalImage,
    contentScale: ContentScale,
    myModifier: Modifier,
    verifiedModifier: Modifier,
    showImage: MutableState<Boolean>,
) {
    val ratio = remember { aspectRatio(content.dim) }

    if (!showImage.value) {
        if (content.blurhash != null && ratio != null) {
            DisplayBlurHash(
                content.blurhash,
                content.description,
                ContentScale.Crop,
                myModifier.aspectRatio(ratio).clickable { showImage.value = true },
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
    } else {
        when (painter.value) {
            null,
            is AsyncImagePainter.State.Loading,
            -> {
                if (content.blurhash != null) {
                    if (ratio != null) {
                        DisplayBlurHash(
                            content.blurhash,
                            content.description,
                            ContentScale.Crop,
                            myModifier.aspectRatio(ratio),
                        )
                    } else {
                        DisplayBlurHash(content.blurhash, content.description, contentScale, myModifier)
                    }
                } else {
                    FlowRow { DisplayUrlWithLoadingSymbol(content) }
                }
            }
            is AsyncImagePainter.State.Error -> {
                BlankNote()
            }
            is AsyncImagePainter.State.Success -> {
                content.isVerified?.let {
                    HashVerificationSymbol(it, verifiedModifier)
                }
            }
            else -> {}
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AddedImageFeatures(
    painter: MutableState<AsyncImagePainter.State?>,
    content: MediaUrlImage,
    contentScale: ContentScale,
    myModifier: Modifier,
    verifiedModifier: Modifier,
    showImage: MutableState<Boolean>,
) {
    val ratio = remember(content.url) { aspectRatio(content.dim) }

    if (!showImage.value) {
        if (content.blurhash != null && ratio != null) {
            DisplayBlurHash(
                content.blurhash,
                content.description,
                ContentScale.Crop,
                myModifier.aspectRatio(ratio).clickable { showImage.value = true },
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
    } else {
        when (painter.value) {
            null,
            is AsyncImagePainter.State.Loading,
            -> {
                if (content.blurhash != null) {
                    if (ratio != null) {
                        DisplayBlurHash(
                            content.blurhash,
                            content.description,
                            ContentScale.Crop,
                            myModifier.aspectRatio(ratio),
                        )
                    } else {
                        DisplayBlurHash(content.blurhash, content.description, contentScale, myModifier)
                    }
                } else {
                    FlowRow(Modifier.fillMaxWidth()) { DisplayUrlWithLoadingSymbol(content) }
                }
            }
            is AsyncImagePainter.State.Error -> {
                FlowRow(Modifier.fillMaxWidth()) {
                    ClickableUrl(urlText = "${content.url} ", url = content.url)
                }
            }
            is AsyncImagePainter.State.Success -> {
                ShowHash(content, verifiedModifier)
            }
            else -> {}
        }
    }
}

@Composable
fun ShowHash(
    content: MediaUrlContent,
    verifiedModifier: Modifier,
) {
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

    verifiedHash?.let { HashVerificationSymbol(it, verifiedModifier) }
}

fun aspectRatio(dim: String?): Float? {
    if (dim == null) return null
    if (dim == "0x0") return null

    val parts = dim.split("x")
    if (parts.size != 2) return null

    return try {
        val width = parts[0].toFloat()
        val height = parts[1].toFloat()

        if (width < 0.1 || height < 0.1) {
            null
        } else {
            width / height
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }
}

@Composable
private fun DisplayUrlWithLoadingSymbol(content: BaseMediaContent) {
    var cnt by remember { mutableStateOf<BaseMediaContent?>(null) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            delay(200)
            cnt = content
        }
    }

    cnt?.let { DisplayUrlWithLoadingSymbolWait(it) }
}

@Composable
private fun DisplayUrlWithLoadingSymbolWait(content: BaseMediaContent) {
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
                    appendInlineContent("inlineContent", "[icon]")
                    pop()
                }

                withStyle(regularText) { append(" ") }
            }
        }

    val inlineContent = mapOf("inlineContent" to InlineLoadingIcon())

    val pressIndicator =
        remember {
            if (content is MediaUrlContent) {
                Modifier.clickable { runCatching { uri.openUri(content.url) } }
            } else {
                Modifier
            }
        }

    Text(
        text = annotatedTermsString,
        modifier = pressIndicator,
        inlineContent = inlineContent,
    )
}

@Composable
private fun InlineLoadingIcon() =
    InlineTextContent(
        Placeholder(
            width = Font17SP,
            height = Font17SP,
            placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
        ),
    ) {
        LoadingAnimation()
    }

@Composable
fun DisplayBlurHash(
    blurhash: String?,
    description: String?,
    contentScale: ContentScale,
    modifier: Modifier,
) {
    if (blurhash == null) return

    val context = LocalContext.current
    AsyncImage(
        model =
            remember {
                BlurHashRequester.imageRequest(
                    context,
                    blurhash,
                )
            },
        contentDescription = description,
        contentScale = contentScale,
        modifier = modifier,
    )
}

@Composable
fun ZoomableImageDialog(
    imageUrl: BaseMediaContent,
    allImages: ImmutableList<BaseMediaContent> = listOf(imageUrl).toImmutableList(),
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    val orientation = LocalConfiguration.current.orientation

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = true,
                decorFitsSystemWindows = false,
            ),
    ) {
        val view = LocalView.current
        val insets = ViewCompat.getRootWindowInsets(view)

        val orientation = LocalConfiguration.current.orientation
        println("This Log only exists to force orientation listener $orientation")

        val activityWindow = getActivityWindow()
        val dialogWindow = getDialogWindow()
        val parentView = LocalView.current.parent as View
        SideEffect {
            if (activityWindow != null && dialogWindow != null) {
                val attributes = WindowManager.LayoutParams()
                attributes.copyFrom(activityWindow.attributes)
                attributes.type = dialogWindow.attributes.type
                dialogWindow.attributes = attributes
                parentView.layoutParams =
                    FrameLayout.LayoutParams(activityWindow.decorView.width, activityWindow.decorView.height)
                view.layoutParams =
                    FrameLayout.LayoutParams(activityWindow.decorView.width, activityWindow.decorView.height)
            }
        }

        DisposableEffect(key1 = Unit) {
            if (Build.VERSION.SDK_INT >= 30) {
                view.windowInsetsController?.hide(
                    android.view.WindowInsets.Type.systemBars(),
                )
            }

            onDispose {
                if (Build.VERSION.SDK_INT >= 30) {
                    view.windowInsetsController?.show(
                        android.view.WindowInsets.Type.systemBars(),
                    )
                }
            }
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                DialogContent(allImages, imageUrl, onDismiss, accountViewModel)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DialogContent(
    allImages: ImmutableList<BaseMediaContent>,
    imageUrl: BaseMediaContent,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    val pagerState: PagerState = rememberPagerState { allImages.size }
    val controllerVisible = remember { mutableStateOf(false) }
    val holdOn = remember { mutableStateOf<Boolean>(true) }

    LaunchedEffect(key1 = pagerState, key2 = imageUrl) {
        launch {
            val page = allImages.indexOf(imageUrl)
            if (page > -1) {
                pagerState.scrollToPage(page)
            }
        }
        launch(Dispatchers.Default) {
            delay(2000)
            holdOn.value = false
        }
    }

    if (allImages.size > 1) {
        SlidingCarousel(
            pagerState = pagerState,
        ) { index ->
            allImages.getOrNull(index)?.let {
                RenderImageOrVideo(
                    content = it,
                    roundedCorner = false,
                    topPaddingForControllers = Size55dp,
                    onControllerVisibilityChanged = { controllerVisible.value = it },
                    onToggleControllerVisibility = { controllerVisible.value = !controllerVisible.value },
                    accountViewModel = accountViewModel,
                )
            }
        }
    } else {
        RenderImageOrVideo(
            content = imageUrl,
            roundedCorner = false,
            topPaddingForControllers = Size55dp,
            onControllerVisibilityChanged = { controllerVisible.value = it },
            onToggleControllerVisibility = { controllerVisible.value = !controllerVisible.value },
            accountViewModel = accountViewModel,
        )
    }

    AnimatedVisibility(
        visible = holdOn.value || controllerVisible.value,
        enter = remember { fadeIn() },
        exit = remember { fadeOut() },
    ) {
        Row(
            modifier = Modifier.padding(10.dp).statusBarsPadding().systemBarsPadding().fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CloseButton(onPress = onDismiss)

            allImages.getOrNull(pagerState.currentPage)?.let { myContent ->
                if (myContent is MediaUrlContent) {
                    Row {
                        CopyToClipboard(content = myContent)
                        Spacer(modifier = StdHorzSpacer)
                        SaveToGallery(url = myContent.url)
                    }
                } else if (myContent is MediaLocalImage && myContent.localFileExists()) {
                    SaveToGallery(
                        localFile = myContent.localFile!!,
                        mimeType = myContent.mimeType,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun InlineCarrousel(
    allImages: ImmutableList<String>,
    imageUrl: String,
) {
    val pagerState: PagerState = rememberPagerState { allImages.size }

    LaunchedEffect(key1 = pagerState, key2 = imageUrl) {
        launch {
            val page = allImages.indexOf(imageUrl)
            if (page > -1) {
                pagerState.scrollToPage(page)
            }
        }
    }

    if (allImages.size > 1) {
        SlidingCarousel(
            pagerState = pagerState,
        ) { index ->
            AsyncImage(
                model = allImages[index],
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CopyToClipboard(content: BaseMediaContent) {
    val popupExpanded = remember { mutableStateOf(false) }

    OutlinedButton(
        modifier = Modifier.padding(horizontal = Size5dp),
        onClick = { popupExpanded.value = true },
    ) {
        Icon(
            imageVector = Icons.Default.Share,
            modifier = Size20Modifier,
            contentDescription = stringResource(R.string.copy_url_to_clipboard),
        )

        ShareImageAction(popupExpanded, content) { popupExpanded.value = false }
    }
}

@Composable
private fun ShareImageAction(
    popupExpanded: MutableState<Boolean>,
    content: BaseMediaContent,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = popupExpanded.value,
        onDismissRequest = onDismiss,
    ) {
        val clipboardManager = LocalClipboardManager.current

        if (content is MediaUrlContent) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.copy_url_to_clipboard)) },
                onClick = {
                    clipboardManager.setText(AnnotatedString(content.url))
                    onDismiss()
                },
            )
            content.uri?.let {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.copy_the_note_id_to_the_clipboard)) },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(it))
                        onDismiss()
                    },
                )
            }
        }

        if (content is MediaPreloadedContent) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.copy_the_note_id_to_the_clipboard)) },
                onClick = {
                    clipboardManager.setText(AnnotatedString(content.uri))
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun RenderImageOrVideo(
    content: BaseMediaContent,
    roundedCorner: Boolean,
    topPaddingForControllers: Dp = Dp.Unspecified,
    onControllerVisibilityChanged: ((Boolean) -> Unit)? = null,
    onToggleControllerVisibility: (() -> Unit)? = null,
    accountViewModel: AccountViewModel,
) {
    val automaticallyStartPlayback = remember { mutableStateOf<Boolean>(true) }

    if (content is MediaUrlImage) {
        val mainModifier =
            Modifier.fillMaxSize()
                .zoomable(
                    rememberZoomState(),
                    onTap = {
                        if (onToggleControllerVisibility != null) {
                            onToggleControllerVisibility()
                        }
                    },
                )

        UrlImageView(
            content = content,
            mainImageModifier = mainModifier,
            topPaddingForControllers = topPaddingForControllers,
            accountViewModel,
            alwayShowImage = true,
        )
    } else if (content is MediaUrlVideo) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize(1f)) {
            VideoViewInner(
                videoUri = content.url,
                title = content.description,
                artworkUri = content.artworkUri,
                authorName = content.authorName,
                roundedCorner = roundedCorner,
                topPaddingForControllers = topPaddingForControllers,
                automaticallyStartPlayback = automaticallyStartPlayback,
                onControllerVisibilityChanged = onControllerVisibilityChanged,
            )
        }
    } else if (content is MediaLocalImage) {
        val mainModifier =
            Modifier.fillMaxSize()
                .zoomable(
                    rememberZoomState(),
                    onTap = {
                        if (onToggleControllerVisibility != null) {
                            onToggleControllerVisibility()
                        }
                    },
                )

        LocalImageView(
            content = content,
            mainImageModifier = mainModifier,
            topPaddingForControllers = topPaddingForControllers,
            accountViewModel,
            alwayShowImage = true,
        )
    } else if (content is MediaLocalVideo) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize(1f)) {
            content.localFile?.let {
                VideoViewInner(
                    videoUri = it.toUri().toString(),
                    title = content.description,
                    artworkUri = content.artworkUri,
                    authorName = content.authorName,
                    roundedCorner = roundedCorner,
                    topPaddingForControllers = topPaddingForControllers,
                    automaticallyStartPlayback = automaticallyStartPlayback,
                    onControllerVisibilityChanged = onControllerVisibilityChanged,
                )
            }
        }
    }
}

@OptIn(ExperimentalCoilApi::class)
private suspend fun verifyHash(content: MediaUrlContent): Boolean? {
    if (content.hash == null) return null

    Amethyst.instance.coilCache.openSnapshot(content.url)?.use { snapshot ->
        val hash = CryptoUtils.sha256(snapshot.data.toFile().readBytes()).toHexKey()

        Log.d("Image Hash Verification", "$hash == ${content.hash}")

        return hash == content.hash
    }

    return null
}

@Composable
private fun HashVerificationSymbol(
    verifiedHash: Boolean,
    modifier: Modifier,
) {
    val localContext = LocalContext.current

    val openDialogMsg = remember { mutableStateOf<String?>(null) }

    openDialogMsg.value?.let {
        InformationDialog(
            title = localContext.getString(R.string.hash_verification_info_title),
            textContent = it,
        ) {
            openDialogMsg.value = null
        }
    }

    Box(
        modifier.width(40.dp).height(40.dp).padding(10.dp),
    ) {
        if (verifiedHash) {
            IconButton(
                onClick = {
                    openDialogMsg.value = localContext.getString(R.string.hash_verification_passed)
                },
            ) {
                HashCheckIcon(Size30dp)
            }
        } else {
            IconButton(
                onClick = {
                    openDialogMsg.value = localContext.getString(R.string.hash_verification_failed)
                },
            ) {
                HashCheckFailedIcon(Size30dp)
            }
        }
    }
}

// Window utils
@Composable
fun getDialogWindow(): Window? = (LocalView.current.parent as? DialogWindowProvider)?.window

@Composable fun getActivityWindow(): Window? = LocalView.current.context.getActivityWindow()

private tailrec fun Context.getActivityWindow(): Window? =
    when (this) {
        is Activity -> window
        is ContextWrapper -> baseContext.getActivityWindow()
        else -> null
    }

@Composable fun getActivity(): Activity? = LocalView.current.context.getActivity()

private tailrec fun Context.getActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.getActivity()
        else -> null
    }
