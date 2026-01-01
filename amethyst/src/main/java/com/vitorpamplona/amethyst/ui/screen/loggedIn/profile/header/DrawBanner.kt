/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.header

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserBanner
import com.vitorpamplona.amethyst.ui.components.ZoomableImageDialog
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawBanner(
    baseUser: User,
    accountViewModel: AccountViewModel,
) {
    val banner by observeUserBanner(baseUser, accountViewModel)

    DrawBanner(banner, accountViewModel)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawBanner(
    banner: String?,
    accountViewModel: AccountViewModel,
) {
    if (!banner.isNullOrBlank()) {
        val clipboardManager = LocalClipboardManager.current
        var zoomImageDialogOpen by remember { mutableStateOf(false) }

        AsyncImage(
            model = banner,
            contentDescription = stringRes(id = R.string.profile_image),
            contentScale = ContentScale.Crop,
            placeholder = painterRes(R.drawable.profile_banner, 1),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .combinedClickable(
                        onClick = { zoomImageDialogOpen = true },
                        onLongClick = { clipboardManager.setText(AnnotatedString(banner)) },
                    ),
        )

        if (zoomImageDialogOpen) {
            ZoomableImageDialog(
                imageUrl = RichTextParser.parseImageOrVideo(banner),
                onDismiss = { zoomImageDialogOpen = false },
                accountViewModel = accountViewModel,
            )
        }
    } else {
        Image(
            painter = painterRes(R.drawable.profile_banner, 2),
            contentDescription = stringRes(id = R.string.profile_banner),
            contentScale = ContentScale.FillWidth,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(150.dp),
        )
    }
}
