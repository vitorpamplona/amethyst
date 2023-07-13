package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.connectivitystatus.ConnectivityStatus
import com.vitorpamplona.amethyst.service.previews.UrlInfoItem
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.subtleBorder

@Composable
fun UrlPreviewCard(
    url: String,
    previewInfo: UrlInfoItem,
    accountViewModel: AccountViewModel
) {
    val settings = accountViewModel.account.settings
    val isMobile = ConnectivityStatus.isOnMobileData.value

    val automaticallyShowUrlPreview = when (settings.automaticallyShowUrlPreview) {
        true -> !isMobile
        false -> false
        else -> true
    }

    if (!automaticallyShowUrlPreview) {
        ClickableUrl(url, url)
    } else {
        val uri = LocalUriHandler.current

        Row(
            modifier = Modifier
                .clickable { runCatching { uri.openUri(url) } }
                .clip(shape = QuoteBorder)
                .border(
                    1.dp,
                    MaterialTheme.colors.subtleBorder,
                    QuoteBorder
                )
        ) {
            Column {
                AsyncImage(
                    model = previewInfo.imageUrlFullPath,
                    contentDescription = stringResource(R.string.preview_card_image_for, previewInfo.url),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = previewInfo.verifiedUrl?.host ?: previewInfo.url,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp),
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = previewInfo.title,
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp),
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
}
