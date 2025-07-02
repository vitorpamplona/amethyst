/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.WarningColor
import com.vitorpamplona.amethyst.ui.theme.allGoodColor
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RelayNameAndRemoveButton(
    item: BasicRelaySetupInfo,
    onClick: () -> Unit,
    onDelete: (BasicRelaySetupInfo) -> Unit,
    modifier: Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.relay.displayUrl(),
                modifier =
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = {
                            clipboardManager.setText(AnnotatedString(item.relay.url))
                        },
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (item.paidRelay) {
                Icon(
                    imageVector = Icons.Default.Paid,
                    null,
                    modifier =
                        Modifier
                            .padding(start = 5.dp, top = 1.dp)
                            .size(14.dp),
                    tint = MaterialTheme.colorScheme.allGoodColor,
                )
            }
        }

        IconButton(
            modifier = Modifier.size(30.dp),
            onClick = { onDelete(item) },
        ) {
            Icon(
                imageVector = Icons.Default.Cancel,
                contentDescription = stringRes(id = R.string.remove),
                modifier =
                    Modifier
                        .padding(start = 10.dp)
                        .size(15.dp),
                tint = WarningColor,
            )
        }
    }
}
