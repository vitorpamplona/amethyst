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

import android.Manifest
import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaLocalImage
import com.vitorpamplona.amethyst.commons.richtext.MediaLocalVideo
import com.vitorpamplona.amethyst.commons.richtext.MediaPreloadedContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.ui.actions.ImageSaver
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size15dp
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

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
                    FrameLayout.LayoutParams(
                        activityWindow.decorView.width,
                        activityWindow.decorView.height,
                    )
                view.layoutParams =
                    FrameLayout.LayoutParams(
                        activityWindow.decorView.width,
                        activityWindow.decorView.height,
                    )
            }
        }

        DisposableEffect(key1 = Unit) {
            if (Build.VERSION.SDK_INT >= 30) {
                view.windowInsetsController?.hide(
                    WindowInsets.Type.systemBars(),
                )
            }

            onDispose {
                if (Build.VERSION.SDK_INT >= 30) {
                    view.windowInsetsController?.show(
                        WindowInsets.Type.systemBars(),
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
@OptIn(ExperimentalFoundationApi::class, ExperimentalPermissionsApi::class)
private fun DialogContent(
    allImages: ImmutableList<BaseMediaContent>,
    imageUrl: BaseMediaContent,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    val pagerState: PagerState = rememberPagerState { allImages.size }
    val controllerVisible = remember { mutableStateOf(true) }

    LaunchedEffect(key1 = pagerState, key2 = imageUrl) {
        launch {
            val page = allImages.indexOf(imageUrl)
            if (page > -1) {
                pagerState.scrollToPage(page)
            }
        }
        launch(Dispatchers.Default) {
            delay(2000)
            withContext(Dispatchers.Main) {
                controllerVisible.value = false
            }
        }
    }

    if (allImages.size > 1) {
        SlidingCarousel(
            pagerState = pagerState,
        ) { index ->
            allImages.getOrNull(index)?.let {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    RenderImageOrVideo(
                        content = it,
                        roundedCorner = false,
                        isFiniteHeight = true,
                        controllerVisible = controllerVisible,
                        onControllerVisibilityChanged = { controllerVisible.value = it },
                        onToggleControllerVisibility = {
                            controllerVisible.value = !controllerVisible.value
                        },
                        accountViewModel = accountViewModel,
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
                onControllerVisibilityChanged = { controllerVisible.value = it },
                onToggleControllerVisibility = { controllerVisible.value = !controllerVisible.value },
                accountViewModel = accountViewModel,
            )
        }
    }

    AnimatedVisibility(
        visible = controllerVisible.value,
        enter = remember { fadeIn() },
        exit = remember { fadeOut() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Size15dp, vertical = Size10dp).statusBarsPadding().systemBarsPadding().fillMaxWidth(),
            horizontalArrangement = spacedBy(Size10dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = Size5dp),
                colors = ButtonDefaults.outlinedButtonColors().copy(containerColor = MaterialTheme.colorScheme.background),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            allImages.getOrNull(pagerState.currentPage)?.let { myContent ->
                val popupExpanded = remember { mutableStateOf(false) }

                OutlinedButton(
                    onClick = { popupExpanded.value = true },
                    contentPadding = PaddingValues(horizontal = Size5dp),
                    colors = ButtonDefaults.outlinedButtonColors().copy(containerColor = MaterialTheme.colorScheme.background),
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        modifier = Size20Modifier,
                        contentDescription = stringResource(R.string.quick_action_share),
                    )

                    ShareImageAction(popupExpanded = popupExpanded, myContent, onDismiss = { popupExpanded.value = false })
                }

                val localContext = LocalContext.current

                val writeStoragePermissionState =
                    rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE) { isGranted ->
                        if (isGranted) {
                            saveImage(myContent, localContext, accountViewModel)
                        }
                    }

                OutlinedButton(
                    onClick = {
                        if (
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                            writeStoragePermissionState.status.isGranted
                        ) {
                            saveImage(myContent, localContext, accountViewModel)
                        } else {
                            writeStoragePermissionState.launchPermissionRequest()
                        }
                    },
                    contentPadding = PaddingValues(horizontal = Size5dp),
                    colors = ButtonDefaults.outlinedButtonColors().copy(containerColor = MaterialTheme.colorScheme.background),
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        modifier = Size20Modifier,
                        contentDescription = stringResource(R.string.save_to_gallery),
                    )
                }
            }
        }
    }
}

private fun saveImage(
    content: BaseMediaContent,
    localContext: Context,
    accountViewModel: AccountViewModel,
) {
    if (content is MediaUrlContent) {
        ImageSaver.saveImage(
            content.url,
            localContext,
            onSuccess = {
                accountViewModel.toast(R.string.image_saved_to_the_gallery, R.string.image_saved_to_the_gallery)
            },
            onError = {
                accountViewModel.toast(R.string.failed_to_save_the_image, null, it)
            },
        )
    } else if (content is MediaPreloadedContent) {
        content.localFile?.let {
            ImageSaver.saveImage(
                it,
                content.mimeType,
                localContext,
                onSuccess = {
                    accountViewModel.toast(R.string.image_saved_to_the_gallery, R.string.image_saved_to_the_gallery)
                },
                onError = {
                    accountViewModel.toast(R.string.failed_to_save_the_image, null, it)
                },
            )
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
private fun RenderImageOrVideo(
    content: BaseMediaContent,
    roundedCorner: Boolean,
    isFiniteHeight: Boolean,
    controllerVisible: MutableState<Boolean>,
    onControllerVisibilityChanged: ((Boolean) -> Unit)? = null,
    onToggleControllerVisibility: (() -> Unit)? = null,
    accountViewModel: AccountViewModel,
) {
    val automaticallyStartPlayback = remember { mutableStateOf<Boolean>(true) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        if (content is MediaUrlImage) {
            val mainModifier =
                Modifier
                    .fillMaxWidth()
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
                isFiniteHeight = isFiniteHeight,
                controllerVisible = controllerVisible,
                accountViewModel = accountViewModel,
                alwayShowImage = true,
            )
        } else if (content is MediaUrlVideo) {
            VideoViewInner(
                videoUri = content.url,
                mimeType = content.mimeType,
                title = content.description,
                artworkUri = content.artworkUri,
                authorName = content.authorName,
                roundedCorner = roundedCorner,
                isFiniteHeight = isFiniteHeight,
                automaticallyStartPlayback = automaticallyStartPlayback,
                onControllerVisibilityChanged = onControllerVisibilityChanged,
                accountViewModel = accountViewModel,
            )
        } else if (content is MediaLocalImage) {
            val mainModifier =
                Modifier
                    .fillMaxWidth()
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
                isFiniteHeight = isFiniteHeight,
                controllerVisible = controllerVisible,
                accountViewModel = accountViewModel,
                alwayShowImage = true,
            )
        } else if (content is MediaLocalVideo) {
            content.localFile?.let {
                VideoViewInner(
                    videoUri = it.toUri().toString(),
                    mimeType = content.mimeType,
                    title = content.description,
                    artworkUri = content.artworkUri,
                    authorName = content.authorName,
                    roundedCorner = roundedCorner,
                    isFiniteHeight = isFiniteHeight,
                    automaticallyStartPlayback = automaticallyStartPlayback,
                    onControllerVisibilityChanged = onControllerVisibilityChanged,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}
