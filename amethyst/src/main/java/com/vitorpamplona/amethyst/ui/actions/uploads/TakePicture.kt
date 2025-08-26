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
package com.vitorpamplona.amethyst.ui.actions.uploads

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TakePictureButton(onPictureTaken: (ImmutableList<SelectedMedia>) -> Unit) {
    var showSelection by remember { mutableStateOf(false) }

    if (showSelection) {
        CameraSelectionDialog(
            onDismiss = { showSelection = false },
            onMediaTaken = { media ->
                showSelection = false
                onPictureTaken(media)
            },
        )
    }

    PictureButton { showSelection = true }
}

@Composable
fun CameraSelectionDialog(
    onDismiss: () -> Unit,
    onMediaTaken: (ImmutableList<SelectedMedia>) -> Unit,
) {
    var showPicture by remember { mutableStateOf(false) }
    var showVideo by remember { mutableStateOf(false) }

    if (showPicture) {
        TakePicture(
            onPictureTaken = { media ->
                showPicture = false
                onDismiss()
                onMediaTaken(media)
            },
        )
    }

    if (showVideo) {
        TakeVideo(
            onVideoTaken = { media ->
                showVideo = false
                onDismiss()
                onMediaTaken(media)
            },
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringRes(R.string.select_media_type),
                    style = MaterialTheme.typography.headlineSmall,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = { showPicture = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                            )
                            Text(
                                text = stringRes(R.string.photo),
                                maxLines = 1,
                            )
                        }
                    }

                    Button(
                        onClick = { showVideo = true },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                            )
                            Text(
                                text = stringRes(R.string.video),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun TakePicture(onPictureTaken: (ImmutableList<SelectedMedia>) -> Unit) {
    val context = LocalContext.current
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture(),
        ) { success ->
            if (success) {
                cameraUri?.let {
                    onPictureTaken(persistentListOf(SelectedMedia(it, "image/jpeg")))
                }
            } else {
                onPictureTaken(persistentListOf())
            }
            cameraUri = null
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
        LaunchedEffect(key1 = Unit) {
            launch(Dispatchers.IO) {
                cameraUri = getPhotoUri(context)
                cameraUri?.let { launcher.launch(it) }
            }
        }
    } else {
        LaunchedEffect(key1 = Unit) {
            cameraPermissionState.launchPermissionRequest()
        }
    }
}

@Composable
fun PictureButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = stringRes(id = R.string.take_a_picture),
            modifier = Modifier.height(22.dp),
            tint = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun TakeVideo(onVideoTaken: (ImmutableList<SelectedMedia>) -> Unit) {
    val context = LocalContext.current
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    val scope = rememberCoroutineScope()

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CaptureVideo(),
        ) { success ->
            if (success) {
                videoUri?.let {
                    onVideoTaken(persistentListOf(SelectedMedia(it, "video/mp4")))
                }
            } else {
                onVideoTaken(persistentListOf())
            }
            videoUri = null
        }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    LaunchedEffect(cameraPermissionState.status.isGranted, audioPermissionState.status.isGranted) {
        when {
            cameraPermissionState.status.isGranted && audioPermissionState.status.isGranted -> {
                scope.launch(Dispatchers.IO) {
                    videoUri = getVideoUri(context)
                    videoUri?.let { launcher.launch(it) }
                }
            }
            !cameraPermissionState.status.isGranted -> {
                cameraPermissionState.launchPermissionRequest()
            }
            !audioPermissionState.status.isGranted -> {
                audioPermissionState.launchPermissionRequest()
            }
        }
    }
}

private fun getMediaUri(
    context: Context,
    directory: String,
    filePrefix: String,
    fileExtension: String,
): Uri {
    val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val storageDir: File? = context.getExternalFilesDir(directory)
    return File
        .createTempFile(
            "${filePrefix}_${timeStamp}_",
            fileExtension,
            storageDir,
        ).let {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                it,
            )
        }
}

fun getPhotoUri(context: Context): Uri =
    getMediaUri(
        context,
        Environment.DIRECTORY_PICTURES,
        "JPEG",
        ".jpg",
    )

fun getVideoUri(context: Context): Uri =
    getMediaUri(
        context,
        Environment.DIRECTORY_MOVIES,
        "VIDEO",
        ".mp4",
    )
