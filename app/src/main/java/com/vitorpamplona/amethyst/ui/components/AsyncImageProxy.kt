package com.vitorpamplona.amethyst.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import java.util.Base64

data class ResizeImage(val url: String?, val size: Dp) {
    fun proxyUrl(): String? {
        if (url == null) return null

        // Fixes Image size to reduce pings to servers for each size used in the app
        val imgPx = 200 // with(LocalDensity.current) { model.size.toPx().toInt() }
        val base64 = Base64.getUrlEncoder().encodeToString(url.toByteArray())

        return "https://d12fidohs5rlxk.cloudfront.net/preset:sharp/rs:fit:$imgPx:$imgPx:0/gravity:sm/$base64"
    }
}

@Composable fun AsyncUserImageProxy(
  pubkeyHex: String,
  model: ResizeImage,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  alignment: Alignment = Alignment.Center,
  contentScale: ContentScale = ContentScale.Fit,
  alpha: Float = DefaultAlpha,
  colorFilter: ColorFilter? = null,
  filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
) {
  var loading by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf(false) }

  if (model.url == null || loading || error) {
    RoboHashAsyncImage(
      message = pubkeyHex,
      contentDescription = contentDescription,
      modifier = modifier,
      alignment = alignment,
      contentScale = contentScale,
      alpha = alpha,
      colorFilter = colorFilter,
      filterQuality = filterQuality,
    )
  } else {
    AsyncImage(
      model = model.proxyUrl(),
      contentDescription = contentDescription,
      modifier = modifier,
      onLoading = { loading = true },
      onSuccess = { loading = false; error = false },
      onError = { loading = false; error = true },
      alignment = alignment,
      contentScale = contentScale,
      alpha = alpha,
      colorFilter = colorFilter,
      filterQuality = filterQuality
    )
  }
}

@Composable
fun AsyncImageProxy(
    model: ResizeImage,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholder: Painter? = null,
    error: Painter? = null,
    fallback: Painter? = error,
    onLoading: ((AsyncImagePainter.State.Loading) -> Unit)? = null,
    onSuccess: ((AsyncImagePainter.State.Success) -> Unit)? = null,
    onError: ((AsyncImagePainter.State.Error) -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = DrawScope.DefaultFilterQuality
) {
    if (model.url == null) {
        AsyncImage(
            model = model.url,
            contentDescription = contentDescription,
            modifier = modifier,
            placeholder = placeholder,
            error = error,
            fallback = fallback,
            onLoading = onLoading,
            onSuccess = onSuccess,
            onError = onError,
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter,
            filterQuality = filterQuality
        )
    } else {
        AsyncImage(
            model = model.proxyUrl(),
            contentDescription = contentDescription,
            modifier = modifier,
            placeholder = placeholder,
            error = error,
            fallback = fallback,
            onLoading = onLoading,
            onSuccess = onSuccess,
            onError = onError,
            alignment = alignment,
            contentScale = contentScale,
            alpha = alpha,
            colorFilter = colorFilter,
            filterQuality = filterQuality
        )
    }
}
