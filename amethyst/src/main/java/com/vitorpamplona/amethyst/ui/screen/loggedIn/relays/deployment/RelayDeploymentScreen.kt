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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.deployment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.service.vps.VpsPlan
import com.vitorpamplona.amethyst.service.vps.VpsProviderInfo
import com.vitorpamplona.amethyst.service.vps.VpsRegion
import com.vitorpamplona.amethyst.service.vps.VpsServer
import com.vitorpamplona.amethyst.service.vps.VpsServerStatus
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

@Composable
fun RelayDeploymentScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel =
        remember {
            RelayDeploymentViewModel(
                userPubkeyHex = accountViewModel.account.signer.pubKey,
            )
        }

    val currentStep by viewModel.currentStep.collectAsState()

    Scaffold(
        topBar = {
            TopBarWithBackButton(
                caption = stringRes(com.vitorpamplona.amethyst.R.string.deploy_relay),
                popBack = {
                    if (currentStep == DeploymentStep.PROVIDER_SELECTION) {
                        nav.popBack()
                    } else {
                        viewModel.goBack()
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (currentStep) {
                DeploymentStep.PROVIDER_SELECTION -> ProviderSelectionStep(viewModel)
                DeploymentStep.API_KEY_INPUT -> ApiKeyInputStep(viewModel)
                DeploymentStep.PLAN_SELECTION -> PlanSelectionStep(viewModel)
                DeploymentStep.CONFIGURE_RELAY -> ConfigureRelayStep(viewModel)
                DeploymentStep.DEPLOYING -> DeployingStep(viewModel)
                DeploymentStep.MANAGEMENT -> ManagementStep(viewModel)
            }
        }
    }
}

@Composable
private fun ProviderSelectionStep(viewModel: RelayDeploymentViewModel) {
    val providers by viewModel.availableProviders.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        Text(
            text = stringRes(com.vitorpamplona.amethyst.R.string.deploy_relay_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringRes(com.vitorpamplona.amethyst.R.string.choose_provider),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(12.dp))

        providers.forEach { provider ->
            ProviderCard(provider) { viewModel.selectProvider(provider.id) }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringRes(com.vitorpamplona.amethyst.R.string.what_is_strfry),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringRes(com.vitorpamplona.amethyst.R.string.strfry_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: VpsProviderInfo,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Cloud,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = provider.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (provider.acceptsCrypto) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.AttachMoney,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            text = stringRes(com.vitorpamplona.amethyst.R.string.accepts_crypto),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeyInputStep(viewModel: RelayDeploymentViewModel) {
    val apiKey by viewModel.apiKey.collectAsState()
    val isValidating by viewModel.isValidatingKey.collectAsState()
    val error by viewModel.error.collectAsState()
    val provider by viewModel.selectedProvider.collectAsState()
    var showKey by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        Text(
            text = stringRes(com.vitorpamplona.amethyst.R.string.enter_api_key_title, provider?.info?.name ?: ""),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringRes(com.vitorpamplona.amethyst.R.string.enter_api_key_description, provider?.info?.website ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { viewModel.updateApiKey(it) },
            label = { Text(stringRes(com.vitorpamplona.amethyst.R.string.api_key)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null,
                    )
                }
            },
        )

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.validateAndProceed() },
            modifier = Modifier.fillMaxWidth(),
            enabled = apiKey.isNotBlank() && !isValidating,
        ) {
            if (isValidating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(stringRes(com.vitorpamplona.amethyst.R.string.validate_and_continue))
        }

        Spacer(Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringRes(com.vitorpamplona.amethyst.R.string.api_key_security_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlanSelectionStep(viewModel: RelayDeploymentViewModel) {
    val regions by viewModel.regions.collectAsState()
    val plans by viewModel.plans.collectAsState()
    val selectedRegion by viewModel.selectedRegion.collectAsState()
    val selectedPlan by viewModel.selectedPlan.collectAsState()
    val existingServers by viewModel.existingServers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    if (isLoading && plans.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (existingServers.isNotEmpty()) {
            item {
                Text(
                    text = stringRes(com.vitorpamplona.amethyst.R.string.existing_servers),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            items(existingServers) { server ->
                ExistingServerCard(server) { viewModel.manageExistingServer(server) }
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    text = stringRes(com.vitorpamplona.amethyst.R.string.deploy_new_relay),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        item {
            Text(
                text = stringRes(com.vitorpamplona.amethyst.R.string.select_region),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            RegionSelector(regions, selectedRegion) { viewModel.selectRegion(it) }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringRes(com.vitorpamplona.amethyst.R.string.select_plan),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        val filteredPlans =
            if (selectedRegion != null) {
                plans.filter { it.regions.contains(selectedRegion!!.id) }
            } else {
                plans
            }

        items(filteredPlans) { plan ->
            PlanCard(plan, plan == selectedPlan) { viewModel.selectPlan(plan) }
        }

        item {
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { viewModel.proceedToConfigure() },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedRegion != null && selectedPlan != null,
            ) {
                Text(stringRes(com.vitorpamplona.amethyst.R.string.continue_button))
            }
        }
    }
}

@Composable
private fun RegionSelector(
    regions: List<VpsRegion>,
    selectedRegion: VpsRegion?,
    onSelect: (VpsRegion) -> Unit,
) {
    val grouped = regions.groupBy { it.continent }.toSortedMap()

    Column {
        grouped.forEach { (continent, regionList) ->
            Text(
                text = continent,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                regionList.sortedBy { it.city }.take(6).forEach { region ->
                    val isSelected = region == selectedRegion
                    OutlinedButton(
                        onClick = { onSelect(region) },
                        colors =
                            if (isSelected) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            region.city,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: VpsPlan,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        border =
            if (isSelected) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
        colors =
            if (isSelected) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            } else {
                CardDefaults.cardColors()
            },
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = plan.specsDisplay,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${plan.bandwidthTb}TB bandwidth",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = plan.monthlyPriceDisplay,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ExistingServerCard(
    server: VpsServer,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Dns,
                contentDescription = null,
                tint =
                    when (server.status) {
                        VpsServerStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                        VpsServerStatus.PENDING -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = server.label.ifEmpty { server.id },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${server.ipv4} (${server.status.name.lowercase()})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConfigureRelayStep(viewModel: RelayDeploymentViewModel) {
    val relayName by viewModel.relayName.collectAsState()
    val relayDescription by viewModel.relayDescription.collectAsState()
    val domain by viewModel.domain.collectAsState()
    val selectedPlan by viewModel.selectedPlan.collectAsState()
    val selectedRegion by viewModel.selectedRegion.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        Text(
            text = stringRes(com.vitorpamplona.amethyst.R.string.configure_relay),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    stringRes(com.vitorpamplona.amethyst.R.string.deployment_summary),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                selectedRegion?.let {
                    Text("${it.city}, ${it.country}", style = MaterialTheme.typography.bodySmall)
                }
                selectedPlan?.let {
                    Text("${it.specsDisplay} - ${it.monthlyPriceDisplay}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = relayName,
            onValueChange = { viewModel.updateRelayName(it) },
            label = { Text(stringRes(com.vitorpamplona.amethyst.R.string.relay_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = relayDescription,
            onValueChange = { viewModel.updateRelayDescription(it) },
            label = { Text(stringRes(com.vitorpamplona.amethyst.R.string.relay_description_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = domain,
            onValueChange = { viewModel.updateDomain(it) },
            label = { Text(stringRes(com.vitorpamplona.amethyst.R.string.domain_optional)) },
            placeholder = { Text("relay.example.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = stringRes(com.vitorpamplona.amethyst.R.string.domain_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.deploy() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringRes(com.vitorpamplona.amethyst.R.string.deploy_now))
        }
    }
}

@Composable
private fun DeployingStep(viewModel: RelayDeploymentViewModel) {
    val logs by viewModel.deploymentLog.collectAsState()
    val error by viewModel.error.collectAsState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        Text(
            text = stringRes(com.vitorpamplona.amethyst.R.string.deploying_relay),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(16.dp))

        if (error == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            LazyColumn(Modifier.padding(12.dp)) {
                items(logs) { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.goBack() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringRes(com.vitorpamplona.amethyst.R.string.go_back))
            }
        }
    }
}

@Composable
private fun ManagementStep(viewModel: RelayDeploymentViewModel) {
    val server by viewModel.deployedServer.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val currentServer = server ?: return

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        Text(
            text = stringRes(com.vitorpamplona.amethyst.R.string.server_management),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Cloud,
                        contentDescription = null,
                        tint =
                            when (currentServer.status) {
                                VpsServerStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                                VpsServerStatus.PENDING -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.error
                            },
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = currentServer.label.ifEmpty { "strfry relay" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint =
                                    when (currentServer.status) {
                                        VpsServerStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                                        VpsServerStatus.PENDING -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.error
                                    },
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text =
                                    currentServer.status.name
                                        .lowercase()
                                        .replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                InfoRow(stringRes(com.vitorpamplona.amethyst.R.string.server_id_label), currentServer.id)
                InfoRow(stringRes(com.vitorpamplona.amethyst.R.string.ipv4_label), currentServer.ipv4)
                if (currentServer.ipv6.isNotEmpty()) {
                    InfoRow(stringRes(com.vitorpamplona.amethyst.R.string.ipv6_label), currentServer.ipv6)
                }
                InfoRow(stringRes(com.vitorpamplona.amethyst.R.string.region_label), currentServer.region)
                InfoRow(stringRes(com.vitorpamplona.amethyst.R.string.os_label), currentServer.os)

                if (currentServer.status == VpsServerStatus.ACTIVE && currentServer.ipv4.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = stringRes(com.vitorpamplona.amethyst.R.string.relay_url_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))

                    val relayUrl = currentServer.relayUrl ?: "ws://${currentServer.ipv4}"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = relayUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(relayUrl))
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { viewModel.refreshServerStatus() },
                modifier = Modifier.weight(1f),
                enabled = !isLoading,
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringRes(com.vitorpamplona.amethyst.R.string.refresh))
            }

            Button(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                enabled = !isLoading,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringRes(com.vitorpamplona.amethyst.R.string.delete_server))
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (currentServer.status == VpsServerStatus.ACTIVE) {
            Spacer(Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringRes(com.vitorpamplona.amethyst.R.string.next_steps_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringRes(com.vitorpamplona.amethyst.R.string.next_steps_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (currentServer.status == VpsServerStatus.PENDING) {
            Spacer(Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringRes(com.vitorpamplona.amethyst.R.string.server_provisioning),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringRes(com.vitorpamplona.amethyst.R.string.delete_server_confirm_title)) },
            text = { Text(stringRes(com.vitorpamplona.amethyst.R.string.delete_server_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteServer()
                    },
                ) {
                    Text(
                        stringRes(com.vitorpamplona.amethyst.R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringRes(com.vitorpamplona.amethyst.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}
