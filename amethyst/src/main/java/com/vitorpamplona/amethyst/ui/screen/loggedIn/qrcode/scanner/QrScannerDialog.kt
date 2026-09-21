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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.scanner

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.point_to_the_qr_code
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_camera_blocked
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_camera_rationale
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_clipboard_empty
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_grant_camera
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_no_code_in_image
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_open_settings
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_unavailable
import com.vitorpamplona.amethyst.ui.call.openAppSettings
import com.vitorpamplona.amethyst.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min

/** What the caller did with a decoded payload — and therefore what the scanner does next. */
enum class ScanOutcome {
    /** The caller acted on it. Close the scanner. */
    Handled,

    /** Decoded fine, but this screen has nothing to do with it. Explain, and keep scanning. */
    NotSupported,
}

/**
 * Full-screen QR scanner.
 *
 * A dialog rather than a navigation route on purpose: one of its callers is the logged-out login
 * field, which lives outside the navigation graph entirely.
 */
@Composable
fun QrCodeScannerDialog(
    onDismiss: () -> Unit,
    onScan: (String) -> ScanOutcome,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        SetDialogToEdgeToEdge()
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            QrScannerScreen(onDismiss = onDismiss, onScan = onScan)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun QrScannerScreen(
    onDismiss: () -> Unit,
    onScan: (String) -> ScanOutcome,
) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    if (cameraPermission.status.isGranted) {
        QrCameraScanner(onDismiss = onDismiss, onScan = onScan)
    } else {
        CameraPermissionGate(permission = cameraPermission, onDismiss = onDismiss)
    }
}

