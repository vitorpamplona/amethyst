package com.vitorpamplona.amethyst.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.ImageRequest
import coil.request.Options
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import java.util.Base64

@Composable
fun RobohashAsyncImage(
    robot: String,
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

    val imageRequest = remember(robot) {
        Robohash.imageRequest(context, robot)
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

@Composable
fun RobohashFallbackAsyncImage(
    robot: String,
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
    loadProfilePicture: Boolean
) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        model = Robohash.imageRequest(context, robot)
    )

    if (model != null && loadProfilePicture) {
        val isBase64 by remember {
            derivedStateOf {
                model.startsWith("data:image/jpeg;base64,")
            }
        }

        if (isBase64) {
            val painter = rememberAsyncImagePainter(
                model = Base64Requester.imageRequest(context, model)
            )

            Image(
                painter = painter,
                contentDescription = null,
                modifier = modifier,
                alignment = alignment,
                contentScale = contentScale,
                colorFilter = colorFilter
            )
        } else {
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
                filterQuality = filterQuality
            )
        }
    } else {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = modifier,
            alignment = alignment,
            contentScale = contentScale,
            colorFilter = colorFilter
        )
    }
}

@Composable
fun RobohashAsyncImageProxy(
    robot: String,
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
    loadProfilePicture: Boolean
) {
    RobohashFallbackAsyncImage(
        robot = robot,
        model = model,
        contentDescription = contentDescription,
        modifier = modifier,
        alignment = alignment,
        contentScale = contentScale,
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = filterQuality,
        loadProfilePicture = loadProfilePicture
    )
}

object Base64Requester {
    fun imageRequest(context: Context, message: String): ImageRequest {
        return ImageRequest
            .Builder(context)
            .data(message)
            .fetcherFactory(Base64Fetcher.Factory)
            .build()
    }
}

@Stable
class Base64Fetcher(
    private val options: Options,
    private val data: Uri
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        checkNotInMainThread()

        val base64String = data.toString().removePrefix("data:image/jpeg;base64,")

        val byteArray = Base64.getDecoder().decode(base64String)
        val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)

        if (bitmap == null) {
            throw Exception("Unable to load base64 $base64String")
        }

        return DrawableResult(
            drawable = bitmap.toDrawable(options.context.resources),
            isSampled = false,
            dataSource = DataSource.MEMORY
        )
    }

    object Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher {
            return Base64Fetcher(options, data)
        }
    }
}
