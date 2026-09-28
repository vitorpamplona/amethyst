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
package com.vitorpamplona.amethyst.ui.components

import android.Manifest
import android.os.Build
import android.view.Window
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewModelScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.back
import com.vitorpamplona.amethyst.commons.resources.download_to_phone
import com.vitorpamplona.amethyst.commons.resources.media_download_has_started_toast
import com.vitorpamplona.amethyst.commons.resources.quick_action_share
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.ui.components.animatedViewerChromeInset
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size15dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.saveMediaToGallery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Chrome shared by the full-screen media viewers -- the zoomable image/video dialog and the PDF
// viewer. Both are opened the same way (tap a media card in a feed), so they immerse, auto-hide,
// and lay their controls out identically.

/**
 * Goes fully immersive for as long as the viewer is on screen: hides both OS bars and restores them
 * on the way out. BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE lets the user swipe to peek the bars back.
 */
@Composable
fun ImmersiveSystemBarsEffect(window: Window?) {
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/**
 * Lays a viewer control row along a screen edge -- the top by default, the bottom when [atBottom].
 *
 * The viewer hides the system bars, so the row would otherwise sit against the screen edge. It
 * takes its distance from [animatedViewerChromeInset], which follows the bar on and off screen
 * rather than permanently reserving room for it.
 *
 * Horizontal display-cutout insets are still applied outright: a landscape notch eats into the
 * sides whatever the bars are doing. The top cutout is deliberately not applied, because on a
 * punch-hole device it is a centred hole that the edge-anchored buttons are nowhere near -- and
 * honouring it as a full-width top inset would push them down by the height of a camera they do
 * not overlap.
 *
 * The row also holds a button's height whatever it carries, so content that outlives the buttons
 * doesn't shift as they come and go.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ViewerControlsRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = spacedBy(Size10dp),
    atBottom: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    // systemBars is visibility-aware under BEHAVIOR_DEFAULT: 0 while the bars are hidden, and the
    // real bar size once the user swipes them in -- so the controls follow them instead of sitting
    // under a bar or reserving space for one that is not there. The 16dp floor keeps them clear of
    // the rounded corners while hidden. Animated so the row slides rather than jumps.
    val animatedInset = animatedViewerChromeInset(atBottom)
    Row(
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(
                    top = if (atBottom) 0.dp else animatedInset,
                    bottom = if (atBottom) animatedInset else 0.dp,
                ).padding(horizontal = Size15dp, vertical = Size10dp)
                .fillMaxWidth()
                .heightIn(min = ButtonDefaults.MinHeight),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Leaves the viewer. Always the first item in the control row. */
@Composable
fun ViewerBackButton(onDismiss: () -> Unit) {
    OutlinedButton(
        onClick = onDismiss,
        contentPadding = PaddingValues(horizontal = Size5dp),
        colors = ButtonDefaults.outlinedButtonColors().copy(containerColor = MaterialTheme.colorScheme.background),
    ) {
        Icon(
            symbol = MaterialSymbols.AutoMirrored.ArrowBack,
            contentDescription = stringRes(Res.string.back),
        )
    }
}

/** Opens the share sheet for whatever the viewer is showing. The sheet anchors to the button. */
@Composable
fun ViewerShareButton(
    content: BaseMediaContent,
    popupExpanded: MutableState<Boolean>,
    accountViewModel: AccountViewModel,
) {
    OutlinedButton(
        onClick = { popupExpanded.value = true },
        contentPadding = PaddingValues(horizontal = Size5dp),
        colors = ButtonDefaults.outlinedButtonColors().copy(containerColor = MaterialTheme.colorScheme.background),
    ) {
        Icon(
            symbol = MaterialSymbols.Share,
            modifier = Size20Modifier,
            contentDescription = stringRes(Res.string.quick_action_share),
        )

        ShareMediaAction(
            accountViewModel = accountViewModel,
            popupExpanded = popupExpanded,
            content = content,
            onDismiss = { popupExpanded.value = false },
        )
    }
}

/**
 * Saves the media to the gallery. Q and up write through MediaStore and need no permission; older
 * releases ask for WRITE_EXTERNAL_STORAGE first and save as soon as it is granted.
 */
@Composable
@OptIn(ExperimentalPermissionsApi::class)
fun ViewerSaveToGalleryButton(
    content: BaseMediaContent,
    accountViewModel: AccountViewModel,
) {
    // The application context and the view model's scope, never the composition's: this button
    // lives inside the AnimatedVisibility that the auto-hide collapses two seconds after the tap
    // that started the download, and a rememberCoroutineScope job would be cancelled with it --
    // killing the save with no file and no error. Matches the download row in ShareMediaAction.
    val localContext = LocalContext.current.applicationContext
    val scope = accountViewModel.viewModelScope

    val writeStoragePermissionState =
        rememberPermissionState(Manifest.permission.WRITE_EXTERNAL_STORAGE) { isGranted ->
            if (isGranted) {
                scope.launch {
                    saveMediaToGallery(content, localContext, accountViewModel)
                }
                scope.launch {
                    Toast
                        .makeText(
                            localContext,
                            loadStringRes(Res.string.media_download_has_started_toast),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }

    OutlinedButton(
        onClick = {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                writeStoragePermissionState.status.isGranted
            ) {
                scope.launch(Dispatchers.IO) {
                    saveMediaToGallery(content, localContext, accountViewModel)
                }
                scope.launch {
                    Toast
                        .makeText(
                            localContext,
                            loadStringRes(Res.string.media_download_has_started_toast),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            } else {
                writeStoragePermissionState.launchPermissionRequest()
            }
        },
        contentPadding = PaddingValues(horizontal = Size5dp),
        colors = ButtonDefaults.outlinedButtonColors().copy(containerColor = MaterialTheme.colorScheme.background),
    ) {
        Icon(
            symbol = MaterialSymbols.Download,
            modifier = Size20Modifier,
            contentDescription = stringRes(Res.string.download_to_phone),
        )
    }
}
