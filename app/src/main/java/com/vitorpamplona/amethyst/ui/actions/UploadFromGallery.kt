package com.vitorpamplona.amethyst.ui.navigation

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.R

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun UploadFromGallery(
    isUploading: Boolean,
    onImageChosen: (Uri) -> Unit,
) {
    val cameraPermissionState =
        rememberPermissionState(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                android.Manifest.permission.READ_MEDIA_IMAGES
            } else {
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            }
        )

    if (cameraPermissionState.status.isGranted) {
        var showGallerySelect by remember { mutableStateOf(false) }
        if (showGallerySelect) {
            GallerySelect(
                onImageUri = { uri ->
                    showGallerySelect = false
                    if (uri != null)
                        onImageChosen(uri)
                }
            )
        } else {
            Box() {
                Button(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(4.dp),
                    enabled = !isUploading,
                    onClick = {
                        showGallerySelect = true
                    }
                ) {
                    if (!isUploading) {
                        Text(stringResource(R.string.upload_image))
                    } else {
                        Text(stringResource(R.string.uploading))
                    }
                }
            }
        }
    } else {
        Column {
            Button(
                onClick = { cameraPermissionState.launchPermissionRequest() },
                enabled = !isUploading,
            ) {
                if (!isUploading) {
                    Text(stringResource(R.string.upload_image))
                } else {
                    Text(stringResource(R.string.uploading))
                }
            }
        }
    }
}


@Composable
fun GallerySelect(
    onImageUri: (Uri?) -> Unit = { }
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            onImageUri(uri)
        }
    )

    @Composable
    fun LaunchGallery() {
        SideEffect {
            launcher.launch("image/*")
        }
    }

    LaunchGallery()
}