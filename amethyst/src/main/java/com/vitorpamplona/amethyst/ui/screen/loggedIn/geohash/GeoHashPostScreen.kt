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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.geohash

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.IAccountViewModel
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.ui.note.nip22Comments.CommentPostViewModel
import com.vitorpamplona.amethyst.ui.note.nip22Comments.GenericCommentPostScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip73ExternalIds.location.GeohashId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GeoHashPostScreen(
    geohash: String? = null,
    message: String? = null,
    attachment: String? = null,
    replyId: HexKey? = null,
    quoteId: HexKey? = null,
    draftId: HexKey? = null,
    accountViewModel: IAccountViewModel,
    nav: Nav,
) {
    @Suppress("NAME_SHADOWING")
    val accountViewModel = accountViewModel as AccountViewModel
    val postViewModel: CommentPostViewModel = viewModel()
    postViewModel.init(accountViewModel)

    val context = LocalContext.current

    LaunchedEffect(postViewModel, accountViewModel) {
        geohash?.let {
            postViewModel.newPostFor(GeohashId(it))
        }
        replyId?.let { accountViewModel.getNoteIfExists(it) }?.let {
            postViewModel.reply(it)
        }
        draftId?.let { accountViewModel.getNoteIfExists(it) }?.let {
            postViewModel.editFromDraft(it)
        }
        quoteId?.let { accountViewModel.getNoteIfExists(it) }?.let {
            postViewModel.quote(it)
        }
        message?.ifBlank { null }?.let {
            postViewModel.message.setTextAndPlaceCursorAtEnd(it)
            postViewModel.onMessageChanged()
        }
        attachment?.ifBlank { null }?.toUri()?.let {
            withContext(Dispatchers.IO) {
                val mediaType = context.contentResolver.getType(it)
                postViewModel.selectImage(persistentListOf(SelectedMedia(it, mediaType)))
            }
        }
    }

    GenericCommentPostScreen(postViewModel, accountViewModel, nav)
}
