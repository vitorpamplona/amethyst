package com.vitorpamplona.amethyst.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

@Composable
fun SensitivityWarning(
    hasSensitiveContent: Boolean,
    accountViewModel: AccountViewModel,
    content: @Composable () -> Unit
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()

    var showContentWarningNote by remember(accountState) {
        mutableStateOf(accountState?.account?.showSensitiveContent != true && hasSensitiveContent)
    }

    if (showContentWarningNote) {
        ContentWarningNote() {
            showContentWarningNote = false
        }
    } else {
        content()
    }
}

@Composable
fun ContentWarningNote(onDismiss: () -> Unit) {
    Column() {
        Row(modifier = Modifier.padding(horizontal = 12.dp)) {
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Box(
                        Modifier
                            .height(80.dp)
                            .width(90.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = stringResource(R.string.content_warning),
                            modifier = Modifier
                                .size(70.dp)
                                .align(Alignment.BottomStart),
                            tint = MaterialTheme.colors.onBackground
                        )
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = stringResource(R.string.content_warning),
                            modifier = Modifier
                                .size(30.dp)
                                .align(Alignment.TopEnd),
                            tint = MaterialTheme.colors.onBackground
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text(
                        text = stringResource(R.string.content_warning),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }

                Row() {
                    Text(
                        text = stringResource(R.string.content_warning_explanation),
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 10.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Button(
                        modifier = Modifier.padding(top = 10.dp),
                        onClick = onDismiss,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults
                            .buttonColors(
                                backgroundColor = MaterialTheme.colors.primary
                            ),
                        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.show_anyway),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
