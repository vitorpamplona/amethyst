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
package com.vitorpamplona.amethyst.service.relayClient.authCommand.compose

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurpose
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.RelayAuthPrompt
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.UserAuthChoice
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserInfo
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/** Above this many counterparties for a purpose, collapse the named rows into an avatar facepile. */
private const val NAMED_ROWS_MAX = 2

/** Avatars shown in the facepile before the "+N" overflow badge. */
private const val FACEPILE_MAX = 5

/**
 * App-wide host for NIP-42 auth prompts. Collects [RelayAuthPromptBus.prompts] and shows one
 * dialog at a time explaining *why* a relay wants the user to log in (who it serves), letting the
 * user allow once, always allow, or block the relay. Dismissing answers [UserAuthChoice.DISMISS],
 * which the bus also falls back to on timeout, so a relay connection never blocks on the UI.
 */
@Composable
fun RelayAuthPromptHost(accountViewModel: AccountViewModel) {
    val bus = remember { Amethyst.instance.authCoordinator.promptBus }
    val queue = remember { mutableStateListOf<RelayAuthPrompt>() }

    LaunchedEffect(bus) {
        bus.prompts.collect { prompt ->
            queue.add(prompt)
            // Drop the prompt whenever it resolves by any path (answered here, answered on another
            // account's dialog, or timed out in the bus) so we never show a stale one.
            prompt.onResolved { queue.remove(prompt) }
        }
    }

    queue.firstOrNull { !it.isResolved }?.let { prompt ->
        RelayAuthPromptDialog(prompt, accountViewModel) { choice ->
            prompt.respond(choice)
            queue.remove(prompt)
        }
    }
}

@Composable
private fun RelayAuthPromptDialog(
    prompt: RelayAuthPrompt,
    accountViewModel: AccountViewModel,
    onChoice: (UserAuthChoice) -> Unit,
) {
    // The action the user was actually doing drives the title and the "if you don't" consequence,
    // so the out-of-context prompt reconnects to their intent.
    val primary = remember(prompt) { prompt.purposes.primaryNamed() }
    val who =
        primary?.let { p ->
            when (p.kind) {
                AuthPurposeKind.POST_VENUE, AuthPurposeKind.READ_VENUE ->
                    p.venues.firstOrNull()?.let { rememberVenueLabel(it, accountViewModel) }
                else -> counterpartyLabel(p.counterparties, accountViewModel)
            }
        }
    val showLabels = prompt.purposes.size > 1

    AlertDialog(
        onDismissRequest = { onChoice(UserAuthChoice.DISMISS) },
        icon = {
            Icon(
                symbol = MaterialSymbols.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(titleFor(primary?.kind, who)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringRes(R.string.relay_auth_prompt_message))
                RelayChip(prompt.relayUrl.url)

                prompt.purposes.forEach { purpose ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (showLabels) {
                            Text(
                                text = stringRes(relayAuthReasonRes(purpose.kind)),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val people = purpose.counterparties.toList()
                        if (people.size <= NAMED_ROWS_MAX) {
                            people.forEach { CounterpartyRow(it, accountViewModel) }
                        } else {
                            CounterpartyFacepile(people, accountViewModel)
                        }
                    }
                }

                consequenceFor(primary?.kind, who)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(
                    onClick = { onChoice(UserAuthChoice.ALLOW_ONCE) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringRes(R.string.relay_auth_allow_once)) }
                // For a DM/notification prompt, offer the broad rule: always log in to deliver my
                // messages to whoever I'm talking to, so these prompts stop appearing. Those toggles
                // only apply under CUSTOM, so switch to it and turn both message toggles on.
                if (primary?.kind == AuthPurposeKind.SEND_DM || primary?.kind == AuthPurposeKind.NOTIFY_INBOX) {
                    FilledTonalButton(
                        onClick = {
                            val settings = accountViewModel.account.settings
                            settings.changeDefaultRelayAuthPolicy(RelayAuthPolicy.CUSTOM)
                            settings.changeRelayAuthTrustMessageFollows(true)
                            settings.changeRelayAuthTrustMessageStrangers(true)
                            onChoice(UserAuthChoice.ALLOW_ONCE)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringRes(R.string.relay_auth_always_deliver)) }
                }
                FilledTonalButton(
                    onClick = { onChoice(UserAuthChoice.ALWAYS_ALLOW) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringRes(R.string.relay_auth_always_allow)) }
                TextButton(
                    onClick = { onChoice(UserAuthChoice.BLOCK) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringRes(R.string.relay_auth_block)) }
            }
        },
    )
}

@Composable
private fun RelayChip(url: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = url,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
    }
}

@Composable
private fun CounterpartyRow(
    pubkey: HexKey,
    accountViewModel: AccountViewModel,
) {
    LoadRelayAuthUser(pubkey, accountViewModel) { user ->
        if (user != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ClickableUserPicture(user, 32.dp, accountViewModel)
                UsernameDisplay(user, accountViewModel = accountViewModel)
            }
        }
    }
}

