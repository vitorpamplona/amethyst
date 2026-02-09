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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.countToHumanReadable
import com.vitorpamplona.amethyst.service.countToHumanReadableBytes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Font12SP
import com.vitorpamplona.amethyst.ui.theme.HalfStartPadding
import com.vitorpamplona.amethyst.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.warningColor

@Composable
fun RelayStatusRow(
    item: BasicRelaySetupInfo,
    onClick: () -> Unit,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
) {
    Row(
        modifier =
            modifier.pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onClick()
                    },
                    onLongPress = {
                        accountViewModel.toastManager.toast(
                            R.string.read_from_relay,
                            R.string.read_from_relay_description,
                        )
                    },
                )
            },
    ) {
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = stringRes(R.string.read_from_relay),
            modifier = Size15Modifier,
            tint = MaterialTheme.colorScheme.allGoodColor,
        )

        Text(
            text = countToHumanReadableBytes(item.relayStat.receivedBytes),
            maxLines = 1,
            fontSize = Font12SP,
            modifier = HalfStartPadding,
            color = MaterialTheme.colorScheme.placeholderText,
        )
    }

    Row(
        modifier =
            modifier.pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onClick()
                    },
                    onLongPress = {
                        accountViewModel.toastManager.toast(
                            R.string.write_to_relay,
                            R.string.write_to_relay_description,
                        )
                    },
                )
            },
    ) {
        Icon(
            imageVector = Icons.Default.Upload,
            stringRes(R.string.write_to_relay),
            modifier = Size15Modifier,
            tint = MaterialTheme.colorScheme.allGoodColor,
        )

        Text(
            text = countToHumanReadableBytes(item.relayStat.sentBytes),
            maxLines = 1,
            fontSize = Font12SP,
            modifier = HalfStartPadding,
            color = MaterialTheme.colorScheme.placeholderText,
        )
    }

    Row(
        modifier =
            modifier.pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onClick()
                    },
                    onLongPress = {
                        accountViewModel.toastManager.toast(
                            R.string.errors,
                            R.string.errors_description,
                        )
                    },
                )
            },
    ) {
        Icon(
            imageVector = Icons.Default.SyncProblem,
            stringRes(R.string.errors),
            modifier = Size15Modifier,
            tint =
                if (item.relayStat.errorCounter > 0) {
                    MaterialTheme.colorScheme.warningColor
                } else {
                    MaterialTheme.colorScheme.allGoodColor
                },
        )

        Text(
            text = countToHumanReadable(item.relayStat.errorCounter, "errors"),
            maxLines = 1,
            fontSize = Font12SP,
            modifier = HalfStartPadding,
            color = MaterialTheme.colorScheme.placeholderText,
        )
    }

    Row(
        modifier =
            modifier.pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        onClick()
                    },
                    onLongPress = {
                        accountViewModel.toastManager.toast(
                            R.string.spam,
                            R.string.spam_description,
                        )
                    },
                )
            },
    ) {
        Icon(
            imageVector = Icons.Default.DeleteSweep,
            stringRes(R.string.spam),
            modifier = Size15Modifier,
            tint =
                if (item.relayStat.spamCounter > 0) {
                    MaterialTheme.colorScheme.warningColor
                } else {
                    MaterialTheme.colorScheme.allGoodColor
                },
        )

        Text(
            text = countToHumanReadable(item.relayStat.spamCounter, "spam"),
            maxLines = 1,
            fontSize = Font12SP,
            modifier = HalfStartPadding,
            color = MaterialTheme.colorScheme.placeholderText,
        )
    }
}
