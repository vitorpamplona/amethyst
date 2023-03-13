package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
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
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

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

                    Text(
                        timeAgo(relay.lastEvent, context = context),
                        maxLines = 1
                    )
                }

                Text(
                    "${relay.counter} ${stringResource(R.string.posts_received)}",
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(modifier = Modifier.padding(start = 10.dp)) {
                if (account.activeRelays()?.none { it.url == relay.url } == true) {
                    AddRelayButton { onAddRelay() }
                } else {
                    RemoveRelayButton { onRemoveRelay() }
                }
            }
        }

        Divider(
            modifier = Modifier.padding(top = 10.dp),
            thickness = 0.25.dp
        )
    }
}

@Composable
fun AddRelayButton(onClick: () -> Unit) {
    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.primary
            ),
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)
    ) {
        Text(text = stringResource(id = R.string.add), color = Color.White)
    }
}

@Composable
fun RemoveRelayButton(onClick: () -> Unit) {
    Button(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = MaterialTheme.colors.primary
            ),
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 16.dp)
    ) {
        Text(text = stringResource(R.string.remove), color = Color.White)
    }
}

fun formattedDateTime(timestamp: Long): String {
    return Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, uuuu hh:mm a"))
}
