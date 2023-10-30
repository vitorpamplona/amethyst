package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import kotlinx.collections.immutable.ImmutableSet

@Composable
fun BlankNote(modifier: Modifier = Modifier, showDivider: Boolean = false, idHex: String? = null) {
    Column(modifier = modifier) {
        Row() {
            Column() {
                Row(
                    modifier = Modifier.padding(
                        start = 20.dp,
                        end = 20.dp,
                        bottom = 8.dp,
                        top = 15.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.post_not_found) + if (idHex != null) ": $idHex" else "",
                        modifier = Modifier.padding(30.dp),
                        color = Color.Gray
                    )
                }

                if (!showDivider) {
                    Divider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        thickness = DividerThickness
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HiddenNote(
    reports: ImmutableSet<Note>,
    isHiddenAuthor: Boolean,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
    isQuote: Boolean = false,
    nav: (String) -> Unit,
    onClick: () -> Unit
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.padding(start = if (!isQuote) 30.dp else 25.dp, end = 20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(30.dp)
            ) {
                Text(
                    text = stringResource(R.string.post_was_flagged_as_inappropriate_by),
                    color = Color.Gray
                )
                FlowRow(modifier = Modifier.padding(top = 10.dp)) {
                    if (isHiddenAuthor) {
                        UserPicture(
                            user = accountViewModel.userProfile(),
                            size = Size35dp,
                            nav = nav,
                            accountViewModel = accountViewModel
                        )
                    }
                    reports.forEach {
                        NoteAuthorPicture(
                            baseNote = it,
                            size = Size35dp,
                            nav = nav,
                            accountViewModel = accountViewModel
                        )
                    }
                }

                Button(
                    modifier = Modifier.padding(top = 10.dp),
                    onClick = onClick,
                    shape = ButtonBorder,
                    colors = ButtonDefaults
                        .buttonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                    contentPadding = ButtonPadding
                ) {
                    Text(text = stringResource(R.string.show_anyway), color = Color.White)
                }
            }
        }

        Divider(
            thickness = DividerThickness
        )
    }
}
