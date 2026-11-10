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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
import com.vitorpamplona.amethyst.commons.resources.agent_keys_add
import com.vitorpamplona.amethyst.commons.resources.agent_keys_cap_tip
import com.vitorpamplona.amethyst.commons.resources.agent_keys_change
import com.vitorpamplona.amethyst.commons.resources.agent_keys_checking
import com.vitorpamplona.amethyst.commons.resources.agent_keys_choose_provider
import com.vitorpamplona.amethyst.commons.resources.agent_keys_connect_openrouter
import com.vitorpamplona.amethyst.commons.resources.agent_keys_custom
import com.vitorpamplona.amethyst.commons.resources.agent_keys_failed
import com.vitorpamplona.amethyst.commons.resources.agent_keys_get_key
import com.vitorpamplona.amethyst.commons.resources.agent_keys_key
import com.vitorpamplona.amethyst.commons.resources.agent_keys_key_optional
import com.vitorpamplona.amethyst.commons.resources.agent_keys_local
import com.vitorpamplona.amethyst.commons.resources.agent_keys_make_preferred
import com.vitorpamplona.amethyst.commons.resources.agent_keys_openrouter_denied
import com.vitorpamplona.amethyst.commons.resources.agent_keys_openrouter_done_page
import com.vitorpamplona.amethyst.commons.resources.agent_keys_openrouter_failed
import com.vitorpamplona.amethyst.commons.resources.agent_keys_openrouter_timed_out
import com.vitorpamplona.amethyst.commons.resources.agent_keys_openrouter_unsupported
import com.vitorpamplona.amethyst.commons.resources.agent_keys_openrouter_waiting
import com.vitorpamplona.amethyst.commons.resources.agent_keys_or
import com.vitorpamplona.amethyst.commons.resources.agent_keys_other_providers
import com.vitorpamplona.amethyst.commons.resources.agent_keys_preferred
import com.vitorpamplona.amethyst.commons.resources.agent_keys_recommended
import com.vitorpamplona.amethyst.commons.resources.agent_keys_rejected
import com.vitorpamplona.amethyst.commons.resources.agent_keys_remove_body
import com.vitorpamplona.amethyst.commons.resources.agent_keys_remove_title
import com.vitorpamplona.amethyst.commons.resources.agent_keys_runs_locally
import com.vitorpamplona.amethyst.commons.resources.agent_keys_status_connected
import com.vitorpamplona.amethyst.commons.resources.agent_keys_status_none
import com.vitorpamplona.amethyst.commons.resources.agent_keys_tagline
import com.vitorpamplona.amethyst.commons.resources.agent_keys_title
import com.vitorpamplona.amethyst.commons.resources.agent_keys_unreachable
import com.vitorpamplona.amethyst.commons.resources.agent_keys_unsupported
import com.vitorpamplona.amethyst.commons.resources.agent_keys_unverified
import com.vitorpamplona.amethyst.commons.resources.agent_keys_url
import com.vitorpamplona.amethyst.commons.resources.agent_keys_url_invalid
import com.vitorpamplona.amethyst.commons.resources.agent_keys_works
import com.vitorpamplona.amethyst.commons.resources.agent_keys_works_models
import com.vitorpamplona.amethyst.commons.resources.agent_keys_your_keys
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Settings › AI agent: the keys (kind 12000) apps use to run AI tasks for this account.
 * The first key is the preferred one; adding a key makes it preferred.
 */
