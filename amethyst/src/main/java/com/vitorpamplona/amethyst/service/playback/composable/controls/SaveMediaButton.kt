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
package com.vitorpamplona.amethyst.service.playback.composable.controls

import android.Manifest
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.PinBottomIconSize
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size50Modifier
import kotlinx.coroutines.launch

@Composable
fun AnimatedSaveButton(
    controllerVisible: State<Boolean>,
    modifier: Modifier,
    onSaveClick: (localContext: Context) -> Unit,
) {
    AnimatedVisibility(
        visible = controllerVisible.value,
        modifier = modifier,
        enter = remember { fadeIn() },
        exit = remember { fadeOut() },
    ) {
        SaveMediaButton(onSaveClick)
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SaveMediaButton(onSaveClick: (localContext: Context) -> Unit) {
    Box(modifier = PinBottomIconSize) {
        Box(
            Modifier
                .clip(CircleShape)
                .fillMaxSize(0.6f)
                .align(Alignment.Center)
                .background(MaterialTheme.colorScheme.background),
        )

        val localContext = LocalContext.current

        val writeStoragePermissionState =
            rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE) { isGranted ->
                if (isGranted) {
                    onSaveClick(localContext)
                }
            }
        val scope = rememberCoroutineScope()
        IconButton(
            onClick = {
                scope.launch {
                    Toast
                        .makeText(
                            localContext,
                            stringRes(localContext, R.string.video_download_has_started_toast),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                    writeStoragePermissionState.status.isGranted
                ) {
                    onSaveClick(localContext)
                } else {
                    writeStoragePermissionState.launchPermissionRequest()
                }
            },
            modifier = Size50Modifier,
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                modifier = Size20Modifier,
                contentDescription = stringRes(R.string.save_to_gallery),
            )
        }
    }
}
