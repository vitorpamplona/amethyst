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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.quartz.routstr.node.RoutstrNodeEvent

/**
 * A Routstr node announcement (kind 38421): its name and about, and where it answers, read-only.
 * Nothing here contacts the node. [nav] is for the card that also offers to use the node.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun RenderRoutstrNode(
    baseNote: Note,
    nav: INav,
) {
    val event = baseNote.event as? RoutstrNodeEvent ?: return
    val facts =
        remember(event) {
            event.endpoints().map { hostOf(it.url) } + listOfNotNull(event.version())
        }

    KindCard(
        icon = MaterialSymbols.Dns,
        title = event.name() ?: kindName(event.kind),
        subtitle = event.about(),
    ) {
        KindCardMeta(facts)
    }
}

/** The host of [url]: no scheme, path or port. */
private fun hostOf(url: String): String = url.substringAfter("://").substringBefore('/').substringBefore(':')
