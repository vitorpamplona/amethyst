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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinResolveState
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinResolveOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Translate a [NamecoinResolveOutcome] from the quartz resolver into the
 * shared [NamecoinResolveState] used elsewhere in the app (e.g. the
 * desktop `SearchScreen`'s inline Namecoin lookup row).
 *
 * Mirrors the wording desktop already ships so the same identifier
 * produces the same diagnostic string regardless of which surface
 * triggered the lookup. Callers are expected to handle
 * [NamecoinResolveOutcome.Success] separately (it needs a [User]
 * lookup through [com.vitorpamplona.amethyst.model.LocalCache], which
 * this helper has no access to).
 */
fun mapOutcomeToResolveState(outcome: NamecoinResolveOutcome): NamecoinResolveState =
    when (outcome) {
        is NamecoinResolveOutcome.Success ->
            // Success is intentionally NOT handled here — callers must
            // resolve the pubkey through LocalCache first.
            error("mapOutcomeToResolveState called with Success outcome; resolve via LocalCache instead")

        is NamecoinResolveOutcome.NameNotFound -> NamecoinResolveState.NotFound

        is NamecoinResolveOutcome.NoNostrField ->
            NamecoinResolveState.Error("${outcome.name} is registered but has no Nostr pubkey")

        is NamecoinResolveOutcome.MalformedRecord ->
            // Surface the parser detail verbatim so the publisher of the
            // broken record can locate the bad byte
            // (kotlinx.serialization includes a column number).
            NamecoinResolveState.Error("${outcome.name} record is malformed: ${outcome.error}")

        is NamecoinResolveOutcome.ServersUnreachable ->
            NamecoinResolveState.Error("ElectrumX servers unreachable — check your connection or try again")

        is NamecoinResolveOutcome.InvalidIdentifier ->
            NamecoinResolveState.Error("Invalid Namecoin identifier")

        NamecoinResolveOutcome.Timeout ->
            NamecoinResolveState.Error("Resolution timed out — servers may be slow, try again")
    }

/**
 * Lightweight syntactic check: does this look like something we should
 * route to Namecoin? Mirrors [com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver.isNamecoinIdentifier]
 * but tolerates a leading `@` (matches the dropdown's [com.vitorpamplona.amethyst.ui.note.creators.userSuggestions.UserSuggestionState.userSearchTermOrNull]).
 */
fun looksLikeNamecoinIdentifier(raw: String): Boolean {
    val trimmed = raw.trim().removePrefix("@").lowercase()
    if (trimmed.length < 5) return false
    return trimmed.endsWith(".bit") ||
        trimmed.contains("@") && trimmed.substringAfter('@').endsWith(".bit")
}

/**
 * Inline Namecoin resolution indicator + result row, sandwiched between
 * the recipient text field and the local-cache suggestion dropdown in
 * [OnchainZapSendDialog].
 *
 * Behaviour:
 *  - Renders nothing when [searchInput] is not a `.bit` identifier.
 *  - Shows a small spinner row ("Resolving on Namecoin…") while the
 *    ElectrumX lookup is in flight (after a 300 ms debounce to match
 *    the dropdown's own debounce).
 *  - On success, shows the resolved user as a tappable row with a
 *    `MaterialSymbols.Link` badge labelled "Namecoin"; tapping calls
 *    [onUserResolved].
 *  - On failure, shows a single explanatory line in the error colour.
 *
 * State is held in [NamecoinResolveState] (the same sealed class the
 * desktop `SearchScreen` and `NamecoinNameService` already use) so this
 * row stays in lockstep with the rest of the app's Namecoin UI.
 *
 * The composable is intentionally self-contained: it owns its own
 * [LaunchedEffect] keyed on [searchInput], so it cancels in-flight
 * lookups whenever the user keeps typing.
 */
@Composable
fun NamecoinResolutionRow(
    searchInput: String,
    accountViewModel: AccountViewModel,
    onUserResolved: (User) -> Unit,
) {
    val trimmed = remember(searchInput) { searchInput.trim().removePrefix("@") }
    if (!looksLikeNamecoinIdentifier(trimmed)) return

    var state by remember { mutableStateOf<NamecoinResolveState?>(null) }

    LaunchedEffect(trimmed) {
        // Match UserSuggestionState's 300 ms debounce so we don't fire a
        // lookup on every keystroke.
        delay(300)
        state = NamecoinResolveState.Loading
        val outcome =
            withContext(Dispatchers.IO) {
                runCatching {
                    Amethyst.instance.namecoinResolver.resolveDetailed(trimmed)
                }.getOrElse {
                    NamecoinResolveOutcome.ServersUnreachable(
                        it.message ?: it::class.simpleName ?: "Lookup error",
                    )
                }
            }
        state =
            when (outcome) {
                is NamecoinResolveOutcome.Success -> NamecoinResolveState.Resolved(outcome.result)
                else -> mapOutcomeToResolveState(outcome)
            }
    }

    Spacer(Modifier.size(8.dp))
    when (val s = state) {
        null, NamecoinResolveState.Loading -> ResolvingChip(trimmed)
        is NamecoinResolveState.Resolved -> ResolvedRow(trimmed, s, accountViewModel, onUserResolved)
        NamecoinResolveState.NotFound -> FailedRow("No record for $trimmed on Namecoin.")
        is NamecoinResolveState.Error -> FailedRow(s.message)
    }
}

@Composable
private fun ResolvingChip(query: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Resolving $query on Namecoin…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ResolvedRow(
    query: String,
    state: NamecoinResolveState.Resolved,
    accountViewModel: AccountViewModel,
    onUserResolved: (User) -> Unit,
) {
    // Look up the User in the same cache the rest of the app uses, exactly
    // the way desktop's SearchScreen does. Falls back to a malformed-record
    // error row if the pubkey somehow fails the hex shape check.
    val user =
        remember(state.result.pubkey) {
            accountViewModel.account.cache.checkGetOrCreateUser(state.result.pubkey)
        }
    if (user == null) {
        FailedRow(
            "${state.result.namecoinName} record is malformed: " +
                "pubkey ${state.result.pubkey} is not a valid hex key",
        )
        return
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onUserResolved(user) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            UserPicture(
                userHex = user.pubkeyHex,
                size = 32.dp,
                accountViewModel = accountViewModel,
                nav = EmptyNav(),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.toBestDisplayName(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = query,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            NamecoinBadge()
        }
    }
}

@Composable
private fun NamecoinBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = "Namecoin",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FailedRow(message: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                symbol = MaterialSymbols.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer),
            )
        }
    }
}
