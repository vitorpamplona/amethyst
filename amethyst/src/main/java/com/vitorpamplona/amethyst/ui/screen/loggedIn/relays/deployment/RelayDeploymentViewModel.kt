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

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.service.vps.StrfryDeployScript
import com.vitorpamplona.amethyst.service.vps.VpsPlan
import com.vitorpamplona.amethyst.service.vps.VpsProvider
import com.vitorpamplona.amethyst.service.vps.VpsProviderInfo
import com.vitorpamplona.amethyst.service.vps.VpsRegion
import com.vitorpamplona.amethyst.service.vps.VpsServer
import com.vitorpamplona.amethyst.service.vps.VpsServerStatus
import com.vitorpamplona.amethyst.service.vps.VultrProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class DeploymentStep {
    PROVIDER_SELECTION,
    API_KEY_INPUT,
    PLAN_SELECTION,
    CONFIGURE_RELAY,
    DEPLOYING,
    MANAGEMENT,
}

@Stable
class RelayDeploymentViewModel(
    private val userPubkeyHex: String,
) : ViewModel() {
    private val providers: Map<String, VpsProvider> =
        mapOf(
            "vultr" to VultrProvider(),
        )

    private val _currentStep = MutableStateFlow(DeploymentStep.PROVIDER_SELECTION)
    val currentStep: StateFlow<DeploymentStep> = _currentStep

    private val _availableProviders = MutableStateFlow(providers.values.map { it.info })
    val availableProviders: StateFlow<List<VpsProviderInfo>> = _availableProviders

    private val _selectedProvider = MutableStateFlow<VpsProvider?>(null)
    val selectedProvider: StateFlow<VpsProvider?> = _selectedProvider

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey

    private val _isValidatingKey = MutableStateFlow(false)
    val isValidatingKey: StateFlow<Boolean> = _isValidatingKey

    private val _regions = MutableStateFlow<List<VpsRegion>>(emptyList())
    val regions: StateFlow<List<VpsRegion>> = _regions

    private val _plans = MutableStateFlow<List<VpsPlan>>(emptyList())
    val plans: StateFlow<List<VpsPlan>> = _plans

    private val _selectedRegion = MutableStateFlow<VpsRegion?>(null)
    val selectedRegion: StateFlow<VpsRegion?> = _selectedRegion

    private val _selectedPlan = MutableStateFlow<VpsPlan?>(null)
    val selectedPlan: StateFlow<VpsPlan?> = _selectedPlan

    private val _relayName = MutableStateFlow("My Nostr Relay")
    val relayName: StateFlow<String> = _relayName

    private val _relayDescription = MutableStateFlow("A personal Nostr relay powered by strfry")
    val relayDescription: StateFlow<String> = _relayDescription

    private val _domain = MutableStateFlow("")
    val domain: StateFlow<String> = _domain

    private val _deployedServer = MutableStateFlow<VpsServer?>(null)
    val deployedServer: StateFlow<VpsServer?> = _deployedServer

    private val _existingServers = MutableStateFlow<List<VpsServer>>(emptyList())
    val existingServers: StateFlow<List<VpsServer>> = _existingServers

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _deploymentLog = MutableStateFlow<List<String>>(emptyList())
    val deploymentLog: StateFlow<List<String>> = _deploymentLog

    private var statusPollingJob: Job? = null

    fun selectProvider(providerId: String) {
        _selectedProvider.value = providers[providerId]
        _currentStep.value = DeploymentStep.API_KEY_INPUT
        _error.value = null
    }

    fun updateApiKey(key: String) {
        _apiKey.value = key
    }

    fun validateAndProceed() {
        val provider = _selectedProvider.value ?: return
        val key = _apiKey.value.trim()
        if (key.isEmpty()) {
            _error.value = "API key cannot be empty"
            return
        }

        _isValidatingKey.value = true
        _error.value = null

        viewModelScope.launch {
            val valid = provider.validateApiKey(key)
            _isValidatingKey.value = false

            if (valid) {
                loadPlansAndRegions()
                loadExistingServers()
                _currentStep.value = DeploymentStep.PLAN_SELECTION
            } else {
                _error.value = "Invalid API key. Please check and try again."
            }
        }
    }

    private fun loadPlansAndRegions() {
        val provider = _selectedProvider.value ?: return
        val key = _apiKey.value.trim()

        viewModelScope.launch {
            _isLoading.value = true
            provider.listRegions(key).onSuccess { _regions.value = it }
            provider.listPlans(key).onSuccess { _plans.value = it }
            _isLoading.value = false
        }
    }

    private fun loadExistingServers() {
        val provider = _selectedProvider.value ?: return
        val key = _apiKey.value.trim()

        viewModelScope.launch {
            provider.listServers(key).onSuccess {
                _existingServers.value = it
            }
        }
    }

    fun selectRegion(region: VpsRegion) {
        _selectedRegion.value = region
    }

    fun selectPlan(plan: VpsPlan) {
        _selectedPlan.value = plan
    }

    fun updateRelayName(name: String) {
        _relayName.value = name
    }

    fun updateRelayDescription(description: String) {
        _relayDescription.value = description
    }

    fun updateDomain(d: String) {
        _domain.value = d
    }

    fun proceedToConfigure() {
        if (_selectedRegion.value == null || _selectedPlan.value == null) {
            _error.value = "Please select a region and plan"
            return
        }
        _error.value = null
        _currentStep.value = DeploymentStep.CONFIGURE_RELAY
    }

    fun deploy() {
        val provider = _selectedProvider.value ?: return
        val key = _apiKey.value.trim()
        val region = _selectedRegion.value ?: return
        val plan = _selectedPlan.value ?: return

        _error.value = null
        _deploymentLog.value = listOf("Starting deployment...")
        _currentStep.value = DeploymentStep.DEPLOYING

        viewModelScope.launch {
            addLog("Generating strfry configuration...")

            val domainValue = _domain.value.trim().ifEmpty { null }
            val userData =
                StrfryDeployScript.generate(
                    relayName = _relayName.value,
                    relayDescription = _relayDescription.value,
                    adminPubkey = userPubkeyHex,
                    domain = domainValue,
                )

            addLog("Creating server in ${region.city}, ${region.country}...")
            addLog("Plan: ${plan.specsDisplay} (${plan.monthlyPriceDisplay})")

            val result =
                provider.createServer(
                    apiKey = key,
                    region = region.id,
                    plan = plan.id,
                    label = "strfry-relay",
                    userData = userData,
                )

            result
                .onSuccess { server ->
                    _deployedServer.value = server
                    addLog("Server created! ID: ${server.id}")
                    addLog("Waiting for server to become active...")
                    startStatusPolling(server.id)
                }.onFailure { e ->
                    addLog("ERROR: ${e.message}")
                    _error.value = e.message
                }
        }
    }

    private fun startStatusPolling(serverId: String) {
        statusPollingJob?.cancel()
        statusPollingJob =
            viewModelScope.launch {
                val provider = _selectedProvider.value ?: return@launch
                val key = _apiKey.value.trim()
                var attempts = 0

                while (attempts < 60) {
                    delay(10_000)
                    attempts++

                    provider.getServer(key, serverId).onSuccess { server ->
                        _deployedServer.value = server

                        when (server.status) {
                            VpsServerStatus.ACTIVE -> {
                                addLog("Server is active! IP: ${server.ipv4}")
                                addLog("strfry is being installed (this takes a few minutes)...")
                                addLog("Your relay will be available at: ws://${server.ipv4}")
                                if (_domain.value.isNotEmpty()) {
                                    addLog("Once DNS is configured: wss://${_domain.value}")
                                }
                                _currentStep.value = DeploymentStep.MANAGEMENT
                                return@launch
                            }

                            VpsServerStatus.PENDING -> {
                                if (attempts % 3 == 0) {
                                    addLog("Still provisioning... (${attempts * 10}s)")
                                }
                            }

                            else -> {
                                addLog("Server status: ${server.status}")
                            }
                        }
                    }
                }
                addLog("Timed out waiting for server. Check your provider dashboard.")
                _currentStep.value = DeploymentStep.MANAGEMENT
            }
    }

    fun refreshServerStatus() {
        val provider = _selectedProvider.value ?: return
        val key = _apiKey.value.trim()
        val server = _deployedServer.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            provider.getServer(key, server.id).onSuccess {
                _deployedServer.value = it
            }
            _isLoading.value = false
        }
    }

    fun deleteServer() {
        val provider = _selectedProvider.value ?: return
        val key = _apiKey.value.trim()
        val server = _deployedServer.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            provider
                .deleteServer(key, server.id)
                .onSuccess {
                    _deployedServer.value = null
                    _currentStep.value = DeploymentStep.PLAN_SELECTION
                    loadExistingServers()
                }.onFailure { e ->
                    _error.value = "Failed to delete server: ${e.message}"
                }

            _isLoading.value = false
        }
    }

    fun manageExistingServer(server: VpsServer) {
        _deployedServer.value = server
        _currentStep.value = DeploymentStep.MANAGEMENT
    }

    fun goBack() {
        _error.value = null
        when (_currentStep.value) {
            DeploymentStep.API_KEY_INPUT -> {
                _currentStep.value = DeploymentStep.PROVIDER_SELECTION
            }

            DeploymentStep.PLAN_SELECTION -> {
                _currentStep.value = DeploymentStep.API_KEY_INPUT
            }

            DeploymentStep.CONFIGURE_RELAY -> {
                _currentStep.value = DeploymentStep.PLAN_SELECTION
            }

            DeploymentStep.MANAGEMENT -> {
                _deployedServer.value = null
                _currentStep.value = DeploymentStep.PLAN_SELECTION
            }

            else -> {}
        }
    }

    fun clearError() {
        _error.value = null
    }

    private fun addLog(message: String) {
        _deploymentLog.value = _deploymentLog.value + message
    }

    override fun onCleared() {
        super.onCleared()
        statusPollingJob?.cancel()
    }
}