@Composable
fun AgentKeysSettingsScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val keys by accountViewModel.account.agentKeys.flow
        .collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.agent_keys_title), nav) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AgentHero(connectedTo = keys.firstOrNull()?.let { AgentProviderPresets.displayName(it.url) })

            if (keys.isNotEmpty()) {
                SavedKeys(
                    keys = keys,
                    onAdd = { adding = !adding },
                    onMakePreferred = { key -> accountViewModel.launchSigner { accountViewModel.account.setPreferredAgentKey(key) } },
                    onRemove = { key -> accountViewModel.launchSigner { accountViewModel.account.removeAgentKey(key) } },
                )
            }

            AddAgentKey(
                showGrid = adding,
                onShowGrid = { adding = true },
                onTest = { key -> accountViewModel.httpClientBuilder.probeAgentKey(key) },
                onConnectOpenRouter = { donePage, openBrowser -> accountViewModel.httpClientBuilder.connectOpenRouter(donePage, openBrowser = openBrowser) },
                onSave = { key ->
                    accountViewModel.launchSigner { accountViewModel.account.addAgentKey(key) }
                    adding = false
                },
                compactStart = keys.isEmpty() && !adding,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AgentHero(connectedTo: String?) {
    var showTip by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val onHero = scheme.onPrimary

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(onHero.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MaterialSymbols.AutoAwesome, contentDescription = null, tint = onHero, modifier = Modifier.size(30.dp), filled = true)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringRes(Res.string.agent_keys_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = onHero)
                    Text(stringRes(Res.string.agent_keys_tagline), style = MaterialTheme.typography.bodyMedium, color = onHero.copy(alpha = 0.85f))
                }
                IconButton(onClick = { showTip = !showTip }) {
                    Icon(MaterialSymbols.Info, contentDescription = stringRes(Res.string.agent_keys_cap_tip), tint = onHero.copy(alpha = 0.85f))
                }
            }

            Surface(shape = CircleShape, color = onHero.copy(alpha = if (connectedTo != null) 0.24f else 0.14f)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (connectedTo != null) Color(0xFF4ADE80) else onHero.copy(alpha = 0.5f)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (connectedTo != null) stringRes(Res.string.agent_keys_status_connected, connectedTo) else stringRes(Res.string.agent_keys_status_none),
                        style = MaterialTheme.typography.labelLarge,
                        color = onHero,
                    )
                }
            }

            AnimatedVisibility(showTip) {
                Text(stringRes(Res.string.agent_keys_cap_tip), style = MaterialTheme.typography.bodySmall, color = onHero.copy(alpha = 0.9f))
            }
        }
    }
}

