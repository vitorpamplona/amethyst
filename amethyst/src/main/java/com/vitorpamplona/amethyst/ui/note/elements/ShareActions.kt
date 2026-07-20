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
import androidx.compose.ui.platform.LocalContext
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.M3ActionRow
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.externalLinkForNote
import com.vitorpamplona.amethyst.ui.stringRes

/**
 * The shared Share rows used by the [ShareOptionsBottomSheet] drawer (opened
 * both from the reaction-row Share button and from the note's 3-dot menu).
 *
 * Only the true "send it somewhere" options live here — browser link, image
 * file, image URL, and the display-only QR code. The copy-to-clipboard
 * options stay in the 3-dot menu, so they are intentionally NOT part of this
 * shared element.
 *
 * Callers only render these for non-private notes: every option exposes the
 * note publicly (a shareable web link, or an image of it), which must never
 * happen for a private gift-wrapped rumor.
 */
@Composable
fun ShareActionRows(
    note: Note,
    nav: INav,
    onDismiss: () -> Unit,
) {
    val actContext = LocalContext.current
    // AddressableNotes are shared by their replaceable address; everything else
    // by event id. The two image routes resolve the note from this same id.
    val shareId = if (note is AddressableNote) note.address.toValue() else note.idHex

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
        nav.nav(Route.ShareNoteAsImageFile(shareId))
        onDismiss()
    }
    M3ActionRow(icon = MaterialSymbols.Image, text = stringRes(R.string.share_as_image_url)) {
        nav.nav(Route.ShareNoteAsImage(shareId))
        onDismiss()
    }
    M3ActionRow(icon = MaterialSymbols.QrCode2, text = stringRes(R.string.share_as_qr)) {
        nav.nav(Route.ShareNoteAsQr(shareId))
        onDismiss()
    }
}
