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
package com.vitorpamplona.amethyst.ui.components.namecoin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.MockNamecoinHistoryProvider
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinHistoryEntry
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinNameHistory
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.namecoin.NamecoinResolveState
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.note.UserPicture
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinResolveOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

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
 *
 * Accepted shapes:
 *  - `host.bit` and `user@host.bit` (domain namespace)
 *  - `d/<name>` (domain namespace, direct reference)
 *  - `id/<name>` (identity namespace)
 *
 * The length floor matches the dropdown's `>2 chars` rule while still
 * requiring at least one character after a `d/`/`id/` prefix, so single-
 * character Namecoin labels (which are valid, if expensive, on chain)
 * still trigger the on-chain lookup.
 */
fun looksLikeNamecoinIdentifier(raw: String): Boolean {
    val trimmed = raw.trim().removePrefix("@").lowercase()
    if (trimmed.length < 3) return false
    if (trimmed.startsWith("d/") && trimmed.length > 2) return true
    if (trimmed.startsWith("id/") && trimmed.length > 3) return true
    if (trimmed.length < 5) return false
    return trimmed.endsWith(".bit") ||
        trimmed.contains("@") && trimmed.substringAfter('@').endsWith(".bit")
}

/**
 * Inline Namecoin resolution indicator + result row. Designed to be
 * mounted alongside any text input whose local-cache prefix search can
 * race ahead of an on-chain `.bit` lookup (the onchain-zap recipient
 * field and the global search bar both have this race).
 *
 * Behaviour:
 *  - Renders nothing when [searchInput] is not a Namecoin-shaped
 *    identifier (`.bit`, `d/<name>`, or `id/<name>`).
 *  - Shows a small spinner row ("Resolving on Namecoin…") while the
 *    ElectrumX lookup is in flight (after a 300 ms debounce to match
 *    typical input-field debounce intervals).
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
 *
 * @param modifier applied to the outer `Column` so callers can position
 *   or pad the row (e.g. the search bar pads horizontally).
 */
@Composable
fun NamecoinResolutionRow(
    searchInput: String,
    accountViewModel: AccountViewModel,
    onUserResolved: (User) -> Unit,
    modifier: Modifier = Modifier,
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

    Column(modifier = modifier) {
        Spacer(Modifier.size(8.dp))
        when (val s = state) {
            null, NamecoinResolveState.Loading -> ResolvingChip(trimmed)
            is NamecoinResolveState.Resolved -> ResolvedRow(trimmed, s, accountViewModel, onUserResolved)
            NamecoinResolveState.NotFound -> FailedRow("No record for $trimmed on Namecoin.")
            is NamecoinResolveState.Error -> FailedRow(s.message)
        }
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

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

        // Previous-values panel. Lazily built from the mock provider
        // until the on-chain history extractor lands. The two
        // independent toggles in NamecoinSettings
        // (showHistoryWithinCurrentOwner / showHistoryAcrossExpiry)
        // decide which subset of entries actually surfaces — the panel
        // is hidden entirely when both are off, or when nothing in the
        // history matches the user's toggles.
        val ncSettings =
            remember { Amethyst.instance.namecoinPrefs.current }
        val filteredHistory =
            remember(state.result.namecoinName, ncSettings) {
                if (!ncSettings.anyHistoryEnabled) {
                    null
                } else {
                    MockNamecoinHistoryProvider
                        .forName(state.result.namecoinName)
                        .filterByToggles(
                            showWithinCurrentOwner = ncSettings.showHistoryWithinCurrentOwner,
                            showAcrossExpiry = ncSettings.showHistoryAcrossExpiry,
                        )
                }
            }
        if (filteredHistory != null && filteredHistory.hasEntries) {
            PreviousValuesPanel(
                history = filteredHistory,
                accountViewModel = accountViewModel,
                onUserResolved = onUserResolved,
            )
        }
    }
}

/**
 * Expandable list of prior `nostr.pubkey` values for the resolved
 * Namecoin name.
 *
 * Hidden by default behind a chevron header ("Show 6 previous values")
 * so it doesn't shout at the user on every successful lookup. When
 * expanded:
 *
 *  - within-current-ownership entries are listed compactly back-to-back
 *  - each entry preceded by an expiry gap is announced with an inline
 *    divider ("⚠ Name expired — registered again …") so the user can
 *    see at a glance that the identity changed at that point
 *  - tapping any prior value navigates to that user, the same way
 *    tapping the current value does — this lets you verify what each
 *    historical owner was doing on Nostr without leaving the search
 */
