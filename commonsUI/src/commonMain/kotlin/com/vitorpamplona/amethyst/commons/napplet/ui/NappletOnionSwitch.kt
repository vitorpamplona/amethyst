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
package com.vitorpamplona.amethyst.commons.napplet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.browser.ui.pill.OnionIcon
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tor_forced
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tor_off
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tor_on
import com.vitorpamplona.amethyst.commons.resources.browser_pill_tor_title
import com.vitorpamplona.amethyst.commons.ui.stringRes

/**
 * An nSite's own route: the onion, "Onion routing" and what it means for the site, and the switch
 * that moves the site between Tor and the open web ([onNetworkMode]; the host restarts it there).
 * [routedOverTor] is when the site chose the open web but goes through Tor anyway, because another
 * open site on the same route wants Tor. Without [showDetail] (a phone's top bar) only the onion and
 * the switch show, and the detail is what a screen reader says.
 */
@Composable
fun NappletOnionSwitch(
    useTor: Boolean,
    routedOverTor: Boolean,
    onNetworkMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showDetail: Boolean = true,
) {
    val title = stringRes(Res.string.browser_pill_tor_title)
    val detail =
        stringRes(
            when {
                useTor -> Res.string.browser_pill_tor_on
                routedOverTor -> Res.string.browser_pill_tor_forced
                else -> Res.string.browser_pill_tor_off
            },
        )
    Row(
        modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            OnionIcon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (useTor || routedOverTor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showDetail) {
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium)
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp),
                )
            }
        }
        Switch(
            checked = useTor,
            onCheckedChange = onNetworkMode,
            modifier = if (showDetail) Modifier else Modifier.semantics { contentDescription = "$title. $detail" },
        )
    }
}
