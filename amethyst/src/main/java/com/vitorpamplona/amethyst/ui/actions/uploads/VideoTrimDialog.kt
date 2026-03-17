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

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.uploads.VideoTrimmer
import com.vitorpamplona.amethyst.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.ui.navigation.topbars.TitleIconModifier
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val THUMBNAIL_COUNT = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoTrimDialog(
    videoUri: Uri,
    onTrimmed: (Uri) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var durationMs by remember { mutableFloatStateOf(0f) }
    var rangeStart by remember { mutableFloatStateOf(0f) }
    var rangeEnd by remember { mutableFloatStateOf(0f) }
    var thumbnails by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isTrimming by remember { mutableStateOf(false) }

    LaunchedEffect(videoUri) {
        withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, videoUri)
                val duration =
                    retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                durationMs = duration.toFloat()
                rangeEnd = duration.toFloat()

                val frames = mutableListOf<Bitmap>()
                val interval = duration / THUMBNAIL_COUNT
                for (i in 0 until THUMBNAIL_COUNT) {
                    val timeUs = i * interval * 1000
                    val frame =
                        retriever.getFrameAtTime(
                            timeUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        )
                    if (frame != null) {
                        frames.add(frame)
                    }
                }
                retriever.release()
                thumbnails = frames
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("VideoTrimDialog", "Failed to extract video info", e)
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isTrimming) onCancel() },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        SetDialogToEdgeToEdge()
        Scaffold(
            topBar = {
                ShorterTopAppBar(
                    title = {
                        Text(
                            text = stringRes(R.string.video_trim_title),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            modifier = TitleIconModifier,
                            enabled = !isTrimming,
                            onClick = onCancel,
                        ) {
                            ArrowBackIcon()
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
            },
        ) { padding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (durationMs > 0f) {
                    // Thumbnail filmstrip
                    if (thumbnails.isNotEmpty()) {
                        LazyRow(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(60.dp),
                            horizontalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            itemsIndexed(thumbnails) { _, bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .width(48.dp)
                                            .height(60.dp),
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(60.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    // Range slider
                    RangeSlider(
                        value = rangeStart..rangeEnd,
                        onValueChange = { range ->
                            rangeStart = range.start
                            rangeEnd = range.endInclusive
                        },
                        valueRange = 0f..durationMs,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Time labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(
                                text = stringRes(R.string.trim_start),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = formatMsToTime(rangeStart.toLong()),
                                fontSize = 14.sp,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringRes(R.string.trim_duration),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = formatMsToTime((rangeEnd - rangeStart).toLong()),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringRes(R.string.trim_end),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = formatMsToTime(rangeEnd.toLong()),
                                fontSize = 14.sp,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Apply button
                    Button(
                        onClick = {
                            isTrimming = true
                            scope.launch {
                                val result =
                                    withContext(Dispatchers.IO) {
                                        VideoTrimmer.trim(
                                            context,
                                            videoUri,
                                            rangeStart.toLong(),
                                            rangeEnd.toLong(),
                                        )
                                    }
                                isTrimming = false
                                if (result != null) {
                                    onTrimmed(result)
                                } else {
                                    onCancel()
                                }
                            }
                        },
                        enabled = !isTrimming && rangeEnd > rangeStart,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isTrimming) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(20.dp).width(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(text = stringRes(R.string.video_trimming_in_progress))
                            }
                        } else {
                            Text(text = stringRes(R.string.apply_trim))
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

private fun formatMsToTime(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    return formatSecondsToTime(totalSeconds)
}
