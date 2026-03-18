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
package com.vitorpamplona.amethyst.desktop.ui.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI

val LocalWindowState = compositionLocalOf<androidx.compose.ui.window.WindowState?> { null }

val LocalAwtWindow = compositionLocalOf<java.awt.Window?> { null }

val LocalIsImmersiveFullscreen = compositionLocalOf { mutableStateOf(false) }

enum class ViewMode { DEFAULT, FULLSCREEN }

private sealed class DownloadState {
    data object Idle : DownloadState()

    data class Downloading(
        val progress: Float,
        val filename: String,
    ) : DownloadState()

    data class Done(
        val file: File,
    ) : DownloadState()

    data class Failed(
        val message: String,
    ) : DownloadState()
}

@Composable
fun LightboxOverlay(
    urls: List<String>,
    initialIndex: Int = 0,
    initialSeekPosition: Float = 0f,
    initialFullscreen: Boolean = false,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, urls.lastIndex)) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var menuExpanded by remember { mutableStateOf(false) }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var viewMode by remember { mutableStateOf(if (initialFullscreen) ViewMode.FULLSCREEN else ViewMode.DEFAULT) }
    val awtWindow = LocalAwtWindow.current
    val isImmersiveFullscreen = LocalIsImmersiveFullscreen.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Sync exclusive fullscreen with viewMode and signal parent layouts
    LaunchedEffect(viewMode) {
        isImmersiveFullscreen.value = viewMode == ViewMode.FULLSCREEN
        if (viewMode == ViewMode.FULLSCREEN) {
            awtWindow?.let { FullscreenHelper.enterFullscreen(it) }
        } else {
            if (FullscreenHelper.isFullscreen()) FullscreenHelper.exitFullscreen()
        }
    }

    // Restore fullscreen on dismiss
    DisposableEffect(Unit) {
        onDispose {
            isImmersiveFullscreen.value = false
            if (FullscreenHelper.isFullscreen()) FullscreenHelper.exitFullscreen()
        }
    }

    // Auto-dismiss banner after 3s
    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Done || downloadState is DownloadState.Failed) {
            delay(3000)
            downloadState = DownloadState.Idle
        }
    }

    val currentUrl = urls[currentIndex]
    val isVideo = RichTextParser.isVideoUrl(currentUrl)

    fun triggerSave() {
        if (downloadState is DownloadState.Downloading) return
        val url = urls[currentIndex]
        val filename = url.substringAfterLast('/').substringBefore('?').ifBlank { "media" }
        downloadState = DownloadState.Downloading(progress = -1f, filename = filename)
        scope.launch {
            val result =
                SaveMediaAction.saveMedia(
                    url = url,
                    onProgress = { downloaded, total ->
                        val progress = if (total > 0) downloaded.toFloat() / total else -1f
                        downloadState = DownloadState.Downloading(progress = progress, filename = filename)
                    },
                )
            downloadState =
                if (result != null) {
                    DownloadState.Done(result)
                } else {
                    DownloadState.Failed("Download failed")
                }
        }
    }

    fun toggleFullscreen() {
        viewMode =
            if (viewMode == ViewMode.FULLSCREEN) ViewMode.DEFAULT else ViewMode.FULLSCREEN
    }

    // Content modifier based on view mode
    val contentModifier =
        if (viewMode == ViewMode.DEFAULT) {
            Modifier.fillMaxSize().padding(48.dp)
        } else {
            Modifier.fillMaxSize()
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(if (viewMode == ViewMode.FULLSCREEN) Color.Black else Color.Black.copy(alpha = 0.9f))
                .focusRequester(focusRequester)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.Escape -> {
                            if (viewMode == ViewMode.FULLSCREEN) {
                                viewMode = ViewMode.DEFAULT
                            } else {
                                onDismiss()
                            }
                            true
                        }

                        Key.F -> {
                            toggleFullscreen()
                            true
                        }

                        Key.DirectionLeft -> {
                            if (currentIndex > 0) currentIndex--
                            true
                        }

                        Key.DirectionRight -> {
                            if (currentIndex < urls.lastIndex) currentIndex++
                            true
                        }

                        Key.S -> {
                            if (event.isCtrlPressed) {
                                triggerSave()
                                true
                            } else {
                                false
                            }
                        }

                        else -> {
                            false
                        }
                    }
                }.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    // Click on backdrop doesn't close — use X button or Esc
                },
    ) {
        // Main content — video or image
        if (isVideo) {
            Box(
                modifier = contentModifier,
                contentAlignment = Alignment.Center,
            ) {
                DesktopVideoPlayer(
                    url = currentUrl,
                    autoPlay = true,
                    initialSeekPosition = if (currentIndex == initialIndex) initialSeekPosition else 0f,
                    viewMode = viewMode,
                    onViewModeChange = { newMode ->
                        viewMode = newMode
                    },
                    modifier =
                        if (viewMode == ViewMode.DEFAULT) {
                            Modifier.widthIn(max = 1200.dp)
                        } else {
                            Modifier
                        },
                    trailingControls = {
                        MoreOptionsMenu(
                            menuExpanded = menuExpanded,
                            onExpandMenu = { menuExpanded = true },
                            onDismissMenu = { menuExpanded = false },
                            onSave = { triggerSave() },
                            onCopyUrl = {
                                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                clipboard.setContents(StringSelection(urls[currentIndex]), null)
                            },
                            onOpenInBrowser = {
                                Desktop.getDesktop().browse(URI(urls[currentIndex]))
                            },
                        )
                    },
                )
            }
        } else {
            ZoomableImage(
                url = currentUrl,
                modifier = contentModifier,
            )
        }

        // Download banner (top)
        AnimatedVisibility(
            visible = downloadState !is DownloadState.Idle,
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
        ) {
            val state = downloadState
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            when (state) {
                                is DownloadState.Failed -> MaterialTheme.colorScheme.errorContainer
                                is DownloadState.Done -> Color(0xFF2E7D32)
                                else -> Color(0xFF1565C0)
                            },
                        ).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (state) {
                    is DownloadState.Downloading -> {
                        Text(
                            state.filename,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        if (state.progress >= 0f) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.width(120.dp),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.3f),
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.width(120.dp),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.3f),
                            )
                        }
                    }

                    is DownloadState.Done -> {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Saved to ${state.file.name}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    is DownloadState.Failed -> {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            state.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    is DownloadState.Idle -> {}
                }
            }
        }

        // "..." menu (bottom right) — only for images; videos get it in the controls bar
        // Hidden in fullscreen to keep the view immersive
        if (!isVideo && viewMode != ViewMode.FULLSCREEN) {
            Box(
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                MoreOptionsMenu(
                    menuExpanded = menuExpanded,
                    onExpandMenu = { menuExpanded = true },
                    onDismissMenu = { menuExpanded = false },
                    onSave = { triggerSave() },
                    onCopyUrl = {
                        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                        clipboard.setContents(StringSelection(urls[currentIndex]), null)
                    },
                    onOpenInBrowser = {
                        Desktop.getDesktop().browse(URI(urls[currentIndex]))
                    },
                )
            }
        }

        // Navigation arrows — hidden in fullscreen
        if (urls.size > 1 && viewMode != ViewMode.FULLSCREEN) {
            if (currentIndex > 0) {
                IconButton(
                    onClick = { currentIndex-- },
                    modifier = Modifier.align(Alignment.CenterStart).padding(16.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }

            if (currentIndex < urls.lastIndex) {
                IconButton(
                    onClick = { currentIndex++ },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(16.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoreOptionsMenu(
    menuExpanded: Boolean,
    onExpandMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onSave: () -> Unit,
    onCopyUrl: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    Box {
        IconButton(onClick = onExpandMenu, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = onDismissMenu,
        ) {
            DropdownMenuItem(
                text = { Text("Save") },
                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                onClick = {
                    onDismissMenu()
                    onSave()
                },
            )
            DropdownMenuItem(
                text = { Text("Copy URL") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                onClick = {
                    onDismissMenu()
                    onCopyUrl()
                },
            )
            DropdownMenuItem(
                text = { Text("Open in Browser") },
                leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                onClick = {
                    onDismissMenu()
                    onOpenInBrowser()
                },
            )
        }
    }
}
