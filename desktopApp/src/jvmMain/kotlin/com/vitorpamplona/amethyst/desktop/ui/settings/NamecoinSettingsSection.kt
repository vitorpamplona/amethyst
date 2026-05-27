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
package com.vitorpamplona.amethyst.desktop.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinSettings
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.DEFAULT_ELECTRUMX_SERVERS
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinBackend
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinCoreRpcConfig
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.RpcProbeResult
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ServerTestResult
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Complete settings section for Namecoin ElectrumX server configuration.
 *
 * Desktop port of the Android `NamecoinSettingsSection.kt` composable.
 * Uses `onPreviewKeyEvent` for Enter-key handling instead of Android's
 * `KeyboardActions`/`LocalSoftwareKeyboardController`.
 *
 * @param settings      Current [NamecoinSettings] state
 * @param onToggleEnabled Called when user toggles the master switch
 * @param onAddServer     Called with `host:port[:tcp]` when user adds a server
 * @param onRemoveServer  Called with the server string to remove
 * @param onReset         Called when user resets to defaults
 * @param onTestServer    Suspend function to test a single server. When
 *                        `null`, the Test Connection UI is hidden — useful
 *                        for previews or when no [DesktopNamecoinNameService]
 *                        is available.
 * @param onPinCert       Called with a PEM-encoded cert when the user
 *                        accepts a TOFU pin prompt. Mirrors the Android
 *                        callback contract.
 * @param onSetBackend                  Switch the primary resolution backend
 *                                      (ElectrumX vs Namecoin Core RPC).
 * @param onSetCoreRpcConfig            Persist Namecoin Core JSON-RPC
 *                                      connection details.
 * @param onSetFallbackToCustomElectrumx Toggle Core RPC → custom ElectrumX
 *                                      fallback. Ignored when ElectrumX
 *                                      is the primary backend.
 * @param onSetFallbackToDefaultElectrumx Toggle fallback to hardcoded public
 *                                      ElectrumX defaults.
 * @param onTestCoreRpc                 Probe an ad-hoc [NamecoinCoreRpcConfig]
 *                                      without persisting it. `null` hides
 *                                      the Test RPC button (e.g. when no
 *                                      HTTP client is plumbed in yet).
 */
@Composable
fun NamecoinSettingsSection(
    settings: NamecoinSettings,
    onToggleEnabled: (Boolean) -> Unit,
    onAddServer: (String) -> Unit,
    onRemoveServer: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
    onTestServer: (suspend (ElectrumxServer) -> ServerTestResult)? = null,
    onPinCert: ((String) -> Unit)? = null,
    onSetBackend: (NamecoinBackend) -> Unit = {},
    onSetCoreRpcConfig: (NamecoinCoreRpcConfig) -> Unit = {},
    onSetFallbackToCustomElectrumx: (Boolean) -> Unit = {},
    onSetFallbackToDefaultElectrumx: (Boolean) -> Unit = {},
    onTestCoreRpc: (suspend (NamecoinCoreRpcConfig) -> RpcProbeResult)? = null,
) {
    Column(modifier = modifier.padding(16.dp)) {
        // ── Section header ─────────────────────────────────────────
        NamecoinSectionHeader(enabled = settings.enabled, onToggle = onToggleEnabled)

        AnimatedVisibility(
            visible = settings.enabled,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(12.dp))

                // ── Explanation ─────────────────────────────────────
                Text(
                    "Namecoin names (.bit, d/, id/) are resolved by querying the Namecoin " +
                        "blockchain. Choose ElectrumX (light-client) or Namecoin Core RPC " +
                        "(your own full node) below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                // ── Backend selector ───────────────────────────────
                NamecoinBackendSelector(
                    selected = settings.backend,
                    onSelect = onSetBackend,
                )

                Spacer(Modifier.height(12.dp))

                AnimatedVisibility(
                    visible = settings.backend == NamecoinBackend.NAMECOIN_CORE_RPC,
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    Column {
                        NamecoinCoreRpcSection(
                            config = settings.namecoinCoreRpc,
                            onConfigChange = onSetCoreRpcConfig,
                            onTestCoreRpc = onTestCoreRpc,
                            onPinCert = onPinCert ?: {},
                        )
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // ── Fallback policy ────────────────────────────────
                NamecoinFallbacksSection(
                    settings = settings,
                    onSetFallbackToCustomElectrumx = onSetFallbackToCustomElectrumx,
                    onSetFallbackToDefaultElectrumx = onSetFallbackToDefaultElectrumx,
                )

                Spacer(Modifier.height(16.dp))

                // ── Active servers display ─────────────────────────
                NamecoinActiveServersDisplay(settings = settings)

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(12.dp))

                // ── Custom servers list ────────────────────────────
                NamecoinCustomServersList(
                    servers = settings.customServers,
                    onRemove = onRemoveServer,
                )

                // ── Add server input ───────────────────────────────────────────
                NamecoinAddServerInput(onAdd = onAddServer)

                // ── Test connection + TOFU pin ────────────────────────────────
                if (onTestServer != null) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(12.dp))
                    NamecoinTestConnectionSection(
                        settings = settings,
                        onTestServer = onTestServer,
                        onPinCert = onPinCert ?: {},
                    )
                }

                Spacer(Modifier.height(8.dp))

                // ── Reset button ───────────────────────────────────
                if (settings.hasCustomServers) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onReset) {
                            Icon(
                                MaterialSymbols.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Reset to defaults")
                        }
                    }
                }
            }
        }
    }
}

