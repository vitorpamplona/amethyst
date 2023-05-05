package com.vitorpamplona.amethyst.ui.actions

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.R
import kotlinx.coroutines.launch
import java.io.File

/**
 * A button to save the remote image to the gallery.
 * May require a storage permission.
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
                    Toast.makeText(
                        localContext,
                        localContext.getString(R.string.image_saved_to_the_gallery),
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            },
            onError = {
                scope.launch {
                    Toast.makeText(
                        localContext,
                        localContext.getString(R.string.failed_to_save_the_image),
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            }
        )
    }

    val writeStoragePermissionState = rememberPermissionState(
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) { isGranted ->
        if (isGranted) {
            saveImage()
        }
    }

    Button(
        onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || writeStoragePermissionState.status.isGranted) {
                saveImage()
            } else {
                writeStoragePermissionState.launchPermissionRequest()
            }
        },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = Color.Gray
            )
    ) {
        Text(text = stringResource(id = R.string.save), color = Color.White)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SaveToGallery(localFile: File, mimeType: String?) {
    val localContext = LocalContext.current
    val scope = rememberCoroutineScope()

    fun saveImage() {
        ImageSaver.saveImage(
            context = localContext,
            localFile = localFile,
            mimeType = mimeType,
            onSuccess = {
                scope.launch {
                    Toast.makeText(
                        localContext,
                        localContext.getString(R.string.image_saved_to_the_gallery),
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            },
            onError = {
                scope.launch {
                    Toast.makeText(
                        localContext,
                        localContext.getString(R.string.failed_to_save_the_image),
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            }
        )
    }

    val writeStoragePermissionState = rememberPermissionState(
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) { isGranted ->
        if (isGranted) {
            saveImage()
        }
    }

    Button(
        onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || writeStoragePermissionState.status.isGranted) {
                saveImage()
            } else {
                writeStoragePermissionState.launchPermissionRequest()
            }
        },
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults
            .buttonColors(
                backgroundColor = Color.Gray
            )
    ) {
        Text(text = stringResource(id = R.string.save), color = Color.White)
    }
}
