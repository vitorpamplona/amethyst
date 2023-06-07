package com.vitorpamplona.amethyst.ui.components

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil.annotation.ExperimentalCoilApi
import coil.compose.AsyncImage
import coil.imageLoader
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.toHexKey
import com.vitorpamplona.amethyst.service.BlurHashRequester
import com.vitorpamplona.amethyst.ui.actions.CloseButton
import com.vitorpamplona.amethyst.ui.actions.LoadingAnimation
import com.vitorpamplona.amethyst.ui.actions.SaveToGallery
import com.vitorpamplona.amethyst.ui.note.BlankNote
import com.vitorpamplona.amethyst.ui.theme.Nip05
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import java.io.File
import java.security.MessageDigest

@Immutable
abstract class ZoomableContent(
    val description: String? = null,
    val dim: String? = null
)

@Immutable
abstract class ZoomableUrlContent(
    val url: String,
    description: String? = null,
    val hash: String? = null,
    dim: String? = null,
    val uri: String? = null
) : ZoomableContent(description, dim)

@Immutable
class ZoomableUrlImage(
    url: String,
    description: String? = null,
    hash: String? = null,
    val blurhash: String? = null,
    dim: String? = null,
    uri: String? = null
) : ZoomableUrlContent(url, description, hash, dim, uri)

@Immutable
class ZoomableUrlVideo(
    url: String,
    description: String? = null,
    hash: String? = null,
    dim: String? = null,
    uri: String? = null
) : ZoomableUrlContent(url, description, hash, dim, uri)

@Immutable
abstract class ZoomablePreloadedContent(
    val localFile: File?,
    description: String? = null,
    val mimeType: String? = null,
    val isVerified: Boolean? = null,
    dim: String? = null,
    val uri: String
) : ZoomableContent(description, dim)

@Immutable
class ZoomableLocalImage(
    localFile: File?,
    mimeType: String? = null,
    description: String? = null,
    val blurhash: String? = null,
    dim: String? = null,
    isVerified: Boolean? = null,
    uri: String
) : ZoomablePreloadedContent(localFile, description, mimeType, isVerified, dim, uri)

@Immutable
class ZoomableLocalVideo(
    localFile: File?,
    mimeType: String? = null,
    description: String? = null,
    dim: String? = null,
    isVerified: Boolean? = null,
    uri: String
) : ZoomablePreloadedContent(localFile, description, mimeType, isVerified, dim, uri)

