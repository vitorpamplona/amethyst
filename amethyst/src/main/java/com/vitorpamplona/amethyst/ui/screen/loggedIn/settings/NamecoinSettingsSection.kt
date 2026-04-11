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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.settings

import android.os.Build
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.namecoin_device_info
import com.vitorpamplona.amethyst.commons.resources.namecoin_diagnostics
import com.vitorpamplona.amethyst.commons.resources.namecoin_last_test
import com.vitorpamplona.amethyst.commons.resources.namecoin_no_test_yet
import com.vitorpamplona.amethyst.commons.resources.namecoin_pin_cert_accept
import com.vitorpamplona.amethyst.commons.resources.namecoin_pin_cert_body
import com.vitorpamplona.amethyst.commons.resources.namecoin_pin_cert_reject
import com.vitorpamplona.amethyst.commons.resources.namecoin_pin_cert_title
import com.vitorpamplona.amethyst.commons.resources.namecoin_response_time
import com.vitorpamplona.amethyst.commons.resources.namecoin_test_connection
import com.vitorpamplona.amethyst.commons.resources.namecoin_test_results
import com.vitorpamplona.amethyst.commons.resources.namecoin_test_success
import com.vitorpamplona.amethyst.commons.resources.namecoin_testing
import com.vitorpamplona.amethyst.commons.resources.namecoin_tls_info
import com.vitorpamplona.amethyst.service.namecoin.NamecoinSettings
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.DEFAULT_ELECTRUMX_SERVERS
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.ServerTestResult
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Complete settings section for Namecoin ElectrumX server configuration.
 *
 * Designed to sit in the Privacy / Settings screen alongside existing
 * Tor settings.
 *
 * @param settings      Current [NamecoinSettings] state
 * @param onToggleEnabled Called when user toggles the master switch
 * @param onAddServer     Called with `host:port[:tcp]` when user adds a server
 * @param onRemoveServer  Called with the server string to remove
 * @param onReset         Called when user resets to defaults
 * @param onTestServer    Suspend function to test a single server
 * @param onPinCert       Called with PEM string to persist a TOFU-pinned cert
 */
@Composable
fun NamecoinSettingsSection(
    settings: NamecoinSettings,
    onToggleEnabled: (Boolean) -> Unit,
    onAddServer: (String) -> Unit,
    onRemoveServer: (String) -> Unit,
    onReset: () -> Unit,
    onTestServer: suspend (ElectrumxServer) -> ServerTestResult,
    onPinCert: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        // ── Section header ─────────────────────────────────────────
        SectionHeader(enabled = settings.enabled, onToggle = onToggleEnabled)

        AnimatedVisibility(
            visible = settings.enabled,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(12.dp))

                // ── Explanation ─────────────────────────────────────
                Text(
                    "Namecoin names (.bit, d/, id/) are resolved via ElectrumX servers. " +
                        "By default, public community servers are used. " +
                        "For maximum privacy, add your own server below — when custom " +
                        "servers are set, the defaults are completely ignored.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                // ── Active servers display ─────────────────────────
                ActiveServersDisplay(settings = settings)

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(12.dp))

                // ── Custom servers list ────────────────────────────
                CustomServersList(
                    servers = settings.customServers,
                    onRemove = onRemoveServer,
                )

                // ── Add server input ───────────────────────────────
                AddServerInput(onAdd = onAddServer)

                Spacer(Modifier.height(8.dp))

                // ── Reset button ───────────────────────────────────
                if (settings.hasCustomServers) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onReset) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Reset to defaults")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(16.dp))

                // ── Test Connection ────────────────────────────────
                TestConnectionSection(
                    settings = settings,
                    onTestServer = onTestServer,
                    onPinCert = onPinCert,
                )
            }
        }
    }
}

// ── Test Connection ────────────────────────────────────────────────────

/**
 * Holds a cert pending user confirmation before pinning (TOFU).
 */
private data class PendingCertPin(
    val serverHost: String,
    val fingerprint: String,
    val pem: String,
)

