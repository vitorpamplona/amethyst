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
package com.vitorpamplona.amethyst.service.playback.composable.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size50Modifier
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.VolumeBottomIconSize

private val FadeIn = fadeIn()
private val FadeOut = fadeOut()

@Preview
@Composable
fun OverflowMenuButtonPreview() {
    ThemeComparisonColumn {
        Box(Modifier.background(BitcoinOrange)) {
            OverflowMenuButton(
                showShare = true,
                showSave = true,
                showPip = true,
                onShareClick = {},
                onSaveClick = {},
                onPipClick = {},
            )
        }
    }
}

@Composable
fun AnimatedOverflowMenuButton(
    controllerVisible: State<Boolean>,
    modifier: Modifier = Modifier,
    showShare: Boolean = true,
    showSave: Boolean = true,
    showPip: Boolean = false,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onPipClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = controllerVisible.value,
        modifier = modifier,
        enter = FadeIn,
        exit = FadeOut,
    ) {
        OverflowMenuButton(
            showShare = showShare,
            showSave = showSave,
            showPip = showPip,
            onShareClick = onShareClick,
            onSaveClick = onSaveClick,
            onPipClick = onPipClick,
        )
    }
}

@Composable
fun OverflowMenuButton(
    showShare: Boolean,
    showSave: Boolean,
    showPip: Boolean,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onPipClick: () -> Unit,
) {
    val menuExpanded = remember { mutableStateOf(false) }

    Box(modifier = VolumeBottomIconSize) {
        Box(
            Modifier
                .clip(CircleShape)
                .fillMaxSize(0.7f)
                .align(Alignment.Center)
                .background(MaterialTheme.colorScheme.background),
        )

        IconButton(
            onClick = { menuExpanded.value = true },
            modifier = Size50Modifier,
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringRes(R.string.more_options),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Size20Modifier,
            )
        }

        DropdownMenu(
            expanded = menuExpanded.value,
            onDismissRequest = { menuExpanded.value = false },
            containerColor = Color.Black.copy(alpha = 0.85f),
        ) {
            if (showShare) {
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.share_or_save), color = Color.White) },
                    onClick = {
                        menuExpanded.value = false
                        onShareClick()
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    },
                )
            }

            if (showSave) {
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.save_to_gallery), color = Color.White) },
                    onClick = {
                        menuExpanded.value = false
                        onSaveClick()
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.SaveAlt,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    },
                )
            }

            if (showPip) {
                DropdownMenuItem(
                    text = { Text(stringRes(R.string.picture_in_picture), color = Color.White) },
                    onClick = {
                        menuExpanded.value = false
                        onPipClick()
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.PictureInPicture,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    },
                )
            }
        }
    }
}
