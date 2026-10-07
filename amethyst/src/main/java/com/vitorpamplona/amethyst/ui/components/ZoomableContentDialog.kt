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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.failed_to_save_the_image
import com.vitorpamplona.amethyst.commons.resources.failed_to_save_the_pdf
import com.vitorpamplona.amethyst.commons.resources.failed_to_save_the_video
import com.vitorpamplona.amethyst.commons.resources.image_saved_to_the_gallery
import com.vitorpamplona.amethyst.commons.resources.pdf_saved_to_the_gallery
import com.vitorpamplona.amethyst.commons.resources.video_saved_to_the_gallery
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaLocalImage
import com.vitorpamplona.amethyst.commons.richtext.MediaLocalVideo
import com.vitorpamplona.amethyst.commons.richtext.MediaPreloadedContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.localJavaFile
import com.vitorpamplona.amethyst.commons.ui.components.SlidingCarousel
import com.vitorpamplona.amethyst.commons.ui.components.getActivityWindow
import com.vitorpamplona.amethyst.commons.ui.components.getDialogWindow
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewerControlsVisibility
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.theme.imageModifier
import com.vitorpamplona.amethyst.commons.video.isHlsMedia
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.service.playback.composable.VideoViewInner
import com.vitorpamplona.amethyst.ui.actions.MediaSaverToDisk
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import net.engawapg.lib.zoomable.ZoomState
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

    // ZoomState of the currently-visible image, hoisted from RenderImageOrVideo so the
    // grow/shrink animation can read live scale/offset and start the exit from the
    // image's actual on-screen bounds when the user has zoomed in.
    var currentZoomState by remember { mutableStateOf<ZoomState?>(null) }

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

        // Keyed, not inline: each assignment below is a window-manager round trip, so it must run
        // on orientation changes only, not on every recomposition of this content.
        DisposableEffect(orientation, activityWindow, dialogWindow) {
            if (activityWindow != null && dialogWindow != null) {
                // Preserve what the dialog window owns: the brightness override applied by the
                // fullscreen swipe controls, and the HDR mode [RequestHdrFor] manages for the
                // image on screen. Copying the activity's instead would snap brightness back
                // mid-session and cap or force HDR by whatever the feed behind happens to show.
                val current = dialogWindow.attributes
                val attributes = WindowManager.LayoutParams()
                attributes.copyFrom(activityWindow.attributes)
                attributes.type = current.type
                // Disable the system dim so the thumbnail stays visible behind the growing dialog.
                attributes.dimAmount = 0f
                attributes.flags = attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
                attributes.screenBrightness = current.screenBrightness
                attributes.colorMode = current.colorMode
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    attributes.desiredHdrHeadroom = current.desiredHdrHeadroom
                }
                dialogWindow.attributes = attributes
            }
            onDispose {}
        }

        // Go fully immersive while the full-screen media viewer is open. Applies to full-screen
        // images and video alike (shared dialog), and matches the PDF viewer.
        ImmersiveSystemBarsEffect(dialogWindow)

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
                imageBounds = { imageBounds },
                onImageBoundsChanged = updateImageBounds,
                currentZoomState = { currentZoomState },
                onZoomStateChanged = { currentZoomState = it },
                progress = progressProvider,
                onDismiss = dismissWithAnimation,
                accountViewModel = accountViewModel,
            )
        }
    }
}

