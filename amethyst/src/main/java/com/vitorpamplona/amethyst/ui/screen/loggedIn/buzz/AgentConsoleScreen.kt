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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.buzz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.commons.model.buzz.AgentFleetMetrics
import com.vitorpamplona.amethyst.commons.model.buzz.AgentUsageSummary
import com.vitorpamplona.amethyst.commons.model.buzz.TokenTotals
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel

/**
 * The workspace owner's agent-owner console — a read-only dashboard over the fleet of
 * AI agents that publish to them. Two tabs:
 * - **Costs** — fleet totals and a per-agent breakdown (turns, sessions, tokens, spend),
 *   derived from decrypted NIP-AM turn metrics (`kind:44200`).
 * - **Personas** — the owner's published persona definitions (NIP-AP `kind:30175`).
 *
 * Data comes from [AgentConsoleViewModel], keyed by the owner pubkey so the fetch/decrypt
 * work survives navigation. This v1 is read-only; persona editing and NIP-OA attestation
 * issuance are follow-ups.
 */
@Composable
fun AgentConsoleScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val pubkey = accountViewModel.account.userProfile().pubkeyHex
    val viewModel: AgentConsoleViewModel = viewModel(key = "AgentConsole-$pubkey")

    viewModel.bindAccountIfMissing(accountViewModel.account)

    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val personas by viewModel.personas.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = remember { listOf("Costs", "Personas") }

    Scaffold(
        topBar = { TopBarWithBackButton("Agent Console", nav) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> CostsTab(metrics)
                    else -> PersonasTab(personas)
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp).size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CostsTab(metrics: AgentFleetMetrics) {
    if (metrics.agents.isEmpty()) {
        EmptyState("No agent turn metrics yet. Metrics appear once your agents publish kind:44200 events to you.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            FleetSummaryCard(metrics)
        }
        items(metrics.agents) { agent ->
            AgentCard(agent)
        }
    }
}

@Composable
private fun FleetSummaryCard(metrics: AgentFleetMetrics) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Fleet total",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = formatCost(metrics.totals.costUsd),
                style = MaterialTheme.typography.headlineMedium,
            )
            MetaRow("Agents", metrics.agents.size.toString())
            MetaRow("Sessions", metrics.totalSessions.toString())
            MetaRow("Turns", metrics.totalTurns.toString())
            TokenBreakdown(metrics.totals)
            if (metrics.hasUnreliableEstimates) {
                Text(
                    text = "⚠ Some totals are estimated from per-turn deltas (a session reported no cumulative counts).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AgentCard(agent: AgentUsageSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = shortKey(agent.agentPubKey),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = formatCost(agent.totals.costUsd),
                style = MaterialTheme.typography.titleLarge,
            )
            MetaRow("Sessions", agent.sessions.toString())
            MetaRow("Turns", agent.turns.toString())
            if (agent.models.isNotEmpty()) MetaRow("Models", agent.models.sorted().joinToString(", "))
            if (agent.harnesses.isNotEmpty()) MetaRow("Harness", agent.harnesses.sorted().joinToString(", "))
            agent.lastActivity?.let { MetaRow("Last turn", it) }
            TokenBreakdown(agent.totals)
        }
    }
}

@Composable
private fun TokenBreakdown(totals: TokenTotals) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    MetaRow("Input tokens", formatCount(totals.inputTokens))
    MetaRow("Output tokens", formatCount(totals.outputTokens))
    MetaRow("Total tokens", formatCount(totals.totalTokens))
    if (totals.cacheReadTokens > 0) MetaRow("Cache read", formatCount(totals.cacheReadTokens))
    if (totals.cacheWriteTokens > 0) MetaRow("Cache write", formatCount(totals.cacheWriteTokens))
}

@Composable
private fun PersonasTab(personas: List<AgentConsoleViewModel.PersonaCard>) {
    if (personas.isEmpty()) {
        EmptyState("No personas published. Personas you publish (kind:30175) appear here.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(personas) { persona ->
            PersonaCardView(persona)
        }
    }
}

@Composable
private fun PersonaCardView(persona: AgentConsoleViewModel.PersonaCard) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = persona.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = persona.slug,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            persona.model?.let { MetaRow("Model", it) }
            persona.provider?.let { MetaRow("Provider", it) }
            persona.runtime?.let { MetaRow("Runtime", it) }
            persona.systemPrompt?.takeIf { it.isNotBlank() }?.let {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun MetaRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatCost(cost: Double): String = "$" + ((cost * 100).toLong() / 100.0).toString().let { padCents(it) }

/** Pads a dollar string to exactly two decimals (e.g. "1.5" -> "1.50", "3" -> "3.00"). */
private fun padCents(value: String): String {
    val dot = value.indexOf('.')
    if (dot < 0) return "$value.00"
    val decimals = value.length - dot - 1
    return when {
        decimals >= 2 -> value.substring(0, dot + 3)
        decimals == 1 -> value + "0"
        else -> value + "00"
    }
}

/** Groups a token count with thin spaces every three digits (1234567 -> "1 234 567"). */
private fun formatCount(n: Long): String {
    val s = n.toString()
    if (s.length <= 3) return s
    val sb = StringBuilder()
    val firstGroup = s.length % 3
    if (firstGroup > 0) {
        sb.append(s, 0, firstGroup)
    }
    var i = firstGroup
    while (i < s.length) {
        if (sb.isNotEmpty()) sb.append(' ')
        sb.append(s, i, i + 3)
        i += 3
    }
    return sb.toString()
}

private fun shortKey(hex: String): String = if (hex.length <= 16) hex else hex.take(8) + "…" + hex.takeLast(8)
