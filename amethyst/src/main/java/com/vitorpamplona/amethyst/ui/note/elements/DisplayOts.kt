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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.ui.note.HeaderPill
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.note.LoadOts
import com.vitorpamplona.amethyst.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Compact pill showing the note's NIP-03 OpenTimestamps proof: how long ago
 * the note is proven to have existed. Tapping it explains the proof and shows
 * the attested date. Sits inline in note headers.
 */
@Composable
fun DisplayOts(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    LoadOts(
        note,
        accountViewModel,
        whenConfirmed = { unixtimestamp ->
            val context = LocalContext.current
            val timeStr by remember(unixtimestamp) {
                mutableStateOf(
                    timeAgoNoDot(
                        unixtimestamp,
                        context = context,
                    ),
                )
            }

            HeaderPill(
                symbol = MaterialSymbols.CheckCircle,
                text = stringRes(R.string.existed_since, timeStr),
                contentDescription = stringRes(R.string.ots_info_title),
                onClick = {
                    accountViewModel.toastManager.toast(
                        R.string.ots_info_title,
                        R.string.ots_info_description,
                        SimpleDateFormat.getDateTimeInstance().format(Date(unixtimestamp * 1000)),
                    )
                },
            )
        },
        whenPending = {
            HeaderPill(
                symbol = MaterialSymbols.Schedule,
                text = stringRes(id = R.string.timestamp_pending_short),
                contentDescription = stringRes(R.string.ots_info_title),
            )
        },
    )
}
