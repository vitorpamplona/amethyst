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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurpose
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.nip11RelayInfo.loadRelayInfo
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.RelayAuthPrompt
import com.vitorpamplona.amethyst.service.relayClient.authCommand.model.UserAuthChoice
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserInfo
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.RelayIconFilter
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl

/** Avatars shown in the facepile before the "+N" overflow badge. */
private const val FACEPILE_MAX = 5

/** Id of the avatar placeholder spliced into the reason sentence. */
private const val AVATAR_SLOT = "avatar"

/**
 * App-wide host for NIP-42 auth prompts. Collects [RelayAuthPromptBus.prompts] and shows one
 * dialog at a time explaining *why* a relay wants the user to log in (who it serves), letting the
 * user allow once, always allow, or block the relay. Dismissing answers [UserAuthChoice.DISMISS],
 * which the bus also falls back to on timeout, so a relay connection never blocks on the UI.
 */
@Composable
fun RelayAuthPromptHost(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
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
        RelayAuthPromptDialog(prompt, accountViewModel, nav) { choice ->
            prompt.respond(choice)
            queue.remove(prompt)
        }
    }
}

/**
 * One question, asked once.
 *
 * The title is constant — the decision is always "do I identify myself here?" — and names the account
 * whose npub would be revealed, which on a multi-account device is the one thing the user cannot
 * infer. Everything that varies is a *single sentence* naming what the relay is refusing to do, with
 * the counterparty's avatar spliced into it inline. That sentence carries what used to be four
 * separate blocks: a purpose-specific title, a purpose label, an avatar row, and a red "if you don't"
 * consequence line, each of which repeated the same name.
 *
 * Two buttons and a switch replace four stacked buttons. Nothing here writes a global setting: the
 * old "always deliver my messages" silently flipped the policy to CUSTOM plus two account-wide
 * toggles, so it is now a link to the screen where those toggles are visible.
 */
@Composable
private fun RelayAuthPromptDialog(
    prompt: RelayAuthPrompt,
    accountViewModel: AccountViewModel,
    nav: INav,
    onChoice: (UserAuthChoice) -> Unit,
) {
    var rememberRelay by remember(prompt) { mutableStateOf(false) }

    // The purpose the user is most likely to recognize as "what I was just doing".
    val primary = remember(prompt) { prompt.purposes.primary() }
    val faces = primary?.counterparties?.toList().orEmpty()
    val who =
        when (primary?.kind) {
            AuthPurposeKind.POST_VENUE, AuthPurposeKind.READ_VENUE ->
                primary.venues.firstOrNull()?.let { rememberVenueLabel(it, primary.kind, accountViewModel) }
            else -> counterpartyLabel(faces, accountViewModel)
        }

    AlertDialog(
        onDismissRequest = { onChoice(UserAuthChoice.DISMISS) },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                RelayHeader(prompt, accountViewModel)
                Text(
                    text = stringRes(R.string.relay_auth_login_as, rememberDisplayName(prompt.askingAccount, accountViewModel)),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ReasonSentence(
                    reason = reasonFor(primary?.kind, who, prompt.isMyOwnRelay),
                    name = who,
                    face = faces.firstOrNull(),
                    accountViewModel = accountViewModel,
                )

                // More than one face behind the name: show them, so "and 4 others" is not a black box.
                if (faces.size > 1) CounterpartyFacepile(faces, accountViewModel)

                // Everything else this relay is holding back, as one line instead of stacked sections.
                secondaryLine(prompt.purposes, primary)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                RememberRow(checked = rememberRelay, onCheckedChange = { rememberRelay = it })
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onChoice(UserAuthChoice.DISMISS) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringRes(R.string.relay_auth_not_now)) }
                    Button(
                        onClick = { onChoice(if (rememberRelay) UserAuthChoice.ALWAYS_ALLOW else UserAuthChoice.ALLOW_ONCE) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringRes(R.string.relay_auth_log_in)) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = { onChoice(UserAuthChoice.BLOCK) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringRes(R.string.relay_auth_never_allow), style = MaterialTheme.typography.labelMedium) }
                    TextButton(
                        onClick = {
                            onChoice(UserAuthChoice.DISMISS)
                            nav.nav(Route.RelayAuthSettings)
                        },
                    ) { Text(stringRes(R.string.relay_auth_how_we_decide), style = MaterialTheme.typography.labelMedium) }
                }
            }
        },
    )
}

