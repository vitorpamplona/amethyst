package com.vitorpamplona.amethyst.ui.components

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Report
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.imageLoader
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.BlurHashRequester
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.actions.LoadingAnimation
import com.vitorpamplona.amethyst.ui.actions.SaveToGallery
import com.vitorpamplona.amethyst.ui.theme.Nip05
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import java.security.MessageDigest

abstract class ZoomableContent(
    val url: String,
    val description: String? = null,
    val hash: String? = null
)

class ZoomableImage(
    url: String,
    description: String? = null,
    hash: String? = null,
    val bluehash: String? = null
) : ZoomableContent(url, description, hash)

class ZoomableVideo(
    url: String,
    description: String? = null,
    hash: String? = null
) : ZoomableContent(url, description, hash)

fun figureOutMimeType(fullUrl: String): ZoomableContent {
    val removedParamsFromUrl = fullUrl.split("?")[0].lowercase()
    val isImage = imageExtensions.any { removedParamsFromUrl.endsWith(it) }
    val isVideo = videoExtensions.any { removedParamsFromUrl.endsWith(it) }

    return if (isImage) {
        ZoomableImage(fullUrl)
    } else if (isVideo) {
        ZoomableVideo(fullUrl)
    } else {
        ZoomableImage(fullUrl)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ZoomableContentView(content: ZoomableContent, images: List<ZoomableContent> = listOf(content)) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // store the dialog open or close state
    var dialogOpen by remember {
        mutableStateOf(false)
    }

    // store the dialog open or close state
    var imageState by remember {
        mutableStateOf<AsyncImagePainter.State?>(null)
    }

    var verifiedHash by remember {
        mutableStateOf<Boolean?>(null)
    }

    LaunchedEffect(key1 = content.url, key2 = imageState) {
        if (imageState is AsyncImagePainter.State.Success) {
            scope.launch(Dispatchers.IO) {
                verifiedHash = verifyHash(content, context)
            }
        }
    }

    val mainImageModifier = Modifier
        .fillMaxWidth()
        .clip(shape = RoundedCornerShape(15.dp))
        .border(
            1.dp,
            MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
            RoundedCornerShape(15.dp)
        )
        .combinedClickable(
            onClick = { dialogOpen = true },
            onLongClick = { clipboardManager.setText(AnnotatedString(content.url)) }
        )

    if (content is ZoomableImage) {
        Box() {
            AsyncImage(
                model = content.url,
                contentDescription = content.description,
                contentScale = ContentScale.FillWidth,
                modifier = mainImageModifier,
                onLoading = {
                    imageState = it
                },
                onSuccess = {
                    imageState = it
                }
            )

            if (imageState is AsyncImagePainter.State.Success) {
                HashVerificationSymbol(verifiedHash, Modifier.align(Alignment.TopEnd))
            }
        }

        if (imageState !is AsyncImagePainter.State.Success) {
            if (content.bluehash != null) {
                DisplayBlueHash(content, mainImageModifier)
            } else {
                DisplayUrlWithLoadingSymbol(content)
            }
        }
    } else {
        VideoView(content.url, content.description) { dialogOpen = true }
    }

    if (dialogOpen) {
        ZoomableImageDialog(content, images, onDismiss = { dialogOpen = false })
    }
}

@Composable
private fun DisplayUrlWithLoadingSymbol(content: ZoomableContent) {
    ClickableUrl(urlText = "${content.url} ", url = content.url)

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

@Composable
private fun DisplayBlueHash(
    content: ZoomableImage,
    modifier: Modifier
) {
    if (content.bluehash == null) return

    val context = LocalContext.current
    AsyncImage(
        model = BlurHashRequester.imageRequest(
            context,
            content.bluehash
        ),
        contentDescription = content.description,
        contentScale = ContentScale.FillWidth,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZoomableImageDialog(imageUrl: ZoomableContent, allImages: List<ZoomableContent> = listOf(imageUrl), onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            Column() {
                val pagerState: PagerState = remember { PagerState() }

                Row(
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloseButton(onCancel = onDismiss)

                    SaveToGallery(url = allImages[pagerState.currentPage].url)
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
private fun RenderImageOrVideo(content: ZoomableContent) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // store the dialog open or close state
    var imageState by remember {
        mutableStateOf<AsyncImagePainter.State?>(null)
    }

    var verifiedHash by remember {
        mutableStateOf<Boolean?>(null)
    }

    LaunchedEffect(key1 = content.url, key2 = imageState) {
        if (imageState is AsyncImagePainter.State.Success) {
            scope.launch(Dispatchers.IO) {
                verifiedHash = verifyHash(content, context)
            }
        }
    }

    if (content is ZoomableImage) {
        Box() {
            AsyncImage(
                model = content.url,
                contentDescription = content.description,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxSize()
                    .zoomable(rememberZoomState()),
                onLoading = {
                    imageState = it
                },
                onSuccess = {
                    imageState = it
                }
            )
            if (imageState !is AsyncImagePainter.State.Success) {
                DisplayBlueHash(content = content, modifier = Modifier.fillMaxWidth())
            } else {
                HashVerificationSymbol(verifiedHash, Modifier.align(Alignment.TopEnd))
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize(1f)) {
            VideoView(content.url, content.description)
        }
    }
}

@OptIn(ExperimentalCoilApi::class)
private suspend fun verifyHash(content: ZoomableContent, context: Context): Boolean? {
    if (content.hash == null) return null

    context.imageLoader.diskCache?.get(content.url)?.use { snapshot ->
        val imageFile = snapshot.data.toFile()
        val bytes = imageFile.readBytes()
        val sha256 = MessageDigest.getInstance("SHA-256")

        val hash = sha256.digest(bytes).toHexKey()

        Log.d("Image Hash Verification", "$hash == ${content.hash}")

        return hash == content.hash
    }

    return null
}

@Composable
private fun HashVerificationSymbol(verifiedHash: Boolean?, modifier: Modifier) {
    if (verifiedHash == null) return

    val localContext = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(
        modifier
            .width(40.dp)
            .height(40.dp)
            .padding(10.dp)
    ) {
        Box(
            Modifier
                .clip(CircleShape)
                .fillMaxSize(0.6f)
                .align(Alignment.Center)
                .background(MaterialTheme.colors.background)
        )

        if (verifiedHash == true) {
            IconButton(
                onClick = {
                    scope.launch {
                        Toast.makeText(
                            localContext,
                            localContext.getString(R.string.hash_verification_passed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_verified),
                    "Hash Verified",
                    tint = Nip05.copy(0.52f).compositeOver(MaterialTheme.colors.background),
                    modifier = Modifier.size(30.dp)
                )
            }
        } else if (verifiedHash == false) {
            IconButton(
                onClick = {
                    scope.launch {
                        Toast.makeText(
                            localContext,
                            localContext.getString(R.string.hash_verification_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            ) {
                Icon(
                    tint = Color.Red,
                    imageVector = Icons.Default.Report,
                    contentDescription = "Invalid Hash",
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}
