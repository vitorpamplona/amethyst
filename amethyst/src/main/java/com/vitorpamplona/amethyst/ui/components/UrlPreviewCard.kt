/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.preview.UrlInfoItem
import com.vitorpamplona.amethyst.ui.components.util.setText
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.MaxWidthWithHorzPadding
import com.vitorpamplona.amethyst.ui.theme.innerPostModifier
import com.vitorpamplona.amethyst.ui.theme.previewCardImageModifier
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UrlPreviewCard(
    url: String,
    previewInfo: UrlInfoItem,
    onUrlComments: (() -> Unit)? = null,
    commentCount: Int = 0,
) {
    val uri = LocalUriHandler.current
    val popupExpanded =
        remember {
            mutableStateOf(false)
        }

    if (popupExpanded.value) {
        val clipboardManager = LocalClipboard.current
        val scope = rememberCoroutineScope()
        M3ActionDialog(
            title = stringRes(R.string.link_actions_dialog_title),
            onDismiss = { popupExpanded.value = false },
        ) {
            M3ActionSection {
                M3ActionRow(
                    icon = MaterialSymbols.ContentCopy,
                    text = stringRes(R.string.copy_url_to_clipboard),
                ) {
                    scope.launch {
                        clipboardManager.setText(url)
                        popupExpanded.value = false
                    }
                }
                onUrlComments?.let {
                    M3ActionRow(
                        icon = MaterialSymbols.Link,
                        text = stringRes(R.string.kind_comments),
                    ) {
                        popupExpanded.value = false
                        it()
                    }
                }
            }
        }
    }

    Column(
        modifier =
            MaterialTheme.colorScheme.innerPostModifier
                .combinedClickable(
                    onClick = {
                        runCatching { uri.openUri(url) }
                    },
                    onLongClick = {
                        popupExpanded.value = true
                    },
                ),
    ) {
        AsyncImage(
            model = previewInfo.imageUrlFullPath,
            contentDescription = previewInfo.title,
            contentScale = ContentScale.FillWidth,
            modifier = previewCardImageModifier,
        )

        Text(
            text = previewInfo.verifiedUrl?.host ?: previewInfo.url,
            style = MaterialTheme.typography.bodySmall,
            modifier = MaxWidthWithHorzPadding,
            color = Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = previewInfo.title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = MaxWidthWithHorzPadding,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = previewInfo.description,
            style = MaterialTheme.typography.bodySmall,
            modifier = MaxWidthWithHorzPadding,
            color = Color.Gray,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )

        if (commentCount > 0 && onUrlComments != null) {
            UrlCommentCountBadge(commentCount, onUrlComments)
        }

        Spacer(modifier = DoubleVertSpacer)
    }
}

/**
 * A small "N comments" affordance shown on a URL preview when the page has NIP-22
 * comments scoped to it. This is the discovery surface: it lets the user notice a
 * conversation exists and tap straight into the URL thread, without long-pressing.
 */
@Composable
private fun UrlCommentCountBadge(
    commentCount: Int,
    onUrlComments: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            MaxWidthWithHorzPadding
                .clickable(onClick = onUrlComments)
                .padding(vertical = 4.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.Forum,
            contentDescription = stringRes(R.string.kind_comments),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = "$commentCount",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
