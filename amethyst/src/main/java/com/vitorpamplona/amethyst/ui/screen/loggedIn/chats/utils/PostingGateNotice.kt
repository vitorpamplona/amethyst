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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.chats.PostingGate
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon as SymbolIcon

/**
 * Takes the composer's place in any chat room this account can't post to, and says why.
 *
 * One renderer for every chat protocol: a [PostingGate.Blocked] is a [PostingGate.Blocked] whether it
 * came from a Concord banlist or a NIP-29 roster, and the user's question is the same either way.
 * Because the reason is a sealed type, adding a gate to a protocol is a compile error here until it is
 * given copy — which is what keeps a new blocked state from degrading into the silent empty slot a
 * banned Concord member used to get.
 */
@Composable
fun PostingGateNotice(
    gate: PostingGate.Blocked,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            SymbolIcon(
                symbol = gate.symbol(),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = postingGateReason(gate),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The user-facing sentence for a blocked gate, separate from [PostingGateNotice] so a screen with no
 * composer to replace (the forum's thread list, whose FAB simply disappears) can state the same reason
 * in its own empty state instead of a generic "read-only".
 */
@Composable
fun postingGateReason(gate: PostingGate.Blocked): String =
    stringRes(
        when (gate) {
            PostingGate.Banned -> R.string.chat_posting_banned
            PostingGate.Dissolved -> R.string.concord_dissolved_read_only
            PostingGate.NoKey -> R.string.chat_posting_no_key
            PostingGate.NotAMember -> R.string.relay_group_join_to_post
            PostingGate.InviteOnly -> R.string.relay_group_invite_only_to_post
        },
    )

private fun PostingGate.Blocked.symbol(): MaterialSymbol =
    when (this) {
        PostingGate.Banned -> MaterialSymbols.Block
        PostingGate.Dissolved -> MaterialSymbols.Lock
        PostingGate.NoKey -> MaterialSymbols.Key
        PostingGate.NotAMember -> MaterialSymbols.Info
        PostingGate.InviteOnly -> MaterialSymbols.Lock
    }
