package com.vitorpamplona.amethyst.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.baha.url.preview.IUrlPreviewCallback
import com.baha.url.preview.UrlInfoItem
import com.vitorpamplona.amethyst.model.UrlCachedPreviewer


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun UrlPreview(url: String, urlText: String, showUrlIfError: Boolean = true) {
  var urlPreviewState by remember { mutableStateOf<UrlPreviewState>(UrlPreviewState.Loading) }

  val uri = LocalUriHandler.current

  // Doesn't use a viewModel because of viewModel reusing issues (too many UrlPreview are created).
  LaunchedEffect(url) {
    UrlCachedPreviewer.previewInfo(url, object : IUrlPreviewCallback {
      override fun onComplete(urlInfo: UrlInfoItem) {
        if (urlInfo.allFetchComplete() && urlInfo.url == url)
          urlPreviewState = UrlPreviewState.Loaded(urlInfo)
        else
          urlPreviewState = UrlPreviewState.Empty
      }

      override fun onFailed(throwable: Throwable) {
        urlPreviewState = UrlPreviewState.Error("Error parsing preview for ${url}: ${throwable.message}")
      }
    })
  }

  Crossfade(targetState = urlPreviewState) { state ->
    when (state) {
      is UrlPreviewState.Loaded -> {
        Row(
          modifier = Modifier.clickable { runCatching { uri.openUri(url) } }
            .clip(shape = RoundedCornerShape(15.dp))
            .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f), RoundedCornerShape(15.dp))
        ) {
          Column {
            AsyncImage(
              model = state.previewInfo.image,
              contentDescription = "Profile Image",
              contentScale = ContentScale.FillWidth,
              modifier = Modifier.fillMaxWidth()
            )

            Text(
              text = state.previewInfo.title,
              style = MaterialTheme.typography.body2,
              modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top= 10.dp),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )

            Text(
              text = state.previewInfo.description,
              style = MaterialTheme.typography.caption,
              modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
              color = Color.Gray,
              maxLines = 3,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
      else -> {
        if (showUrlIfError) {
          ClickableText(
            text = AnnotatedString("$urlText "),
            onClick = { runCatching { uri.openUri(url) } },
            style = LocalTextStyle.current.copy(color = MaterialTheme.colors.primary),
          )
        }
      }
    }
  }

}