package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.baha.url.preview.UrlInfoItem

@Composable
fun UrlPreviewCard(
  url: String,
  previewInfo: UrlInfoItem
) {
  val uri = LocalUriHandler.current

  Row(
    modifier = Modifier
      .clickable { runCatching { uri.openUri(url) } }
      .clip(shape = RoundedCornerShape(15.dp))
      .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f), RoundedCornerShape(15.dp))
  ) {
    Column {
      AsyncImage(
        model = previewInfo.image,
        contentDescription = "Profile Image",
        contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth()
      )

      Text(
        text = previewInfo.title,
        style = MaterialTheme.typography.body2,
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 10.dp, end = 10.dp, top = 10.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      Text(
        text = previewInfo.description,
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