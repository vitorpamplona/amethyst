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
package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.profile.relays.MyRelayInfo
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.ButtonPadding
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdPadding
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RelayCompose(
    relay: MyRelayInfo,
    accountViewModel: AccountViewModel,
    onAddRelay: () -> Unit,
    onRemoveRelay: () -> Unit,
) {
    val context = LocalContext.current

    Row(
        modifier = StdPadding,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Size5dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = relay.url.displayUrl(),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val lastTime by
                    remember(relay.lastEvent) {
                        derivedStateOf { timeAgo(relay.lastEvent, context = context) }
                    }

                Text(
                    text = lastTime,
                    maxLines = 1,
                )
            }

            Text(
                "${relay.counter} ${stringRes(R.string.posts_received)}",
                color = MaterialTheme.colorScheme.placeholderText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(modifier = Modifier.padding(start = 10.dp)) {
            RelayOptions(accountViewModel, relay, onAddRelay, onRemoveRelay)
        }
    }
}

@Composable
private fun RelayOptions(
    accountViewModel: AccountViewModel,
    relay: MyRelayInfo,
    onAddRelay: () -> Unit,
    onRemoveRelay: () -> Unit,
) {
    val userState by accountViewModel.account.trustedRelays.flow
        .collectAsStateWithLifecycle()

    if (!userState.contains(relay.url)) {
        AddRelayButton(onAddRelay)
    } else {
        RemoveRelayButton(onRemoveRelay)
    }
}

@Composable
fun AddRelayButton(onClick: () -> Unit) {
    OutlinedButton(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = onClick,
        shape = ButtonBorder,
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringRes(id = R.string.add))
    }
}

@Composable
fun RemoveRelayButton(onClick: () -> Unit) {
    OutlinedButton(
        modifier = Modifier.padding(horizontal = 3.dp),
        onClick = onClick,
        shape = ButtonBorder,
        contentPadding = ButtonPadding,
    ) {
        Text(text = stringRes(R.string.remove))
    }
}

fun formattedDateTime(timestamp: Long): String =
    Instant
        .ofEpochSecond(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MMM d, uuuu hh:mm a"))
