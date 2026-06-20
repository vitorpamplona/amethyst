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
package com.vitorpamplona.amethyst.ui.note.elements

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.ui.components.util.setText
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.externalLinkForNote
import com.vitorpamplona.amethyst.ui.note.types.EditState
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The shared Copy & Share rows used both by the note's 3-dot
 * [NoteDropDownMenu] and by the [ShareOptionsBottomSheet] opened from the
 * reaction-row Share button. Extracted so both surfaces stay in sync — the
 * single source of truth for what "sharing a note" offers.
 *
 * The Copy rows are always shown; the three Share rows (browser link, image
 * file, image URL) are hidden for private rumors because each would publish
 * an e-tag of an unsigned note to public relays.
 */
@Composable
fun ShareCopyActionRows(
    note: Note,
    isPrivateRumor: Boolean,
    editState: State<GenericLoadable<EditState>>?,
    accountViewModel: AccountViewModel,
    nav: INav,
    onDismiss: () -> Unit,
) {
    val clipboardManager = LocalClipboard.current
    val actContext = LocalContext.current
    val scope = rememberCoroutineScope()

    M3ActionRow(icon = MaterialSymbols.ContentCopy, text = stringRes(R.string.copy_text)) {
        val lastNoteVersion = (editState?.value as? GenericLoadable.Loaded)?.loaded?.modificationToShow?.value ?: note
        accountViewModel.decrypt(lastNoteVersion) {
            scope.launch {
                clipboardManager.setText(it)
            }
        }
        onDismiss()
    }
    M3ActionRow(icon = MaterialSymbols.ContentCopy, text = stringRes(R.string.copy_user_pubkey)) {
        note.author?.let {
            scope.launch(Dispatchers.IO) {
                clipboardManager.setText("nostr:${it.pubkeyNpub()}")
                onDismiss()
            }
        }
    }
    M3ActionRow(icon = MaterialSymbols.ContentCopy, text = stringRes(R.string.copy_note_id)) {
        scope.launch(Dispatchers.IO) {
            clipboardManager.setText(note.toNostrUri())
            onDismiss()
        }
    }
    M3ActionRow(icon = MaterialSymbols.ContentCopy, text = stringRes(R.string.copy_raw_json)) {
        val event = note.event
        if (event != null) {
            scope.launch {
                val json = withContext(Dispatchers.Default) { JacksonMapper.toJsonPretty(event) }
                clipboardManager.setText(json)
                onDismiss()
            }
        } else {
            onDismiss()
        }
    }
    if (!isPrivateRumor) {
        M3ActionRow(icon = MaterialSymbols.Share, text = stringRes(R.string.quick_action_share)) {
            val sendIntent =
                Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, externalLinkForNote(note))
                    putExtra(Intent.EXTRA_TITLE, stringRes(actContext, R.string.quick_action_share_browser_link))
                }
            val shareIntent = Intent.createChooser(sendIntent, stringRes(actContext, R.string.quick_action_share))
            actContext.startActivity(shareIntent)
            onDismiss()
        }
        M3ActionRow(icon = MaterialSymbols.Image, text = stringRes(R.string.share_as_image)) {
            val shareId = if (note is AddressableNote) note.address.toValue() else note.idHex
            nav.nav(Route.ShareNoteAsImageFile(shareId))
            onDismiss()
        }
        M3ActionRow(icon = MaterialSymbols.Image, text = stringRes(R.string.share_as_image_url)) {
            val shareId = if (note is AddressableNote) note.address.toValue() else note.idHex
            nav.nav(Route.ShareNoteAsImage(shareId))
            onDismiss()
        }
    }
}
