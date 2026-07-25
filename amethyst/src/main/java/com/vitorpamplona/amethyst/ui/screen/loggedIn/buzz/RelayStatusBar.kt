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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.amethyst.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.ui.theme.warningColor
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthSnapshot
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * A compact, tappable header that makes **all** of one relay's live state observable in context:
 * connection (from `client.connectedRelaysFlow`), NIP-42 auth phase (from the auth coordinator),
 * the Buzz-dialect marker, and — expanded — the NIP-11 document (software, supported NIPs,
 * limitations, description) plus latency. Every signal here is a real `StateFlow`/`produceState`,
 * so the bar reflects reality as it changes rather than a one-shot snapshot.
 *
 * Reusable across the agent surfaces; the Jobs board pins it above the backlog.
 */
@Composable
fun RelayStatusBar(
    relay: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
) {
    val connected by accountViewModel.account.client
        .connectedRelaysFlow()
        .collectAsStateWithLifecycle()
    val authMap by Amethyst.instance.authCoordinator.receiver.authStateFlow
        .collectAsStateWithLifecycle()
    val buzzRelays by BuzzRelayDialect.flow.collectAsStateWithLifecycle()
    val info by loadRelayInfo(relay)

    val isConnected = relay in connected
    val phase = authMap[relay]?.phase ?: RelayAuthSnapshot.Phase.IDLE
    val isBuzz = relay in buzzRelays
    val stat = remember(relay, isConnected) { Amethyst.instance.relayStats.get(relay) }

    val (dotColor, statusLabel) = health(isConnected, phase)
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.clickable { expanded = !expanded }.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiveDot(dotColor, pulsing = phase == RelayAuthSnapshot.Phase.AUTHENTICATING || (isConnected && phase != RelayAuthSnapshot.Phase.AUTH_FAILED))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = info.name?.takeIf { it.isNotBlank() } ?: relay.url,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = dotColor,
                    )
                }
                if (isBuzz) Chip("Buzz", MaterialSymbols.Bolt)
                Icon(
                    symbol = if (phase == RelayAuthSnapshot.Phase.AUTHENTICATED) MaterialSymbols.Lock else MaterialSymbols.LockOpen,
                    contentDescription = if (phase == RelayAuthSnapshot.Phase.AUTHENTICATED) "Authenticated" else "Not authenticated",
                    tint = if (phase == RelayAuthSnapshot.Phase.AUTHENTICATED) MaterialTheme.colorScheme.allGoodColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Icon(
                    symbol = if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand relay details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    InfoLine("Host", relay.url)
                    InfoLine("Connection", if (isConnected) "Connected" else "Disconnected")
                    InfoLine("Auth (NIP-42)", phase.name.lowercase().replace('_', ' '))
                    InfoLine("Dialect", if (isBuzz) "Buzz workspace" else "vanilla NIP-29")
                    if (stat.pingInMs > 0) InfoLine("Latency", "${stat.pingInMs} ms")
                    info.software?.takeIf { it.isNotBlank() }?.let { InfoLine("Software", it + (info.version?.let { v -> " $v" } ?: "")) }
                    info.supported_nips?.takeIf { it.isNotEmpty() }?.let { InfoLine("Supported NIPs", it.joinToString(", ")) }
                    info.limitation?.let { lim ->
                        val flags =
                            buildList {
                                if (lim.auth_required == true) add("auth required")
                                if (lim.payment_required == true) add("payment required")
                                if (lim.restricted_writes == true) add("restricted writes")
                            }
                        if (flags.isNotEmpty()) InfoLine("Limitations", flags.joinToString(", "))
                    }
                    info.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveDot(
    color: Color,
    pulsing: Boolean,
) {
    val alpha =
        if (pulsing) {
            val t = rememberInfiniteTransition(label = "relayDot")
            t
                .animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                    label = "relayDotAlpha",
                ).value
        } else {
            1f
        }
    Box(
        modifier =
            Modifier
                .size(10.dp)
                .alpha(alpha)
                .clip(CircleShape)
                .background(color),
    )
}

@Composable
private fun Chip(
    label: String,
    symbol: MaterialSymbol,
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(symbol = symbol, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(13.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoLine(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.6f))
    }
}

@Composable
private fun health(
    isConnected: Boolean,
    phase: RelayAuthSnapshot.Phase,
): Pair<Color, String> =
    when {
        !isConnected -> MaterialTheme.colorScheme.error to "Disconnected"
        phase == RelayAuthSnapshot.Phase.AUTH_FAILED -> MaterialTheme.colorScheme.error to "Auth failed"
        phase == RelayAuthSnapshot.Phase.AUTHENTICATING -> MaterialTheme.colorScheme.warningColor to "Authenticating…"
        phase == RelayAuthSnapshot.Phase.AUTHENTICATED -> MaterialTheme.colorScheme.allGoodColor to "Connected · authenticated"
        else -> MaterialTheme.colorScheme.allGoodColor to "Connected"
    }
