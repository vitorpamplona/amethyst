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
package com.vitorpamplona.amethyst.ui.actions

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import kotlinx.coroutines.launch
import java.io.File

/**
 * A button to save the remote image to the gallery. May require a storage permission.
 *
 * @param url URL of the image
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SaveToGallery(url: String) {
    val localContext = LocalContext.current
    val scope = rememberCoroutineScope()

    fun saveImage() {
        ImageSaver.saveImage(
            context = localContext,
            url = url,
            onSuccess = {
                scope.launch {
                    Toast
                        .makeText(
                            localContext,
                            stringRes(localContext, R.string.image_saved_to_the_gallery),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            },
            onError = {
                scope.launch {
                    Toast
                        .makeText(
                            localContext,
                            stringRes(localContext, R.string.failed_to_save_the_image),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            },
        )
    }

    val writeStoragePermissionState =
        rememberPermissionState(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) { isGranted ->
            if (isGranted) {
                saveImage()
            }
        }

    OutlinedButton(
        onClick = {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                writeStoragePermissionState.status.isGranted
            ) {
                saveImage()
            } else {
                writeStoragePermissionState.launchPermissionRequest()
            }
        },
    ) {
        Text(text = stringRes(id = R.string.save))
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SaveToGallery(
    localFile: File,
    mimeType: String?,
) {
    val localContext = LocalContext.current
    val scope = rememberCoroutineScope()

    fun saveImage() {
        ImageSaver.saveImage(
            context = localContext,
            localFile = localFile,
            mimeType = mimeType,
            onSuccess = {
                scope.launch {
                    Toast
                        .makeText(
                            localContext,
                            stringRes(localContext, R.string.image_saved_to_the_gallery),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            },
            onError = {
                scope.launch {
                    Toast
                        .makeText(
                            localContext,
                            stringRes(localContext, R.string.failed_to_save_the_image),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            },
        )
    }

    val writeStoragePermissionState =
        rememberPermissionState(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) { isGranted ->
            if (isGranted) {
                saveImage()
            }
        }

    OutlinedButton(
        onClick = {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                writeStoragePermissionState.status.isGranted
            ) {
                saveImage()
            } else {
                writeStoragePermissionState.launchPermissionRequest()
            }
        },
        shape = ButtonBorder,
    ) {
        Text(text = stringRes(id = R.string.save))
    }
}