// ── Sub-composables ────────────────────────────────────────────────────

@Composable
private fun NamecoinSectionHeader(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                MaterialSymbols.Lock,
                contentDescription = null,
                tint = Color(0xFF4A90D9), // Namecoin blue
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "Namecoin Resolution",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Blockchain identity lookups (.bit)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun NamecoinActiveServersDisplay(settings: NamecoinSettings) {
    val servers = settings.toElectrumxServers() ?: DEFAULT_ELECTRUMX_SERVERS
    val isCustom = settings.hasCustomServers

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Active servers",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            if (isCustom) {
                Text(
                    "CUSTOM",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4A90D9),
                    modifier =
                        Modifier
                            .background(
                                Color(0xFF4A90D9).copy(alpha = 0.1f),
                                RoundedCornerShape(4.dp),
                            ).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            } else {
                Text(
                    "DEFAULT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        servers.forEach { server ->
            NamecoinServerRow(
                displayText =
                    "${server.host}:${server.port}" +
                        if (!server.useSsl) " (tcp)" else " (tls)",
                isActive = true,
            )
        }
    }
}

@Composable
private fun NamecoinCustomServersList(
    servers: List<String>,
    onRemove: (String) -> Unit,
) {
    if (servers.isEmpty()) {
        Text(
            "No custom servers configured",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(vertical = 4.dp),
        )
    } else {
        Text(
            "Custom servers (used exclusively)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        servers.forEach { server ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = server,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onRemove(server) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        MaterialSymbols.Close,
                        contentDescription = "Remove server",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun NamecoinAddServerInput(onAdd: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun tryAdd() {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            validationError = "Enter a server address"
            return
        }
        val parsed = NamecoinSettings.parseServerString(trimmed)
        if (parsed == null) {
            validationError = "Invalid format. Use host:port or host:port:tcp"
            return
        }
        validationError = null
        onAdd(trimmed)
        input = ""
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                validationError = null
            },
            label = { Text("Add ElectrumX server") },
            placeholder = { Text("host:port or host:port:tcp") },
            singleLine = true,
            isError = validationError != null,
            supportingText =
                validationError?.let { err ->
                    { Text(err, color = MaterialTheme.colorScheme.error) }
                },
            modifier =
                Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            tryAdd()
                            true
                        } else {
                            false
                        }
                    },
            shape = RoundedCornerShape(8.dp),
            textStyle =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = { tryAdd() },
            modifier =
                Modifier
                    .padding(top = 8.dp)
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp),
                    ),
        ) {
            Icon(
                MaterialSymbols.Add,
                contentDescription = "Add server",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ── Test connection sub-section ────────────────────────────────────────────────

private data class PendingCertPin(
    val serverHost: String,
    val fingerprint: String,
    val pem: String,
)

@Composable
private fun NamecoinTestConnectionSection(
    settings: NamecoinSettings,
    onTestServer: suspend (ElectrumxServer) -> ServerTestResult,
    onPinCert: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isTesting by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf<List<ServerTestResult>>(emptyList()) }
    var pendingCerts by remember { mutableStateOf<List<PendingCertPin>>(emptyList()) }
    var confirmingCert by remember { mutableStateOf<PendingCertPin?>(null) }

    val servers = settings.toElectrumxServers() ?: DEFAULT_ELECTRUMX_SERVERS

    // ── Cert confirmation dialog ──────────────────────────────────────────
    confirmingCert?.let { pending ->
        AlertDialog(
            onDismissRequest = {
                pendingCerts = pendingCerts.drop(1)
                confirmingCert = pendingCerts.firstOrNull()
            },
            title = { Text("Pin server certificate?") },
            text = {
                Column {
                    Text(
                        "Trust this certificate for ${pending.serverHost}? " +
                            "Subsequent lookups against this host will require the same cert.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "SHA-256:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = pending.fingerprint,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onPinCert(pending.pem)
                    pendingCerts = pendingCerts.drop(1)
                    confirmingCert = pendingCerts.firstOrNull()
                }) {
                    Text("Pin")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingCerts = pendingCerts.drop(1)
                    confirmingCert = pendingCerts.firstOrNull()
                }) {
                    Text("Skip")
                }
            },
        )
    }

    Column {
        Button(
            onClick = {
                if (!isTesting) {
                    isTesting = true
                    testResults = emptyList()
                    pendingCerts = emptyList()
                    scope.launch {
                        val results = mutableListOf<ServerTestResult>()
                        val newCerts = mutableListOf<PendingCertPin>()
                        for (server in servers) {
                            val result = onTestServer(server)
                            results.add(result)
                            testResults = results.toList()
                            val pem = result.serverCertPem
                            val fp = result.certFingerprint
                            if (result.success && pem != null && fp != null) {
                                newCerts.add(
                                    PendingCertPin(
                                        serverHost = "${server.host}:${server.port}",
                                        fingerprint = fp,
                                        pem = pem,
                                    ),
                                )
                            }
                        }
                        isTesting = false
                        if (newCerts.isNotEmpty()) {
                            pendingCerts = newCerts
                            confirmingCert = newCerts.first()
                        }
                    }
                }
            },
            enabled = !isTesting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Testing…")
            } else {
                Text("Test connection & pin certs")
            }
        }

        if (testResults.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Test results",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            testResults.forEach { result ->
                NamecoinServerTestResultRow(result)
            }
        }
    }
}

