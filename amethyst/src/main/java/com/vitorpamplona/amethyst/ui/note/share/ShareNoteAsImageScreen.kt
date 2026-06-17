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
package com.vitorpamplona.amethyst.ui.note.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.service.uploads.UploadingState
import com.vitorpamplona.amethyst.ui.actions.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.ui.actions.uploads.UploadProgressIndicator
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.components.TextSpinner
import com.vitorpamplona.amethyst.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size18Modifier
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Full-screen flow that turns [id]'s note into a shareable image: it renders the post into a
 * framed card, captures it to a bitmap, uploads the PNG to one of the user's Blossom media
 * servers, and hands the resulting URL to the Android share sheet.
 */
@Composable
fun ShareNoteAsImageScreen(
    id: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadNote(id, accountViewModel) { note ->
        if (note != null) {
            ShareNoteAsImageScreen(note, accountViewModel, nav)
        }
    }
}

/**
 * Renders [id]'s note into the same framed card as [ShareNoteAsImageScreen], captures it to a
 * bitmap, writes it to the cache as a PNG and hands the local file straight to the Android share
 * sheet — no preview, no upload. The screen only shows a brief progress indicator while the card
 * is being captured.
 */
@Composable
fun ShareNoteAsImageFileScreen(
    id: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadNote(id, accountViewModel) { note ->
        if (note != null) {
            ShareNoteAsImageFileScreen(note, accountViewModel, nav)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareNoteAsImageFileScreen(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current
    val graphicsLayer = rememberGraphicsLayer()

    // Guards against capturing/sharing more than once across recompositions.
    var shared by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(R.string.share_as_image), nav) },
    ) { pad ->
        Box(
            modifier =
                Modifier
                    .padding(pad)
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            // Rendered (but unpainted) only to feed the snapshot; the user never sees this card.
            CaptureSource(
                note = note,
                graphicsLayer = graphicsLayer,
                accountViewModel = accountViewModel,
                nav = nav,
            )

            GeneratingPreview()

            LaunchedEffect(note) {
                // Wait a couple of frames so the card is measured and drawn, then let network
                // media settle before the final snapshot.
                withFrameNanos {}
                withFrameNanos {}
                graphicsLayer.toImageBitmap()
                delay(IMAGE_SETTLE_MS)
                val bitmap = graphicsLayer.toImageBitmap()

                if (!shared) {
                    shared = true
                    val uri =
                        withContext(Dispatchers.IO) {
                            saveBitmapToCache(context, bitmap.asAndroidBitmap())
                        }
                    startShareImageFileIntent(context, uri)
                    nav.popBack()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareNoteAsImageScreen(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val account = accountViewModel.account
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val graphicsLayer = rememberGraphicsLayer()
    val orchestrator = remember { UploadOrchestrator() }

    // The captured bitmap that becomes the preview and, on share, the uploaded file.
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    // Once true the off-screen capture source is disposed; [preview] holds the final bitmap.
    var captureComplete by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    val fileServers by account.blossomServers.hostNameFlow.collectAsState()

    var selectedServer by remember(fileServers) {
        mutableStateOf(
            fileServers.firstOrNull { it == account.settings.defaultFileServer }
                ?: fileServers.firstOrNull()
                ?: DEFAULT_MEDIA_SERVERS[0],
        )
    }

    val fileServerOptions =
        remember(fileServers) {
            fileServers.map { TitleExplainer(it.name, it.baseUrl) }.toImmutableList()
        }

    fun shareImage() {
        val image = preview ?: return
        if (isProcessing) return
        isProcessing = true
        scope.launch {
            val server = selectedServer
            val finalState =
                withContext(Dispatchers.IO) {
                    val uri = saveBitmapToCache(context, image.asAndroidBitmap())
                    orchestrator.upload(
                        uri = uri,
                        mimeType = PNG_MIME,
                        alt = null,
                        contentWarningReason = null,
                        compressionQuality = CompressorQuality.UNCOMPRESSED,
                        server = server,
                        account = account,
                        context = context,
                        stripMetadata = false,
                    )
                }

            isProcessing = false

            when (finalState) {
                is UploadingState.Finished -> {
                    val result = finalState.result
                    if (result is UploadOrchestrator.OrchestratorResult.ServerResult) {
                        account.settings.changeDefaultFileServer(server)
                        startShareUrlIntent(context, result.url)
                        nav.popBack()
                    } else {
                        accountViewModel.toastManager.toast(
                            R.string.failed_to_upload_media_no_details,
                            R.string.server_did_not_provide_a_url_after_uploading,
                        )
                    }
                }

                is UploadingState.Error -> {
                    accountViewModel.toastManager.toast(
                        R.string.failed_to_upload_media_no_details,
                        finalState.errorResource,
                        *finalState.params,
                    )
                }

                else -> {}
            }
        }
    }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(R.string.share_as_image_url), nav) },
        bottomBar = {
            ShareBottomBar(
                serverName = selectedServer.name,
                serverOptions = fileServerOptions,
                onSelectServer = { selectedServer = fileServers[it] },
                shareEnabled = preview != null && !isProcessing,
                onShare = ::shareImage,
            )
        },
    ) { pad ->
        Box(
            modifier =
                Modifier
                    .padding(pad)
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            // Keep the note rendered (but unpainted) until the snapshot is final, so async
            // images have a chance to load into the layer before the second capture.
            if (!captureComplete) {
                CaptureSource(
                    note = note,
                    graphicsLayer = graphicsLayer,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
                LaunchedEffect(note) {
                    // Wait a couple of frames so the card is measured and drawn, snapshot a
                    // first preview, then refine once network media has had time to settle.
                    withFrameNanos {}
                    withFrameNanos {}
                    preview = graphicsLayer.toImageBitmap()
                    delay(IMAGE_SETTLE_MS)
                    preview = graphicsLayer.toImageBitmap()
                    captureComplete = true
                }
            }

            val captured = preview
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (captured != null) {
                    Image(
                        bitmap = captured,
                        contentDescription = stringRes(R.string.share_as_image),
                        contentScale = ContentScale.FillWidth,
                        modifier =
                            Modifier
                                .widthIn(max = 460.dp)
                                .fillMaxWidth()
                                .shadow(18.dp, PreviewShape, clip = false)
                                .clip(PreviewShape),
                    )
                } else {
                    GeneratingPreview()
                }
            }

            if (isProcessing) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = PreviewShape,
                        tonalElevation = 6.dp,
                    ) {
                        UploadProgressIndicator(
                            orchestrator,
                            modifier = Modifier.widthIn(min = 160.dp).padding(horizontal = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratingPreview() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircularProgressIndicator()
        Text(
            text = stringRes(R.string.share_as_image_generating),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareBottomBar(
    serverName: String,
    serverOptions: ImmutableList<TitleExplainer>,
    onSelectServer: (Int) -> Unit,
    shareEnabled: Boolean,
    onShare: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextSpinner(
                label = stringRes(R.string.file_server),
                placeholder = serverName,
                options = serverOptions,
                onSelect = onSelectServer,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = onShare,
                enabled = shareEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    symbol = MaterialSymbols.Share,
                    contentDescription = null,
                    modifier = Size18Modifier,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringRes(R.string.quick_action_share))
            }
        }
    }
}

/**
 * The framed card that is recorded into [graphicsLayer] but not painted to the screen — the
 * visible preview is the captured [ImageBitmap] instead. The capture modifier is first in the
 * chain so the opaque background and border are part of the bitmap.
 */
@Composable
private fun CaptureSource(
    note: Note,
    graphicsLayer: GraphicsLayer,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(
        modifier =
            Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .drawWithContent {
                    graphicsLayer.record { this@drawWithContent.drawContent() }
                    // Intentionally no drawLayer: this source stays invisible and only feeds the
                    // snapshot; the user sees the resulting Image.
                }.clip(PreviewShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PreviewShape)
                .padding(14.dp),
    ) {
        NoteCompose(
            baseNote = note,
            isQuotedNote = true,
            quotesLeft = 1,
            accountViewModel = accountViewModel,
            nav = nav,
        )

        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.amethyst),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringRes(R.string.share_as_image_watermark),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val PNG_MIME = "image/png"

// Time given for async media to load into the off-screen card before the final snapshot.
private const val IMAGE_SETTLE_MS = 900L

private val PreviewShape = RoundedCornerShape(18.dp)

private fun saveBitmapToCache(
    context: Context,
    bitmap: Bitmap,
): Uri {
    val file = File(context.cacheDir, "amethyst_share_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}

private fun startShareUrlIntent(
    context: Context,
    url: String,
) {
    val sendIntent =
        Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
    val shareIntent = Intent.createChooser(sendIntent, stringRes(context, R.string.share_as_image_url))
    context.startActivity(shareIntent)
}

private fun startShareImageFileIntent(
    context: Context,
    uri: Uri,
) {
    val sendIntent =
        Intent().apply {
            action = Intent.ACTION_SEND
            type = PNG_MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    val shareIntent = Intent.createChooser(sendIntent, stringRes(context, R.string.share_as_image))
    context.startActivity(shareIntent)
}
