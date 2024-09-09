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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.video

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.GallerySelect
import com.vitorpamplona.amethyst.ui.actions.NewMediaModel
import com.vitorpamplona.amethyst.ui.actions.NewMediaView
import com.vitorpamplona.amethyst.ui.actions.getPhotoUri
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size55Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun NewImageButton(
    accountViewModel: AccountViewModel,
    nav: INav,
    navScrollToTop: () -> Unit,
) {
    val context = LocalContext.current

    var isOpen by remember { mutableStateOf(false) }

    var wantsToPostFromGallery by remember { mutableStateOf(false) }

    var wantsToPostFromCamera by remember { mutableStateOf(false) }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    var pickedURI by remember { mutableStateOf<Uri?>(null) }

    val scope = rememberCoroutineScope()

    val postViewModel: NewMediaModel = viewModel()
    postViewModel.onceUploaded {
        scope.launch(Dispatchers.Default) {
            delay(500)
            withContext(Dispatchers.Main) { navScrollToTop() }
        }
    }

    if (wantsToPostFromCamera) {
        val launcher =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.TakePicture(),
            ) { success ->
                if (success) {
                    cameraUri?.let {
                        pickedURI = it
                    }
                }
                cameraUri = null
                wantsToPostFromCamera = false
            }

        val cameraPermissionState =
            rememberPermissionState(
                Manifest.permission.CAMERA,
                onPermissionResult = {
                    if (it) {
                        scope.launch(Dispatchers.IO) {
                            cameraUri = getPhotoUri(context)
                            cameraUri?.let { launcher.launch(it) }
                        }
                    }
                },
            )

        if (cameraPermissionState.status.isGranted) {
            LaunchedEffect(key1 = accountViewModel) {
                launch(Dispatchers.IO) {
                    cameraUri = getPhotoUri(context)
                    cameraUri?.let { launcher.launch(it) }
                }
            }
        } else {
            LaunchedEffect(key1 = accountViewModel) { cameraPermissionState.launchPermissionRequest() }
        }
    }

    if (wantsToPostFromGallery) {
        val cameraPermissionState =
            rememberPermissionState(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                },
            )

        if (cameraPermissionState.status.isGranted) {
            var showGallerySelect by remember { mutableStateOf(false) }
            if (showGallerySelect) {
                GallerySelect(
                    onImageUri = { uri ->
                        wantsToPostFromGallery = false
                        showGallerySelect = false
                        pickedURI = uri
                    },
                )
            }

            showGallerySelect = true
        } else {
            LaunchedEffect(key1 = accountViewModel) { cameraPermissionState.launchPermissionRequest() }
        }
    }

    pickedURI?.let {
        NewMediaView(
            uri = it,
            onClose = { pickedURI = null },
            postViewModel = postViewModel,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    if (postViewModel.isUploadingImage) {
        ShowProgress(postViewModel)
    } else {
        Column {
            if (isOpen) {
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
                        contentDescription = stringRes(id = R.string.upload_image),
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

            FloatingActionButton(
                onClick = {
                    isOpen = !isOpen
                },
                modifier = Size55Modifier,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_compose),
                    contentDescription = stringRes(id = R.string.new_short),
                    modifier = Modifier.size(26.dp),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ShowProgress(postViewModel: NewMediaModel) {
    Box(Modifier.size(55.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress =
                animateFloatAsState(
                    targetValue = postViewModel.uploadingPercentage.value,
                    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                ).value,
            modifier =
                Size55Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background),
            strokeWidth = 5.dp,
        )
        postViewModel.uploadingDescription.value?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
