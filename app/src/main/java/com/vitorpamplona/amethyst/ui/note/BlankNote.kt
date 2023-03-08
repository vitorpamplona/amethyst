package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.accompanist.flowlayout.FlowRow
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User

@Composable
fun BlankNote(modifier: Modifier = Modifier, isQuote: Boolean = false) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.padding(horizontal = if (!isQuote) 12.dp else 6.dp)) {
            Column(modifier = Modifier.padding(start = if (!isQuote) 10.dp else 5.dp)) {
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
                        text = stringResource(R.string.post_not_found),
                        modifier = Modifier.padding(30.dp),
                        color = Color.Gray
                    )
                }

                Divider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    thickness = 0.25.dp
                )
            }
        }
    }
}

@Composable
fun HiddenNote(reports: Set<Note>, loggedIn: User, modifier: Modifier = Modifier, isQuote: Boolean = false, navController: NavController, onClick: () -> Unit) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.padding(horizontal = if (!isQuote) 12.dp else 6.dp)) {
            Column(modifier = Modifier.padding(start = if (!isQuote) 10.dp else 5.dp)) {
                Row(
                    modifier = Modifier.padding(
                        start = 20.dp,
                        end = 20.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(30.dp)) {
                        Text(
                            text = stringResource(R.string.post_was_flagged_as_inappropriate_by),
                            color = Color.Gray
                        )
                        FlowRow(modifier = Modifier.padding(top = 10.dp)) {
                            reports.forEach {
                                NoteAuthorPicture(
                                    note = it,
                                    navController = navController,
                                    userAccount = loggedIn,
                                    size = 35.dp
                                )
                            }
                        }

                        Button(
                            modifier = Modifier.padding(top = 10.dp),
                            onClick = onClick,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults
                                .buttonColors(
                                    backgroundColor = MaterialTheme.colors.primary
                                ),
                            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)
                        ) {
                            Text(text = stringResource(R.string.show_anyway), color = Color.White)
                        }
                    }
                }

                Divider(
                    thickness = 0.25.dp
                )
            }
        }
    }
}
