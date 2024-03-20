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
package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

@Composable
@Preview
fun BlankNotePreview() {
    ThemeComparisonColumn { BlankNote() }
}

@Composable
fun BlankNote(
    modifier: Modifier = Modifier,
    idHex: String? = null,
) {
    Column(modifier = modifier) {
        Row {
            Column {
                Row(
                    modifier =
                        Modifier.padding(
                            start = 20.dp,
                            end = 20.dp,
                            bottom = 8.dp,
                            top = 8.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.post_not_found) + if (idHex != null) ": $idHex" else "",
                        modifier = Modifier.padding(30.dp),
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
@Preview
fun HiddenNotePreview() {
    val accountViewModel = mockAccountViewModel()
    val nav: (String) -> Unit = {}

    ThemeComparisonColumn(
        toPreview = {
            HiddenNote(
                reports = persistentSetOf<Note>(),
                isHiddenAuthor = true,
                accountViewModel = accountViewModel,
                nav = nav,
            ) {}
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HiddenNote(
    reports: ImmutableSet<Note>,
    isHiddenAuthor: Boolean,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    nav: (String) -> Unit,
    onClick: () -> Unit,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(30.dp),
            ) {
                Text(
                    text = stringResource(R.string.post_was_flagged_as_inappropriate_by),
                    color = Color.Gray,
                )
                FlowRow(modifier = Modifier.padding(top = 10.dp)) {
                    if (isHiddenAuthor) {
                        UserPicture(
                            user = accountViewModel.userProfile(),
                            size = Size35dp,
                            nav = nav,
                            accountViewModel = accountViewModel,
                        )
                    }
                    reports.forEach {
                        NoteAuthorPicture(
                            baseNote = it,
                            size = Size35dp,
                            nav = nav,
                            accountViewModel = accountViewModel,
                        )
                    }
                }

                Button(
                    modifier = Modifier.padding(top = 10.dp),
                    onClick = onClick,
                    shape = ButtonBorder,
                    colors =
                        ButtonDefaults.buttonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    contentPadding = ButtonPadding,
                ) {
                    Text(text = stringResource(R.string.show_anyway), color = Color.White)
                }
            }
        }
    }
}

@Preview
@Composable
fun HiddenNoteByMePreview() {
    ThemeComparisonColumn(
        toPreview = { HiddenNoteByMe {} },
    )
}

@Composable
fun HiddenNoteByMe(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(30.dp),
            ) {
                Text(
                    text = stringResource(R.string.post_was_hidden),
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                )

                Button(
                    modifier = Modifier.padding(top = 10.dp),
                    onClick = onClick,
                    shape = ButtonBorder,
                    colors =
                        ButtonDefaults.buttonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    contentPadding = ButtonPadding,
                ) {
                    Text(text = stringResource(R.string.show_anyway), color = Color.White)
                }
            }
        }
    }
}
