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
package com.vitorpamplona.amethyst.commons.qrcode.ui.scanner

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vitorpamplona.amethyst.commons.qrcode.ScanBounds
import com.vitorpamplona.amethyst.commons.qrcode.ScanFrame
import com.vitorpamplona.amethyst.commons.qrcode.ScanPoint
import com.vitorpamplona.amethyst.commons.qrcode.ScanResult
import com.vitorpamplona.amethyst.commons.qrcode.classifyScannedPayload
import com.vitorpamplona.amethyst.commons.qrcode.ui.QrImageCodeChooser
import com.vitorpamplona.amethyst.commons.qrcode.ui.ScanOutcomeSheet
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.point_to_the_qr_code
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_camera_blocked_ios
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_camera_rationale
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_clipboard_empty
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_grant_camera
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_no_code_in_image
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_open_settings
import com.vitorpamplona.amethyst.commons.resources.qr_scanner_unavailable
import com.vitorpamplona.amethyst.commons.ui.actions.uploads.pickFromPhotoLibrary
import com.vitorpamplona.amethyst.commons.ui.components.SetDialogToEdgeToEdge
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode.ScanOutcome
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.quartz.utils.Log
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset1280x720
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.hasTorch
import platform.AVFoundation.requestAccessForMediaType
import platform.AVFoundation.torchMode
import platform.AVFoundation.videoZoomFactor
import platform.CoreGraphics.CGRectZero
import platform.CoreImage.CIDetector
import platform.CoreImage.CIDetectorAccuracy
import platform.CoreImage.CIDetectorAccuracyHigh
import platform.CoreImage.CIDetectorTypeQRCode
import platform.CoreImage.CIImage
import platform.CoreImage.CIQRCodeFeature
import platform.Foundation.NSURL
import platform.QuartzCore.CATransaction
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIColor
import platform.UIKit.UIImage
import platform.UIKit.UIPasteboard
import platform.UIKit.UIView
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import kotlin.time.TimeSource

private const val TAG = "IosQrScanner"
private const val NOTICE_DURATION_MS = 3_000L
private const val FOCUS_RING_DURATION_MS = 800L
private const val MAX_ZOOM = 8f
private const val MIN_TAP_RADIUS_PX = 120f

/**
 * The full-screen QR scanner on iOS: the shared scanner (QrScannerState, QrScannerControls, the
 * "can't open this" sheet, the several-codes chooser) over AVFoundation's QR detector, as Android
 * draws it over CameraX and zxing-cpp. A dialog, not a screen, because the logged-out login field
 * opens it too.
 */
@Composable
fun IosQrCodeScannerDialog(
    onDismiss: () -> Unit,
    onScan: (String) -> ScanOutcome,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        SetDialogToEdgeToEdge()
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            var status by remember { mutableStateOf(AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) }
            var asked by remember { mutableStateOf(false) }
            val ask = {
                asked = true
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { _ ->
                    dispatch_async(dispatch_get_main_queue()) { status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) }
                }
            }
            LaunchedEffect(Unit) { if (status == AVAuthorizationStatusNotDetermined) ask() }

            if (status == AVAuthorizationStatusAuthorized) {
                CameraScanner(onDismiss, onScan)
            } else {
                CameraPermissionGate(blocked = status != AVAuthorizationStatusNotDetermined || asked, onAsk = ask, onDismiss = onDismiss)
            }
        }
    }
}

/** Asks for the camera, and explains itself when refused: iOS asks only once, then only Settings can. */
@Composable
private fun CameraPermissionGate(
    blocked: Boolean,
    onAsk: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringRes(if (blocked) Res.string.qr_scanner_camera_blocked_ios else Res.string.qr_scanner_camera_rationale),
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (blocked) {
            Button(onClick = { openAppSettings() }) { Text(stringRes(Res.string.qr_scanner_open_settings)) }
        } else {
            Button(onClick = onAsk) { Text(stringRes(Res.string.qr_scanner_grant_camera)) }
        }
        TextButton(onClick = onDismiss) { Text(stringRes(Res.string.close), color = Color.White) }
    }
}

