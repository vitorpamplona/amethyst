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
package com.vitorpamplona.amethyst.desktop.ui.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.account.AccountInfo
import com.vitorpamplona.amethyst.commons.model.account.SignerType
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import kotlinx.collections.immutable.ImmutableList

/**
 * Resolve display name from cache. Returns null if no metadata or name matches hex fallback.
 */
private fun resolveDisplayName(
    npub: String,
    localCache: DesktopLocalCache?,
): String? {
    if (localCache == null) return null
    val pubkeyHex = decodePublicKeyAsHexOrNull(npub) ?: return null
    val user = localCache.getUserIfExists(pubkeyHex) ?: return null
    val name = user.toBestDisplayName()
    // Don't return hex fallback as "display name"
    return name.takeIf { it != user.pubkeyDisplayHex() }
}

/**
 * Middle-truncates an npub: "npub1abcdef...wxyz"
 */
private fun npubShortMiddle(npub: String): String {
    if (npub.length <= 20) return npub
    return "${npub.take(10)}...${npub.takeLast(6)}"
}

@Composable
fun AccountSwitcherDropdown(
    activeNpub: String?,
    allAccounts: ImmutableList<AccountInfo>,
    localCache: DesktopLocalCache?,
    onSwitchAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onRemoveAccount: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var confirmLogoutNpub by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = "Switch account",
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            offset = DpOffset(x = 48.dp, y = 0.dp),
            modifier = Modifier.heightIn(max = 400.dp),
            scrollState = rememberScrollState(),
        ) {
            allAccounts.forEach { account ->
                val isActive = account.npub == activeNpub

                // Resolve display name from cache (no remember — cheap lookup, re-evaluates each recomposition)
                val displayName = resolveDisplayName(account.npub, localCache)
                val shortNpub = npubShortMiddle(account.npub)
                val signerLabel =
                    when (account.signerType) {
                        is SignerType.Remote -> " · Bunker"
                        is SignerType.ViewOnly -> " · View only"
                        is SignerType.Internal -> ""
                    }

                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                // Title: display name or npub
                                Text(
                                    displayName ?: shortNpub,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                // Subtitle: only show if different from title
                                val subtitle = shortNpub + signerLabel
                                if (displayName != null) {
                                    Text(
                                        subtitle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else if (signerLabel.isNotEmpty()) {
                                    // No display name but has signer label — show it
                                    Text(
                                        signerLabel.removePrefix(" · "),
                                        maxLines = 1,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            if (isActive) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Active",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            IconButton(
                                onClick = {
                                    expanded = false
                                    confirmLogoutNpub = account.npub
                                },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove account",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        if (!isActive) onSwitchAccount(account.npub)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Add Account") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    onAddAccount()
                },
            )
        }
    }

    // Logout confirmation dialog
    val logoutNpub = confirmLogoutNpub
    if (logoutNpub != null) {
        val logoutDisplayName = resolveDisplayName(logoutNpub, localCache)

        AlertDialog(
            onDismissRequest = { confirmLogoutNpub = null },
            title = { Text("Remove Account") },
            text = {
                Text(
                    "Remove ${logoutDisplayName ?: npubShortMiddle(logoutNpub)}? " +
                        "This will delete the account from this device.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogoutNpub = null
                        onRemoveAccount(logoutNpub)
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogoutNpub = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}
