/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.layouts

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.note.elements.TimeAgo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chatlist.NewItemsBubble
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.Height4dpModifier
import com.vitorpamplona.amethyst.ui.theme.Size55Modifier
import com.vitorpamplona.quartz.utils.TimeUtils

@Composable
@Preview
fun ChannelNamePreview() {
    Column {
        ChatHeaderLayout(
            channelPicture = {
                Image(
                    painter = painterResource(R.drawable.github),
                    contentDescription = stringRes(id = R.string.profile_banner),
                    contentScale = ContentScale.FillWidth,
                )
            },
            firstRow = {
                Text("This is my author", Modifier.weight(1f))
                TimeAgo(TimeUtils.now())
            },
            secondRow = {
                Text("This is a message from this person", Modifier.weight(1f))
                NewItemsBubble()
            },
            onClick = {},
        )

        HorizontalDivider(thickness = DividerThickness)

        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("This is my author", Modifier.weight(1f))
                    TimeAgo(TimeUtils.now())
                }
            },
            supportingContent = {
                Row {
                    Text("This is a message from this person", Modifier.weight(1f))
                    NewItemsBubble()
                }
            },
            leadingContent = {
                Image(
                    painter = painterResource(R.drawable.github),
                    contentDescription = stringRes(id = R.string.profile_banner),
                    contentScale = ContentScale.FillWidth,
                    modifier = Size55Modifier,
                )
            },
        )

        HorizontalDivider(thickness = DividerThickness)
    }
}

@Composable
fun ChatHeaderLayout(
    channelPicture: @Composable () -> Unit,
    firstRow: @Composable RowScope.() -> Unit,
    secondRow: @Composable RowScope.() -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Size55Modifier) { channelPicture() }

        Spacer(modifier = DoubleHorzSpacer)

        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                firstRow()
            }

            Spacer(modifier = Height4dpModifier)

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                secondRow()
            }
        }
    }
}
