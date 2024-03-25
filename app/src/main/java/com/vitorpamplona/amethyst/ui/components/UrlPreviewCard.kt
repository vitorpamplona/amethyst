/**
 * Copyright (c) 2024 Vitor Pamplona
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.previews.UrlInfoItem
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.MaxWidthWithHorzPadding
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.innerPostModifier

@Composable
private fun CopyToClipboard(
    popupExpanded: MutableState<Boolean>,
    content: String,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = popupExpanded.value,
        onDismissRequest = onDismiss,
    ) {
        val clipboardManager = LocalClipboardManager.current
        DropdownMenuItem(
            text = { Text(stringResource(R.string.copy_url_to_clipboard)) },
            onClick = {
                clipboardManager.setText(AnnotatedString(content))
                onDismiss()
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UrlPreviewCard(
    url: String,
    previewInfo: UrlInfoItem,
) {
    val uri = LocalUriHandler.current
    val popupExpanded =
        remember {
            mutableStateOf(false)
        }

    if (popupExpanded.value) {
        CopyToClipboard(
            popupExpanded = popupExpanded,
            content = url,
        ) {
            popupExpanded.value = false
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
            contentDescription = stringResource(R.string.preview_card_image_for, previewInfo.url),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
        )

        Spacer(modifier = StdVertSpacer)

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

        Spacer(modifier = DoubleVertSpacer)
    }
}