@Composable
private fun SavedKeys(
    keys: List<AgentKeyTag>,
    onAdd: () -> Unit,
    onMakePreferred: (AgentKeyTag) -> Unit,
    onRemove: (AgentKeyTag) -> Unit,
) {
    var confirmRemoval by remember { mutableStateOf<AgentKeyTag?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringRes(Res.string.agent_keys_your_keys), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onAdd) {
                Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringRes(Res.string.agent_keys_add))
            }
        }

        keys.forEachIndexed { index, key ->
            val preferred = index == 0
            val preset = AgentProviderPresets.forUrl(key.url)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (preferred) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainer,
                border = if (preferred) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProviderAvatar(preset?.id ?: key.url, AgentProviderPresets.displayName(key.url), size = 40)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(AgentProviderPresets.displayName(key.url), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (key.hasKey()) {
                            Text(
                                AgentProviderPresets.maskKey(key.key),
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { if (!preferred) onMakePreferred(key) }) {
                        Icon(
                            MaterialSymbols.Star,
                            contentDescription = stringRes(if (preferred) Res.string.agent_keys_preferred else Res.string.agent_keys_make_preferred),
                            tint = if (preferred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            filled = preferred,
                        )
                    }
                    IconButton(onClick = { confirmRemoval = key }) {
                        Icon(MaterialSymbols.Delete, contentDescription = stringRes(Res.string.remove), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
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
                }) { Text(stringRes(Res.string.remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoval = null }) { Text(stringRes(Res.string.cancel)) }
            },
        )
    }
}

/** A letter on a colour picked from the provider id, so each provider keeps its colour. */
@Composable
private fun ProviderAvatar(
    seed: String,
    name: String,
    size: Int,
) {
    val hue = (seed.hashCode().toLong() and 0xFFFFFFFFL) % 360
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.hsl(hue.toFloat(), 0.55f, 0.48f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = if (size >= 40) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun AddAgentKey(
    showGrid: Boolean,
    onShowGrid: () -> Unit,
    onTest: suspend (AgentKeyTag) -> AgentKeyProbeResult,
    onConnectOpenRouter: suspend (donePageText: String, openBrowser: (String) -> Unit) -> OpenRouterConnectResult,
    onSave: (AgentKeyTag) -> Unit,
    compactStart: Boolean,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val donePageText = stringRes(Res.string.agent_keys_openrouter_done_page)

    var preset by remember { mutableStateOf<AgentProviderPreset?>(null) }
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AgentKeyProbeResult?>(null) }
    var connecting by remember { mutableStateOf<Job?>(null) }
    var connectProblem by remember { mutableStateOf<OpenRouterConnectResult?>(null) }

    val selected = preset
    val urlValid = AgentProviderPresets.isValidUrl(url)
    val canCheck = selected != null && urlValid && (!selected.needsKey || key.isNotBlank())
    val canSave = canCheck && !testing && result != null && result !is AgentKeyProbeResult.Rejected

    fun select(next: AgentProviderPreset) {
        preset = next
        url = next.url
        key = ""
        result = null
        connectProblem = null
    }

    fun connectOpenRouter() {
        connectProblem = null
        connecting =
            scope.launch {
                try {
                    when (val outcome = onConnectOpenRouter(donePageText) { uriHandler.openUri(it) }) {
                        // The key lands in the form and checks itself; saving stays the user's call.
                        is OpenRouterConnectResult.Connected -> {
                            select(AgentProviderPresets.OPENROUTER)
                            key = outcome.key
                        }
                        else -> connectProblem = outcome
                    }
                } finally {
                    connecting = null
                }
            }
    }

    // Checks the key as soon as the user stops typing: no Test button to forget.
    LaunchedEffect(url, key, selected) {
        result = null
        if (!canCheck) return@LaunchedEffect
        delay(700)
        testing = true
        try {
            result = onTest(AgentKeyTag(key.trim(), url.trim()))
        } finally {
            testing = false
        }
    }

    // Empty account: one big way in, and the rest one tap away.
    if (compactStart && selected == null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConnectOpenRouterButton(connecting) { connectOpenRouter() }
            ConnectProblem(connectProblem)
            if (!showGrid) {
                OutlinedButton(onClick = onShowGrid, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(stringRes(Res.string.agent_keys_other_providers))
                }
            }
        }
        if (!showGrid) return
    }

    AnimatedVisibility(showGrid || selected != null, enter = fadeIn(), exit = fadeOut()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // The grid folds away once a provider is picked, so the key field is right there.
            if (showGrid && selected == null) {
                Text(stringRes(Res.string.agent_keys_choose_provider), style = MaterialTheme.typography.titleMedium)
                ProviderGrid(selected) { select(it) }
            }

            if (selected != null) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selected == AgentProviderPresets.CUSTOM) {
                                Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(32.dp))
                            } else {
                                ProviderAvatar(selected.id, selected.name, size = 32)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (selected == AgentProviderPresets.CUSTOM) stringRes(Res.string.agent_keys_custom) else selected.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                preset = null
                                key = ""
                                onShowGrid()
                            }) { Text(stringRes(Res.string.agent_keys_change)) }
                        }

                        if (selected == AgentProviderPresets.OPENROUTER) {
                            ConnectOpenRouterButton(connecting) { connectOpenRouter() }
                            ConnectProblem(connectProblem)
                            OrDivider()
                        }

                        if (selected == AgentProviderPresets.CUSTOM) {
                            OutlinedTextField(
                                value = url,
                                onValueChange = { url = it },
                                label = { Text(stringRes(Res.string.agent_keys_url)) },
                                singleLine = true,
                                isError = url.isNotBlank() && !urlValid,
                                supportingText = if (url.isNotBlank() && !urlValid) urlInvalidText else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        OutlinedTextField(
                            value = key,
                            onValueChange = {
                                key = it
                                connectProblem = null
                            },
                            label = { Text(stringRes(if (selected.needsKey) Res.string.agent_keys_key else Res.string.agent_keys_key_optional)) },
                            singleLine = true,
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CheckStatusIcon(testing, result)
                                    IconButton(onClick = { showKey = !showKey }) {
                                        Icon(
                                            if (showKey) MaterialSymbols.VisibilityOff else MaterialSymbols.Visibility,
                                            contentDescription = stringRes(if (showKey) Res.string.hide_password else Res.string.show_password),
                                        )
                                    }
                                }
                            },
                            supportingText = checkCaption(testing, result),
                            isError = result is AgentKeyProbeResult.Rejected,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when {
                                selected.isLocal ->
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(stringRes(Res.string.agent_keys_runs_locally)) },
                                    )
                                selected.keyPage != null -> {
                                    val keyPage = selected.keyPage
                                    AssistChip(
                                        onClick = { keyPage?.let { uriHandler.openUri(it) } },
                                        label = { Text(stringRes(Res.string.agent_keys_get_key)) },
                                        trailingIcon = { Icon(MaterialSymbols.AutoMirrored.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Button(
                                enabled = canSave,
                                onClick = {
                                    onSave(AgentKeyTag(key.trim(), url.trim()))
                                    preset = null
                                    key = ""
                                    showKey = false
                                    result = null
                                },
                            ) {
                                Text(stringRes(Res.string.save))
                            }
                        }
                    }
                }
            }
        }
    }
}