fun figureOutMimeType(fullUrl: String): ZoomableContent {
    val removedParamsFromUrl = fullUrl.split("?")[0].lowercase()
    val isImage = imageExtensions.any { removedParamsFromUrl.endsWith(it) }
    val isVideo = videoExtensions.any { removedParamsFromUrl.endsWith(it) }

    return if (isImage) {
        ZoomableUrlImage(fullUrl)
    } else if (isVideo) {
        ZoomableUrlVideo(fullUrl)
    } else {
        ZoomableUrlImage(fullUrl)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ZoomableContentView(content: ZoomableContent, images: ImmutableList<ZoomableContent> = listOf(content).toImmutableList()) {
    val clipboardManager = LocalClipboardManager.current

    // store the dialog open or close state
    var dialogOpen by remember {
        mutableStateOf(false)
    }

    var mainImageModifier = Modifier
        .fillMaxWidth()
        .clip(shape = RoundedCornerShape(15.dp))
        .border(
            1.dp,
            MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
            RoundedCornerShape(15.dp)
        )

    if (content is ZoomableUrlContent) {
        mainImageModifier = mainImageModifier.combinedClickable(
            onClick = { dialogOpen = true },
            onLongClick = { clipboardManager.setText(AnnotatedString(content.uri ?: content.url)) }
        )
    } else if (content is ZoomablePreloadedContent) {
        mainImageModifier = mainImageModifier.combinedClickable(
            onClick = { dialogOpen = true },
            onLongClick = { clipboardManager.setText(AnnotatedString(content.uri)) }
        )
    } else {
        mainImageModifier = mainImageModifier.clickable {
            dialogOpen = true
        }
    }

    when (content) {
        is ZoomableUrlImage -> UrlImageView(content, mainImageModifier)
        is ZoomableUrlVideo -> VideoView(content.url, content.description) { dialogOpen = true }
        is ZoomableLocalImage -> LocalImageView(content, mainImageModifier)
        is ZoomableLocalVideo ->
            content.localFile?.let {
                VideoView(it.toUri().toString(), content.description) { dialogOpen = true }
            }
    }

    if (dialogOpen) {
        ZoomableImageDialog(content, images, onDismiss = { dialogOpen = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalImageView(
    content: ZoomableLocalImage,
    mainImageModifier: Modifier
) {
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        // store the dialog open or close state
        var imageLoadingState by remember {
            mutableStateOf<Boolean?>(null)
        }

        val myModifier = remember {
            mainImageModifier
                .widthIn(max = maxWidth)
                .heightIn(max = maxHeight)
                .run {
                    aspectRatio(content.dim)?.let { ratio ->
                        this.aspectRatio(ratio, false)
                    } ?: this
                }
        }

        val contentScale = remember {
            if (maxHeight.isFinite) ContentScale.Fit else ContentScale.FillWidth
        }

        if (content.localFile != null && content.localFile.exists()) {
            AsyncImage(
                model = content.localFile,
                contentDescription = content.description,
                contentScale = contentScale,
                modifier = myModifier,
                onError = {
                    if (imageLoadingState != false) {
                        imageLoadingState = false
                    }
                },
                onSuccess = {
                    if (imageLoadingState != true) {
                        imageLoadingState = true
                    }
                }
            )
        }

        if (imageLoadingState == true) {
            if (content.isVerified != null) {
                HashVerificationSymbol(content.isVerified, Modifier.align(Alignment.TopEnd))
            }
        } else if (imageLoadingState == false || content.localFile == null || !content.localFile.exists()) {
            BlankNote()
        } else {
            if (content.blurhash != null) {
                DisplayBlurHash(content.blurhash, content.description, contentScale, myModifier)
            } else {
                FlowRow() {
                    DisplayUrlWithLoadingSymbol(content)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UrlImageView(
    content: ZoomableUrlImage,
    mainImageModifier: Modifier
) {
    val scope = rememberCoroutineScope()

    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val context = LocalContext.current

        // store the dialog open or close state
        var imageLoadingStatus by remember {
            mutableStateOf<Boolean?>(null)
        }

        var verifiedHash by remember {
            mutableStateOf<Boolean?>(null)
        }

        val myModifier = remember {
            mainImageModifier
                .widthIn(max = maxWidth)
                .heightIn(max = maxHeight)
                .run {
                    aspectRatio(content.dim)?.let { ratio ->
                        this.aspectRatio(ratio, false)
                    } ?: this
                }
        }

        val contentScale = remember {
            if (maxHeight.isFinite) ContentScale.Fit else ContentScale.FillWidth
        }

        AsyncImage(
            model = content.url,
            contentDescription = content.description,
            contentScale = contentScale,
            modifier = myModifier,
            onError = {
                if (imageLoadingStatus != false) {
                    imageLoadingStatus = false
                }
            },
            onSuccess = {
                if (verifiedHash == null) {
                    scope.launch(Dispatchers.IO) {
                        verifiedHash = verifyHash(content, context)
                    }
                }
                if (imageLoadingStatus != true) {
                    imageLoadingStatus = true
                }
            }
        )

        if (imageLoadingStatus == true) {
            verifiedHash?.let {
                HashVerificationSymbol(it, Modifier.align(Alignment.TopEnd))
            }
        } else if (imageLoadingStatus == false) {
            ClickableUrl(urlText = "${content.url} ", url = content.url)
        } else {
            if (content.blurhash != null) {
                DisplayBlurHash(content.blurhash, content.description, contentScale, myModifier)
            } else {
                FlowRow() {
                    DisplayUrlWithLoadingSymbol(content)
                }
            }
        }
    }
}

private fun aspectRatio(dim: String?): Float? {
    if (dim == null) return null

    val parts = dim.split("x")
    if (parts.size != 2) return null

    return try {
        val width = parts[0].toFloat()
        val height = parts[1].toFloat()
        width / height
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun DisplayUrlWithLoadingSymbol(content: ZoomableContent) {
    var cnt by remember { mutableStateOf<ZoomableContent?>(null) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            delay(200)
            cnt = content
        }
    }

    cnt?.let { DisplayUrlWithLoadingSymbolWait(it) }
}

@Composable
private fun DisplayUrlWithLoadingSymbolWait(content: ZoomableContent) {
    if (content is ZoomableUrlContent) {
        ClickableUrl(urlText = "${content.url} ", url = content.url)
    } else {
        Text("Loading content... ")
    }

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
private fun DisplayBlurHash(
    blurhash: String?,
    description: String?,
    contentScale: ContentScale,
    modifier: Modifier
) {
    if (blurhash == null) return

    val context = LocalContext.current
    AsyncImage(
        model = remember {
            BlurHashRequester.imageRequest(
                context,
                blurhash
            )
        },
        contentDescription = description,
        contentScale = contentScale,
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZoomableImageDialog(imageUrl: ZoomableContent, allImages: ImmutableList<ZoomableContent> = listOf(imageUrl).toImmutableList(), onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            Column() {
                val pagerState: PagerState = remember { PagerState() }

                LaunchedEffect(key1 = pagerState, key2 = imageUrl) {
                    val page = allImages.indexOf(imageUrl)
                    if (page > -1) {
                        pagerState.scrollToPage(page)
                    }
                }

                Row(
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloseButton(onCancel = onDismiss)

                    val myContent = allImages[pagerState.currentPage]
                    if (myContent is ZoomableUrlContent) {
                        SaveToGallery(url = myContent.url)
                    } else if (myContent is ZoomableLocalImage && myContent.localFile != null) {
                        SaveToGallery(localFile = myContent.localFile, mimeType = myContent.mimeType)
                    }
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
fun RenderImageOrVideo(content: ZoomableContent) {
    val mainModifier = Modifier
        .fillMaxSize()
        .zoomable(rememberZoomState())

    if (content is ZoomableUrlImage) {
        UrlImageView(content = content, mainImageModifier = mainModifier)
    } else if (content is ZoomableUrlVideo) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize(1f)) {
            VideoView(content.url, content.description)
        }
    } else if (content is ZoomableLocalImage) {
        LocalImageView(content = content, mainImageModifier = mainModifier)
    } else if (content is ZoomableLocalVideo) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize(1f)) {
            content.localFile?.let {
                VideoView(it.toUri().toString(), content.description)
            }
        }
    }
}

@OptIn(ExperimentalCoilApi::class)
private fun verifyHash(content: ZoomableUrlContent, context: Context): Boolean? {
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
private fun HashVerificationSymbol(verifiedHash: Boolean, modifier: Modifier) {
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
