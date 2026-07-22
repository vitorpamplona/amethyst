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
package com.vitorpamplona.amethyst.desktop.ui.note

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.nip88Polls.TallyResults
import com.vitorpamplona.amethyst.commons.ui.components.UserAvatar
import com.vitorpamplona.amethyst.desktop.account.AccountState
import com.vitorpamplona.amethyst.desktop.cache.DesktopLocalCache
import com.vitorpamplona.amethyst.desktop.network.DesktopRelayConnectionManager
import com.vitorpamplona.amethyst.desktop.subscriptions.SubscriptionConfig
import com.vitorpamplona.amethyst.desktop.subscriptions.generateSubId
import com.vitorpamplona.amethyst.desktop.subscriptions.rememberSubscription
import com.vitorpamplona.amethyst.desktop.ui.toNoteDisplayData
import com.vitorpamplona.amethyst.desktop.ui.voteOnPoll
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.poll.tags.OptionTag
import com.vitorpamplona.quartz.nip88Polls.poll.tags.PollType
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A single poll option paired with its (stable, per-event) tally flow so each option
 * row is its own leaf collector — no combining all options into one flow (which would
 * cause a recomposition storm).
 */
private class PollOptionFlow(
    val option: OptionTag,
    val results: Flow<TallyResults>,
    val currentResults: () -> TallyResults,
)

/**
 * Desktop NIP-88 poll card. Reuses [NoteCard] for the author/description/media header
 * (via [bottomContent] slot for the interactive options) and renders the tally itself.
 *
 * Hide-until-voted (decision #2): controls are shown unless the viewer is the author,
 * has voted, the poll ended, or opted into "View results". Re-vote allowed (decision #6):
 * results view offers "Change vote".
 */