@Composable
private fun DialogContent(
    allImages: ImmutableList<BaseMediaContent>,
    imageUrl: BaseMediaContent,
    sourceBounds: Rect?,
    imageBounds: () -> Rect?,
    onImageBoundsChanged: (Rect) -> Unit,
    currentZoomState: () -> ZoomState?,
    onZoomStateChanged: (ZoomState) -> Unit,
    progress: () -> Float,
    onDismiss: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    val pagerState: PagerState = rememberPagerState { allImages.size }

    // The media must be measured in the transformed layer's own (pre-transform) space.
    // boundsInWindow() of anything inside the layer already includes the grow transform,
    // and every transform change re-fires the media's onGloballyPositioned. Measured
    // that way, the bounds read right after the first transform is applied are the
    // thumbnail-sized ones, so the next frame computes a start scale of ~1: the image
    // pops to full size and only the clip window animates.
    val layerSpace = remember { LayerSpace() }
    val onContentPositioned: (LayoutCoordinates, Float?) -> Unit = { coordinates, aspectRatio ->
        layerSpace.untransformedBounds(coordinates)?.let { onImageBoundsChanged(it.fitAspectRatio(aspectRatio)) }
    }

    val sharePopupExpanded = remember { mutableStateOf(false) }
    val controllerVisible = rememberViewerControlsVisibility(holdOpen = sharePopupExpanded.value)

    LaunchedEffect(key1 = pagerState, key2 = imageUrl) {
        val page = allImages.indexOf(imageUrl)
        if (page > -1) {
            pagerState.scrollToPage(page)
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
            ).onPlaced { layerSpace.parent = it },
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
                        val img = imageBounds()
                        if (src != null && img != null && src.hasArea() && img.hasArea()) {
                            // Account for user-applied zoom: the exit animation must start from
                            // the visible bounds, not the unzoomed layout bounds — otherwise
                            // dismissing a zoomed-in image jumps.
                            val zoomed = img.zoomedBy(currentZoomState())
                            // Uniform scale so non-square images keep their aspect ratio during
                            // the grow animation. The image covers the source rect; the overflow
                            // is clipped below.
                            val startScale = coverScale(src, zoomed)
                            val p = progress()
                            val scale = lerp(startScale, 1f, p)
                            val tx = lerp(src.center.x - startScale * zoomed.center.x, 0f, p)
                            val ty = lerp(src.center.y - startScale * zoomed.center.y, 0f, p)

                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = scale
                            scaleY = scale
                            translationX = tx
                            translationY = ty

                            if (p < 1f) {
                                // The thumbnail may be a crop of the image (e.g. a square gallery
                                // cell showing a 4:3 photo). Clip to a window that morphs from the
                                // thumbnail's rect into the whole viewport, so the transition opens
                                // from and closes into exactly what was on screen. The viewport (not
                                // the measured image) is the end state so there is nothing to snap
                                // when the clip turns off at p = 1, where pager neighbours must show.
                                // Window coordinates, mapped back into this layer's pre-transform
                                // space. A layer outline, not a draw-phase clip, so animating it
                                // doesn't re-record the pager's display list every frame.
                                shape =
                                    RectClipShape(
                                        Rect(
                                            left = (lerp(src.left, 0f, p) - tx) / scale,
                                            top = (lerp(src.top, 0f, p) - ty) / scale,
                                            right = (lerp(src.right, size.width, p) - tx) / scale,
                                            bottom = (lerp(src.bottom, size.height, p) - ty) / scale,
                                        ),
                                    )
                                clip = true
                            }
                        } else {
                            // No source bounds: fall back to a plain fade.
                            alpha = progress()
                        }
                    }
                    // After the graphicsLayer: these coordinates live inside the layer.
                    .onPlaced { layerSpace.layer = it },
        ) {
            if (allImages.size > 1) {
                SlidingCarousel(
                    pagerState = pagerState,
                ) { index ->
                    allImages.getOrNull(index)?.let { pageContent ->
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val isCurrent = index == pagerState.currentPage
                            RenderImageOrVideo(
                                content = pageContent,
                                roundedCorner = false,
                                isFiniteHeight = true,
                                controllerVisible = controllerVisible,
                                accountViewModel = accountViewModel,
                                onContentPositioned =
                                    if (isCurrent) onContentPositioned else null,
                                onZoomStateChanged =
                                    if (isCurrent) onZoomStateChanged else null,
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
                        onContentPositioned = onContentPositioned,
                        onZoomStateChanged = onZoomStateChanged,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controllerVisible.value,
            enter = fadeIn(),
            exit = fadeOut(),
            // Also fade with the grow animation so controls appear/disappear alongside it.
            modifier = Modifier.graphicsLayer { alpha = progress().coerceIn(0f, 1f) },
        ) {
            ViewerControlsRow {
                ViewerBackButton(onDismiss)

                Spacer(modifier = Modifier.weight(1f))

                allImages.getOrNull(pagerState.currentPage)?.let { myContent ->
                    if (myContent is MediaUrlImage || myContent is MediaLocalImage) {
                        ViewerShareButton(myContent, sharePopupExpanded, accountViewModel)
                    }

                    val isPdfOrStaticImage = myContent is MediaUrlImage || myContent is MediaLocalImage || myContent is MediaUrlPdf
                    val isNotLiveStream = myContent !is MediaUrlContent || !isHlsMedia(myContent.url, myContent.mimeType)
                    if (isPdfOrStaticImage && isNotLiveStream) {
                        ViewerSaveToGalleryButton(myContent, accountViewModel)
                    }
                }
            }
        }
    }
}

// Takes the resolved text, not the catalog entry: MediaSaverToDisk's onSuccess is a
// plain callback, and the catalog's only non-composable accessor suspends.
private fun showToastOnMain(
    context: Context,
    text: String,
) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
    }
}

