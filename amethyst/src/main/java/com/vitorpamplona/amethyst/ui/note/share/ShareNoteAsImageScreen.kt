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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.service.uploads.UploadOrchestrator
import com.vitorpamplona.amethyst.service.uploads.UploadingState
import com.vitorpamplona.amethyst.ui.actions.mediaServers.DEFAULT_MEDIA_SERVERS
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.components.TextSpinner
import com.vitorpamplona.amethyst.ui.components.TitleExplainer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.ActionTopBar
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.settings.SettingsRow
import com.vitorpamplona.amethyst.ui.stringRes
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
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
        topBar = {
            ActionTopBar(
                postRes = R.string.quick_action_share,
                titleRes = R.string.share_as_image,
                isActive = { preview != null && !isProcessing },
                onCancel = { nav.popBack() },
                onPost = ::shareImage,
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsRow(R.string.file_server, R.string.file_server_description) {
                TextSpinner(
                    label = "",
                    placeholder = selectedServer.name,
                    options = fileServerOptions,
                    onSelect = { selectedServer = fileServers[it] },
                )
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val captured = preview
                if (captured == null) {
                    // Render the note off-screen into the graphics layer, then snapshot it.
                    CaptureSource(
                        note = note,
                        graphicsLayer = graphicsLayer,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                    LaunchedEffect(note) {
                        // Wait a couple of frames so the card is measured and drawn before capture.
                        withFrameNanos {}
                        withFrameNanos {}
                        preview = graphicsLayer.toImageBitmap()
                    }
                    CircularProgressIndicator()
                } else {
                    Image(
                        bitmap = captured,
                        contentDescription = stringRes(R.string.share_as_image),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (isProcessing) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
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
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .drawWithContent {
                    graphicsLayer.record { this@drawWithContent.drawContent() }
                    // Intentionally do not call drawLayer: this source stays invisible and only
                    // feeds the snapshot; the user sees the resulting Image.
                }.clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(8.dp),
    ) {
        NoteCompose(
            baseNote = note,
            isQuotedNote = true,
            quotesLeft = 1,
            accountViewModel = accountViewModel,
            nav = nav,
        )

        Text(
            text = stringRes(R.string.share_as_image_watermark),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, end = 4.dp),
        )
    }
}

private const val PNG_MIME = "image/png"

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
    val shareIntent = Intent.createChooser(sendIntent, stringRes(context, R.string.share_as_image))
    context.startActivity(shareIntent)
}
