package com.vitorpamplona.amethyst.ui.layouts

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.note.NewItemsBubble
import com.vitorpamplona.amethyst.ui.note.TimeAgo
import com.vitorpamplona.amethyst.ui.theme.ChatHeadlineBorders
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.Height4dpModifier
import com.vitorpamplona.amethyst.ui.theme.Size55Modifier
import com.vitorpamplona.amethyst.ui.theme.StdTopPadding
import com.vitorpamplona.quartz.utils.TimeUtils

@SuppressLint("UnrememberedMutableState")
@Composable
@Preview
fun ChannelNamePreview() {
    Column {
        ChatHeaderLayout(
            channelPicture = {
                Image(
                    painter = painterResource(R.drawable.github),
                    contentDescription = stringResource(id = R.string.profile_banner),
                    contentScale = ContentScale.FillWidth
                )
            },
            firstRow = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("This is my author", Modifier.weight(1f))
                    TimeAgo(TimeUtils.now())
                }
            },
            secondRow = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("This is a message from this person", Modifier.weight(1f))
                    NewItemsBubble()
                }
            },
            onClick = {
            }
        )

        Divider()

        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("This is my author", Modifier.weight(1f))
                    TimeAgo(TimeUtils.now())
                }
            },
            supportingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("This is a message from this person", Modifier.weight(1f))
                    NewItemsBubble()
                }
            },
            leadingContent = {
                Image(
                    painter = painterResource(R.drawable.github),
                    contentDescription = stringResource(id = R.string.profile_banner),
                    contentScale = ContentScale.FillWidth,
                    modifier = Size55Modifier
                )
            }
        )
    }
}

@Composable
fun ChatHeaderLayout(
    channelPicture: @Composable () -> Unit,
    firstRow: @Composable () -> Unit,
    secondRow: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Column(modifier = remember { Modifier.clickable(onClick = onClick) }) {
        Row(
            modifier = ChatHeadlineBorders,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Size55Modifier) {
                channelPicture()
            }

            Spacer(modifier = DoubleHorzSpacer)

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                firstRow()

                Spacer(modifier = Height4dpModifier)

                secondRow()
            }
        }

        Divider(
            modifier = StdTopPadding,
            thickness = DividerThickness
        )
    }
}