private val urlInvalidText: @Composable () -> Unit = { Text(stringRes(Res.string.agent_keys_url_invalid)) }

@Composable
private fun ConnectOpenRouterButton(
    connecting: Job?,
    onConnect: () -> Unit,
) {
    if (connecting != null) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringRes(Res.string.agent_keys_openrouter_waiting), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { connecting.cancel() }) { Text(stringRes(Res.string.cancel)) }
            }
        }
    } else {
        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(MaterialSymbols.Bolt, contentDescription = null, modifier = Modifier.size(20.dp), filled = true)
            Spacer(Modifier.width(8.dp))
            Text(stringRes(Res.string.agent_keys_connect_openrouter), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun ConnectProblem(problem: OpenRouterConnectResult?) {
    if (problem == null) return
    Text(
        stringRes(
            when (problem) {
                OpenRouterConnectResult.Denied -> Res.string.agent_keys_openrouter_denied
                OpenRouterConnectResult.TimedOut -> Res.string.agent_keys_openrouter_timed_out
                OpenRouterConnectResult.Unsupported -> Res.string.agent_keys_openrouter_unsupported
                else -> Res.string.agent_keys_openrouter_failed
            },
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun OrDivider() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            stringRes(Res.string.agent_keys_or),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderGrid(
    selected: AgentProviderPreset?,
    onSelect: (AgentProviderPreset) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns =
            when {
                maxWidth >= 640.dp -> 4
                maxWidth >= 400.dp -> 3
                else -> 2
            }
        val gap = 10.dp
        // A hair under the exact share, so rounding never wraps the last tile of a row.
        val tileWidth = (maxWidth - gap * (columns - 1)) / columns - 1.dp
        FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
            AgentProviderPresets.ALL.forEach { preset ->
                ProviderTile(preset, preset == selected, Modifier.width(tileWidth)) { onSelect(preset) }
            }
        }
    }
}

@Composable
private fun ProviderTile(
    preset: AgentProviderPreset,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val isCustom = preset == AgentProviderPresets.CUSTOM
    val name = if (isCustom) stringRes(Res.string.agent_keys_custom) else preset.name
    val badge =
        when {
            preset == AgentProviderPresets.OPENROUTER -> stringRes(Res.string.agent_keys_recommended)
            preset.isLocal -> stringRes(Res.string.agent_keys_local)
            else -> null
        }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 8.dp).widthIn(min = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isCustom) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MaterialSymbols.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            } else {
                ProviderAvatar(preset.id, preset.name, size = 36)
            }
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(
                badge ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CheckStatusIcon(
    testing: Boolean,
    result: AgentKeyProbeResult?,
) {
    AnimatedContent(testing to result, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "keyCheck") { (busy, outcome) ->
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                busy -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                outcome is AgentKeyProbeResult.Ok && outcome.keyChecked ->
                    Icon(MaterialSymbols.CheckCircle, contentDescription = null, tint = Color(0xFF22C55E), filled = true)
                outcome is AgentKeyProbeResult.Ok || outcome is AgentKeyProbeResult.Unsupported ->
                    Icon(MaterialSymbols.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                outcome != null -> Icon(MaterialSymbols.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, filled = true)
            }
        }
    }
}

private fun checkCaption(
    testing: Boolean,
    result: AgentKeyProbeResult?,
): (@Composable () -> Unit)? {
    if (!testing && result == null) return null
    return {
        Text(
            when {
                testing -> stringRes(Res.string.agent_keys_checking)
                result is AgentKeyProbeResult.Ok && !result.keyChecked -> stringRes(Res.string.agent_keys_unverified)
                result is AgentKeyProbeResult.Ok -> {
                    val count = result.modelCount
                    if (count != null) pluralStringRes(Res.plurals.agent_keys_works_models, count, count) else stringRes(Res.string.agent_keys_works)
                }
                result is AgentKeyProbeResult.Rejected -> stringRes(Res.string.agent_keys_rejected)
                result is AgentKeyProbeResult.Failed -> stringRes(Res.string.agent_keys_failed, result.code)
                result is AgentKeyProbeResult.Unsupported -> stringRes(Res.string.agent_keys_unsupported)
                else -> stringRes(Res.string.agent_keys_unreachable)
            },
        )
    }
}