/** The relay leads the dialog, because the relay is the thing being trusted. */
@Composable
private fun RelayHeader(
    prompt: RelayAuthPrompt,
    accountViewModel: AccountViewModel,
) {
    val info = loadRelayInfo(prompt.relayUrl).value
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        RobohashFallbackAsyncImage(
            robot = info?.id ?: prompt.relayUrl.displayUrl(),
            model = info?.icon,
            contentDescription = null,
            colorFilter = RelayIconFilter,
            modifier = Modifier.size(34.dp).clip(MaterialTheme.shapes.small),
            loadProfilePicture = accountViewModel.settings.showProfilePictures(),
            loadRobohash = accountViewModel.settings.isNotPerformanceMode(),
        )
        Column {
            Text(
                text = prompt.relayUrl.displayUrl(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Text(
                text = stringRes(R.string.relay_auth_relay_asks),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The one variable line, with [face]'s avatar spliced in ahead of [name] so the person reads as part
 * of the sentence rather than a separate block to parse.
 */
@Composable
private fun ReasonSentence(
    reason: String,
    name: String?,
    face: HexKey?,
    accountViewModel: AccountViewModel,
) {
    // The reason is already formatted with the name in it, so we locate that substring to bold it and
    // to splice the avatar in ahead of it. Nothing depends on finding it: a translation that drops or
    // rewrites the placeholder simply renders as plain text with no avatar.
    val subject = name?.takeIf { it.isNotBlank() && reason.contains(it) }
    val annotated: AnnotatedString =
        remember(reason, subject, face) {
            buildAnnotatedString {
                if (subject == null) {
                    append(reason)
                } else {
                    val at = reason.indexOf(subject)
                    append(reason.substring(0, at))
                    if (face != null) appendInlineContent(AVATAR_SLOT, " ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(subject) }
                    append(reason.substring(at + subject.length))
                }
            }
        }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge,
        inlineContent =
            if (face == null) {
                emptyMap()
            } else {
                mapOf(
                    AVATAR_SLOT to
                        InlineTextContent(
                            Placeholder(22.sp, 22.sp, PlaceholderVerticalAlign.TextCenter),
                        ) {
                            LoadRelayAuthUser(face, accountViewModel) { user ->
                                if (user != null) ClickableUserPicture(user, 20.dp, accountViewModel)
                            }
                        },
                )
            },
    )
}

/** Remembering becomes a switch, so "allow" and "allow forever" stop competing as two buttons. */
@Composable
private fun RememberRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringRes(R.string.relay_auth_remember_relay),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = checked, onCheckedChange = onCheckedChange)
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

/**
 * The purpose that best describes what the user was doing, most user-facing first. Unlike the old
 * ordering this also considers the purposes that name nobody — reading your own inbox and reading the
 * conversation on screen — which previously had no representation at all.
 */
private fun List<AuthPurpose>.primary(): AuthPurpose? =
    listOf(
        AuthPurposeKind.SEND_DM,
        AuthPurposeKind.NOTIFY_INBOX,
        AuthPurposeKind.POST_VENUE,
        AuthPurposeKind.MY_INBOX,
        AuthPurposeKind.THREAD,
        AuthPurposeKind.READ_OUTBOX,
        AuthPurposeKind.READ_VENUE,
    ).firstNotNullOfOrNull { kind ->
        firstOrNull { it.kind == kind && (it.counterparties.isNotEmpty() || it.venues.isNotEmpty() || it.kind.namesNobody()) }
    }

/** True for the purposes that are fully described without a counterparty or a venue. */
private fun AuthPurposeKind.namesNobody() = this == AuthPurposeKind.MY_INBOX || this == AuthPurposeKind.THREAD

/**
 * What the relay is refusing to do. One sentence — it states the reason *and* the consequence of
 * declining ("won't accept … unless"), which is why there is no separate red consequence line.
 */
@Composable
private fun reasonFor(
    kind: AuthPurposeKind?,
    who: String?,
    isMyOwnRelay: Boolean,
): String =
    when (kind) {
        AuthPurposeKind.SEND_DM -> stringRes(R.string.relay_auth_why_send_dm, who ?: "")
        AuthPurposeKind.NOTIFY_INBOX -> stringRes(R.string.relay_auth_why_notify_inbox, who ?: "")
        AuthPurposeKind.READ_OUTBOX -> stringRes(R.string.relay_auth_why_read_outbox, who ?: "")
        AuthPurposeKind.POST_VENUE -> stringRes(R.string.relay_auth_why_post_venue, who ?: "")
        AuthPurposeKind.READ_VENUE -> stringRes(R.string.relay_auth_why_read_venue, who ?: "")
        AuthPurposeKind.MY_INBOX -> stringRes(R.string.relay_auth_why_my_inbox)
        AuthPurposeKind.THREAD -> stringRes(R.string.relay_auth_why_thread)
        // No attributable purpose. If it is the user's own relay we can at least say that much,
        // which is the only way MY_OWN_RELAY is ever reachable — the deriver is account-agnostic
        // (one shared socket, many accounts) so it cannot know whose relay this is.
        else ->
            if (isMyOwnRelay) {
                stringRes(R.string.relay_auth_why_my_own_relay)
            } else {
                stringRes(R.string.relay_auth_why_other)
            }
    }

/** "Also holding back: …" — every other live purpose, as one line rather than stacked sections. */
@Composable
private fun secondaryLine(
    purposes: List<AuthPurpose>,
    primary: AuthPurpose?,
): String? {
    val others = purposes.filter { it !== primary }
    if (others.isEmpty()) return null
    return stringRes(R.string.relay_auth_also_holding_back, others.size)
}

/** A short label for a set of counterparties: the first person's name, or "Alice and 4 others". */
@Composable
private fun counterpartyLabel(
    pubkeys: List<HexKey>,
    accountViewModel: AccountViewModel,
): String {
    val first = pubkeys.firstOrNull() ?: return ""
    val name = rememberDisplayName(first, accountViewModel)
    return if (pubkeys.size > 1) stringRes(R.string.relay_auth_name_and_n_others, name, (pubkeys.size - 1).toString()) else name
}

/**
 * A display name for a venue id — a public chat channel (64-hex event id), a NIP-53 live activity,
 * or a NIP-72 community.
 *
 * Only a [AuthPurposeKind.POST_VENUE] id is *known* to be a channel (it is the root of a channel
 * message we are sending). A READ id may have come from the tag-shape fallback, where a bare `#e`
 * list is as likely to be note ids on a thread as channel roots — so we only ever *look up* an
 * existing channel there. Get-or-creating on read is what used to mint phantom public chats in
 * [LocalCache] for ordinary notes, complete with a metadata subscription for a room that never was.
 */
@Composable
private fun rememberVenueLabel(
    venueId: String,
    kind: AuthPurposeKind,
    accountViewModel: AccountViewModel,
): String {
    val channel: Channel? =
        remember(venueId, kind) {
            when {
                venueId.length == 64 ->
                    if (kind == AuthPurposeKind.POST_VENUE) {
                        accountViewModel.checkGetOrCreatePublicChatChannel(venueId)
                    } else {
                        LocalCache.getPublicChatChannelIfExists(venueId)
                    }
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

/**
 * The best display name for [pubkey], reactive to metadata arriving from relays. Falls back to a
 * generic "someone you haven't loaded yet" rather than a hex prefix dressed up as a person's name.
 */
@Composable
private fun rememberDisplayName(
    pubkey: HexKey,
    accountViewModel: AccountViewModel,
): String {
    var user by remember(pubkey) { mutableStateOf(accountViewModel.getUserIfExists(pubkey)) }
    if (user == null) {
        LaunchedEffect(pubkey) { user = accountViewModel.checkGetOrCreateUser(pubkey) }
    }
    val loaded = user ?: return stringRes(R.string.relay_auth_someone_unloaded)
    // Reading the observed metadata registers a snapshot read, so the name updates when it arrives.
    val metadata by observeUserInfo(loaded, accountViewModel)
    return metadata?.info?.bestName() ?: loaded.toBestDisplayName()
}
