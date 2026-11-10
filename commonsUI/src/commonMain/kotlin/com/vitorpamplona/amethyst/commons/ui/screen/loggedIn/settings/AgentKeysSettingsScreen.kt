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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.agents.AgentKeyProbeResult
import com.vitorpamplona.amethyst.commons.agents.AgentProviderPreset
import com.vitorpamplona.amethyst.commons.agents.AgentProviderPresets
import com.vitorpamplona.amethyst.commons.agents.OpenRouterConnectResult
import com.vitorpamplona.amethyst.commons.agents.connectOpenRouter
import com.vitorpamplona.amethyst.commons.agents.probeAgentKey
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.agent_keys_add_title
import com.vitorpamplona.amethyst.commons.resources.agent_keys_empty
import com.vitorpamplona.amethyst.commons.resources.agent_keys_explanation
import com.vitorpamplona.amethyst.commons.resources.agent_keys_get_key_at
import com.vitorpamplona.amethyst.commons.resources.agent_keys_key
import com.vitorpamplona.amethyst.commons.resources.agent_keys_key_optional
import com.vitorpamplona.amethyst.commons.resources.agent_keys_local_hint
import com.vitorpamplona.amethyst.commons.resources.agent_keys_make_preferred
import com.vitorpamplona.amethyst.commons.resources.agent_keys_no_key
import com.vitorpamplona.amethyst.commons.resources.agent_keys_openrouter_connect
import com.vitorpamplona.amethyst.commons.resources.agent_keys_openrouter_denied
import com.vitorpamplona.amethyst.commons.resources.agent_keys_openrouter_done_page
import com.vitorpamplona.amethyst.commons.resources.agent_keys_openrouter_failed
import com.vitorpamplona.amethyst.commons.resources.agent_keys_openrouter_or_paste
import com.vitorpamplona.amethyst.commons.resources.agent_keys_openrouter_timed_out
import com.vitorpamplona.amethyst.commons.resources.agent_keys_openrouter_unsupported
import com.vitorpamplona.amethyst.commons.resources.agent_keys_openrouter_waiting
import com.vitorpamplona.amethyst.commons.resources.agent_keys_preferred
import com.vitorpamplona.amethyst.commons.resources.agent_keys_provider
import com.vitorpamplona.amethyst.commons.resources.agent_keys_remove_body
import com.vitorpamplona.amethyst.commons.resources.agent_keys_remove_title
import com.vitorpamplona.amethyst.commons.resources.agent_keys_spending_cap_tip
import com.vitorpamplona.amethyst.commons.resources.agent_keys_test
import com.vitorpamplona.amethyst.commons.resources.agent_keys_test_failed
import com.vitorpamplona.amethyst.commons.resources.agent_keys_test_ok
import com.vitorpamplona.amethyst.commons.resources.agent_keys_test_ok_models
import com.vitorpamplona.amethyst.commons.resources.agent_keys_test_rejected
import com.vitorpamplona.amethyst.commons.resources.agent_keys_test_unreachable
import com.vitorpamplona.amethyst.commons.resources.agent_keys_test_unsupported
import com.vitorpamplona.amethyst.commons.resources.agent_keys_test_unverified
import com.vitorpamplona.amethyst.commons.resources.agent_keys_testing
import com.vitorpamplona.amethyst.commons.resources.agent_keys_title
import com.vitorpamplona.amethyst.commons.resources.agent_keys_url
import com.vitorpamplona.amethyst.commons.resources.agent_keys_url_invalid
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.hide_password
import com.vitorpamplona.amethyst.commons.resources.remove
import com.vitorpamplona.amethyst.commons.resources.save
import com.vitorpamplona.amethyst.commons.resources.show_password
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nipXXAgentKeys.tags.AgentKeyTag
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Settings › AI agent: the keys (kind 12000) apps use to run AI tasks for this account.
 * The first key is the preferred one; adding a key makes it preferred.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentKeysSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val keys by accountViewModel.account.agentKeys.flow
        .collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.agent_keys_title), nav) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringRes(Res.string.agent_keys_explanation), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringRes(Res.string.agent_keys_spending_cap_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SavedAgentKeys(
                keys = keys,
                onMakePreferred = { key -> accountViewModel.launchSigner { accountViewModel.account.setPreferredAgentKey(key) } },
                onRemove = { key -> accountViewModel.launchSigner { accountViewModel.account.removeAgentKey(key) } },
            )

            HorizontalDivider()

            AddAgentKey(
                onTest = { key -> accountViewModel.httpClientBuilder.probeAgentKey(key) },
                onConnectOpenRouter = { donePage, openBrowser -> accountViewModel.httpClientBuilder.connectOpenRouter(donePage, openBrowser = openBrowser) },
                onSave = { key -> accountViewModel.launchSigner { accountViewModel.account.addAgentKey(key) } },
            )
        }
    }
}

