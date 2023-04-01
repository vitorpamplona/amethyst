package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.PagerState
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.actions.LoadingAnimation
import com.vitorpamplona.amethyst.ui.actions.SaveToGallery
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ZoomableImageView(word: String, images: List<String> = listOf(word)) {
    val clipboardManager = LocalClipboardManager.current

    // store the dialog open or close state
    var dialogOpen by remember {
        mutableStateOf(false)
    }

    // store the dialog open or close state
    var imageState by remember {
        mutableStateOf<AsyncImagePainter.State?>(null)
    }

    if (imageExtensions.any { word.endsWith(it, true) }) {
        AsyncImage(
            model = word,
            contentDescription = word,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .clip(shape = RoundedCornerShape(15.dp))
                .border(
                    1.dp,
                    MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
                    RoundedCornerShape(15.dp)
                )
                .combinedClickable(
                    onClick = { dialogOpen = true },
                    onLongClick = { clipboardManager.setText(AnnotatedString(word)) }
                ),
            onLoading = {
                imageState = it
            },
            onSuccess = {
                imageState = it
            }
        )

        if (imageState !is AsyncImagePainter.State.Success) {
            ClickableUrl(urlText = "$word ", url = word)

            val myId = "inlineContent"
            val emptytext = buildAnnotatedString {
                withStyle(
                    LocalTextStyle.current.copy(color = MaterialTheme.colors.primary).toSpanStyle()
                ) {
                    append("")
                    appendInlineContent(myId, "[icon]")
                }
            }
            val inlineContent = mapOf(
                Pair(
                    myId,
                    InlineTextContent(
                        Placeholder(
                            width = 17.sp,
                            height = 17.sp,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                        )
                    ) {
                        LoadingAnimation()
                    }
                )
            )

            // Empty Text for Size of Icon
            Text(
                text = emptytext,
                inlineContent = inlineContent
            )
        }
    } else {
        VideoView(word) { dialogOpen = true }
    }

    if (dialogOpen) {
        ZoomableImageDialog(word, images, onDismiss = { dialogOpen = false })
    }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun ZoomableImageDialog(imageUrl: String, allImages: List<String> = listOf(imageUrl), onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            Column() {
                var pagerState: PagerState = remember { PagerState() }

                Row(
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloseButton(onCancel = onDismiss)

                    SaveToGallery(url = allImages[pagerState.currentPage])
                }

                if (allImages.size > 1) {
                    SlidingCarousel(
                        pagerState = pagerState,
                        itemsCount = allImages.size,
                        itemContent = { index ->
                            RenderImageOrVideo(allImages[index])
                        }
                    )
                } else {
                    RenderImageOrVideo(imageUrl)
                }
            }
        }
    }
}

@Composable
private fun RenderImageOrVideo(imageUrl: String) {
    if (imageExtensions.any { imageUrl.endsWith(it, true) }) {
        AsyncImage(
            model = imageUrl,
            contentDescription = stringResource(id = R.string.profile_image),
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxSize()
                .zoomable(rememberZoomState())
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize(1f)) {
            VideoView(imageUrl)
        }
    }
}
