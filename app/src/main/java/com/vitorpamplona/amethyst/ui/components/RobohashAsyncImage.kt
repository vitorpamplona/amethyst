package com.vitorpamplona.amethyst.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.size.Size
import java.util.Date

@Composable
fun RobohashAsyncImage(
    robot: String,
    robotSize: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    transform: (AsyncImagePainter.State) -> AsyncImagePainter.State = AsyncImagePainter.DefaultTransform,
    onState: ((AsyncImagePainter.State) -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality
) {
    val context = LocalContext.current
    val size = with(LocalDensity.current) {
        remember {
            robotSize.roundToPx()
        }
    }

    val imageRequest = remember(robotSize, robot) {
        Robohash.imageRequest(
            context,
            robot,
            Size(size, size)
        )
    }

    AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        modifier = modifier,
        transform = transform,
        onState = onState,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = filterQuality
    )
}

var imageErrors = mapOf<String, Long>()

@Composable
fun RobohashFallbackAsyncImage(
    robot: String,
    robotSize: Dp,
    model: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality
) {
    val errorCache = remember(imageErrors) { imageErrors[model] }

    if (errorCache != null && (Date().time / 1000) - errorCache < (60 * 5)) {
        RobohashAsyncImage(
            robot = robot,
            robotSize = robotSize,
            contentDescription = contentDescription,
            modifier = modifier,
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter,
            filterQuality = filterQuality
        )
    } else {
        val context = LocalContext.current
        val painter = with(LocalDensity.current) {
            rememberAsyncImagePainter(
                model = Robohash.imageRequest(
                    context,
                    robot,
                    Size(robotSize.roundToPx(), robotSize.roundToPx())
                )
            )
        }

        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier,
            placeholder = painter,
            fallback = painter,
            error = painter,
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter,
            filterQuality = filterQuality,
            onError = {
                imageErrors = imageErrors + Pair(model, Date().time / 1000)
            }
        )
    }
}

@Composable
fun RobohashAsyncImageProxy(
    robot: String,
    model: ResizeImage,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality
) {
    val proxy = remember(model) { model.proxyUrl() }

    if (proxy == null) {
        RobohashAsyncImage(
            robot = robot,
            robotSize = model.size,
            contentDescription = contentDescription,
            modifier = modifier,
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter,
            filterQuality = filterQuality
        )
    } else {
        RobohashFallbackAsyncImage(
            robot = robot,
            robotSize = model.size,
            model = proxy,
            contentDescription = contentDescription,
            modifier = modifier,
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter,
            filterQuality = filterQuality
        )
    }
}
