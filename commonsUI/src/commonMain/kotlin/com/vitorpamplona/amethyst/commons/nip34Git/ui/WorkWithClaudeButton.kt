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
package com.vitorpamplona.amethyst.commons.nip34Git.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.claude_code_work_on_this
import com.vitorpamplona.amethyst.commons.ui.components.HoverTooltip
import com.vitorpamplona.amethyst.commons.ui.navigation.host.canOpen
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.Address

/**
 * Starts a Claude Code session on [repository], or on one of its issues or proposals. Only where
 * the front end can run Claude Code (the desktop app); elsewhere it draws nothing.
 */
@Composable
fun WorkWithClaudeButton(
    repository: Address,
    nav: INav,
    issueId: String? = null,
    proposalId: String? = null,
    modifier: Modifier = Modifier,
) {
    val route = Route.ClaudeCodeStart(repository.kind, repository.pubKeyHex, repository.dTag, issueId, proposalId)
    if (!canOpen(route)) return
    OutlinedButton(onClick = { nav.nav(route) }, modifier = modifier.padding(top = 8.dp)) {
        Icon(MaterialSymbols.Assistant, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringRes(Res.string.claude_code_work_on_this))
    }
}

/** [WorkWithClaudeButton] as a top-bar icon, for the repository's own screen. */
@Composable
fun WorkWithClaudeAction(
    repository: Address,
    nav: INav,
) {
    val route = Route.ClaudeCodeStart(repository.kind, repository.pubKeyHex, repository.dTag)
    if (!canOpen(route)) return
    val label = stringRes(Res.string.claude_code_work_on_this)
    HoverTooltip(label) {
        IconButton(onClick = { nav.nav(route) }) { Icon(MaterialSymbols.Assistant, contentDescription = label) }
    }
}
