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
package com.vitorpamplona.amethyst.ui.note.elements

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.note.BaseUserPicture
import com.vitorpamplona.amethyst.ui.note.WatchAuthor
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.authorNotePictureForImageHeader

@Composable
fun DefaultImageHeader(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    WatchAuthor(baseNote = note) {
        Box {
            BannerImage(it)

            Box(authorNotePictureForImageHeader.align(Alignment.BottomStart)) {
                BaseUserPicture(it, Size55dp, accountViewModel, Modifier)
            }
        }
    }
}

@Composable
fun BannerImage(
    author: User,
    imageModifier: Modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
) {
    val currentInfo by author.live().userMetadataInfo.observeAsState()
    currentInfo?.banner?.let {
        AsyncImage(
            model = it,
            contentDescription =
                stringResource(
                    R.string.preview_card_image_for,
                    it,
                ),
            contentScale = ContentScale.Crop,
            modifier = imageModifier,
            placeholder = painterResource(R.drawable.profile_banner),
        )
    } ?: run {
        Image(
            painter = painterResource(R.drawable.profile_banner),
            contentDescription = stringResource(R.string.profile_banner),
            contentScale = ContentScale.Crop,
            modifier = imageModifier,
        )
    }
}