private fun openAppSettings() {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun CameraScanner(
    onDismiss: () -> Unit,
    onScan: (String) -> ScanOutcome,
) {
    val haptic = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val clock = remember { TimeSource.Monotonic.markNow() }
    val nowMs = { clock.elapsedNow().inWholeMilliseconds }

    val state = remember { QrScannerState() }
    val camera = remember { QrCamera() }

    val currentOnScan by rememberUpdatedState(onScan)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val noCodeInImage = stringRes(Res.string.qr_scanner_no_code_in_image)
    val clipboardEmpty = stringRes(Res.string.qr_scanner_clipboard_empty)
    val unavailable = stringRes(Res.string.qr_scanner_unavailable)

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var focusRing by remember { mutableStateOf<Offset?>(null) }

    // One accept path for camera reads, a tapped candidate, a picked picture and a paste, as on Android.
    val submit: (String) -> Unit = { scanned ->
        val text = scanned.trim()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        when (currentOnScan(text)) {
            ScanOutcome.Handled -> currentOnDismiss()
            ScanOutcome.NotSupported -> state.onRejected(classifyScannedPayload(text))
        }
    }

    // Several codes in one picture go to the chooser; one opens; none says so.
    val readCodes: (List<String>) -> Unit = { texts ->
        when (texts.size) {
            0 -> state.notice = noCodeInImage
            1 -> submit(texts.first())
            else -> state.imageCodes = texts.map(::classifyScannedPayload)
        }
    }

    DisposableEffect(camera) {
        camera.onCodes = { results ->
            // The detector reports in the preview layer's points; the overlay draws in pixels.
            val frame = ScanFrame(viewSize.width, viewSize.height)
            val scaled = results.map { it.copy(bounds = it.bounds?.scaled(density)) }
            if (!state.isAwaitingUser) state.onFrame(FrameScan(scaled, frame, brightness = 0.5f), nowMs())?.let(submit)
        }
        if (!camera.start()) state.notice = unavailable
        state.torchAvailable = camera.hasTorch
        state.maxZoomRatio = camera.maxZoom
        onDispose { camera.stop() }
    }

    LaunchedEffect(camera) { snapshotFlow { state.torchOn }.collect { camera.setTorch(it) } }
    LaunchedEffect(camera) { snapshotFlow { state.zoomRatio }.collect { camera.setZoom(it) } }

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

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) state.zoomRatio = (state.zoomRatio * zoom).coerceIn(1f, maxOf(1f, state.maxZoomRatio))
                    }
                }.pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val picked = pickCandidateAt(tap, state, viewSize)
                        if (picked != null) state.onCandidateTapped(picked, nowMs())?.let(submit) else focusRing = tap
                    }
                },
    ) {
        UIKitView(
            factory = { CameraPreviewView(camera.session).also { camera.attach(it.previewLayer) } },
            modifier = Modifier.fillMaxSize(),
            properties = UIKitInteropProperties(isInteractive = false, isNativeAccessibilityEnabled = false),
        )

        val highlight = MaterialTheme.colorScheme.primary
        Canvas(modifier = Modifier.fillMaxSize()) {
            val mapping = ScanViewMapping.of(state.frame, viewSize)
            if (state.candidates.isEmpty()) {
                drawViewfinderBrackets(Color.White.copy(alpha = 0.65f))
            } else if (mapping != null) {
                state.candidates.forEach { candidate ->
                    candidate.bounds?.let { drawPath(path = it.toViewPath(mapping), color = highlight, style = Stroke(width = 4.dp.toPx())) }
                }
            }
            focusRing?.let { drawCircle(color = Color.White.copy(alpha = 0.8f), radius = 28.dp.toPx(), center = it, style = Stroke(width = 2.dp.toPx())) }
        }

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
            onPickImage = {
                scope.launch {
                    val picked = pickFromPhotoLibrary(selectionLimit = 1, imagesOnly = true).firstOrNull() ?: return@launch
                    val codes = withContext(Dispatchers.IO) { CIImage.imageWithContentsOfURL(NSURL.fileURLWithPath(picked.uri.toString()))?.let(::qrCodesIn) }
                    readCodes(codes.orEmpty())
                }
            },
            onPaste = {
                val pasteboard = UIPasteboard.generalPasteboard
                val image = if (pasteboard.hasImages) pasteboard.image else null
                when {
                    image != null -> readCodes(qrCodesIn(image))
                    pasteboard.hasStrings && !pasteboard.string.isNullOrBlank() -> submit(pasteboard.string!!)
                    else -> state.notice = clipboardEmpty
                }
            },
        )
    }

    if (state.imageCodes.isNotEmpty()) {
        QrImageCodeChooser(
            codes = state.imageCodes,
            note = state.imageCodesNote,
            onPick = { picked ->
                state.imageCodes = emptyList()
                submit(picked.raw)
            },
            onDismiss = { state.imageCodes = emptyList() },
        )
    }

    state.rejected?.let { payload ->
        ScanOutcomeSheet(
            payload = payload,
            onDismiss = { state.dismissRejection(nowMs()) },
            onOpenLink = { url ->
                runCatching { uriHandler.openUri(url) }
                state.dismissRejection(nowMs())
                currentOnDismiss()
            },
            onCopy = { text ->
                UIPasteboard.generalPasteboard.string = text
                state.dismissRejection(nowMs())
            },
            onOpenProfile = { npub ->
                state.dismissRejection(nowMs())
                submit(npub)
            },
        )
    }
}

private fun ScanBounds.scaled(factor: Float): ScanBounds {
    fun ScanPoint.times() = ScanPoint(x * factor, y * factor)
    return ScanBounds(topLeft.times(), topRight.times(), bottomRight.times(), bottomLeft.times())
}

