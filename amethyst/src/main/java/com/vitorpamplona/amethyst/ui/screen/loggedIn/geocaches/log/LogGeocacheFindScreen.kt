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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.geocaches.log

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.geocache_log_add_photo
import com.vitorpamplona.amethyst.commons.resources.geocache_log_cache_label
import com.vitorpamplona.amethyst.commons.resources.geocache_log_mission_answer
import com.vitorpamplona.amethyst.commons.resources.geocache_log_no_location_attached
import com.vitorpamplona.amethyst.commons.resources.geocache_log_placeholder
import com.vitorpamplona.amethyst.commons.resources.geocache_log_post
import com.vitorpamplona.amethyst.commons.resources.geocache_log_proof_bad_code
import com.vitorpamplona.amethyst.commons.resources.geocache_log_proof_explain
import com.vitorpamplona.amethyst.commons.resources.geocache_log_proof_optional
import com.vitorpamplona.amethyst.commons.resources.geocache_log_proof_section
import com.vitorpamplona.amethyst.commons.resources.geocache_log_proof_verified
import com.vitorpamplona.amethyst.commons.resources.geocache_log_proof_wrong_cache
import com.vitorpamplona.amethyst.commons.resources.geocache_log_scan_again
import com.vitorpamplona.amethyst.commons.resources.geocache_log_scan_code
import com.vitorpamplona.amethyst.commons.resources.geocache_log_your_log
import com.vitorpamplona.amethyst.commons.resources.geocache_unnamed
import com.vitorpamplona.amethyst.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.geocaches.datasource.GeocachesFilterAssemblerSubscription
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.SimpleQrCodeScanner
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.Address
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The kind 7516 composer, and the only screen allowed to scan a cache's verification secret.
 *
 * That restriction matters: the app's general QR scanner maps what it reads to a navigation
 * route, which is right for a cache's published `naddr` and catastrophic for a private key. Here
 * the scanner is the raw-string variant, the result never becomes a route, and the key is
 * consumed inside the view model and dropped.
 *
 * The cache's name sits fixed at the top rather than being editable, so there is no way to log
 * the wrong cache, and the screen says out loud that the finder's own location is not attached —
 * the one privacy question a geocaching client owes an answer to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogGeocacheFindScreen(
    kind: Int,
    pubKeyHex: String,
    dTag: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    GeocachesFilterAssemblerSubscription(accountViewModel)

    val address = remember(kind, pubKeyHex, dTag) { Address(kind, pubKeyHex, dTag) }
    val model: LogGeocacheFindViewModel = viewModel(key = "LogGeocacheFind-${address.toValue()}")
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    remember(address) {
        model.init(accountViewModel, address)
        true
    }

    val listing = model.listing()
    var scanning by remember { mutableStateOf(false) }

    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                scope.launch { model.uploadImage(uri, null, context) }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.route_log_geocache_find)) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBack() }) {
                        Icon(
                            symbol = MaterialSymbols.Close,
                            contentDescription = stringRes(R.string.back),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    TextButton(
                        enabled = model.isValid() && !model.isPublishing.value,
                        onClick = {
                            scope.launch {
                                if (model.publish()) nav.popBack()
                            }
                        },
                    ) { Text(stringResource(Res.string.geocache_log_post)) }
                },
            )
        },
    ) { pad ->
        Column(
            modifier =
                Modifier
                    .padding(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding())
                    .consumeWindowInsets(pad)
                    .imePaddingSafe()
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(Res.string.geocache_log_cache_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = listing?.cacheName()?.trim()?.ifBlank { null } ?: stringResource(Res.string.geocache_unnamed),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = model.message.value,
                onValueChange = { model.message.value = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(Res.string.geocache_log_your_log)) },
                placeholder = { Text(stringResource(Res.string.geocache_log_placeholder)) },
                minLines = 4,
            )

            if (listing?.hasMission() == true) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = model.missionAnswer.value,
                    onValueChange = { model.missionAnswer.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.geocache_log_mission_answer)) },
                    minLines = 2,
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !model.isUploading.value,
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                ) {
                    if (model.isUploading.value) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(Res.string.geocache_log_add_photo))
                    }
                }

                if (model.images.isNotEmpty()) {
                    Text(
                        text = "📷 ${model.images.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(Res.string.geocache_log_proof_section),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(6.dp))

            if (listing?.requiresVerification() != true) {
                Text(
                    text = stringResource(Res.string.geocache_log_proof_optional),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                when (model.scanOutcome.value) {
                    ScanOutcome.VERIFIED ->
                        ProofNotice(stringResource(Res.string.geocache_log_proof_verified), MaterialTheme.colorScheme.primary)
                    ScanOutcome.NOT_A_KEY ->
                        ProofNotice(stringResource(Res.string.geocache_log_proof_bad_code), MaterialTheme.colorScheme.error)
                    ScanOutcome.WRONG_CACHE ->
                        ProofNotice(stringResource(Res.string.geocache_log_proof_wrong_cache), MaterialTheme.colorScheme.error)
                    ScanOutcome.NONE -> Unit
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(onClick = { scanning = true }) {
                    Icon(
                        symbol = MaterialSymbols.QrCode2,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        if (model.hasProof()) {
                            stringResource(Res.string.geocache_log_scan_again)
                        } else {
                            stringResource(Res.string.geocache_log_scan_code)
                        },
                    )
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = stringResource(Res.string.geocache_log_proof_explain),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (scanning) {
                SimpleQrCodeScanner { scanned ->
                    scanning = false
                    scope.launch { model.consumeScannedCode(scanned) }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = stringResource(Res.string.geocache_log_no_location_attached),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProofNotice(
    text: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(10.dp),
        )
    }
}