/**
 * Asks for the camera, and explains itself when refused.
 *
 * The old scanner simply closed its activity when the permission was denied, which from the
 * user's side is a button that does nothing.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun CameraPermissionGate(
    permission: PermissionState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var asked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        asked = true
        permission.launchPermissionRequest()
    }

    // `shouldShowRationale` is false both before the first ask and after a permanent denial, so
    // the two are only distinguishable once we know we have asked.
    val blocked = asked && !permission.status.shouldShowRationale

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text =
                if (blocked) {
                    stringRes(Res.string.qr_scanner_camera_blocked)
                } else {
                    stringRes(Res.string.qr_scanner_camera_rationale)
                },
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )

        if (blocked) {
            Button(onClick = { openAppSettings(context) }) {
                Text(stringRes(Res.string.qr_scanner_open_settings))
            }
        } else {
            Button(onClick = { permission.launchPermissionRequest() }) {
                Text(stringRes(Res.string.qr_scanner_grant_camera))
            }
        }

        // Closes the scanner, so it says so: with the camera refused there is nothing to scan again.
        TextButton(onClick = onDismiss) {
            Text(stringRes(Res.string.close), color = Color.White)
        }
    }
}

@Composable
private fun QrCameraScanner(
    onDismiss: () -> Unit,
    onScan: (String) -> ScanOutcome,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val state = remember { QrScannerState() }
    val decoder = remember { runCatching { ZxingCppBarcodeDecoder() }.getOrNull() }

    val currentOnScan by rememberUpdatedState(onScan)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val noCodeInImage = stringRes(Res.string.qr_scanner_no_code_in_image)
    val clipboardEmpty = stringRes(Res.string.qr_scanner_clipboard_empty)
    val decoderUnavailable = stringRes(Res.string.qr_scanner_unavailable)

    // One shared accept path: camera frames, a tapped candidate and an imported image all land
    // here, so the haptic, the dedupe and the "we can't open this" branch behave identically
    // however the payload arrived.
    val submit: (String) -> Unit = { text ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        when (currentOnScan(text)) {
            ScanOutcome.Handled -> currentOnDismiss()
            ScanOutcome.NotSupported -> state.onRejected(classifyScannedPayload(text))
        }
    }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    // CONFLATED: the analysis thread outruns the UI, and an old frame is worthless — the only
    // one worth acting on is the newest.
    val frames = remember { Channel<FrameScan>(Channel.CONFLATED) }

    val previewView =
        remember {
            PreviewView(context).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var focusRing by remember { mutableStateOf<Offset?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            frames.close()
            runCatching { provider?.unbindAll() }
        }
    }

    LaunchedEffect(decoder) {
        if (decoder == null) state.notice = decoderUnavailable
    }

    // Bind once the preview has a size: ViewPort needs a laid-out view, and binding both use
    // cases under one viewport is what makes the overlay's coordinate mapping exact.
    LaunchedEffect(decoder, viewSize) {
        if (decoder == null || viewSize == IntSize.Zero) return@LaunchedEffect

        val cameraProvider =
            try {
                ProcessCameraProvider.awaitInstance(context)
            } catch (e: Exception) {
                Log.w("QrScanner", "Camera provider unavailable", e)
                state.notice = decoderUnavailable
                return@LaunchedEffect
            }
        provider = cameraProvider

        val preview = Preview.Builder().build().apply { setSurfaceProvider(previewView.surfaceProvider) }

        val analysis =
            ImageAnalysis
                .Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setResolutionSelector(ANALYSIS_RESOLUTION)
                .build()
                .apply {
                    setAnalyzer(analysisExecutor, QrFrameAnalyzer(decoder) { frames.trySend(it) })
                }

        val group =
            UseCaseGroup
                .Builder()
                .addUseCase(preview)
                .addUseCase(analysis)
                .apply { previewView.viewPort?.let { setViewPort(it) } }
                .build()

        try {
            cameraProvider.unbindAll()
            camera =
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group).also {
                    state.torchAvailable = it.cameraInfo.hasFlashUnit()
                    state.maxZoomRatio = it.cameraInfo.zoomState.value
                        ?.maxZoomRatio ?: 1f
                }
        } catch (e: Exception) {
            Log.w("QrScanner", "Could not bind the camera", e)
            state.notice = decoderUnavailable
        }
    }

    LaunchedEffect(Unit) {
        for (scan in frames) {
            state.onFrame(scan, SystemClock.elapsedRealtime())?.let(submit)
        }
    }

    // One writer each for torch and zoom, driven off state rather than from the gesture handlers,
    // so the auto-zoom sweep and a pinch cannot fight over the camera control.
    LaunchedEffect(camera) {
        val control = camera?.cameraControl ?: return@LaunchedEffect
        snapshotFlow { state.torchOn }.collect { runCatching { control.enableTorch(it) } }
    }

    LaunchedEffect(camera) {
        val control = camera?.cameraControl ?: return@LaunchedEffect
        snapshotFlow { state.zoomRatio }.collect { runCatching { control.setZoomRatio(it) } }
    }

    AutoZoomSweep(state = state, enabled = camera != null)

    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            delay(NOTICE_DURATION_MS)
            state.notice = null
        }
    }

    LaunchedEffect(focusRing) {
        if (focusRing != null) {
            delay(FOCUS_RING_DURATION_MS)
            focusRing = null
        }
    }

    val pickImage =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null || decoder == null) return@rememberLauncherForActivityResult
            scope.launch {
                val found = QrImageImport.decode(context, uri, decoder).firstOrNull()?.text
                if (found == null) state.notice = noCodeInImage else submit(found)
            }
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) {
                            state.onPinch()
                            state.zoomRatio = (state.zoomRatio * zoom).coerceIn(1f, maxOf(1f, state.maxZoomRatio))
                        }
                    }
                }.pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val picked = pickCandidateAt(tap, state, viewSize)
                        if (picked != null) {
                            state.onCandidateTapped(picked, SystemClock.elapsedRealtime())?.let(submit)
                        } else {
                            focusRing = tap
                            focusAt(previewView, camera, tap)
                        }
                    }
                },
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        ScanOverlayCanvas(state = state, viewSize = viewSize, focusRing = focusRing)

        Text(
            text = stringRes(Res.string.point_to_the_qr_code),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp).fillMaxWidth(0.8f),
        )

        QrScannerControls(
            state = state,
            onClose = onDismiss,
            onToggleTorch = { state.torchOn = !state.torchOn },
            onPickImage = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onPaste = {
                pasteFromClipboard(
                    context = context,
                    decoder = decoder,
                    onText = submit,
                    onImage = { uri ->
                        if (decoder != null) {
                            scope.launch {
                                val found = QrImageImport.decode(context, uri, decoder).firstOrNull()?.text
                                if (found == null) state.notice = noCodeInImage else submit(found)
                            }
                        }
                    },
                    onEmpty = { state.notice = clipboardEmpty },
                )
            },
        )
    }

    state.rejected?.let { payload ->
        ScanOutcomeSheet(
            payload = payload,
            onDismiss = { state.dismissRejection(SystemClock.elapsedRealtime()) },
            onOpenLink = { url ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                state.dismissRejection(SystemClock.elapsedRealtime())
                currentOnDismiss()
            },
            onCopy = { text ->
                copyToClipboard(context, text)
                state.dismissRejection(SystemClock.elapsedRealtime())
            },
        )
    }
}

/** Draws the aiming brackets, any codes we can see, and the tap-to-focus ring. */
@Composable
private fun ScanOverlayCanvas(
    state: QrScannerState,
    viewSize: IntSize,
    focusRing: Offset?,
) {
    val highlight = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.fillMaxSize()) {
        val mapping = ScanViewMapping.of(state.frame, viewSize)

        if (state.candidates.isEmpty()) {
            drawViewfinderBrackets(Color.White.copy(alpha = 0.65f))
        } else if (mapping != null) {
            state.candidates.forEach { candidate ->
                candidate.bounds?.let {
                    drawPath(
                        path = it.toViewPath(mapping),
                        color = highlight,
                        style = Stroke(width = 4.dp.toPx()),
                    )
                }
            }
        }

        focusRing?.let {
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = 28.dp.toPx(),
                center = it,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

/**
 * Sweeps the zoom while nothing is decoding.
 *
 * A code too small in frame to resolve is the single most common reason a scan fails, and no
 * amount of decoder tuning fixes it — there are not enough pixels per module to read. Rather than
 * leave the user to work that out and walk closer, the camera pushes in and back out on its own.
 * It stops for good once the user pinches: they have taken over.
 */
@Composable
private fun AutoZoomSweep(
    state: QrScannerState,
    enabled: Boolean,
) {
    LaunchedEffect(enabled, state.autoZoomEnabled) {
        if (!enabled || !state.autoZoomEnabled) return@LaunchedEffect

        var sweep = 0f
        while (isActive) {
            delay(AUTO_ZOOM_TICK_MS)

            if (state.msSinceLastDetection < QrScannerState.AUTO_ZOOM_AFTER_MS) {
                if (sweep != 0f) {
                    sweep = 0f
                    state.zoomRatio = 1f
                }
                continue
            }

            val ceiling = min(state.maxZoomRatio, QrScannerState.AUTO_ZOOM_MAX)
            if (ceiling <= 1.01f) continue

            sweep = (sweep + AUTO_ZOOM_TICK_MS.toFloat() / AUTO_ZOOM_PERIOD_MS) % 1f
            val triangle = if (sweep < 0.5f) sweep * 2f else (1f - sweep) * 2f
            state.zoomRatio = 1f + triangle * (ceiling - 1f)
        }
    }
}

/** The visible code nearest the tap, or null when the tap was not on one. */
private fun pickCandidateAt(
    tap: Offset,
    state: QrScannerState,
    viewSize: IntSize,
): ScanResult? {
    if (state.candidates.size <= 1) return null
    val mapping = ScanViewMapping.of(state.frame, viewSize) ?: return null

    return state.candidates
        .mapNotNull { candidate ->
            val bounds = candidate.bounds ?: return@mapNotNull null
            val center = bounds.centerInView(mapping)
            // In view pixels, to match `distance`. bounds.longestSide is image-space.
            val radius = maxOf(bounds.longestSideInView(mapping), MIN_TAP_RADIUS_PX)
            val distance = (center - tap).getDistance()
            if (distance <= radius) candidate to distance else null
        }.minByOrNull { it.second }
        ?.first
}

private fun focusAt(
    previewView: PreviewView,
    camera: Camera?,
    tap: Offset,
) {
    val control = camera?.cameraControl ?: return
    runCatching {
        val point = previewView.meteringPointFactory.createPoint(tap.x, tap.y)
        control.startFocusAndMetering(
            FocusMeteringAction
                .Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(FOCUS_AUTO_CANCEL_SECONDS, TimeUnit.SECONDS)
                .build(),
        )
    }
}

private fun pasteFromClipboard(
    context: Context,
    decoder: BarcodeDecoder?,
    onText: (String) -> Unit,
    onImage: (Uri) -> Unit,
    onEmpty: () -> Unit,
) {
    val image = QrImageImport.clipboardImage(context)
    if (image != null && decoder != null) {
        onImage(image)
        return
    }

    val text = QrImageImport.clipboardText(context)
    if (!text.isNullOrBlank()) onText(text) else onEmpty()
}

/** Shared with the shared-image scan screen, which offers the same action on the same sheet. */
internal fun copyToClipboard(
    context: Context,
    text: String,
) {
    runCatching {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("", text))
    }
}

/**
 * 1280x720 is the sweet spot: plenty of pixels per module for a code at arm's length, while
 * staying inside what every device can sustain at full frame rate through an analysis pipeline.
 * Falling back higher before lower keeps detail on devices that cannot produce exactly this.
 */
private val ANALYSIS_RESOLUTION =
    ResolutionSelector
        .Builder()
        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
        .setResolutionStrategy(
            ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
        ).build()

private const val AUTO_ZOOM_TICK_MS = 100L
private const val AUTO_ZOOM_PERIOD_MS = 3_000f
private const val NOTICE_DURATION_MS = 3_000L
private const val FOCUS_RING_DURATION_MS = 800L
private const val FOCUS_AUTO_CANCEL_SECONDS = 4L
private const val MIN_TAP_RADIUS_PX = 120f