@Composable
private fun CounterpartyFacepile(
    pubkeys: List<HexKey>,
    accountViewModel: AccountViewModel,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((-8).dp),
    ) {
        pubkeys.take(FACEPILE_MAX).forEach { pubkey ->
            LoadRelayAuthUser(pubkey, accountViewModel) { user ->
                if (user != null) {
                    ClickableUserPicture(
                        baseUser = user,
                        size = 30.dp,
                        accountViewModel = accountViewModel,
                        modifier = Modifier.border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    )
                }
            }
        }
        val extra = pubkeys.size - FACEPILE_MAX
        if (extra > 0) {
            Text(
                text = "+$extra",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
    }
}

/** The purpose that best describes what the user was doing (most user-facing first). */
private fun List<AuthPurpose>.primaryNamed(): AuthPurpose? =
    listOf(
        AuthPurposeKind.SEND_DM,
        AuthPurposeKind.NOTIFY_INBOX,
        AuthPurposeKind.POST_VENUE,
        AuthPurposeKind.READ_OUTBOX,
        AuthPurposeKind.READ_VENUE,
    ).firstNotNullOfOrNull { kind ->
        firstOrNull { it.kind == kind && (it.counterparties.isNotEmpty() || it.venues.isNotEmpty()) }
    }

@Composable
private fun titleFor(
    kind: AuthPurposeKind?,
    who: String?,
): String =
    when (kind) {
        AuthPurposeKind.SEND_DM -> stringRes(R.string.relay_auth_title_send_dm, who ?: "")
        AuthPurposeKind.NOTIFY_INBOX -> stringRes(R.string.relay_auth_title_notify, who ?: "")
        AuthPurposeKind.READ_OUTBOX -> stringRes(R.string.relay_auth_title_read, who ?: "")
        AuthPurposeKind.POST_VENUE -> stringRes(R.string.relay_auth_title_post_venue, who ?: "")
        AuthPurposeKind.READ_VENUE -> stringRes(R.string.relay_auth_title_read_venue, who ?: "")
        else -> stringRes(R.string.relay_auth_prompt_title)
    }

@Composable
private fun consequenceFor(
    kind: AuthPurposeKind?,
    who: String?,
): String? =
    when (kind) {
        AuthPurposeKind.SEND_DM -> stringRes(R.string.relay_auth_consequence_send_dm, who ?: "")
        AuthPurposeKind.NOTIFY_INBOX -> stringRes(R.string.relay_auth_consequence_notify, who ?: "")
        AuthPurposeKind.READ_OUTBOX -> stringRes(R.string.relay_auth_consequence_read, who ?: "")
        AuthPurposeKind.POST_VENUE -> stringRes(R.string.relay_auth_consequence_post_venue, who ?: "")
        AuthPurposeKind.READ_VENUE -> stringRes(R.string.relay_auth_consequence_read_venue, who ?: "")
        else -> null
    }

/** A short label for a set of counterparties: the first person's name, or "Alice and others". */
@Composable
private fun counterpartyLabel(
    pubkeys: Set<HexKey>,
    accountViewModel: AccountViewModel,
): String {
    val first = pubkeys.firstOrNull() ?: return ""
    val name = rememberDisplayName(first, accountViewModel)
    return if (pubkeys.size > 1) stringRes(R.string.relay_auth_name_and_others, name) else name
}

/**
 * A display name for a venue id — a public chat channel (64-hex event id), a NIP-53 live activity,
 * or a NIP-72 community. For channels/live activities it get-or-creates the channel and observes it,
 * subscribing to relays for the title so it loads and updates like a person's name; a community's
 * name is its NIP-72 d-identifier, taken straight from the address.
 */
@Composable
private fun rememberVenueLabel(
    venueId: String,
    accountViewModel: AccountViewModel,
): String {
    val channel: Channel? =
        remember(venueId) {
            when {
                venueId.length == 64 -> accountViewModel.checkGetOrCreatePublicChatChannel(venueId)
                venueId.startsWith("30311:") -> Address.parse(venueId)?.let { accountViewModel.checkGetOrCreateLiveActivityChannel(it) }
                else -> null
            }
        }

    if (channel != null) {
        // Subscribes for the channel's metadata and recomposes when it arrives.
        val state by observeChannel(channel, accountViewModel)
        val name = (state?.channel ?: channel).toBestDisplayName()
        if (name.isNotBlank()) return name
    }

    // Community: the d-identifier is the name in NIP-72. Also the fallback for an unresolved channel.
    return venueId.substringAfterLast(':').ifEmpty { venueId.take(8) }
}

/** The best display name for [pubkey], reactive to metadata arriving from relays. */
@Composable
private fun rememberDisplayName(
    pubkey: HexKey,
    accountViewModel: AccountViewModel,
): String {
    var user by remember(pubkey) { mutableStateOf(accountViewModel.getUserIfExists(pubkey)) }
    if (user == null) {
        LaunchedEffect(pubkey) { user = accountViewModel.checkGetOrCreateUser(pubkey) }
    }
    val loaded = user ?: return pubkey.take(8)
    // Reading the observed metadata registers a snapshot read, so the name updates when it arrives.
    val metadata by observeUserInfo(loaded, accountViewModel)
    return metadata?.info?.bestName() ?: loaded.toBestDisplayName()
}