@Composable
private fun PreviousValuesPanel(
    history: NamecoinNameHistory,
    accountViewModel: AccountViewModel,
    onUserResolved: (User) -> Unit,
) {
    var expanded by remember(history.namecoinName) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = previousValuesHeaderText(history, expanded),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    symbol = if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                    contentDescription = if (expanded) "Hide previous values" else "Show previous values",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                ) {
                    history.entries.forEachIndexed { idx, entry ->
                        if (entry.precededByExpiryGap) {
                            ExpiryGapDivider(entry)
                        }
                        PreviousValueRow(
                            entry = entry,
                            ordinal = idx + 1,
                            accountViewModel = accountViewModel,
                            onUserResolved = onUserResolved,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Header text that summarises what's in the panel without expanding
 * it: how many prior values exist, and how many of them sit behind an
 * expiry boundary (since those are the ones a user is most likely to
 * care about — they're literally a different person).
 */
private fun previousValuesHeaderText(
    history: NamecoinNameHistory,
    expanded: Boolean,
): String {
    if (expanded) return "Hide previous values"
    val n = history.entries.size
    val gaps = history.expiryGapCount
    val suffix =
        when {
            gaps == 0 -> ""
            gaps == 1 -> " · 1 prior owner"
            else -> " · $gaps prior owners"
        }
    return "Show $n previous value${if (n == 1) "" else "s"}$suffix"
}

/**
 * Inline divider rendered above the first entry that comes from a
 * different ownership ratchet. Mirrors GitHub's "force-pushed" style
 * marker — a horizontal rule plus a short label — so the visual break
 * is unambiguous without needing a full-width banner.
 */
@Composable
private fun ExpiryGapDivider(entry: NamecoinHistoryEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier.width(16.dp),
            color = MaterialTheme.colorScheme.error,
        )
        Icon(
            symbol = MaterialSymbols.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "Name expired — registered again ${formatDate(entry.timestampSec)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * Compact row for a single historical value. Reuses [UserPicture] so
 * the avatar lights up if Amethyst already has metadata cached for
 * that pubkey, falling back to the auto-generated identicon otherwise.
 */
@Composable
private fun PreviousValueRow(
    entry: NamecoinHistoryEntry,
    ordinal: Int,
    accountViewModel: AccountViewModel,
    onUserResolved: (User) -> Unit,
) {
    val user =
        remember(entry.pubkeyHex) {
            accountViewModel.account.cache.checkGetOrCreateUser(entry.pubkeyHex)
        }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (user != null) Modifier.clickable { onUserResolved(user) } else Modifier,
                ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Small ordinal pill so users can refer to entries by
            // position ("the #3 owner") in conversation.
            Box(
                modifier =
                    Modifier
                        .size(20.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(4.dp),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$ordinal",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (user != null) {
                UserPicture(
                    userHex = user.pubkeyHex,
                    size = 24.dp,
                    accountViewModel = accountViewModel,
                    nav = EmptyNav(),
                )
            } else {
                Spacer(Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user?.toBestDisplayName() ?: shortPubkey(entry.pubkeyHex),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "set ${formatDate(entry.timestampSec)} · block ${entry.blockHeight}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/** "abcd1234…wxyz5678" — visually distinct, fits in a single row. */
private fun shortPubkey(hex: String): String = if (hex.length <= 16) hex else "${hex.take(8)}…${hex.takeLast(8)}"

/**
 * UTC date in `YYYY-MM-DD` form. Deliberately not localised — block
 * heights are global and dates here should match what a user would
 * see on a Namecoin block explorer.
 */
private fun formatDate(epochSeconds: Long?): String {
    if (epochSeconds == null) return "unknown date"
    val date = Instant.ofEpochSecond(epochSeconds).atOffset(ZoneOffset.UTC).toLocalDate()
    return date.format(DateTimeFormatter.ISO_LOCAL_DATE)
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
