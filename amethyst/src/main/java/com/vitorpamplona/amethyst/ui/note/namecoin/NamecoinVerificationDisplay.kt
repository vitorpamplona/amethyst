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
package com.vitorpamplona.amethyst.ui.note.namecoin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.service.namecoin.NamecoinNameService
import com.vitorpamplona.amethyst.service.namecoin.NamecoinResolveState

/**
 * Display a Namecoin-verified identity badge.
 *
 * Shows the Namecoin name with a blockchain icon when verified.
 * Renders nothing if verification fails or is still loading.
 *
 * @param nip05 The nip05 field value ending in .bit or starting with id/
 * @param expectedPubkeyHex The profile's pubkey to verify against
 * @param modifier Standard compose modifier
 */
@Composable
fun NamecoinVerificationDisplay(
    nip05: String,
    expectedPubkeyHex: String,
    modifier: Modifier = Modifier,
) {
    var isVerified by remember(nip05, expectedPubkeyHex) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(nip05, expectedPubkeyHex) {
        isVerified = NamecoinNameService.getInstance().verifyNip05(nip05, expectedPubkeyHex)
    }

    if (isVerified == true) {
        NamecoinBadge(
            displayName = formatDisplayName(nip05),
            namespace = inferNamespace(nip05),
            modifier = modifier,
        )
    }
}

/**
 * The visual badge component.
 *
 * Shows: [chain icon] [display name]
 * With namespace-appropriate styling.
 */
@Composable
private fun NamecoinBadge(
    displayName: String,
    namespace: String, // "d/" or "id/"
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(top = 1.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        // Blockchain chain-link icon
        // In production, replace with a proper vector drawable.
        // For now, use a text glyph as placeholder.
        Text(
            text = "\u26D3", // ⛓ chain link emoji
            fontSize = 12.sp,
            color = NamecoinColors.chainIcon,
            modifier = Modifier.padding(end = 2.dp),
        )

        // Namespace indicator
        Text(
            text = displayName,
            fontSize = 14.sp,
            color = NamecoinColors.verifiedText,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Composable for the search result when a Namecoin name resolves.
 *
 * Shows the resolved Namecoin name, the Nostr pubkey, and relay hints.
 * Used in the search results list.
 */
@Composable
fun NamecoinSearchResult(
    identifier: String,
    onProfileClick: (pubkeyHex: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolveState by NamecoinNameService
        .getInstance()
        .resolveLive(identifier)
        .collectAsState()

    when (val state = resolveState) {
        is NamecoinResolveState.Loading -> {
            Row(
                modifier = modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "\u26D3", // ⛓
                    fontSize = 16.sp,
                    color = NamecoinColors.chainIcon,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Resolving $identifier via Namecoin…",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is NamecoinResolveState.Resolved -> {
            Row(
                modifier =
                    modifier
                        .clickable { onProfileClick(state.result.pubkey) }
                        .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "\u26D3", // ⛓
                    fontSize = 16.sp,
                    color = NamecoinColors.verified,
                )
                Spacer(Modifier.width(8.dp))
                // The resolved identity
                Text(
                    text = formatDisplayName(identifier),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "(${state.result.pubkey.take(8)}…)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is NamecoinResolveState.NotFound -> {
            Row(
                modifier = modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "No Nostr identity found for $identifier",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is NamecoinResolveState.Error -> {
            Row(
                modifier = modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Namecoin lookup failed: ${state.message}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ── Display Helpers ────────────────────────────────────────────────────

/**
 * Format a raw identifier for user-friendly display.
 *
 * "alice@example.bit"  → "alice@example.bit"
 * "_@example.bit"      → "example.bit"        (NIP-05 root convention)
 * "d/example"          → "example.bit"
 * "id/alice"           → "id/alice"
 */
private fun formatDisplayName(identifier: String): String {
    val input = identifier.trim()

    // _@domain.bit → show just domain.bit
    if (input.startsWith("_@")) {
        return input.removePrefix("_@")
    }

    // d/name → name.bit
    if (input.startsWith("d/", ignoreCase = true)) {
        return input.removePrefix("d/").removePrefix("D/") + ".bit"
    }

    // id/name stays as-is
    if (input.startsWith("id/", ignoreCase = true)) {
        return input
    }

    return input
}

private fun inferNamespace(identifier: String): String {
    val input = identifier.trim().lowercase()
    return when {
        input.startsWith("id/") -> "id/"
        else -> "d/"
    }
}

/**
 * Color palette for Namecoin verification UI.
 */
private object NamecoinColors {
    val chainIcon = Color(0xFF4A90D9) // Namecoin blue
    val verified = Color(0xFF2E8B57) // Sea green — distinct from NIP-05 blue
    val verifiedText = Color(0xFF4A90D9) // Namecoin blue for the text
}
