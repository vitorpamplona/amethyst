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

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaLocalImage
import com.vitorpamplona.amethyst.commons.richtext.MediaLocalVideo
import com.vitorpamplona.amethyst.commons.richtext.MediaPreloadedContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.service.playback.composable.VideoViewInner
import com.vitorpamplona.amethyst.service.playback.diskCache.isLiveStreaming
import com.vitorpamplona.amethyst.ui.actions.MediaSaverToDisk
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size15dp
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.imageModifier
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

@Composable
fun ZoomableImageDialog(
    imageUrl: BaseMediaContent,
    allImages: ImmutableList<BaseMediaContent> = listOf(imageUrl).toImmutableList(),
    sourceBounds: Rect? = null,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    // Animation progress: 0f = at source position/size, 1f = fullscreen.
    val progress = remember { Animatable(0f) }
    var isExiting by remember { mutableStateOf(false) }

    // Natural layout bounds of the currently-visible image/video inside the dialog.
    // Used as the "target" of the grow animation so the image itself — not the dialog
    // viewport — aligns with the tapped thumbnail at progress = 0.
    var imageBounds by remember { mutableStateOf<Rect?>(null) }

    // Start the enter animation as soon as valid image bounds are available. Without
    // this gate, the animation can begin before onGloballyPositioned has reported real
    // bounds and the graphicsLayer falls back to its alpha-only branch.
    LaunchedEffect(Unit) {
        snapshotFlow { imageBounds }
            .filter { it != null && it.width > 0f && it.height > 0f }
            .first()
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        )
    }

    LaunchedEffect(isExiting) {
        if (isExiting) {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
            )
            onDismiss()
        }
    }

    val dismissWithAnimation: () -> Unit = { if (!isExiting) isExiting = true }
    val progressProvider: () -> Float = { progress.value }

    // Accept the first set of valid bounds unconditionally. Subsequent updates are
    // only accepted while the progress Animatable is idle, so a layout change
    // mid-transition (e.g. an async image finishing loading, or a pager settling)
    // can't re-target the transform and cause a hiccup.
    val updateImageBounds: (Rect) -> Unit = { newBounds ->
        if (newBounds.width > 0f && newBounds.height > 0f) {
            val current = imageBounds
            if (current == null) {
                imageBounds = newBounds
            } else if (!progress.isRunning && current != newBounds) {
                imageBounds = newBounds
            }
        }
    }

    Dialog(
        onDismissRequest = dismissWithAnimation,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = true,
                decorFitsSystemWindows = false,
            ),
    ) {
        val orientation = LocalConfiguration.current.orientation
        println("This Log only exists to force orientation listener $orientation")

        val activityWindow = getActivityWindow()
        val dialogWindow = getDialogWindow()

        if (activityWindow != null && dialogWindow != null) {
            val attributes = WindowManager.LayoutParams()
            attributes.copyFrom(activityWindow.attributes)
            attributes.type = dialogWindow.attributes.type
            // Disable the system dim so the thumbnail stays visible behind the growing dialog.
            attributes.dimAmount = 0f
            attributes.flags = attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
            dialogWindow.attributes = attributes
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Background surface that fades in as the content grows to fullscreen.
            Surface(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = progressProvider() },
            ) {}

            DialogContent(
                allImages = allImages,
                imageUrl = imageUrl,
                sourceBounds = sourceBounds,
                imageBounds = imageBounds,
                onImageBoundsChanged = updateImageBounds,
                progress = progressProvider,
                onDismiss = dismissWithAnimation,
                accountViewModel = accountViewModel,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalPermissionsApi::class)
private fun DialogContent(
    allImages: ImmutableList<BaseMediaContent>,
    imageUrl: BaseMediaContent,
    sourceBounds: Rect?,
    imageBounds: Rect?,
    onImageBoundsChanged: (Rect) -> Unit,
    progress: () -> Float,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    val pagerState: PagerState = rememberPagerState { allImages.size }
    val controllerVisible = remember { mutableStateOf(true) }
    val sharePopupExpanded = remember { mutableStateOf(false) }

    LaunchedEffect(key1 = pagerState, key2 = imageUrl) {
        launch {
            val page = allImages.indexOf(imageUrl)
            if (page > -1) {
                pagerState.scrollToPage(page)
            }
        }
        launch {
            delay(2000)
            if (!sharePopupExpanded.value) {
                controllerVisible.value = false
            }
        }
    }

    // Re-trigger auto-hide after the share dialog is dismissed
    LaunchedEffect(sharePopupExpanded.value) {
        if (!sharePopupExpanded.value && controllerVisible.value) {
            delay(2000)
            controllerVisible.value = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                onClick = {
                    if (!sharePopupExpanded.value) {
                        controllerVisible.value = !controllerVisible.value
                    }
                },
            ),
        Alignment.TopCenter,
    ) {
        // Transformed image/video container. Only this layer scales & translates so the
        // image aligns with the tapped thumbnail on enter/exit. Controls stay put.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val src = sourceBounds
                        val img = imageBounds
                        if (src != null && img != null &&
                            src.width > 0f && src.height > 0f &&
                            img.width > 0f && img.height > 0f
                        ) {
                            transformOrigin = TransformOrigin(0f, 0f)
                            // Uniform scale so non-square images keep their aspect ratio during
                            // the grow animation. max() so the image covers the source rect in
                            // at least one dimension; the other overflows centered on the tap.
                            val startScale = maxOf(src.width / img.width, src.height / img.height)
                            val srcCenter = src.center
                            val imgCenter = img.center
                            val p = progress()
                            scaleX = lerp(startScale, 1f, p)
                            scaleY = lerp(startScale, 1f, p)
                            translationX = lerp(srcCenter.x - startScale * imgCenter.x, 0f, p)
                            translationY = lerp(srcCenter.y - startScale * imgCenter.y, 0f, p)
                        } else {
                            // No source bounds: fall back to a plain fade.
                            alpha = progress()
                        }
                    },
        ) {
            if (allImages.size > 1) {
                SlidingCarousel(
                    pagerState = pagerState,
                ) { index ->
                    allImages.getOrNull(index)?.let { pageContent ->
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            RenderImageOrVideo(
                                content = pageContent,
                                roundedCorner = false,
                                isFiniteHeight = true,
                                controllerVisible = controllerVisible,
                                accountViewModel = accountViewModel,
                                onContentBoundsChanged =
                                    if (index == pagerState.currentPage) {
                                        onImageBoundsChanged
                                    } else {
                                        null
                                    },
                            )
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    RenderImageOrVideo(
                        content = imageUrl,
                        roundedCorner = false,
                        isFiniteHeight = true,
                        controllerVisible = controllerVisible,
                        accountViewModel = accountViewModel,
                        onContentBoundsChanged = onImageBoundsChanged,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controllerVisible.value,
            enter = remember { fadeIn() },
            exit = remember { fadeOut() },
            // Also fade with the grow animation so controls appear/disappear alongside it.
            modifier = Modifier.graphicsLayer { alpha = progress().coerceIn(0f, 1f) },
        ) {
            Row(
                modifier =
                    Modifier
                        .padding(horizontal = Size15dp, vertical = Size10dp)
                        .statusBarsPadding()
                        .systemBarsPadding()
                        .fillMaxWidth(),
                horizontalArrangement = spacedBy(Size10dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = Size5dp),
                    colors = ButtonDefaults.outlinedButtonColors().copy(containerColor = MaterialTheme.colorScheme.background),
                ) {
                    Icon(
                        symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                        contentDescription = stringRes(R.string.back),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                allImages.getOrNull(pagerState.currentPage)?.let { myContent ->
                    if (myContent is MediaUrlImage || myContent is MediaLocalImage) {
                        OutlinedButton(
                            onClick = { sharePopupExpanded.value = true },
                            contentPadding = PaddingValues(horizontal = Size5dp),
                            colors = ButtonDefaults.outlinedButtonColors().copy(containerColor = MaterialTheme.colorScheme.background),
                        ) {
                            Icon(
                                symbol = MaterialSymbols.Share,
                                modifier = Size20Modifier,
                                contentDescription = stringRes(R.string.quick_action_share),
                            )

                            ShareMediaAction(accountViewModel = accountViewModel, popupExpanded = sharePopupExpanded, myContent, onDismiss = { sharePopupExpanded.value = false })
                        }

                        if (myContent !is MediaUrlContent || !isLiveStreaming(myContent.url)) {
                            val localContext = LocalContext.current

                            val scope = rememberCoroutineScope()

                            val writeStoragePermissionState =
                                rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE) { isGranted ->
                                    if (isGranted) {
                                        scope.launch {
                                            saveMediaToGallery(myContent, localContext, accountViewModel)
                                        }
                                        scope.launch {
                                            Toast
                                                .makeText(
                                                    localContext,
                                                    stringRes(localContext, R.string.media_download_has_started_toast),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                        }
                                    }
                                }

                            OutlinedButton(
                                onClick = {
                                    if (
                                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                                        writeStoragePermissionState.status.isGranted
                                    ) {
                                        scope.launch(Dispatchers.IO) {
                                            saveMediaToGallery(myContent, localContext, accountViewModel)
                                        }
                                        scope.launch {
                                            Toast
                                                .makeText(
                                                    localContext,
                                                    stringRes(localContext, R.string.media_download_has_started_toast),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                        }
                                    } else {
                                        writeStoragePermissionState.launchPermissionRequest()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = Size5dp),
                                colors = ButtonDefaults.outlinedButtonColors().copy(containerColor = MaterialTheme.colorScheme.background),
                            ) {
                                Icon(
                                    symbol = MaterialSymbols.Download,
                                    modifier = Size20Modifier,
                                    contentDescription = stringRes(R.string.download_to_phone),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun showToastOnMain(
    context: Context,
    resId: Int,
) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context.applicationContext, resId, Toast.LENGTH_SHORT).show()
    }
}

private suspend fun saveMediaToGallery(
    content: BaseMediaContent,
    localContext: Context,
    accountViewModel: AccountViewModel,
) {
    val isImage = content is MediaUrlImage || content is MediaLocalImage

    val success = if (isImage) R.string.image_saved_to_the_gallery else R.string.video_saved_to_the_gallery
    val failure = if (isImage) R.string.failed_to_save_the_image else R.string.failed_to_save_the_video

    if (content is MediaUrlContent) {
        MediaSaverToDisk.downloadAndSave(
            content.url,
            mimeType = content.mimeType,
            okHttpClient = {
                if (isImage) {
                    accountViewModel.httpClientBuilder.okHttpClientForImage(it)
                } else {
                    accountViewModel.httpClientBuilder.okHttpClientForVideo(it)
                }
            },
            localContext,
            onSuccess = {
                showToastOnMain(localContext, success)
            },
            onError = {
                accountViewModel.toastManager.toast(failure, null, it)
            },
        )
    } else if (content is MediaPreloadedContent) {
        content.localFile?.let {
            MediaSaverToDisk.save(
                it,
                content.mimeType,
                localContext,
                onSuccess = {
                    showToastOnMain(localContext, success)
                },
                onError = { innerIt ->
                    accountViewModel.toastManager.toast(failure, null, innerIt)
                },
            )
        }
    }
}

@Composable
private fun RenderImageOrVideo(
    content: BaseMediaContent,
    roundedCorner: Boolean,
    isFiniteHeight: Boolean,
    controllerVisible: MutableState<Boolean>,
    accountViewModel: AccountViewModel,
    onContentBoundsChanged: ((Rect) -> Unit)? = null,
) {
    val contentScale =
        if (isFiniteHeight) {
            ContentScale.Fit
        } else {
            ContentScale.FillWidth
        }

    val rowModifier =
        if (onContentBoundsChanged != null) {
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { onContentBoundsChanged(it.boundsInWindow()) }
        } else {
            Modifier.fillMaxWidth()
        }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = rowModifier) {
        when (content) {
            is MediaUrlImage -> {
                val mainModifier =
                    Modifier
                        .fillMaxWidth()
                        .zoomable(
                            rememberZoomState(),
                            onTap = {
                                controllerVisible.value = !controllerVisible.value
                            },
                        )

                UrlImageView(
                    content = content,
                    contentScale = contentScale,
                    mainImageModifier = mainModifier,
                    loadedImageModifier = Modifier.fillMaxWidth(),
                    controllerVisible = controllerVisible,
                    accountViewModel = accountViewModel,
                    alwayShowImage = true,
                    fullResolution = true,
                )
            }

            is MediaUrlVideo -> {
                val borderModifier =
                    if (roundedCorner) {
                        MaterialTheme.colorScheme.imageModifier
                    } else {
                        Modifier.fillMaxWidth()
                    }

                val ratio = content.dim?.aspectRatio() ?: MediaAspectRatioCache.get(content.url)

                val modifier =
                    if (ratio != null) {
                        Modifier.aspectRatio(ratio)
                    } else {
                        Modifier
                    }

                Box(modifier, contentAlignment = Alignment.Center) {
                    VideoViewInner(
                        videoUri = content.url,
                        mimeType = content.mimeType,
                        aspectRatio = ratio,
                        title = content.description,
                        artworkUri = content.artworkUri,
                        authorName = content.authorName,
                        borderModifier = borderModifier,
                        contentScale = contentScale,
                        nostrUriCallback = content.uri,
                        automaticallyStartPlayback = true,
                        controllerVisible = controllerVisible,
                        hasBlurhash = content.blurhash != null,
                        isFullscreen = true,
                        accountViewModel = accountViewModel,
                    )
                }
            }

            is MediaLocalImage -> {
                val mainModifier =
                    Modifier
                        .fillMaxWidth()
                        .zoomable(
                            rememberZoomState(),
                            onTap = {
                                controllerVisible.value = !controllerVisible.value
                            },
                        )

                LocalImageView(
                    content = content,
                    contentScale = contentScale,
                    mainImageModifier = mainModifier,
                    loadedImageModifier = Modifier.fillMaxWidth(),
                    controllerVisible = controllerVisible,
                    accountViewModel = accountViewModel,
                    alwayShowImage = true,
                    fullResolution = true,
                )
            }

            is MediaLocalVideo -> {
                val borderModifier =
                    if (roundedCorner) {
                        MaterialTheme.colorScheme.imageModifier
                    } else {
                        Modifier.fillMaxWidth()
                    }

                content.localFile?.let {
                    val ratio = content.dim?.aspectRatio() ?: MediaAspectRatioCache.get(it.toUri().toString())

                    val modifier =
                        if (ratio != null) {
                            Modifier.aspectRatio(ratio)
                        } else {
                            Modifier
                        }

                    Box(modifier, contentAlignment = Alignment.Center) {
                        VideoViewInner(
                            videoUri = it.toUri().toString(),
                            mimeType = content.mimeType,
                            aspectRatio = ratio,
                            title = content.description,
                            artworkUri = content.artworkUri,
                            authorName = content.authorName,
                            borderModifier = borderModifier,
                            contentScale = contentScale,
                            nostrUriCallback = content.uri,
                            automaticallyStartPlayback = true,
                            controllerVisible = controllerVisible,
                            hasBlurhash = content.blurhash != null,
                            isFullscreen = true,
                            accountViewModel = accountViewModel,
                        )
                    }
                }
            }
        }
    }
}
