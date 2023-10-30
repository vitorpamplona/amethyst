package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.RelayInfo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RelayCompose(
    relay: RelayInfo,
    accountViewModel: AccountViewModel,
    onAddRelay: () -> Unit,
    onRemoveRelay: () -> Unit
) {
    val context = LocalContext.current

    Column() {
        Row(
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp, top = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        relay.url.trim().removePrefix("wss://"),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val lastTime by remember(relay.lastEvent) {
                        derivedStateOf {
                            timeAgo(relay.lastEvent, context = context)
                        }
                    }

                    Text(
                        text = lastTime,
                        maxLines = 1
                    )
                }

                Text(
                    "${relay.counter} ${stringResource(R.string.posts_received)}",
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(modifier = Modifier.padding(start = 10.dp)) {
                RelayOptions(accountViewModel, relay, onAddRelay, onRemoveRelay)
            }
        }

        Divider(
            modifier = Modifier.padding(top = 10.dp),
            thickness = DividerThickness
        )
    }
}

@Composable
private fun RelayOptions(
    accountViewModel: AccountViewModel,
    relay: RelayInfo,
    onAddRelay: () -> Unit,
    onRemoveRelay: () -> Unit
) {
    val userState by accountViewModel.userRelays.observeAsState()

    val isNotUsingRelay = remember(userState) {
        accountViewModel.account.activeRelays()?.none { it.url == relay.url } == true
    }

    if (isNotUsingRelay) {
        AddRelayButton(onAddRelay)
    } else {
        RemoveRelayButton(onRemoveRelay)
    }
}

@Composable
fun AddRelayButton(onClick: () -> Unit) {
    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = onClick,
        shape = ButtonBorder,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        contentPadding = ButtonPadding
    ) {
        Text(text = stringResource(id = R.string.add), color = Color.White)
    }
}

@Composable
fun RemoveRelayButton(onClick: () -> Unit) {
    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = onClick,
        shape = ButtonBorder,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        contentPadding = ButtonPadding
    ) {
        Text(text = stringResource(R.string.remove), color = Color.White)
    }
}

fun formattedDateTime(timestamp: Long): String {
    return Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, uuuu hh:mm a"))
}