@Composable
private fun NamecoinServerTestResultRow(result: ServerTestResult) {
    val serverLabel = "${result.server.host}:${result.server.port}"
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (result.success) "✓" else "✗",
            color =
                if (result.success) {
                    Color(0xFF2E8B57)
                } else {
                    MaterialTheme.colorScheme.error
                },
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = serverLabel,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail: String =
                when {
                    result.success && result.tlsVersion != null ->
                        "${result.tlsVersion} · ${result.responseTimeMs} ms"
                    result.success -> "${result.responseTimeMs} ms"
                    !result.error.isNullOrBlank() -> result.error!!
                    else -> "Failed"
                }
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NamecoinServerRow(
    displayText: String,
    isActive: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "•",
            fontSize = 10.sp,
            color =
                if (isActive) {
                    Color(0xFF2E8B57)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.padding(end = 6.dp),
        )
        Text(
            text = displayText,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Backend selector ──────────────────────────────────────────────────

@Composable
private fun NamecoinBackendSelector(
    selected: NamecoinBackend,
    onSelect: (NamecoinBackend) -> Unit,
) {
    Column {
        Text(
            "Resolution backend",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))

        NamecoinBackendRadioRow(
            label = "ElectrumX (light client)",
            description = "Query public or custom ElectrumX servers. Lightweight, no full node required.",
            selected = selected == NamecoinBackend.ELECTRUMX,
            onClick = { onSelect(NamecoinBackend.ELECTRUMX) },
        )
        NamecoinBackendRadioRow(
            label = "Namecoin Core RPC",
            description = "Query your own Namecoin Core node directly via JSON-RPC. Most sovereign.",
            selected = selected == NamecoinBackend.NAMECOIN_CORE_RPC,
            onClick = { onSelect(NamecoinBackend.NAMECOIN_CORE_RPC) },
        )
    }
}

@Composable
private fun NamecoinBackendRadioRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Namecoin Core RPC sub-section ──────────────────────────────────────

/**
 * Holds a Core RPC server cert pending user confirmation before pinning
 * (TOFU). Kept separate from the ElectrumX [PendingCertPin] above because
 * the two prompts can race when both backends are configured.
 */
private data class RpcPendingCertPin(
    val serverHost: String,
    val fingerprint: String,
    val pem: String,
)

@Composable
private fun NamecoinCoreRpcSection(
    config: NamecoinCoreRpcConfig,
    onConfigChange: (NamecoinCoreRpcConfig) -> Unit,
    onTestCoreRpc: (suspend (NamecoinCoreRpcConfig) -> RpcProbeResult)?,
    onPinCert: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var url by rememberSaveable(config.url) { mutableStateOf(config.url) }
    var user by rememberSaveable(config.username) { mutableStateOf(config.username) }
    var pass by rememberSaveable(config.password) { mutableStateOf(config.password) }
    var passVisible by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var lastProbe by remember { mutableStateOf<RpcProbeResult?>(null) }
    var pendingPin by remember { mutableStateOf<RpcPendingCertPin?>(null) }

    // ── Cert confirmation dialog (TOFU) ──────────────────────────────
    pendingPin?.let { pin ->
        AlertDialog(
            onDismissRequest = { pendingPin = null },
            title = { Text("Pin Namecoin Core RPC certificate?") },
            text = {
                Column {
                    Text(
                        "Trust this certificate for ${pin.serverHost}? " +
                            "Subsequent Namecoin Core RPC calls against this host will " +
                            "require the same cert.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "SHA-256:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = pin.fingerprint,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onPinCert(pin.pem)
                    onConfigChange(
                        config.copy(
                            url = url.trim(),
                            username = user.trim(),
                            password = pass,
                            usePinnedTrustStore = true,
                        ),
                    )
                    pendingPin = null
                }) {
                    Text("Trust")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPin = null }) {
                    Text("Skip")
                }
            },
        )
    }

    fun commit() {
        onConfigChange(
            config.copy(
                url = url.trim(),
                username = user.trim(),
                password = pass,
            ),
        )
    }

    Column {
        Text(
            "Namecoin Core RPC endpoint",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "URL, username and password for your Namecoin Core node. " +
                "StartOS / Start9 users: copy these from the package's Properties tab. " +
                "Umbrel users: see the Namecoin Core app's 'Connect From Outside' card. " +
                "Tor onion URL recommended for remote access.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("RPC URL") },
            placeholder = { Text("http://<onion>.onion:8336/") },
            singleLine = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown && ev.key == Key.Enter) {
                            commit()
                            true
                        } else {
                            false
                        }
                    },
            shape = RoundedCornerShape(8.dp),
            textStyle =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("RPC username") },
            singleLine = true,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown && ev.key == Key.Enter) {
                            commit()
                            true
                        } else {
                            false
                        }
                    },
            shape = RoundedCornerShape(8.dp),
            textStyle =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("RPC password") },
            singleLine = true,
            visualTransformation =
                if (passVisible) {
                    androidx.compose.ui.text.input.VisualTransformation.None
                } else {
                    androidx.compose.ui.text.input
                        .PasswordVisualTransformation()
                },
            trailingIcon = {
                IconButton(onClick = { passVisible = !passVisible }) {
                    Icon(
                        MaterialSymbols.Lock,
                        contentDescription = null,
                    )
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown && ev.key == Key.Enter) {
                            commit()
                            true
                        } else {
                            false
                        }
                    },
            shape = RoundedCornerShape(8.dp),
            textStyle =
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { commit() },
                modifier = Modifier.weight(1f),
            ) {
                Text("Save")
            }
            if (onTestCoreRpc != null) {
                Button(
                    onClick = {
                        commit()
                        if (testing) return@Button
                        testing = true
                        lastProbe = null
                        val candidate =
                            config.copy(
                                url = url.trim(),
                                username = user.trim(),
                                password = pass,
                            )
                        scope.launch {
                            try {
                                val probe = onTestCoreRpc(candidate)
                                lastProbe = probe
                                val pem = probe.serverCertPem
                                val fp = probe.certFingerprint
                                if (
                                    pem != null && fp != null && (
                                        probe.tlsHandshakeFailed ||
                                            !candidate.usePinnedTrustStore
                                    )
                                ) {
                                    val host =
                                        try {
                                            java.net.URI(candidate.url).host
                                                ?: candidate.url
                                        } catch (_: Exception) {
                                            candidate.url
                                        }
                                    pendingPin =
                                        RpcPendingCertPin(
                                            serverHost = host,
                                            fingerprint = fp,
                                            pem = pem,
                                        )
                                }
                            } finally {
                                testing = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !testing && url.isNotBlank(),
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Test RPC")
                    }
                }
            }
        }

        lastProbe?.let { probe ->
            Spacer(Modifier.height(10.dp))
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            if (probe.success) {
                                Color(0x222E8B57)
                            } else {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                            },
                    ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    if (probe.success) {
                        Text(
                            "Connected (${probe.elapsedMs} ms)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val chain = probe.chain ?: "?"
                        val blocks = probe.blocks?.toString() ?: "?"
                        val pct =
                            probe.verificationProgress?.let {
                                String.format(Locale.ROOT, "%.2f%%", it * 100)
                            } ?: "?"
                        Text(
                            "chain=$chain  height=$blocks  sync=$pct" +
                                (if (probe.initialBlockDownload == true) "  (IBD)" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    } else {
                        Text(
                            "Failed",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            probe.error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        if (probe.tlsHandshakeFailed && probe.certFingerprint != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "TLS rejected by the system trust store. Tap Test RPC " +
                                    "again, then choose Trust to pin this server's certificate.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val fp = probe.certFingerprint
                    if (fp != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text =
                                "cert SHA-256: ${fp.take(23)}\u2026" +
                                    if (config.usePinnedTrustStore) "  (pinned)" else "",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ── Fallback policy section ────────────────────────────────────────────

@Composable
private fun NamecoinFallbacksSection(
    settings: NamecoinSettings,
    onSetFallbackToCustomElectrumx: (Boolean) -> Unit,
    onSetFallbackToDefaultElectrumx: (Boolean) -> Unit,
) {
    Column {
        Text(
            "Fallback policy",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "What to try if the primary backend can't be reached. " +
                "Off by default — fallbacks can leak your lookups to other operators.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        if (settings.backend == NamecoinBackend.NAMECOIN_CORE_RPC) {
            NamecoinToggleRow(
                label = "Fall back to my custom ElectrumX servers",
                description =
                    if (settings.hasCustomServers) {
                        "If Namecoin Core RPC fails, try the ${settings.customServers.size} " +
                            "custom ElectrumX server(s) configured below."
                    } else {
                        "No custom ElectrumX servers configured. Add one in the section below " +
                            "to enable this option."
                    },
                checked = settings.fallbackToCustomElectrumx,
                onCheckedChange = onSetFallbackToCustomElectrumx,
                enabled = settings.hasCustomServers,
            )
        }

        NamecoinToggleRow(
            label = "Fall back to default public ElectrumX servers",
            description =
                if (settings.backend == NamecoinBackend.ELECTRUMX) {
                    if (settings.hasCustomServers) {
                        "If all of my custom ElectrumX servers fail, also try the hardcoded " +
                            "public defaults."
                    } else {
                        "(Already using defaults — toggle has no extra effect.)"
                    }
                } else {
                    "If everything above fails, try the hardcoded public ElectrumX servers."
                },
            checked = settings.fallbackToDefaultElectrumx,
            onCheckedChange = onSetFallbackToDefaultElectrumx,
            enabled =
                settings.backend == NamecoinBackend.NAMECOIN_CORE_RPC ||
                    settings.hasCustomServers,
        )
    }
}

@Composable
private fun NamecoinToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}
