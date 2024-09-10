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

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.GetMediaActivityResultContract
import com.vitorpamplona.amethyst.ui.stringRes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun UploadFromGallery(
    isUploading: Boolean,
    tint: Color,
    modifier: Modifier,
    onImageChosen: (Uri) -> Unit,
) {
    val cameraPermissionState =
        rememberPermissionState(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                android.Manifest.permission.READ_MEDIA_IMAGES
            } else {
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            },
        )

    if (cameraPermissionState.status.isGranted) {
        var showGallerySelect by remember { mutableStateOf(false) }
        if (showGallerySelect) {
            GallerySelect(
                onImageUri = { uri ->
                    showGallerySelect = false
                    if (uri != null) {
                        onImageChosen(uri)
                    }
                },
            )
        }

        UploadBoxButton(isUploading, tint, modifier) { showGallerySelect = true }
    } else {
        UploadBoxButton(isUploading, tint, modifier) { cameraPermissionState.launchPermissionRequest() }
    }
}

@Composable
private fun UploadBoxButton(
    isUploading: Boolean,
    tint: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box {
        IconButton(
            modifier = modifier.align(Alignment.Center),
            enabled = !isUploading,
            onClick = { onClick() },
        ) {
            if (!isUploading) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = stringRes(id = R.string.upload_image),
                    modifier = Modifier.height(25.dp),
                    tint = tint,
                )
            } else {
                LoadingAnimation()
            }
        }
    }
}

fun getPhotoUri(context: Context): Uri {
    val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    return File
        .createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir,
        ).let {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                it,
            )
        }
}

val DefaultAnimationColors =
    listOf(
        Color(0xFF5851D8),
        Color(0xFF833AB4),
        Color(0xFFC13584),
        Color(0xFFE1306C),
        Color(0xFFFD1D1D),
        Color(0xFFF56040),
        Color(0xFFF77737),
        Color(0xFFFCAF45),
        Color(0xFFFFDC80),
        Color(0xFF5851D8),
    ).toImmutableList()

@Composable
fun LoadingAnimation(
    indicatorSize: Dp = 20.dp,
    circleColors: ImmutableList<Color> = DefaultAnimationColors,
    animationDuration: Int = 1000,
) {
    val infiniteTransition = rememberInfiniteTransition()

    val rotateAnimation by
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = animationDuration,
                            easing = LinearEasing,
                        ),
                ),
        )

    CircularProgressIndicator(
        modifier =
            Modifier
                .size(size = indicatorSize)
                .rotate(degrees = rotateAnimation)
                .border(
                    width = 4.dp,
                    brush = Brush.sweepGradient(circleColors),
                    shape = CircleShape,
                ),
        progress = 1f,
        strokeWidth = 1.dp,
        color = MaterialTheme.colorScheme.background,
    )
}

@Composable
fun GallerySelect(onImageUri: (Uri?) -> Unit = {}) {
    var hasLaunched by remember { mutableStateOf(AtomicBoolean(false)) }
    val launcher =
        rememberLauncherForActivityResult(
            contract = GetMediaActivityResultContract(),
            onResult = { uri: Uri? ->
                onImageUri(uri)
                hasLaunched.set(false)
            },
        )

    @Composable
    fun LaunchGallery() {
        SideEffect {
            if (!hasLaunched.getAndSet(true)) {
                launcher.launch("*/*")
            }
        }
    }

    LaunchGallery()
}
