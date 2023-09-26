package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.ConnectivityType
import com.vitorpamplona.amethyst.service.connectivitystatus.ConnectivityStatus
import com.vitorpamplona.amethyst.service.previews.UrlInfoItem
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.MaxWidthWithHorzPadding
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.innerPostModifier

@Composable
fun UrlPreviewCard(
    url: String,
    previewInfo: UrlInfoItem,
    accountViewModel: AccountViewModel
) {
    val automaticallyShowUrlPreview = remember {
        when (accountViewModel.account.settings.automaticallyShowUrlPreview) {
            ConnectivityType.WIFI_ONLY -> !ConnectivityStatus.isOnMobileData.value
            ConnectivityType.NEVER -> false
            ConnectivityType.ALWAYS -> true
        }
    }

    if (!automaticallyShowUrlPreview) {
        ClickableUrl(url, url)
    } else {
        val uri = LocalUriHandler.current

        Row(
            modifier = MaterialTheme.colors.innerPostModifier
                .clickable {
                    runCatching { uri.openUri(url) }
                }
        ) {
            Column {
                AsyncImage(
                    model = previewInfo.imageUrlFullPath,
                    contentDescription = stringResource(R.string.preview_card_image_for, previewInfo.url),
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = StdVertSpacer)

                Text(
                    text = previewInfo.verifiedUrl?.host ?: previewInfo.url,
                    style = MaterialTheme.typography.caption,
                    modifier = MaxWidthWithHorzPadding,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = previewInfo.title,
                    style = MaterialTheme.typography.body2,
                    modifier = MaxWidthWithHorzPadding,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = previewInfo.description,
                    style = MaterialTheme.typography.caption,
                    modifier = MaxWidthWithHorzPadding,
                    color = Color.Gray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = DoubleVertSpacer)
            }
        }
    }
}
