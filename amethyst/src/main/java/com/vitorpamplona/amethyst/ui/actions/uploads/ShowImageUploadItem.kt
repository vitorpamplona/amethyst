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

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.playback.composable.VideoView
import com.vitorpamplona.amethyst.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.service.uploads.UploadingState
import com.vitorpamplona.amethyst.ui.components.AutoNonlazyGrid
import com.vitorpamplona.amethyst.ui.note.CloseIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size40Modifier
import com.vitorpamplona.amethyst.ui.theme.Size55Modifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ShowImageUploadGallery(
    list: MultiOrchestrator,
    onDelete: (SelectedMediaProcessing) -> Unit,
    accountViewModel: AccountViewModel,
) {
    AutoNonlazyGrid(list.size()) {
        ShowImageUploadItem(list.get(it), onDelete, accountViewModel)
    }
}

/**
 * Creates a bitmap thumbnail from video uri of the scheme type content://
 */
fun createVideoThumb(
    context: Context,
    uri: Uri,
): Bitmap? {
    try {
        val mediaMetadataRetriever = MediaMetadataRetriever()
        mediaMetadataRetriever.setDataSource(context, uri)
        return mediaMetadataRetriever.frameAtTime
    } catch (ex: Exception) {
        Log.w("NewPostView", "Couldn't create thumbnail, but the video can be uploaded", ex)
    }
    return null
}

@Composable
fun ShowImageGallery(
    media: SelectedMedia,
    accountViewModel: AccountViewModel,
) {
    if (media.isImage() == true) {
        AsyncImage(
            model = media.uri.toString(),
            contentDescription = media.uri.toString(),
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)),
        )
    } else if (media.isVideo() == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        val context = LocalContext.current

        LaunchedEffect(key1 = media) {
            launch(Dispatchers.IO) {
                try {
                    bitmap = createVideoThumb(context, media.uri)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w("NewPostView", "Couldn't create thumbnail, but the video can be uploaded", e)
                }
            }
        }

        if (bitmap != null) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "some useful description",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            VideoView(
                videoUri = media.uri.toString(),
                mimeType = media.mimeType,
                roundedCorner = false,
                contentScale = ContentScale.Crop,
                accountViewModel = accountViewModel,
            )
        }
    } else {
        VideoView(
            videoUri = media.uri.toString(),
            mimeType = media.mimeType,
            roundedCorner = false,
            contentScale = ContentScale.Crop,
            accountViewModel = accountViewModel,
        )
    }
}

@Composable
fun ShowImageUploadItem(
    item: SelectedMediaProcessing,
    onDelete: (SelectedMediaProcessing) -> Unit,
    accountViewModel: AccountViewModel,
) {
    ShowImageGallery(item.media, accountViewModel)

    OrchestratorOverlay(item.orchestrator) {
        onDelete(item)
    }
}

@Composable
fun OrchestratorOverlay(
    orchestrator: UploadOrchestrator,
    onDelete: () -> Unit,
) {
    val progress by orchestrator.progress.collectAsState()
    val progressState by orchestrator.progressState.collectAsState()

    if (progressState is UploadingState.Ready) {
        DeleteButton(onDelete)
    } else {
        UploadingState(progress, progressState)
    }
}

@Composable
fun DeleteButton(onDelete: () -> Unit) {
    Box(
        contentAlignment = Alignment.TopEnd,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Size40Modifier, contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .fillMaxSize(0.6f)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.background),
            )

            IconButton(
                modifier = Size20Modifier,
                onClick = onDelete,
            ) {
                CloseIcon()
            }
        }
    }
}

@Composable
fun UploadingState(
    progress: Double,
    progressState: UploadingState,
) {
    Box(Modifier.size(55.dp), contentAlignment = Alignment.Center) {
        val animatedProgress by animateFloatAsState(
            targetValue = progress.toFloat(),
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        )

        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier =
                Size55Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background),
            strokeWidth = 5.dp,
        )

        val txt =
            when (progressState) {
                is UploadingState.Ready -> stringRes(R.string.uploading_state_ready)
                is UploadingState.Compressing -> stringRes(R.string.uploading_state_compressing)
                is UploadingState.Uploading -> stringRes(R.string.uploading_state_uploading)
                is UploadingState.ServerProcessing -> stringRes(R.string.uploading_state_server_processing)
                is UploadingState.Downloading -> stringRes(R.string.uploading_state_downloading)
                is UploadingState.Hashing -> stringRes(R.string.uploading_state_hashing)
                is UploadingState.Finished -> stringRes(R.string.uploading_state_finished)
                is UploadingState.Error -> stringRes(R.string.uploading_state_error)
            }

        Text(
            txt,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
    }
}