@Composable
fun DesktopPollCard(
    note: Note,
    event: PollEvent,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
    account: AccountState.LoggedIn?,
    myPubKeyHex: String?,
    onNavigateToThread: (String) -> Unit = {},
    onNavigateToProfile: (String) -> Unit = {},
    onHashtagClick: ((String) -> Unit)? = null,
) {
    val options = remember(event) { event.options() }
    if (options.isEmpty()) return

    val pollState = remember(note) { note.pollState() }
    val forKey = myPubKeyHex ?: ""

    // Load this poll's responses from the poll's OWN declared relays (NIP-88 `relay` tags)
    // unioned with the viewer's connected relays. Votes are published to the poll's relays,
    // which the viewer usually isn't subscribed to — so the feed/thread/search interaction
    // fetches (which only query the viewer's relays) miss them and the tally shows just the
    // viewer's own vote. Querying the poll's declared relays makes the full tally load in
    // any context that renders this card.
    val connectedRelays by relayManager.connectedRelays.collectAsState()
    val responseRelays =
        remember(event, connectedRelays) {
            (event.relays() + connectedRelays).toSet()
        }
    rememberSubscription(responseRelays, relayManager = relayManager) {
        if (responseRelays.isEmpty()) return@rememberSubscription null
        SubscriptionConfig(
            subId = generateSubId("poll-resp-${event.id.take(8)}"),
            filters =
                listOf(
                    Filter(
                        kinds = listOf(PollResponseEvent.KIND),
                        tags = mapOf("e" to listOf(event.id)),
                    ),
                ),
            relays = responseRelays,
            onEvent = { ev, _, relay, _ -> localCache.consume(ev, relay, wasVerified = false) },
        )
    }

    // One stable flow per option, built once per event (Delta #6).
    val optionFlows =
        remember(event) {
            options.map { option ->
                PollOptionFlow(
                    option = option,
                    results = pollState.tallyFlow(option.code, forKey, localCache.followedUsers),
                    currentResults = { pollState.currentTally(option.code, forKey, localCache.followedUsers.value) },
                )
            }
        }

    val pollType = remember(event) { event.pollType() }
    val hasEnded = remember(event) { event.hasEnded() }
    val isMyPoll = myPubKeyHex != null && event.pubKey == myPubKeyHex
    // A read-only (watch-only) account can't sign — show results instead of dead controls.
    val canVote = account != null && !account.isReadOnly

    // Seed the voted-gate synchronously to avoid a first-frame flash (Delta #7).
    val myUser = remember(note, myPubKeyHex) { myPubKeyHex?.let { localCache.getOrCreateUser(it) } }
    val hasVotedSeed = remember(pollState, myUser) { myUser?.let { pollState.hasPubKeyVoted(it) } ?: false }
    val hasVoted by
        remember(pollState, myUser) {
            myUser?.let { pollState.hasPubKeyVotedFlow(it) } ?: flowOf(false)
        }.collectAsState(hasVotedSeed)

    // Local UI state keyed by note id so LazyColumn slot recycling can't leak one
    // poll's selection into another (Delta #9). `viewingResults` = opted into results
    // before voting; `revoting` = tapped "Change vote" to reopen controls after voting.
    var viewingResults by remember(note.idHex) { mutableStateOf(false) }
    var revoting by remember(note.idHex) { mutableStateOf(false) }

    // Tap a result row to see who voted for that option.
    var voterPopup by remember(note.idHex) { mutableStateOf<Pair<String, List<User>>?>(null) }

    // Total votes + deadline label for the footer.
    val tallyState by pollState.responses.collectAsState()
    // Distinct voters (not total selections) so a multi-choice voter counts once.
    val totalVotes = tallyState.votes.size
    // Pre-seed a multi-choice re-vote with the viewer's existing selection.
    val myCurrentVote =
        remember(tallyState, myUser) {
            myUser?.let { tallyState.votes[it]?.responses()?.toSet() } ?: emptySet()
        }
    val endsAtSec = remember(event) { event.endsAt() }
    val deadlineLabel =
        remember(endsAtSec, hasEnded) {
            endsAtSec?.let { (if (hasEnded) "Ended " else "Ends ") + formatPollTimestamp(it) }
        }

    val displayData = remember(event) { event.toNoteDisplayData(localCache) }

    NoteCard(
        note = displayData,
        modifier = Modifier.fillMaxWidth(),
        localCache = localCache,
        onClick = { onNavigateToThread(event.id) },
        onAuthorClick = onNavigateToProfile,
        onMentionClick = onNavigateToProfile,
        onHashtagClick = onHashtagClick,
        onNavigateToThread = onNavigateToThread,
        bottomContent = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Results gate (decision #2): author / ended / already-voted / opted-in
                // see results — unless the viewer explicitly reopened controls to re-vote.
                val showResults = !revoting && (isMyPoll || hasVoted || hasEnded || viewingResults || !canVote)
                if (showResults) {
                    optionFlows.forEach { of ->
                        key(of.option.code) {
                            PollResultRow(of, forKey) { label, voters ->
                                voterPopup = label to voters
                            }
                        }
                    }
                    if (!hasEnded && !isMyPoll && canVote && hasVoted) {
                        Text(
                            text = "Change vote",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { revoting = true }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    when (pollType) {
                        PollType.SINGLE_CHOICE ->
                            SingleChoiceOptions(options, account) { code ->
                                revoting = false
                                castVote(event, setOf(code), account, relayManager, localCache)
                            }
                        PollType.MULTI_CHOICE ->
                            MultiChoiceOptions(note, options, account, myCurrentVote) { codes ->
                                revoting = false
                                castVote(event, codes, account, relayManager, localCache)
                            }
                    }
                    Text(
                        text = if (revoting) "Back to results" else "View results",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    if (revoting) revoting = false else viewingResults = true
                                }.padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }

                if (totalVotes > 0 || deadlineLabel != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "$totalVotes ${if (totalVotes == 1) "vote" else "votes"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        deadlineLabel?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    )

    voterPopup?.let { (label, voters) ->
        VoterListPopup(
            optionLabel = label,
            voters = voters,
            forKey = forKey,
            onDismiss = { voterPopup = null },
            onNavigateToProfile = onNavigateToProfile,
        )
    }
}

private fun castVote(
    event: PollEvent,
    codes: Set<String>,
    account: AccountState.LoggedIn?,
    relayManager: DesktopRelayConnectionManager,
    localCache: DesktopLocalCache,
) {
    if (account == null || account.isReadOnly || codes.isEmpty()) return
    // Launch on the cache-scoped scope, NOT the card's scope, so the consume→broadcast
    // pair can't be half-cancelled when the card leaves composition (Delta #2).
    localCache.appScope.launch {
        voteOnPoll(event, codes, account, relayManager, localCache)
    }
}

@Composable
private fun SingleChoiceOptions(
    options: List<OptionTag>,
    account: AccountState.LoggedIn?,
    onRespond: (String) -> Unit,
) {
    options.forEach { option ->
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .then(
                        if (account != null) {
                            Modifier.clickable { onRespond(option.code) }
                        } else {
                            Modifier
                        },
                    ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    symbol = MaterialSymbols.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(text = option.label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun MultiChoiceOptions(
    note: Note,
    options: List<OptionTag>,
    account: AccountState.LoggedIn?,
    initialSelection: Set<String>,
    onRespond: (Set<String>) -> Unit,
) {
    // Keyed by note id so recycling doesn't leak selection across polls (Delta #9);
    // seeded with the viewer's existing vote so a re-vote starts from prior choices.
    var selected by remember(note.idHex) { mutableStateOf(initialSelection) }

    options.forEach { option ->
        val isChecked = option.code in selected
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .clickable {
                        selected = if (isChecked) selected - option.code else selected + option.code
                    },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // No CheckBox glyph in the subset font — a bordered box with a Check
                // glyph when selected (avoids a new codepoint / font regen).
                Box(
                    modifier =
                        Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .then(
                                if (isChecked) {
                                    Modifier.background(MaterialTheme.colorScheme.primary)
                                } else {
                                    Modifier.border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(4.dp),
                                    )
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isChecked) {
                        Icon(
                            symbol = MaterialSymbols.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Text(text = option.label, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Button(
            onClick = { onRespond(selected) },
            enabled = account != null && selected.isNotEmpty(),
        ) {
            Text("Submit")
        }
    }
}

@Composable
private fun PollResultRow(
    of: PollOptionFlow,
    forKey: String,
    onShowVoters: (String, List<User>) -> Unit,
) {
    val tally by of.results.collectAsState(of.currentResults())

    // First-frame bar guard: snap on first emission, animate afterwards (Delta #10).
    val animated = remember { Animatable(tally.percent) }
    LaunchedEffect(tally.percent) {
        animated.animateTo(tally.percent)
    }

    val isMyVote = forKey.isNotEmpty() && tally.users.any { it.pubkeyHex == forKey }
    val winning = tally.isWinning
    val barColor = if (winning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    // Border marks YOUR choice (primary); the winner is conveyed by the bar fill color.
    val borderColor =
        when {
            isMyVote -> MaterialTheme.colorScheme.primary
            winning -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.outline
        }
    val borderWidth = if (isMyVote) 2.dp else 1.dp

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .clickable { onShowVoters(of.option.label, tally.users) },
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .alpha(0.32f)
                    .drawWithContent {
                        clipRect(right = size.width * animated.value) {
                            drawRect(barColor)
                        }
                        drawContent()
                    },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isMyVote) {
                    Icon(
                        symbol = MaterialSymbols.Check,
                        contentDescription = "Your vote",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = of.option.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isMyVote) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            Spacer(Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                VoterGallery(tally.users, forKey)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${(tally.percent * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun VoterGallery(
    users: List<User>,
    forKey: String,
) {
    if (users.isEmpty()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((-10).dp),
    ) {
        users.take(4).forEachIndexed { index, user ->
            key(user.pubkeyHex) {
                val isMe = forKey.isNotEmpty() && user.pubkeyHex == forKey
                UserAvatar(
                    userHex = user.pubkeyHex,
                    pictureUrl = user.profilePicture(),
                    size = 24.dp,
                    // Earlier avatars draw on top so the leftmost (you, sorted first) is
                    // front-most instead of buried under the next ones; ring your own.
                    modifier =
                        Modifier
                            .zIndex((users.size - index).toFloat())
                            .then(
                                if (isMe) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                } else {
                                    Modifier
                                },
                            ),
                )
            }
        }
        if (users.size > 4) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Text(
                    text = "+${users.size - 4}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun VoterListPopup(
    optionLabel: String,
    voters: List<User>,
    forKey: String,
    onDismiss: () -> Unit,
    onNavigateToProfile: (String) -> Unit,
) {
    Popup(
        alignment = Alignment.Center,
        offset = IntOffset(0, 0),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        ElevatedCard(modifier = Modifier.widthIn(max = 320.dp)) {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = 360.dp)
                        .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${voters.size} ${if (voters.size == 1) "vote" else "votes"} · $optionLabel",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                HorizontalDivider()
                if (voters.isEmpty()) {
                    Text(
                        text = "No votes yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    voters.forEach { user ->
                        key(user.pubkeyHex) {
                            val isMe = forKey.isNotEmpty() && user.pubkeyHex == forKey
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            onNavigateToProfile(user.pubkeyHex)
                                            onDismiss()
                                        }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                UserAvatar(
                                    userHex = user.pubkeyHex,
                                    pictureUrl = user.profilePicture(),
                                    size = 28.dp,
                                )
                                Text(
                                    text = user.toBestDisplayName() + if (isMe) " (you)" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val POLL_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")

private fun formatPollTimestamp(epochSeconds: Long): String =
    Instant
        .ofEpochSecond(epochSeconds)
        .atZone(ZoneId.systemDefault())
        .format(POLL_TIME_FORMAT)
