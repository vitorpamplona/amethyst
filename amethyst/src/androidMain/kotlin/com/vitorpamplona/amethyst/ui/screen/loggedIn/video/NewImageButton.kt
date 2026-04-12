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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.NewMediaModel
import com.vitorpamplona.amethyst.ui.actions.NewMediaView
import com.vitorpamplona.amethyst.ui.actions.uploads.GallerySelect
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.actions.uploads.TakePicture
import com.vitorpamplona.amethyst.ui.actions.uploads.TakeVideo
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.painterRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size26Modifier
import com.vitorpamplona.amethyst.ui.theme.Size55Modifier
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NewImageButton(
    accountViewModel: AccountViewModel,
    nav: INav,
    navScrollToTop: () -> Unit,
) {
    var isOpen by remember { mutableStateOf(false) }

    var wantsToPostFromGallery by remember { mutableStateOf(false) }

    var wantsToPostFromCamera by remember { mutableStateOf(false) }

    var wantsToPostFromVideo by remember { mutableStateOf(false) }

    var pickedURIs by remember { mutableStateOf<ImmutableList<SelectedMedia>>(persistentListOf()) }

    val scope = rememberCoroutineScope()

    val postViewModel: NewMediaModel = viewModel()
    postViewModel.onceUploaded {
        scope.launch(Dispatchers.IO) {
            delay(500)
            withContext(Dispatchers.Main) { navScrollToTop() }
        }
    }

    if (wantsToPostFromCamera) {
        TakePicture { uri ->
            wantsToPostFromCamera = false
            pickedURIs = uri
        }
    }

    if (wantsToPostFromVideo) {
        TakeVideo { uri ->
            wantsToPostFromVideo = false
            pickedURIs = uri
        }
    }

    if (wantsToPostFromGallery) {
        GallerySelect(
            onImageUri = { uri ->
                wantsToPostFromGallery = false
                pickedURIs = uri
            },
        )
    }

    if (pickedURIs.isNotEmpty()) {
        NewMediaView(
            uris = pickedURIs,
            onClose = { pickedURIs = persistentListOf() },
            postViewModel = postViewModel,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    Column {
        AnimatedVisibility(
            visible = isOpen,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        ) {
            Column {
                FloatingActionButton(
                    onClick = {
                        wantsToPostFromCamera = true
                        isOpen = false
                    },
                    modifier = Size55Modifier,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = stringRes(id = R.string.take_a_picture),
                        modifier = Modifier.size(26.dp),
                        tint = Color.White,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                FloatingActionButton(
                    onClick = {
                        wantsToPostFromVideo = true
                        isOpen = false
                    },
                    modifier = Size55Modifier,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = stringRes(id = R.string.record_a_video),
                        modifier = Modifier.size(26.dp),
                        tint = Color.White,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                FloatingActionButton(
                    onClick = {
                        wantsToPostFromGallery = true
                        isOpen = false
                    },
                    modifier = Size55Modifier,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = stringRes(id = R.string.upload_image),
                        modifier = Modifier.size(26.dp),
                        tint = Color.White,
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        FloatingActionButton(
            onClick = {
                isOpen = !isOpen
            },
            modifier = Size55Modifier,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
        ) {
            AnimatedVisibility(
                visible = isOpen,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringRes(id = R.string.new_short),
                    modifier = Size26Modifier,
                    tint = Color.White,
                )
            }

            AnimatedVisibility(
                visible = !isOpen,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Icon(
                    painter = painterRes(R.drawable.ic_compose, 5),
                    contentDescription = stringRes(id = R.string.new_short),
                    modifier = Size26Modifier,
                    tint = Color.White,
                )
            }
        }
    }
}