@Composable
private fun SavedAgentKeys(
    keys: List<AgentKeyTag>,
    onMakePreferred: (AgentKeyTag) -> Unit,
    onRemove: (AgentKeyTag) -> Unit,
) {
    var confirmRemoval by remember { mutableStateOf<AgentKeyTag?>(null) }

    if (keys.isEmpty()) {
        Text(
            stringRes(Res.string.agent_keys_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    keys.forEachIndexed { index, key ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(MaterialSymbols.Key, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    AgentProviderPresets.displayName(key.url),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (key.hasKey()) AgentProviderPresets.maskKey(key.key) else stringRes(Res.string.agent_keys_no_key),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    key.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (index == 0) {
                Text(
                    stringRes(Res.string.agent_keys_preferred),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            } else {
                TextButton(onClick = { onMakePreferred(key) }) {
                    Text(stringRes(Res.string.agent_keys_make_preferred))
                }
            }
            IconButton(onClick = { confirmRemoval = key }) {
                Icon(MaterialSymbols.Delete, contentDescription = stringRes(Res.string.remove))
            }
        }
    }

    confirmRemoval?.let { key ->
        AlertDialog(
            onDismissRequest = { confirmRemoval = null },
            title = { Text(stringRes(Res.string.agent_keys_remove_title)) },
            text = { Text(stringRes(Res.string.agent_keys_remove_body, AgentProviderPresets.displayName(key.url))) },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(key)
                    confirmRemoval = null
                }) { Text(stringRes(Res.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoval = null }) { Text(stringRes(Res.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAgentKey(
    onTest: suspend (AgentKeyTag) -> AgentKeyProbeResult,
    onConnectOpenRouter: suspend (donePageText: String, openBrowser: (String) -> Unit) -> OpenRouterConnectResult,
    onSave: (AgentKeyTag) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val donePageText = stringRes(Res.string.agent_keys_openrouter_done_page)
    var connecting by remember { mutableStateOf<Job?>(null) }
    var connectProblem by remember { mutableStateOf<OpenRouterConnectResult?>(null) }
    var preset by remember { mutableStateOf(AgentProviderPresets.OPENROUTER) }
    var url by remember { mutableStateOf(preset.url) }
    var key by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AgentKeyProbeResult?>(null) }

    val urlValid = AgentProviderPresets.isValidUrl(url)
    val keyRequired = preset.needsKey
    val canSave = urlValid && (!keyRequired || key.isNotBlank())

    fun draft() = AgentKeyTag(key.trim(), url.trim())

    fun test(candidate: AgentKeyTag) {
        testing = true
        result = null
        scope.launch {
            try {
                result = onTest(candidate)
            } finally {
                testing = false
            }
        }
    }

    fun connectOpenRouter() {
        connectProblem = null
        connecting =
            scope.launch {
                try {
                    when (val outcome = onConnectOpenRouter(donePageText) { uriHandler.openUri(it) }) {
                        is OpenRouterConnectResult.Connected -> {
                            // The key arrives in the form, tested; saving stays the user's call.
                            preset = AgentProviderPresets.OPENROUTER
                            url = AgentProviderPresets.OPENROUTER.url
                            key = outcome.key
                            test(draft())
                        }
                        else -> connectProblem = outcome
                    }
                } finally {
                    connecting = null
                }
            }
    }

    val urlError: (@Composable () -> Unit)? =
        if (url.isNotBlank() && !urlValid) {
            { Text(stringRes(Res.string.agent_keys_url_invalid)) }
        } else {
            null
        }

    Text(stringRes(Res.string.agent_keys_add_title), style = MaterialTheme.typography.titleMedium)

    ProviderPicker(preset) {
        preset = it
        url = it.url
        result = null
        connectProblem = null
    }

    if (preset == AgentProviderPresets.OPENROUTER) {
        val job = connecting
        if (job != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringRes(Res.string.agent_keys_openrouter_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { job.cancel() }) { Text(stringRes(Res.string.cancel)) }
            }
        } else {
            Button(onClick = { connectOpenRouter() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringRes(Res.string.agent_keys_openrouter_connect))
            }
            Text(
                stringRes(Res.string.agent_keys_openrouter_or_paste),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        connectProblem?.let { problem ->
            Text(
                stringRes(
                    when (problem) {
                        OpenRouterConnectResult.Denied -> Res.string.agent_keys_openrouter_denied
                        OpenRouterConnectResult.TimedOut -> Res.string.agent_keys_openrouter_timed_out
                        OpenRouterConnectResult.Unsupported -> Res.string.agent_keys_openrouter_unsupported
                        else -> Res.string.agent_keys_openrouter_failed
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    OutlinedTextField(
        value = url,
        onValueChange = {
            url = it
            result = null
        },
        label = { Text(stringRes(Res.string.agent_keys_url)) },
        singleLine = true,
        isError = url.isNotBlank() && !urlValid,
        supportingText = urlError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = key,
        onValueChange = {
            key = it
            result = null
            connectProblem = null
        },
        label = { Text(stringRes(if (keyRequired) Res.string.agent_keys_key else Res.string.agent_keys_key_optional)) },
        singleLine = true,
        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { showKey = !showKey }) {
                Icon(
                    if (showKey) MaterialSymbols.VisibilityOff else MaterialSymbols.Visibility,
                    contentDescription = stringRes(if (showKey) Res.string.hide_password else Res.string.show_password),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    val hint =
        when {
            preset.isLocal -> stringRes(Res.string.agent_keys_local_hint)
            preset.keyPage != null -> stringRes(Res.string.agent_keys_get_key_at, preset.keyPage)
            else -> null
        }
    hint?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            enabled = canSave && !testing,
            onClick = { test(draft()) },
        ) {
            Text(stringRes(if (testing) Res.string.agent_keys_testing else Res.string.agent_keys_test))
        }
        Button(
            enabled = canSave,
            onClick = {
                onSave(draft())
                key = ""
                connectProblem = null
                showKey = false
                result = null
            },
        ) {
            Text(stringRes(Res.string.save))
        }
    }

    result?.let { ProbeResultText(it) }
    Spacer(Modifier.height(16.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderPicker(
    selected: AgentProviderPreset,
    onSelect: (AgentProviderPreset) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringRes(Res.string.agent_keys_provider)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AgentProviderPresets.ALL.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.name) },
                    onClick = {
                        onSelect(preset)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ProbeResultText(result: AgentKeyProbeResult) {
    val ok = result is AgentKeyProbeResult.Ok
    val text =
        when (result) {
            is AgentKeyProbeResult.Ok -> {
                val count = result.modelCount
                when {
                    !result.keyChecked -> stringRes(Res.string.agent_keys_test_unverified)
                    count != null -> pluralStringRes(Res.plurals.agent_keys_test_ok_models, count, count)
                    else -> stringRes(Res.string.agent_keys_test_ok)
                }
            }
            is AgentKeyProbeResult.Rejected -> stringRes(Res.string.agent_keys_test_rejected, result.code)
            is AgentKeyProbeResult.Failed -> stringRes(Res.string.agent_keys_test_failed, result.code)
            AgentKeyProbeResult.Unreachable -> stringRes(Res.string.agent_keys_test_unreachable)
            AgentKeyProbeResult.Unsupported -> stringRes(Res.string.agent_keys_test_unsupported)
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) MaterialSymbols.CheckCircle else MaterialSymbols.Error,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