@Composable
private fun TestConnectionSection(
    settings: NamecoinSettings,
    onTestServer: suspend (ElectrumxServer) -> ServerTestResult,
    onPinCert: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var isTesting by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf<List<ServerTestResult>>(emptyList()) }
    var lastTestTimestamp by remember { mutableStateOf<Long?>(null) }
    // Certs discovered during testing that need user confirmation
    var pendingCerts by remember { mutableStateOf<List<PendingCertPin>>(emptyList()) }
    // Which cert is currently shown in the confirmation dialog
    var confirmingCert by remember { mutableStateOf<PendingCertPin?>(null) }

    val servers = settings.toElectrumxServers() ?: DEFAULT_ELECTRUMX_SERVERS

    // ── Cert confirmation dialog ───────────────────────────────
    confirmingCert?.let { pending ->
        AlertDialog(
            onDismissRequest = {
                // Remove from pending list and move to next (or close)
                pendingCerts = pendingCerts.drop(1)
                confirmingCert = pendingCerts.firstOrNull()
            },
            title = { Text(stringResource(Res.string.namecoin_pin_cert_title)) },
            text = {
                Column {
                    Text(
                        stringResource(Res.string.namecoin_pin_cert_body, pending.serverHost),
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
                    Text(stringResource(Res.string.namecoin_pin_cert_accept))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingCerts = pendingCerts.drop(1)
                    confirmingCert = pendingCerts.firstOrNull()
                }) {
                    Text(stringResource(Res.string.namecoin_pin_cert_reject))
                }
            },
        )
    }

    Column {
        // ── Test button ────────────────────────────────────────
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
                            // Collect certs for user confirmation (not auto-pinned)
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
                        lastTestTimestamp = System.currentTimeMillis()
                        isTesting = false
                        // Show confirmation dialog for each new cert
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
                Text(stringResource(Res.string.namecoin_testing))
            } else {
                Text(stringResource(Res.string.namecoin_test_connection))
            }
        }

        // ── Per-server results ─────────────────────────────────
        if (testResults.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))

            Text(
                stringResource(Res.string.namecoin_test_results),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))

            testResults.forEach { result ->
                ServerTestResultRow(result)
            }

            if (isTesting && testResults.size < servers.size) {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Testing next server…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ── Diagnostic card ────────────────────────────────────
        if (testResults.isNotEmpty() || lastTestTimestamp != null) {
            Spacer(Modifier.height(16.dp))
            DiagnosticCard(
                testResults = testResults,
                lastTestTimestamp = lastTestTimestamp,
            )
        }
    }
}

@Composable
private fun ServerTestResultRow(result: ServerTestResult) {
    val serverLabel = "${result.server.host}:${result.server.port}"
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = if (result.success) "✅" else "❌",
            fontSize = 14.sp,
            modifier = Modifier.padding(end = 6.dp, top = 1.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = serverLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(Res.string.namecoin_response_time, result.responseTimeMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (result.success) {
                Text(
                    text = stringResource(Res.string.namecoin_test_success),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF2E8B57),
                )
                val fp = result.certFingerprint
                if (fp != null) {
                    Text(
                        text = "Cert: ${fp.take(23)}…",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                val errorText = result.error
                if (errorText != null) {
                    Text(
                        text = errorText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

// ── Diagnostic Card ────────────────────────────────────────────────────

@Composable
private fun DiagnosticCard(
    testResults: List<ServerTestResult>,
    lastTestTimestamp: Long?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(Res.string.namecoin_diagnostics),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))

            // Last test timestamp
            if (lastTestTimestamp != null) {
                val formatted =
                    remember(lastTestTimestamp) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(Date(lastTestTimestamp))
                    }
                val successCount = testResults.count { it.success }
                val totalCount = testResults.size
                DiagnosticRow(
                    label = stringResource(Res.string.namecoin_last_test),
                    value = "$formatted ($successCount/$totalCount OK)",
                )
            } else {
                DiagnosticRow(
                    label = stringResource(Res.string.namecoin_last_test),
                    value = stringResource(Res.string.namecoin_no_test_yet),
                )
            }

            Spacer(Modifier.height(4.dp))

            // Device info
            DiagnosticRow(
                label = stringResource(Res.string.namecoin_device_info),
                value = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            )

            Spacer(Modifier.height(4.dp))

            // TLS info from test results
            val tlsVersions =
                testResults
                    .mapNotNull { it.tlsVersion }
                    .distinct()
            val tlsDisplay =
                if (tlsVersions.isNotEmpty()) {
                    tlsVersions.joinToString(", ")
                } else {
                    "—"
                }
            DiagnosticRow(
                label = stringResource(Res.string.namecoin_tls_info),
                value = tlsDisplay,
            )
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(0.65f),
        )
    }
}

// ── Original Sub-composables ───────────────────────────────────────────

@Composable
private fun SectionHeader(
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
                Icons.Default.Lock,
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
private fun ActiveServersDisplay(settings: NamecoinSettings) {
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
            ServerRow(
                displayText =
                    "${server.host}:${server.port}" +
                        if (!server.useSsl) " (tcp)" else " (tls)",
                isActive = true,
            )
        }
    }
}

@Composable
private fun CustomServersList(
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
                        Icons.Default.Close,
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
private fun AddServerInput(onAdd: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    val kb = LocalSoftwareKeyboardController.current

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
        kb?.hide()
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
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { tryAdd() }),
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
                Icons.Default.Add,
                contentDescription = "Add server",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ServerRow(
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