internal suspend fun saveMediaToGallery(
    content: BaseMediaContent,
    localContext: Context,
    accountViewModel: AccountViewModel,
) {
    val isImage = content is MediaUrlImage || content is MediaLocalImage
    val isPdf = content is MediaUrlPdf

    val success =
        when {
            isImage -> Res.string.image_saved_to_the_gallery
            isPdf -> Res.string.pdf_saved_to_the_gallery
            else -> Res.string.video_saved_to_the_gallery
        }
    val failure =
        when {
            isImage -> Res.string.failed_to_save_the_image
            isPdf -> Res.string.failed_to_save_the_pdf
            else -> Res.string.failed_to_save_the_video
        }
    val successText = loadStringRes(success)

    if (content is MediaUrlContent) {
        MediaSaverToDisk.downloadAndSave(
            content.url,
            mimeType = content.mimeType,
            okHttpClient = {
                if (isImage || isPdf) {
                    accountViewModel.httpClientBuilder.okHttpClientForImage(it)
                } else {
                    accountViewModel.httpClientBuilder.okHttpClientForVideo(it)
                }
            },
            localContext,
            resolveBlossom = {
                Amethyst.instance.blossomResolver
                    .findServers(it)
                    ?.serverUrl
            },
            onSuccess = {
                showToastOnMain(localContext, successText)
            },
            onError = {
                accountViewModel.toastManager.toast(failure, null, it)
            },
        )
    } else if (content is MediaPreloadedContent) {
        content.localJavaFile?.let {
            MediaSaverToDisk.save(
                it,
                content.mimeType,
                localContext,
                onSuccess = {
                    showToastOnMain(localContext, successText)
                },
                onError = { innerIt ->
                    accountViewModel.toastManager.toast(failure, null, innerIt)
                },
            )
        }
    }
}

private fun Rect.hasArea() = width > 0f && height > 0f

/**
 * Maps the media's coordinates to window space as if the grow/shrink transform
 * were not applied. [parent] is the untransformed container; [layer] is the
 * fill-size box inside the animated graphicsLayer, sitting at the parent's origin.
 */
private class LayerSpace {
    var parent: LayoutCoordinates? = null
    var layer: LayoutCoordinates? = null

    fun untransformedBounds(content: LayoutCoordinates): Rect? {
        val parent = parent ?: return null
        val layer = layer ?: return null
        if (!parent.isAttached || !layer.isAttached || !content.isAttached) return null
        // Unclipped: the layer clips to the morphing thumbnail window while animating.
        return layer.localBoundingBoxOf(content, clipBounds = false).translate(parent.positionInWindow())
    }
}

