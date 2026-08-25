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
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewModelScope
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size15dp
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Chrome shared by the full-screen media viewers -- the zoomable image/video dialog and the PDF
// viewer. Both are opened the same way (tap a media card in a feed), so they immerse, auto-hide,
// and lay their controls out identically.

// How long the controls stay up before the viewer fades them out on its own.
private const val CONTROLS_AUTO_HIDE_DELAY_MS = 2000L

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
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/**
 * Visibility of the viewer controls: they start on screen, fade out on their own after
 * [CONTROLS_AUTO_HIDE_DELAY_MS], and the caller flips the returned state on tap.
 *
 * [holdOpen] freezes the timer while something anchored to the controls -- the share sheet, say --
 * is up, and re-arms it once that closes. [armed] withholds the countdown until there is something
 * to look at, so a viewer that spends three seconds fetching its media doesn't reveal the first
 * frame with the controls already gone.
 *
 * A tap that brings the controls back deliberately gets no timer: the user asked for them, so they
 * stay until tapped away. That is why the countdown races the controls going away rather than just
 * sleeping -- a timer left over from an earlier show would otherwise wipe controls the user tapped
 * back up in the meantime.
 */
@Composable
fun rememberViewerControlsVisibility(
    holdOpen: Boolean,
    armed: Boolean = true,
): MutableState<Boolean> {
    val visible = remember { mutableStateOf(true) }

    LaunchedEffect(armed, holdOpen) {
        if (!armed || holdOpen) return@LaunchedEffect

        val hiddenFirst =
            withTimeoutOrNull(CONTROLS_AUTO_HIDE_DELAY_MS) {
                snapshotFlow { visible.value }.first { !it }
            }

        if (hiddenFirst == null) visible.value = false
    }

    return visible
}

/**
 * Lays the viewer's control row along the top edge.
 *
 * The viewers hide the system bars, which drops statusBarsPadding() to zero and lands the controls
 * against the screen edge. That strip stays owned by the system while
 * BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE is set -- it is the area watching for the swipe that peeks
 * the bars back, and there is no API to turn it off -- so touches there never reach the buttons and
 * only their lower halves respond. Reserving the space the bars would take even while they are
 * hidden keeps the whole button out of that strip, and keeps the controls from jumping when the
 * user swipes the bars back in.
 *
 * The row also holds a button's height whatever it carries, so content that outlives the buttons
 * (a page counter) doesn't shift as they come and go.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ViewerControlsRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = spacedBy(Size10dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .windowInsetsPadding(
                    WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout),
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
            contentDescription = stringRes(R.string.back),
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
            contentDescription = stringRes(R.string.quick_action_share),
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
                            stringRes(localContext, R.string.media_download_has_started_toast),
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
                            stringRes(localContext, R.string.media_download_has_started_toast),
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
            contentDescription = stringRes(R.string.download_to_phone),
        )
    }
}
