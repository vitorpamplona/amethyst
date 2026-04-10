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
package com.vitorpamplona.amethyst.ios.nip05

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.Nip05State
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.Nip05VerifState
import com.vitorpamplona.amethyst.ios.cache.IosLocalCache
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05Client

/**
 * Displays NIP-05 verification badge for a user.
 *
 * Mirrors the Android NIP05VerificationDisplay but uses the iOS-specific
 * Nip05Fetcher (NSURLSession-based).
 *
 * Shows:
 * - ✓ user@domain.com (green) when verified
 * - ⟳ user@domain.com (yellow) when checking
 * - ✕ user@domain.com (red) when failed
 * - Nothing when user has no NIP-05
 */
@Composable
fun Nip05VerificationDisplay(
    pubKeyHex: String,
    localCache: IosLocalCache,
    modifier: Modifier = Modifier,
) {
    val user = localCache.getUserIfExists(pubKeyHex) ?: return
    val nip05State = user.nip05State()

    val nip05Flow by nip05State.flow.collectAsState()

    when (val state = nip05Flow) {
        is Nip05State.Exists -> {
            Nip05ExistsDisplay(
                state = state,
                modifier = modifier,
            )
        }

        is Nip05State.NotFound -> {
            // No NIP-05 set — show nothing
        }
    }
}

/**
 * Composable for when a NIP-05 identifier exists — displays the name,
 * verification icon, and domain.
 */
@Composable
private fun Nip05ExistsDisplay(
    state: Nip05State.Exists,
    modifier: Modifier = Modifier,
) {
    val verifState by state.verificationState.collectAsState()

    // Trigger verification if expired or not started
    LaunchedEffect(verifState) {
        try {
            state.checkAndUpdate {
                Nip05Client(
                    fetcher = IosNip05Fetcher(),
                    namecoinResolverBuilder = null, // Namecoin resolution handled separately on iOS
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            platform.Foundation.NSLog("NIP-05 verification error: " + (e.message ?: "unknown"))
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        val nip05 = state.nip05

        // Show user part if not "_"
        if (nip05.name != "_") {
            Text(
                text = nip05.name,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(2.dp))

        // Verification icon
        Nip05VerifIcon(verifState)

        Spacer(Modifier.width(2.dp))

        // Domain
        Text(
            text = nip05.domain,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Renders the verification state icon.
 */
@Composable
private fun Nip05VerifIcon(state: Nip05VerifState) {
    when (state) {
        is Nip05VerifState.Verified -> {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "NIP-05 verified",
                modifier = Modifier.size(14.dp),
                tint = Color(0xFF4CAF50), // Material Green 500
            )
        }

        is Nip05VerifState.Verifying, is Nip05VerifState.NotStarted -> {
            Icon(
                imageVector = Icons.Default.Downloading,
                contentDescription = "Checking NIP-05",
                modifier = Modifier.size(14.dp),
                tint = Color(0xFFFFC107), // Material Amber 500
            )
        }

        is Nip05VerifState.Failed, is Nip05VerifState.Error -> {
            Icon(
                imageVector = Icons.Default.Report,
                contentDescription = "NIP-05 verification failed",
                modifier = Modifier.size(14.dp),
                tint = Color(0xFFF44336), // Material Red 500
            )
        }
    }
}
