package com.vitorpamplona.amethyst.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.LocalImageLoader
import java.net.URLEncoder
import java.util.Base64

data class ResizeImage(val url: String?, val size: Dp)


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
  filterQuality: FilterQuality = DrawScope.DefaultFilterQuality,
) {
  if (model.url == null) {
    AsyncImage(
      model = model.url,
      contentDescription = contentDescription,
      imageLoader = LocalImageLoader.current,
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
    val imgPx = with(LocalDensity.current) { model.size.toPx().toInt() }
    val base64 = Base64.getUrlEncoder().encodeToString(model.url.toByteArray())
    val extension = model.url.split(".").lastOrNull()

    println("https://d12fidohs5rlxk.cloudfront.net/preset:sharp/rs:fit:$imgPx:$imgPx:0/gravity:sm/$base64")

    AsyncImage(
      model = "https://d12fidohs5rlxk.cloudfront.net/preset:sharp/rs:fit:$imgPx:$imgPx:0/gravity:sm/$base64",
      contentDescription = contentDescription,
      imageLoader = LocalImageLoader.current,
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