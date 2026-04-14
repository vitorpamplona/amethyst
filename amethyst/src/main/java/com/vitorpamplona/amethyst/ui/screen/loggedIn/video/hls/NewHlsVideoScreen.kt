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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.video.hls

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.davotoula.lightcompressor.hls.HlsLadder
import com.davotoula.lightcompressor.utils.CompressorUtils
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.components.TextSpinner
import com.vitorpamplona.amethyst.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewHlsVideoScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val vm: NewHlsVideoViewModel = viewModel()
    val context = LocalContext.current

    LaunchedEffect(accountViewModel) {
        vm.load(accountViewModel.account, context)
    }

    Scaffold(
        topBar = {
            TopBarWithBackButton(
                caption = stringResource(R.string.share_hls_video),
                popBack = nav::popBack,
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            NewHlsVideoBody(vm, nav)
        }
    }
}

@Composable
private fun NewHlsVideoBody(
    vm: NewHlsVideoViewModel,
    nav: INav,
) {
    val publishState by vm.state.collectAsState()

    when (val state = publishState) {
        is HlsPublishState.Idle -> IdleBody(vm)

        is HlsPublishState.Transcoding,
        is HlsPublishState.Uploading,
        is HlsPublishState.Publishing,
        -> ProgressBody(vm, state)

        is HlsPublishState.Success -> SuccessBody(vm, state, nav)

        is HlsPublishState.Failure -> FailureBody(vm, state)
    }
}

@Composable
private fun IdleBody(vm: NewHlsVideoViewModel) {
    val context = LocalContext.current

    val pickLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            vm.onVideoPicked(uri, metadata = null)
        }

    // Probe source metadata in the background whenever pickedUri flips to a new Uri.
    LaunchedEffect(vm.pickedUri) {
        val uri = vm.pickedUri ?: return@LaunchedEffect
        if (vm.sourceMetadata != null) return@LaunchedEffect
        val probed = probeSourceMetadata(context, uri)
        if (probed != null) vm.onVideoPicked(uri, probed)
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        val pickedUri = vm.pickedUri
        if (pickedUri == null) {
            EmptyPickVideoCard(
                onClick = {
                    pickLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                    )
                },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.hls_pick_video_helper),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PickedVideoCard(
                vm = vm,
                onChange = {
                    vm.clearPickedVideo()
                    pickLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                    )
                },
            )
            Spacer(Modifier.height(16.dp))
            FormFields(vm)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { vm.publish(context) },
                enabled = vm.title.isNotBlank() && vm.selectedServer != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.hls_publish_button))
            }
        }
    }
}

@Composable
private fun EmptyPickVideoCard(onClick: () -> Unit) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.VideoLibrary,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.hls_pick_video_primary),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun PickedVideoCard(
    vm: NewHlsVideoViewModel,
    onChange: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.VideoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = vm.pickedUri?.lastPathSegment ?: "Video",
                    style = MaterialTheme.typography.bodyLarge,
                )
                val meta = vm.sourceMetadata
                if (meta != null) {
                    val duration = "${meta.durationSeconds / 60}:${(meta.durationSeconds % 60).toString().padStart(2, '0')}"
                    Text(
                        text = "$duration · ${meta.width}×${meta.height} · ${formatSize(meta.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onChange) {
                Text(stringResource(R.string.hls_change_video))
            }
        }
    }
}

