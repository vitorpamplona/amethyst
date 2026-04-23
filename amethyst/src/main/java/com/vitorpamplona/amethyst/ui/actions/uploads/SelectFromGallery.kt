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
package com.vitorpamplona.amethyst.ui.actions.uploads

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.components.LoadingAnimation
import com.vitorpamplona.amethyst.ui.stringRes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.util.concurrent.atomic.AtomicBoolean

@Stable
class SelectedMedia(
    val uri: Uri,
    val mimeType: String?,
) {
    fun isImage() = mimeType?.startsWith("image")

    fun isGif() = mimeType?.equals("image/gif", ignoreCase = true) == true

    fun isVideo() = mimeType?.startsWith("video")

    fun isAudio() = mimeType?.startsWith("audio")

    fun isNotMedia() =
        mimeType?.let {
            !(it.startsWith("image") || it.startsWith("video") || it.startsWith("audio"))
        } ?: true

    fun isDocument() = mimeType == "application/pdf"
}

@Composable
fun SelectFromGallery(
    isUploading: Boolean,
    enabled: Boolean = true,
    tint: Color,
    modifier: Modifier,
    onImageChosen: (ImmutableList<SelectedMedia>) -> Unit,
) {
    var showGallerySelect by remember { mutableStateOf(false) }
    if (showGallerySelect) {
        GallerySelect(
            onImageUri = { uri ->
                showGallerySelect = false
                if (uri.isNotEmpty()) {
                    onImageChosen(uri)
                }
            },
        )
    }

    GallerySelectButton(isUploading, enabled, tint, modifier) { showGallerySelect = true }
}

@Composable
fun SelectSingleFromGallery(
    isUploading: Boolean,
    tint: Color,
    modifier: Modifier,
    onImageChosen: (SelectedMedia) -> Unit,
) {
    var showGallerySelect by remember { mutableStateOf(false) }
    if (showGallerySelect) {
        GallerySelectSingle(
            onImageUri = { media ->
                showGallerySelect = false
                if (media != null) {
                    onImageChosen(media)
                }
            },
        )
    }

    GallerySelectButton(isUploading, true, tint, modifier) { showGallerySelect = true }
}

@Composable
private fun GallerySelectButton(
    isUploading: Boolean,
    enabled: Boolean,
    tint: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    IconButton(
        modifier = modifier,
        enabled = enabled && !isUploading,
        onClick = { onClick() },
    ) {
        if (!isUploading) {
            Icon(
                symbol = MaterialSymbols.AddPhotoAlternate,
                contentDescription = stringRes(id = R.string.upload_image),
                modifier = Modifier.height(25.dp),
                tint = tint,
            )
        } else {
            LoadingAnimation()
        }
    }
}

@Composable
fun GallerySelect(onImageUri: (ImmutableList<SelectedMedia>) -> Unit = {}) {
    val hasLaunched by remember { mutableStateOf(AtomicBoolean(false)) }
    val resolver = LocalContext.current.contentResolver

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickMultipleVisualMedia(10),
            onResult = { uris: List<Uri> ->
                onImageUri(
                    uris
                        .map {
                            SelectedMedia(it, resolver.getType(it))
                        }.toImmutableList(),
                )
                hasLaunched.set(false)
            },
        )

    @Composable
    fun LaunchGallery() {
        SideEffect {
            if (!hasLaunched.getAndSet(true)) {
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            }
        }
    }

    LaunchGallery()
}

@Composable
fun GallerySelectSingle(onImageUri: (SelectedMedia?) -> Unit = {}) {
    val hasLaunched by remember { mutableStateOf(AtomicBoolean(false)) }
    val resolver = LocalContext.current.contentResolver

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
            onResult = { uri: Uri? ->
                if (uri != null) {
                    onImageUri(SelectedMedia(uri, resolver.getType(uri)))
                } else {
                    onImageUri(null)
                }

                hasLaunched.set(false)
            },
        )

    @Composable
    fun LaunchGallery() {
        SideEffect {
            if (!hasLaunched.getAndSet(true)) {
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            }
        }
    }

    LaunchGallery()
}
