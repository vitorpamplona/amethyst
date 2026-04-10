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
package com.vitorpamplona.amethyst.ios.ui.qr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

/**
 * A UIView subclass that hosts an AVCaptureVideoPreviewLayer and keeps it
 * sized to the view bounds via layoutSubviews.
 */
@OptIn(ExperimentalForeignApi::class)
private class CameraPreviewUIView(
    session: AVCaptureSession,
) : UIView(frame = CGRectMake(0.0, 0.0, 1.0, 1.0)) {
    private val previewLayer: AVCaptureVideoPreviewLayer =
        AVCaptureVideoPreviewLayer(session = session).apply {
            videoGravity = AVLayerVideoGravityResizeAspectFill
        }

    init {
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setValue(true, forKey = kCATransactionDisableActions)
        previewLayer.frame = bounds
        CATransaction.commit()
    }

    fun updateLayout() {
        setNeedsLayout()
    }
}

/**
 * Composable that shows an AVFoundation-based QR code scanner.
 * Calls [onScanned] with the decoded string when a QR code is detected.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
fun QrScannerView(
    onScanned: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var cameraPermission by remember { mutableStateOf<CameraPermission>(CameraPermission.Unknown) }
    var hasScanned by remember { mutableStateOf(false) }

    // Check camera permission on first composition
    DisposableEffect(Unit) {
        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        cameraPermission =
            when (status) {
                AVAuthorizationStatusAuthorized -> {
                    CameraPermission.Granted
                }

                AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> {
                    CameraPermission.Denied
                }

                AVAuthorizationStatusNotDetermined -> {
                    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                        cameraPermission =
                            if (granted) CameraPermission.Granted else CameraPermission.Denied
                    }
                    CameraPermission.Unknown
                }

                else -> {
                    CameraPermission.Unknown
                }
            }
        onDispose {}
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        when (cameraPermission) {
            CameraPermission.Granted -> {
                CameraPreview(
                    onScanned = { code ->
                        if (!hasScanned) {
                            hasScanned = true
                            onScanned(code)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Scanner overlay
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    // Semi-transparent overlay hint
                    Text(
                        "Point camera at a QR code",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 120.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.6f),
                                    RoundedCornerShape(8.dp),
                                ).padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            CameraPermission.Denied -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Camera access is required to scan QR codes.\n\nPlease enable camera access in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            CameraPermission.Unknown -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Requesting camera access…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                }
            }
        }

        // Close button
        IconButton(
            onClick = onDismiss,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .size(44.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close scanner",
                tint = Color.White,
            )
        }
    }
}

/**
 * Full-screen QR scanner dialog with a paste-from-clipboard fallback.
 */
@Composable
fun QrScannerSheet(
    onResult: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showScanner by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (showScanner) {
            QrScannerView(
                onScanned = onResult,
                onDismiss = onDismiss,
            )
        }

        // Paste fallback at the bottom
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OutlinedButton(
                onClick = {
                    val clipboard = platform.UIKit.UIPasteboard.generalPasteboard.string
                    if (!clipboard.isNullOrBlank()) {
                        onResult(clipboard)
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
            ) {
                Text("Paste from Clipboard", color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
private fun CameraPreview(
    onScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val captureSession = remember { AVCaptureSession() }

    DisposableEffect(Unit) {
        setupCaptureSession(captureSession, onScanned)
        captureSession.startRunning()
        onDispose {
            captureSession.stopRunning()
        }
    }

    UIKitView(
        factory = {
            val view = CameraPreviewUIView(captureSession)
            view
        },
        modifier = modifier,
        update = { view ->
            view.updateLayout()
        },
    )
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun setupCaptureSession(
    session: AVCaptureSession,
    onScanned: (String) -> Unit,
) {
    val device =
        AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return

    val input =
        try {
            AVCaptureDeviceInput(device = device, error = null)
        } catch (_: Exception) {
            return
        }

    if (session.canAddInput(input)) {
        session.addInput(input)
    }

    val metadataOutput = AVCaptureMetadataOutput()

    if (session.canAddOutput(metadataOutput)) {
        session.addOutput(metadataOutput)

        val delegate =
            QrMetadataDelegate { code ->
                onScanned(code)
            }

        metadataOutput.setMetadataObjectsDelegate(delegate, queue = dispatch_get_main_queue())

        if (metadataOutput.availableMetadataObjectTypes.contains(AVMetadataObjectTypeQRCode)) {
            metadataOutput.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        }
    }
}

private class QrMetadataDelegate(
    private val onScanned: (String) -> Unit,
) : NSObject(),
    AVCaptureMetadataOutputObjectsDelegateProtocol {
    override fun captureOutput(
        output: platform.AVFoundation.AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: platform.AVFoundation.AVCaptureConnection,
    ) {
        val code =
            didOutputMetadataObjects
                .filterIsInstance<AVMetadataMachineReadableCodeObject>()
                .firstOrNull { it.type == AVMetadataObjectTypeQRCode }
                ?.stringValue

        if (code != null) {
            onScanned(code)
        }
    }
}

private enum class CameraPermission {
    Unknown,
    Granted,
    Denied,
}