@Composable
private fun FormFields(vm: NewHlsVideoViewModel) {
    OutlinedTextField(
        value = vm.title,
        onValueChange = { vm.title = it },
        label = { Text(stringResource(R.string.hls_title_label)) },
        placeholder = { Text(stringResource(R.string.hls_title_placeholder)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = vm.description,
        onValueChange = { vm.description = it },
        label = { Text(stringResource(R.string.hls_description_label)) },
        placeholder = { Text(stringResource(R.string.hls_description_placeholder)) },
        modifier = Modifier.fillMaxWidth().height(120.dp),
    )

    Spacer(Modifier.height(16.dp))

    // Content warning toggle
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.content_warning),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(
            checked = vm.sensitiveContent,
            onCheckedChange = { vm.sensitiveContent = it },
        )
    }

    if (vm.sensitiveContent) {
        OutlinedTextField(
            value = vm.contentWarningReason,
            onValueChange = { vm.contentWarningReason = it },
            placeholder = { Text(stringResource(R.string.hls_content_warning_reason_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }

    Spacer(Modifier.height(8.dp))

    // Cross-post as kind-1 note toggle
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.hls_cross_post_as_note),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.hls_cross_post_as_note_explainer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = vm.crossPostAsNote,
            onCheckedChange = { vm.crossPostAsNote = it },
        )
    }

    Spacer(Modifier.height(16.dp))

    // Server picker — reads the user's configured Blossom servers from the account
    Text(
        text = stringResource(R.string.file_server),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    val servers by vm.availableServers.collectAsState()
    val serverOptions =
        remember(servers) {
            servers.map { TitleExplainer(it.name, it.baseUrl) }.toImmutableList()
        }
    TextSpinner(
        label = "",
        placeholder = vm.selectedServer?.name ?: servers.firstOrNull()?.name ?: "",
        options = serverOptions,
        onSelect = { index -> servers.getOrNull(index)?.let { vm.selectedServer = it } },
    )

    Spacer(Modifier.height(16.dp))

    // Codec toggle
    CodecToggle(
        useH265 = vm.useH265,
        onChange = { vm.useH265 = it },
    )

    Spacer(Modifier.height(16.dp))

    // Renditions preview (read-only)
    RenditionsPreview(vm.sourceMetadata)
}

@Composable
private fun CodecToggle(
    useH265: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val hevcSupported = remember { CompressorUtils.isHevcEncodingSupported() }

    Text(
        text = stringResource(R.string.hls_codec_label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = useH265 && hevcSupported,
            enabled = hevcSupported,
            onClick = { onChange(true) },
            label = { Text(stringResource(R.string.hls_codec_h265)) },
            colors = FilterChipDefaults.filterChipColors(),
        )
        FilterChip(
            selected = !useH265 || !hevcSupported,
            onClick = { onChange(false) },
            label = { Text(stringResource(R.string.hls_codec_h264)) },
        )
    }
    if (!hevcSupported) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.hls_codec_fallback_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RenditionsPreview(metadata: HlsSourceMetadata?) {
    Text(
        text = stringResource(R.string.hls_renditions_label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    if (metadata == null) {
        Text(
            text = "360p · 540p · 720p · 1080p · 4K",
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    val shortSide = minOf(metadata.width, metadata.height)
    val ladder =
        HlsLadder
            .default()
            .forSource(shortSide)
            .renditions
            .map { it.resolution.label }
    val skipped =
        HlsLadder
            .default()
            .renditions
            .map { it.resolution.label }
            .filter { it !in ladder }

    Text(
        text = stringResource(R.string.hls_renditions_source_format, metadata.width, metadata.height),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = stringResource(R.string.hls_renditions_produce_format, ladder.joinToString(" · ")),
        style = MaterialTheme.typography.bodyMedium,
    )
    if (skipped.isNotEmpty()) {
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.hls_renditions_skipped_format, skipped.joinToString(", ")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProgressBody(
    vm: NewHlsVideoViewModel,
    state: HlsPublishState,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.hls_publishing_header_format, vm.title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(24.dp))

        PhaseRow(
            label = stringResource(R.string.hls_state_transcoding_format, (state as? HlsPublishState.Transcoding)?.currentLabel?.ifBlank { "…" } ?: "…"),
            active = state is HlsPublishState.Transcoding,
            done = state is HlsPublishState.Uploading || state is HlsPublishState.Publishing,
            progressFraction = (state as? HlsPublishState.Transcoding)?.percent?.let { it / 100f },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        val uploadingFraction =
            (state as? HlsPublishState.Uploading)?.let { up ->
                if (up.total == 0) 0f else up.done.toFloat() / up.total
            }
        val uploadingLabel =
            when (state) {
                is HlsPublishState.Uploading -> stringResource(R.string.hls_state_uploading_format, state.done, state.total)
                else -> stringResource(R.string.hls_state_uploading_format, 0, 0)
            }
        PhaseRow(
            label = uploadingLabel,
            active = state is HlsPublishState.Uploading,
            done = state is HlsPublishState.Publishing,
            progressFraction = uploadingFraction,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        PhaseRow(
            label = stringResource(R.string.hls_state_publishing),
            active = state is HlsPublishState.Publishing,
            done = false,
            progressFraction = null,
        )

        Spacer(Modifier.height(32.dp))

        OutlinedButton(
            onClick = { vm.cancel() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun PhaseRow(
    label: String,
    active: Boolean,
    done: Boolean,
    progressFraction: Float?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            done -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF22C55E),
                    modifier = Modifier.size(20.dp),
                )
            }

            active -> {
                Spacer(
                    Modifier
                        .size(20.dp)
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                )
            }

            else -> {
                Spacer(Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (active && progressFraction != null) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SuccessBody(
    vm: NewHlsVideoViewModel,
    state: HlsPublishState.Success,
    nav: INav,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = Color(0xFF22C55E),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.hls_state_success_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.hls_state_success_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = state.masterUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        val noteId = state.noteEventId
        if (noteId != null) {
            Button(
                onClick = {
                    vm.reset()
                    nav.nav(Route.Note(noteId))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.hls_view_note))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    vm.reset()
                    nav.popBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.hls_done))
            }
        } else {
            Button(
                onClick = {
                    vm.reset()
                    nav.popBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.hls_done))
            }
        }
    }
}

@Composable
private fun FailureBody(
    vm: NewHlsVideoViewModel,
    state: HlsPublishState.Failure,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.hls_state_failure_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { vm.reset() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.hls_try_again))
        }
    }
}

private suspend fun probeSourceMetadata(
    context: android.content.Context,
    uri: Uri,
): HlsSourceMetadata? =
    withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return@withContext null
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return@withContext null
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val (w, h) = if (rotation == 90 || rotation == 270) height to width else width to height
            val size =
                context.contentResolver
                    .openFileDescriptor(uri, "r")
                    ?.use { it.statSize } ?: 0L
            HlsSourceMetadata(
                width = w,
                height = h,
                durationSeconds = (durationMs / 1000).toInt(),
                sizeBytes = size,
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1) String.format("%.1f MB", mb) else String.format("%.0f KB", bytes / 1024.0)
}
