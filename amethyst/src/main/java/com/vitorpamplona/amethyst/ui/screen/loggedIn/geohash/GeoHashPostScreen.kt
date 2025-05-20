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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.geohash

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.navigation.Nav
import com.vitorpamplona.amethyst.ui.note.nip22Comments.CommentPostViewModel
import com.vitorpamplona.amethyst.ui.note.nip22Comments.GenericCommentPostScreen
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip73ExternalIds.location.GeohashId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GeoHashPostScreen(
    geohash: String? = null,
    message: String? = null,
    attachment: Uri? = null,
    reply: Note? = null,
    quote: Note? = null,
    draft: Note? = null,
    accountViewModel: AccountViewModel,
    nav: Nav,
) {
    val postViewModel: CommentPostViewModel = viewModel()
    postViewModel.init(accountViewModel)

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        postViewModel.reloadRelaySet()
        geohash?.let {
            postViewModel.newPostFor(GeohashId(it))
        }
        reply?.let {
            postViewModel.reply(it)
        }
        draft?.let {
            postViewModel.editFromDraft(it)
        }
        quote?.let {
            postViewModel.quote(it)
        }
        message?.ifBlank { null }?.let {
            postViewModel.updateMessage(TextFieldValue(it))
        }
        attachment?.let {
            withContext(Dispatchers.IO) {
                val mediaType = context.contentResolver.getType(it)
                postViewModel.selectImage(persistentListOf(SelectedMedia(it, mediaType)))
            }
        }
    }

    GenericCommentPostScreen(postViewModel, accountViewModel, nav)
}
