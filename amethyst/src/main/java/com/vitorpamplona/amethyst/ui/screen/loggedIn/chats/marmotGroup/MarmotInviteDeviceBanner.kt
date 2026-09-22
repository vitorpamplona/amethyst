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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.marmotGroup

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.marmot_invite_device_banner
import com.vitorpamplona.amethyst.commons.resources.marmot_invite_device_banner_dismiss
import com.vitorpamplona.amethyst.commons.resources.marmot_invite_device_publish
import com.vitorpamplona.amethyst.model.LatestKeyPackageOwner
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Warns, on the Marmot groups screen, that another install of this account is
 * the one receiving its invites.
 *
 * This is the symptom's own screen. A user whose invites are landing on a
 * second device sees an empty group list and no new requests — exactly what
 * "nobody has invited me" looks like — so without a banner here the failure is
 * indistinguishable from nothing happening, and the diagnosis sits behind a
 * settings row nobody has a reason to open.
 *
 * Publishing stays a tap, never automatic: an inviter takes the newest
 * KeyPackage, so a banner that republished itself on sight would deadlock the
 * two installs against each other, each device's correction being the other's
 * trigger.
 */
@Composable
fun MarmotInviteDeviceBanner(
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    var owner by remember { mutableStateOf<LatestKeyPackageOwner?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        try {
            owner = withContext(Dispatchers.IO) { accountViewModel.latestKeyPackageOwner() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A relay we cannot reach is not evidence of anything, so it stays
            // silent rather than accusing a device that may well be this one.
            owner = null
        }
    }

    AnimatedVisibility(visible = owner == LatestKeyPackageOwner.OTHER_DEVICE && !dismissed) {
        Row(
            modifier =
                modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                symbol = MaterialSymbols.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringRes(Res.string.marmot_invite_device_banner),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
            )
            TextButton(
                onClick = {
                    // Hidden optimistically: the publish makes this device the
                    // newest KeyPackage, so re-asking the relays to learn what
                    // we just did would only add a round trip.
                    owner = LatestKeyPackageOwner.THIS_DEVICE
                    scope.launch(Dispatchers.IO) {
                        val successMessage = stringRes(context, R.string.marmot_invite_device_success)
                        try {
                            accountViewModel.publishMarmotKeyPackage()
                            launch(Dispatchers.Main) {
                                Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            val failureMessage =
                                stringRes(context, R.string.marmot_invite_device_failure, e.message ?: "")
                            launch(Dispatchers.Main) {
                                Toast.makeText(context, failureMessage, Toast.LENGTH_LONG).show()
                                // The warning was right after all, so put it back.
                                owner = LatestKeyPackageOwner.OTHER_DEVICE
                            }
                        }
                    }
                },
            ) {
                Text(
                    text = stringRes(Res.string.marmot_invite_device_publish),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            IconButton(onClick = { dismissed = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    symbol = MaterialSymbols.Close,
                    contentDescription = stringRes(Res.string.marmot_invite_device_banner_dismiss),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
