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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.ui.theme.liveStreamTag
import com.vitorpamplona.quartz.utils.TimeUtils
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun LiveFlag() {
    Text(
        text = stringRes(id = R.string.live_stream_live_tag),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        modifier =
            remember {
                Modifier
                    .clip(SmallBorder)
                    .background(Color.Red)
                    .padding(horizontal = 5.dp)
            },
    )
}

@Composable
fun EndedFlag() {
    Text(
        text = stringRes(id = R.string.live_stream_ended_tag),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier =
            remember {
                Modifier
                    .clip(SmallBorder)
                    .background(Color.Black)
                    .padding(horizontal = 5.dp)
            },
    )
}

@Composable
fun OfflineFlag() {
    Text(
        text = stringRes(id = R.string.live_stream_offline_tag),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier =
            remember {
                Modifier
                    .clip(SmallBorder)
                    .background(Color.Black)
                    .padding(horizontal = 5.dp)
            },
    )
}

@Composable
fun ScheduledFlag(starts: Long?) {
    val startsIn =
        starts?.let {
            if (it > TimeUtils.now()) {
                SimpleDateFormat
                    .getDateTimeInstance(
                        DateFormat.SHORT,
                        DateFormat.SHORT,
                    ).format(Date(starts * 1000))
            } else {
                null
            }
        }

    Text(
        text = startsIn ?: stringRes(id = R.string.live_stream_planned_tag),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = liveStreamTag,
    )
}
