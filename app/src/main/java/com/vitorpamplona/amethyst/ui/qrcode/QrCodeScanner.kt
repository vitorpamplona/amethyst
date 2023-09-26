package com.vitorpamplona.amethyst.ui.qrcode

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.res.stringResource
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.quartz.encoders.Nip19

@Composable
fun NIP19QrCodeScanner(onScan: (String?) -> Unit) {
    SimpleQrCodeScanner {
        try {
            val nip19 = Nip19.uriToRoute(it)
            val startingPage = when (nip19?.type) {
                Nip19.Type.USER -> "User/${nip19.hex}"
                Nip19.Type.NOTE -> "Note/${nip19.hex}"
                Nip19.Type.EVENT -> "Event/${nip19.hex}"
                Nip19.Type.ADDRESS -> "Note/${nip19.hex}"
                else -> null
            }

            if (startingPage != null) {
                onScan(startingPage)
            } else {
                onScan(null)
            }
        } catch (e: Throwable) {
            Log.e("NIP19 Scanner", "Error parsing $it", e)
            // QR can be anything, do not throw errors.
            onScan(null)
        }
    }
}

@Composable
fun SimpleQrCodeScanner(onScan: (String?) -> Unit) {
    val qrLauncher =
        rememberLauncherForActivityResult(ScanContract()) {
            if (it.contents != null) {
                onScan(it.contents)
            } else {
                onScan(null)
            }
        }

    val scanOptions = ScanOptions().apply {
        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        setPrompt(stringResource(id = R.string.point_to_the_qr_code))
        setBeepEnabled(false)
        setOrientationLocked(false)
        addExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN)
    }

    DisposableEffect(Unit) {
        qrLauncher.launch(scanOptions)
        onDispose { }
    }
}
