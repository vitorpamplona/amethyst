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
package com.vitorpamplona.amethyst.commons.ui.navigation.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.navigation_close_tab
import com.vitorpamplona.amethyst.commons.resources.navigation_new_tab
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.Nav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * The tabs layout's strip above the main pane: one tab per open screen, in order. A click shows the
 * tab; the cross or a middle click closes it; the plus opens a fresh search.
 */
@Composable
fun ScreenTabStrip(
    nav: Nav,
    accountViewModel: AccountViewModel,
    modifier: Modifier = Modifier,
) {
    val stacks = nav.stacks
    val activeId = stacks.top.id
    Box(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            stacks.stack.toList().forEach { entry ->
                key(entry.id) {
                    ScreenTab(
                        title = routeTitle(entry.route, accountViewModel),
                        selected = entry.id == activeId,
                        onClick = { stacks.activate(entry.id) },
                        onClose = { stacks.close(entry.id) },
                    )
                }
            }
            IconButton(onClick = { stacks.open(Route.Search(), forceNew = true) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    MaterialSymbols.Add,
                    contentDescription = stringRes(Res.string.navigation_new_tab),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun ScreenTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .width(180.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(if (selected) colors.background else colors.surfaceContainerLowest)
            .pointerInput(onClose) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isTertiaryPressed) onClose()
                    }
                }
            }.clickable(onClick = onClick)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onBackground else colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
            Icon(
                MaterialSymbols.Close,
                contentDescription = stringRes(Res.string.navigation_close_tab),
                modifier = Modifier.size(16.dp),
                tint = if (selected) colors.onBackground else colors.onSurfaceVariant,
            )
        }
    }
}