/**
 * The rect a [ContentScale.Fit] image of [aspectRatio] actually paints inside this
 * layout rect. The media's layout fills the width, so a tall image is pillarboxed
 * inside it; scaling the layout rect onto the thumbnail would leave it at ~1x.
 */
private fun Rect.fitAspectRatio(aspectRatio: Float?): Rect {
    if (aspectRatio == null || aspectRatio <= 0f || !hasArea()) return this
    return if (width / height > aspectRatio) {
        val fittedWidth = height * aspectRatio
        Rect(center.x - fittedWidth / 2f, top, center.x + fittedWidth / 2f, bottom)
    } else {
        val fittedHeight = width / aspectRatio
        Rect(left, center.y - fittedHeight / 2f, right, center.y + fittedHeight / 2f)
    }
}

/**
 * The image's on-screen bounds after the user's pinch zoom: the zoomable scales
 * uniformly around the layout center, then offsets.
 */
private fun Rect.zoomedBy(zoom: ZoomState?): Rect {
    val zScale = zoom?.scale ?: 1f
    val halfWidth = width * zScale / 2f
    val halfHeight = height * zScale / 2f
    val centerX = center.x + (zoom?.offsetX ?: 0f)
    val centerY = center.y + (zoom?.offsetY ?: 0f)
    return Rect(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight)
}

/** Uniform scale at which [image] covers [source] in both dimensions. */
private fun coverScale(
    source: Rect,
    image: Rect,
): Float = maxOf(source.width / image.width, source.height / image.height)

/** Clips a layer to a fixed [rect] in its own coordinates, regardless of the layer's size. */
private class RectClipShape(
    private val rect: Rect,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rectangle(rect)
}

private fun BaseMediaContent.aspectRatioOrNull(): Float? =
    dim?.aspectRatioOrNull()
        ?: when (this) {
            is MediaUrlContent -> MediaAspectRatioCache.get(url)
            is MediaPreloadedContent -> localJavaFile?.let { MediaAspectRatioCache.get(it.toUri().toString()) }
            else -> null
        }

@Composable
private fun RenderImageOrVideo(
    content: BaseMediaContent,
    roundedCorner: Boolean,
    isFiniteHeight: Boolean,
    controllerVisible: MutableState<Boolean>,
    accountViewModel: AccountViewModel,
    onContentPositioned: ((LayoutCoordinates, aspectRatio: Float?) -> Unit)? = null,
    onZoomStateChanged: ((ZoomState) -> Unit)? = null,
) {
    val contentScale =
        if (isFiniteHeight) {
            ContentScale.Fit
        } else {
            ContentScale.FillWidth
        }

    val rowModifier =
        if (onContentPositioned != null) {
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { onContentPositioned(it, content.aspectRatioOrNull()) }
        } else {
            Modifier.fillMaxWidth()
        }

    // Hoist ZoomState so the parent dialog can read live scale/offset and start the
    // exit animation from the image's actual on-screen bounds when zoomed in.
    val zoomState = rememberZoomState()
    LaunchedEffect(zoomState, onZoomStateChanged) {
        onZoomStateChanged?.invoke(zoomState)
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = rowModifier) {
        when (content) {
            is MediaUrlImage -> {
                val mainModifier =
                    Modifier
                        .fillMaxWidth()
                        .zoomable(
                            zoomState,
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

                val ratio = content.dim?.aspectRatioOrNull() ?: MediaAspectRatioCache.get(content.url)
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
                        captions = content.captions,
                        accountViewModel = accountViewModel,
                    )
                }
            }

            is MediaLocalImage -> {
                val mainModifier =
                    Modifier
                        .fillMaxWidth()
                        .zoomable(
                            zoomState,
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

                content.localJavaFile?.let {
                    val ratio = content.dim?.aspectRatioOrNull() ?: MediaAspectRatioCache.get(it.toUri().toString())

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