/** The visible code nearest the tap, when more than one is in view. */
private fun pickCandidateAt(
    tap: Offset,
    state: QrScannerState,
    viewSize: IntSize,
): ScanResult? {
    if (state.candidates.size <= 1) return null
    val mapping = ScanViewMapping.of(state.frame, viewSize) ?: return null
    return state.candidates
        .filterNot { it.isPartOfSequence }
        .mapNotNull { candidate ->
            val bounds = candidate.bounds ?: return@mapNotNull null
            val distance = (bounds.centerInView(mapping) - tap).getDistance()
            val radius = maxOf(bounds.longestSideInView(mapping), MIN_TAP_RADIUS_PX)
            if (distance <= radius) candidate to distance else null
        }.minByOrNull { it.second }
        ?.first
}

/** Every QR code CoreImage finds in [image]. */
private fun qrCodesIn(image: CIImage): List<String> {
    val detector = CIDetector.detectorOfType(CIDetectorTypeQRCode, context = null, options = mapOf<Any?, Any?>(CIDetectorAccuracy to CIDetectorAccuracyHigh)) ?: return emptyList()
    return detector.featuresInImage(image).mapNotNull { (it as? CIQRCodeFeature)?.messageString?.ifBlank { null } }.distinct()
}

@OptIn(ExperimentalForeignApi::class)
private fun qrCodesIn(image: UIImage): List<String> = (image.CIImage ?: image.CGImage?.let { CIImage.imageWithCGImage(it) })?.let(::qrCodesIn).orEmpty()

/** The capture session: the back camera into a QR detector, started off the main thread. */
@OptIn(ExperimentalForeignApi::class)
private class QrCamera {
    val session = AVCaptureSession()
    private val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
    private val output = AVCaptureMetadataOutput()
    private var previewLayer: AVCaptureVideoPreviewLayer? = null

    var onCodes: (List<ScanResult>) -> Unit = {}

    val hasTorch: Boolean get() = device?.hasTorch == true
    val maxZoom: Float get() = minOf(MAX_ZOOM, device?.activeFormat?.videoMaxZoomFactor?.toFloat() ?: 1f)

    private val delegate =
        object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
            override fun captureOutput(
                output: AVCaptureOutput,
                didOutputMetadataObjects: List<*>,
                fromConnection: AVCaptureConnection,
            ) {
                val layer = previewLayer
                val results =
                    didOutputMetadataObjects.mapNotNull { obj ->
                        val code = obj as? AVMetadataMachineReadableCodeObject ?: return@mapNotNull null
                        val text = code.stringValue ?: return@mapNotNull null
                        // In the preview layer's coordinates, so the outline lands on the code on screen.
                        val shown = layer?.transformedMetadataObjectForMetadataObject(code) ?: code
                        val bounds =
                            shown.bounds.useContents {
                                val left = origin.x.toFloat()
                                val top = origin.y.toFloat()
                                val right = left + size.width.toFloat()
                                val bottom = top + size.height.toFloat()
                                ScanBounds(ScanPoint(left, top), ScanPoint(right, top), ScanPoint(right, bottom), ScanPoint(left, bottom))
                            }
                        ScanResult(text, bounds)
                    }
                onCodes(results)
            }
        }

    /** Wires and starts the session; false when there is no camera to read from. */
    fun start(): Boolean {
        val camera = device ?: return false
        val input = AVCaptureDeviceInput.deviceInputWithDevice(camera, error = null) as? AVCaptureDeviceInput ?: return false
        session.beginConfiguration()
        if (session.canSetSessionPreset(AVCaptureSessionPreset1280x720)) session.sessionPreset = AVCaptureSessionPreset1280x720
        if (!session.canAddInput(input) || !session.canAddOutput(output)) {
            session.commitConfiguration()
            Log.w(TAG) { "The camera cannot feed a QR detector" }
            return false
        }
        session.addInput(input)
        session.addOutput(output)
        output.setMetadataObjectsDelegate(delegate, queue = dispatch_get_main_queue())
        // Only after the output joins the session: before, it offers no types at all.
        output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        session.commitConfiguration()
        // startRunning blocks until the camera is up.
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) { session.startRunning() }
        return true
    }

    fun attach(layer: AVCaptureVideoPreviewLayer) {
        previewLayer = layer
    }

    fun stop() {
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) { session.stopRunning() }
    }

    fun setTorch(on: Boolean) = configure { if (it.hasTorch) it.torchMode = if (on) AVCaptureTorchModeOn else AVCaptureTorchModeOff }

    fun setZoom(ratio: Float) = configure { it.videoZoomFactor = ratio.coerceIn(1f, maxZoom).toDouble() }

    private fun configure(change: (AVCaptureDevice) -> Unit) {
        val camera = device ?: return
        if (!camera.lockForConfiguration(null)) return
        try {
            change(camera)
        } finally {
            camera.unlockForConfiguration()
        }
    }
}

/** The camera feed, filling the view as the overlay assumes (aspect fill, as Android's FILL_CENTER). */
@OptIn(ExperimentalForeignApi::class)
private class CameraPreviewView(
    session: AVCaptureSession,
) : UIView(frame = CGRectZero.readValue()) {
    val previewLayer = AVCaptureVideoPreviewLayer(session = session)

    init {
        backgroundColor = UIColor.blackColor
        userInteractionEnabled = false
        previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.frame = bounds
        CATransaction.commit()
    }
}
