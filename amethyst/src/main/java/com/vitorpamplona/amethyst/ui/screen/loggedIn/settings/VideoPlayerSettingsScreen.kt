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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.VideoButtonLocation
import com.vitorpamplona.amethyst.model.VideoPlayerAction
import com.vitorpamplona.amethyst.model.VideoPlayerButtonItem
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonRow

@Composable
@Preview(device = "spec:width=2100px,height=2340px,dpi=440")
fun VideoPlayerSettingsScreenPreview() {
    ThemeComparisonRow {
        VideoPlayerSettingsScreen(
            mockAccountViewModel(),
            EmptyNav(),
        )
    }
}

@Composable
fun VideoPlayerSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(id = R.string.video_player_settings), nav::popBack)
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            VideoPlayerSettingsContent(accountViewModel)
        }
    }
}

@Composable
fun VideoPlayerSettingsContent(accountViewModel: AccountViewModel) {
    val buttonItems by accountViewModel.videoPlayerButtonItemsFlow().collectAsStateWithLifecycle()
    var items by remember(buttonItems) { mutableStateOf(buttonItems.toList()) }

    fun save(newItems: List<VideoPlayerButtonItem>) {
        items = newItems.toMutableList()
        accountViewModel.changeVideoPlayerButtonItems(newItems)
    }

    var draggedItemIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<Int, Float>() }
    val isDragging = draggedItemIndex >= 0
    val scrollState = remember { ScrollState(0) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState, enabled = !isDragging),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringRes(R.string.video_player_settings_description),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp, start = Size20dp, end = Size20dp),
        )

        items.forEachIndexed { index, item ->
            val isItemDragging = draggedItemIndex == index
            val targetElevation = if (isItemDragging) 8f else 0f
            val animatedElevation by animateFloatAsState(
                targetValue = targetElevation,
                label = "dragElevation",
            )

            VideoPlayerButtonItemCard(
                item = item,
                isDragging = isItemDragging,
                dragOffsetY = if (isItemDragging) dragOffset else 0f,
                elevation = animatedElevation,
                onSelectLocation = { newLocation ->
                    if (item.location != newLocation) {
                        val newItems = items.toMutableList()
                        newItems[index] = item.copy(location = newLocation)
                        save(newItems)
                    }
                },
                onMeasured = { height ->
                    itemHeights[index] = height
                },
                onDragStart = {
                    draggedItemIndex = index
                    dragOffset = 0f
                },
                onDrag = { dragAmount ->
                    dragOffset += dragAmount

                    val currentIndex = draggedItemIndex
                    if (currentIndex < 0) return@VideoPlayerButtonItemCard

                    if (dragOffset < 0 && currentIndex > 0) {
                        val aboveHeight = itemHeights[currentIndex - 1] ?: 0f
                        if (-dragOffset > aboveHeight / 2f) {
                            val newItems = items.toMutableList()
                            val temp = newItems[currentIndex - 1]
                            newItems[currentIndex - 1] = newItems[currentIndex]
                            newItems[currentIndex] = temp
                            items = newItems

                            val h1 = itemHeights[currentIndex]
                            val h2 = itemHeights[currentIndex - 1]
                            if (h1 != null) itemHeights[currentIndex - 1] = h1
                            if (h2 != null) itemHeights[currentIndex] = h2

                            dragOffset += aboveHeight
                            draggedItemIndex = currentIndex - 1
                        }
                    }

                    if (dragOffset > 0 && currentIndex < items.lastIndex) {
                        val belowHeight = itemHeights[currentIndex + 1] ?: 0f
                        if (dragOffset > belowHeight / 2f) {
                            val newItems = items.toMutableList()
                            val temp = newItems[currentIndex + 1]
                            newItems[currentIndex + 1] = newItems[currentIndex]
                            newItems[currentIndex] = temp
                            items = newItems

                            val h1 = itemHeights[currentIndex]
                            val h2 = itemHeights[currentIndex + 1]
                            if (h1 != null) itemHeights[currentIndex + 1] = h1
                            if (h2 != null) itemHeights[currentIndex] = h2

                            dragOffset -= belowHeight
                            draggedItemIndex = currentIndex + 1
                        }
                    }
                },
                onDragEnd = {
                    draggedItemIndex = -1
                    dragOffset = 0f
                    save(items)
                },
                onDragCancel = {
                    draggedItemIndex = -1
                    dragOffset = 0f
                },
                modifier =
                    Modifier
                        .zIndex(if (isItemDragging) 1f else 0f),
            )
            if (index < items.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = Size20dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun VideoPlayerButtonItemCard(
    item: VideoPlayerButtonItem,
    isDragging: Boolean,
    dragOffsetY: Float,
    elevation: Float,
    onSelectLocation: (VideoButtonLocation) -> Unit,
    onMeasured: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionName = videoPlayerActionName(item.action)
    val actionDescription = videoPlayerActionDescription(item.action)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    onMeasured(coordinates.size.height.toFloat())
                }.graphicsLayer {
                    translationY = dragOffsetY
                    shadowElevation = elevation
                    if (isDragging) {
                        scaleX = 1.02f
                        scaleY = 1.02f
                    }
                }.padding(vertical = 8.dp, horizontal = Size20dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.y)
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragCancel() },
                    )
                },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = actionName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = actionDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Box(
                modifier = Modifier.size(32.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.DragIndicator,
                    contentDescription = stringRes(R.string.video_player_settings_reorder),
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = item.location == VideoButtonLocation.TopBar,
                onClick = { onSelectLocation(VideoButtonLocation.TopBar) },
                label = { Text(stringRes(R.string.video_player_settings_location_top_bar)) },
            )
            FilterChip(
                selected = item.location == VideoButtonLocation.OverflowMenu,
                onClick = { onSelectLocation(VideoButtonLocation.OverflowMenu) },
                label = { Text(stringRes(R.string.video_player_settings_location_overflow)) },
            )
        }
    }
}

@Composable
fun videoPlayerActionName(action: VideoPlayerAction): String =
    when (action) {
        VideoPlayerAction.Fullscreen -> stringRes(R.string.video_player_settings_action_fullscreen)
        VideoPlayerAction.Mute -> stringRes(R.string.video_player_settings_action_mute)
        VideoPlayerAction.Quality -> stringRes(R.string.video_player_settings_action_quality)
        VideoPlayerAction.Share -> stringRes(R.string.video_player_settings_action_share)
        VideoPlayerAction.Download -> stringRes(R.string.video_player_settings_action_download)
        VideoPlayerAction.PictureInPicture -> stringRes(R.string.video_player_settings_action_pip)
    }

@Composable
fun videoPlayerActionDescription(action: VideoPlayerAction): String =
    when (action) {
        VideoPlayerAction.Fullscreen -> stringRes(R.string.video_player_settings_action_fullscreen_description)
        VideoPlayerAction.Mute -> stringRes(R.string.video_player_settings_action_mute_description)
        VideoPlayerAction.Quality -> stringRes(R.string.video_player_settings_action_quality_description)
        VideoPlayerAction.Share -> stringRes(R.string.video_player_settings_action_share_description)
        VideoPlayerAction.Download -> stringRes(R.string.video_player_settings_action_download_description)
        VideoPlayerAction.PictureInPicture -> stringRes(R.string.video_player_settings_action_pip_description)
    }
