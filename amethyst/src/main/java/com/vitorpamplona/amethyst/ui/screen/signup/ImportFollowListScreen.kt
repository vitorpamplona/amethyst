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
package com.vitorpamplona.amethyst.ui.screen.signup

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.service.followimport.FollowEntry

// ── Public entry points ────────────────────────────────────────────────

/**
 * Embeddable section for the signup wizard.
 */
@Composable
fun ImportFollowListSection(
    onFollowsApplied: suspend (List<FollowEntry>) -> Unit,
    onSkip: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportFollowListViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        ImportHeader()
        Spacer(Modifier.height(20.dp))
        InputSection(
            enabled = state is ImportFollowState.Idle || state is ImportFollowState.Error,
            onLookup = { viewModel.startImport(it) },
        )
        Spacer(Modifier.height(16.dp))

        Box(modifier = Modifier.weight(1f)) {
            when (val s = state) {
                is ImportFollowState.Idle -> {
                    IdleHint()
                }

                is ImportFollowState.Resolving -> {
                    LoadingIndicator("Resolving ${s.identifier}…")
                }

                is ImportFollowState.Fetching -> {
                    LoadingIndicator("Fetching follow list…")
                }

                is ImportFollowState.Preview -> {
                    PreviewList(
                        state = s,
                        onToggle = { viewModel.toggleSelection(it) },
                        onSelectAll = { viewModel.setSelectAll(it) },
                    )
                }

                is ImportFollowState.Applying -> {
                    LoadingIndicator("Following ${s.count} accounts…")
                }

                is ImportFollowState.Done -> {
                    DoneMessage(s.count, onDone)
                }

                is ImportFollowState.Error -> {
                    ErrorMessage(s.message) { viewModel.reset() }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        BottomActions(state, onSkip, { viewModel.applySelectedFollows(onFollowsApplied) }, onDone)
    }
}

/**
 * Standalone dialog for post-signup use (settings / profile screen).
 */
@Composable
fun ImportFollowListDialog(
    onDismiss: () -> Unit,
    onFollowsApplied: suspend (List<FollowEntry>) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            ImportFollowListSection(
                onFollowsApplied = onFollowsApplied,
                onSkip = onDismiss,
                onDone = onDismiss,
                modifier = Modifier.height(600.dp),
            )
        }
    }
}

// ── Internal composables ───────────────────────────────────────────────

@Composable
private fun ImportHeader() {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.PersonAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Import Follow List",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Start with a great feed by following the same people as someone you trust.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InputSection(
    enabled: Boolean,
    onLookup: (String) -> Unit,
) {
    var identifier by rememberSaveable { mutableStateOf("") }
    val kb = LocalSoftwareKeyboardController.current

    Column {
        OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = it },
            label = { Text("Profile to import from") },
            placeholder = { Text("npub1…, alice@example.com, or example.bit") },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            supportingText = {
                Text(
                    "Supports npub, NIP-05, hex, and Namecoin (.bit / d/ / id/)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions =
                KeyboardActions(onGo = {
                    kb?.hide()
                    onLookup(identifier)
                }),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                kb?.hide()
                onLookup(identifier)
            },
            enabled = enabled && identifier.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) { Text("Look Up Follow List") }
    }
}

@Composable
private fun IdleHint() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text(
                "Tip",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Enter the profile of a friend or community leader. " +
                    "You can use their npub, NIP-05 address, or a Namecoin name " +
                    "like alice@example.bit or id/alice for blockchain-verified identities.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingIndicator(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(Modifier.size(40.dp), strokeWidth = 3.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreviewList(
    state: ImportFollowState.Preview,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // Namecoin badge if resolved via blockchain
        AnimatedVisibility(
            visible = state.namecoinSource != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            NamecoinResolvedBadge(state.namecoinSource ?: "")
        }

        // Summary
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${state.totalCount} accounts found",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${state.selectedCount} selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Select all
        Row(
            Modifier.fillMaxWidth().clickable { onSelectAll(state.selectedCount < state.totalCount) }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = state.selectedCount == state.totalCount,
                onCheckedChange = { onSelectAll(it) },
            )
            Spacer(Modifier.width(8.dp))
            Text("Select All", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }

        HorizontalDivider()

        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp), modifier = Modifier.fillMaxSize()) {
            items(items = state.follows, key = { it.pubkeyHex }) { entry ->
                FollowEntryRow(entry, entry.pubkeyHex in state.selected) { onToggle(entry.pubkeyHex) }
            }
        }
    }
}

/**
 * Badge shown when the source profile was resolved via Namecoin blockchain.
 */
@Composable
private fun NamecoinResolvedBadge(namecoinSource: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .background(
                    color = Color(0xFF4A90D9).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                ).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("\u26D3", fontSize = 16.sp) // ⛓ chain link
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                "Resolved via Namecoin",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF4A90D9),
            )
            Text(
                formatNamecoinDisplay(namecoinSource),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FollowEntryRow(
    entry: FollowEntry,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint =
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                entry.pubkeyHex.take(2).uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.petname ?: shortPubkey(entry.pubkeyHex),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (entry.petname != null) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.petname != null) {
                Text(
                    shortPubkey(entry.pubkeyHex),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.relayHint != null) {
                Text(
                    entry.relayHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DoneMessage(
    count: Int,
    onContinue: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Now following $count accounts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Your feed is ready.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorMessage(
    message: String,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) { Text("Try Again") }
        }
    }
}

@Composable
private fun BottomActions(
    state: ImportFollowState,
    onSkip: () -> Unit,
    onApply: () -> Unit,
    onDone: () -> Unit,
) {
    when (state) {
        is ImportFollowState.Preview -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onSkip) { Text("Skip") }
                Button(onClick = onApply, enabled = state.selectedCount > 0, shape = RoundedCornerShape(12.dp)) {
                    Text("Follow ${state.selectedCount} accounts")
                }
            }
        }

        is ImportFollowState.Done -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onDone, shape = RoundedCornerShape(12.dp)) { Text("Continue") }
            }
        }

        is ImportFollowState.Idle, is ImportFollowState.Error -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSkip) { Text("Skip for now") }
            }
        }

        else -> {}
    }
}

// ── Helpers ────────────────────────────────────────────────────────────

private fun shortPubkey(hex: String): String = if (hex.length < 12) hex else "npub:${hex.take(8)}…${hex.takeLast(4)}"

private fun formatNamecoinDisplay(source: String): String {
    val s = source.trim()
    return when {
        s.startsWith("d/", ignoreCase = true) -> "${s.removePrefix("d/")}.bit"
        s.startsWith("_@") -> s.removePrefix("_@")
        else -> s
    }
}
