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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip53LiveActivities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.model.LiveActivitiesChannel
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.ChannelFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.ZoomableContentView
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.home.CrossfadeCheckIfVideoIsOnline
import com.vitorpamplona.amethyst.ui.theme.StreamingHeaderModifier

@Composable
fun ShowVideoStreaming(
    baseChannel: LiveActivitiesChannel,
    accountViewModel: AccountViewModel,
) {
    baseChannel.info?.let {
        SensitivityWarning(
            event = it,
            accountViewModel = accountViewModel,
        ) {
            ChannelFinderFilterAssemblerSubscription(baseChannel, accountViewModel.dataSources().channelFinder)
            val streamingInfoEvent by
                baseChannel.live
                    .map {
                        (it.channel as? LiveActivitiesChannel)?.info
                    }.distinctUntilChanged()
                    .observeAsState(baseChannel.info)

            streamingInfoEvent?.let { event ->
                event.streaming()?.let { url ->
                    CrossfadeCheckIfVideoIsOnline(url, accountViewModel) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = StreamingHeaderModifier,
                        ) {
                            val zoomableUrlVideo =
                                remember(streamingInfoEvent) {
                                    MediaUrlVideo(
                                        url = url,
                                        description = baseChannel.toBestDisplayName(),
                                        artworkUri = event.image(),
                                        authorName = baseChannel.creatorName(),
                                        uri = baseChannel.toNAddr(),
                                    )
                                }

                            ZoomableContentView(
                                content = zoomableUrlVideo,
                                roundedCorner = false,
                                contentScale = ContentScale.FillWidth,
                                accountViewModel = accountViewModel,
                            )
                        }
                    }
                }
            }
        }
    }
}
