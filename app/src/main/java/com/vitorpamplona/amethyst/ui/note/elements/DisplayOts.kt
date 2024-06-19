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

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.note.LoadOts
import com.vitorpamplona.amethyst.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.lessImportantLink
import java.text.SimpleDateFormat
import java.util.Date

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

            ClickableText(
                text =
                    buildAnnotatedString {
                        append(
                            stringRes(
                                id = R.string.existed_since,
                                timeStr,
                            ),
                        )
                    },
                onClick = {
                    val fullDateTime =
                        SimpleDateFormat.getDateTimeInstance().format(Date(unixtimestamp * 1000))

                    accountViewModel.toast(
                        R.string.ots_info_title,
                        R.string.ots_info_description,
                        fullDateTime,
                    )
                },
                style =
                    LocalTextStyle.current.copy(
                        color = MaterialTheme.colorScheme.lessImportantLink,
                        fontSize = Font14SP,
                        fontWeight = FontWeight.Bold,
                    ),
                maxLines = 1,
            )
        },
        whenPending = {
            Text(
                stringRes(id = R.string.timestamp_pending_short),
                color = MaterialTheme.colorScheme.lessImportantLink,
                fontSize = Font14SP,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        },
    )
}
