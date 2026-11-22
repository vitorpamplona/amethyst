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
package com.vitorpamplona.amethyst.commons.ui.components.accountLifecycle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.AccountLifecycleEvaluation
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.AccountLifecycleState
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.AccountLifecycleStatus
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.AccountLifecycleSubjectState
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.LifecycleMetadataProvider
import com.vitorpamplona.amethyst.commons.model.accountLifecycle.SuccessorConfirmation
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.routeForUser
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.close
import com.vitorpamplona.amethyst.commons.resources.lifecycle_backlink_current
import com.vitorpamplona.amethyst.commons.resources.lifecycle_backlink_historical
import com.vitorpamplona.amethyst.commons.resources.lifecycle_backlink_missing
import com.vitorpamplona.amethyst.commons.resources.lifecycle_compromised_note
import com.vitorpamplona.amethyst.commons.resources.lifecycle_compromised_profile
import com.vitorpamplona.amethyst.commons.resources.lifecycle_confirmation_absent
import com.vitorpamplona.amethyst.commons.resources.lifecycle_confirmation_confirmed
import com.vitorpamplona.amethyst.commons.resources.lifecycle_confirmation_conflicting
import com.vitorpamplona.amethyst.commons.resources.lifecycle_confirmation_invalid
import com.vitorpamplona.amethyst.commons.resources.lifecycle_confirmation_stale
import com.vitorpamplona.amethyst.commons.resources.lifecycle_confirmation_unconfirmed
import com.vitorpamplona.amethyst.commons.resources.lifecycle_confirmation_withdrawn
import com.vitorpamplona.amethyst.commons.resources.lifecycle_details
import com.vitorpamplona.amethyst.commons.resources.lifecycle_error_status
import com.vitorpamplona.amethyst.commons.resources.lifecycle_invalid
import com.vitorpamplona.amethyst.commons.resources.lifecycle_localpart
import com.vitorpamplona.amethyst.commons.resources.lifecycle_no_auto_actions
import com.vitorpamplona.amethyst.commons.resources.lifecycle_old_account_actions
import com.vitorpamplona.amethyst.commons.resources.lifecycle_open_predecessor
import com.vitorpamplona.amethyst.commons.resources.lifecycle_open_successor
import com.vitorpamplona.amethyst.commons.resources.lifecycle_predecessor_confirmation
import com.vitorpamplona.amethyst.commons.resources.lifecycle_predecessor_profile
import com.vitorpamplona.amethyst.commons.resources.lifecycle_provenance_backend
import com.vitorpamplona.amethyst.commons.resources.lifecycle_provenance_expiry
import com.vitorpamplona.amethyst.commons.resources.lifecycle_provenance_height
import com.vitorpamplona.amethyst.commons.resources.lifecycle_provenance_txid
import com.vitorpamplona.amethyst.commons.resources.lifecycle_provisional
import com.vitorpamplona.amethyst.commons.resources.lifecycle_recheck
import com.vitorpamplona.amethyst.commons.resources.lifecycle_retired_note
import com.vitorpamplona.amethyst.commons.resources.lifecycle_retired_profile
import com.vitorpamplona.amethyst.commons.resources.lifecycle_since_unverified
import com.vitorpamplona.amethyst.commons.resources.lifecycle_source_name
import com.vitorpamplona.amethyst.commons.resources.lifecycle_stale
import com.vitorpamplona.amethyst.commons.resources.lifecycle_successor_actions
import com.vitorpamplona.amethyst.commons.resources.lifecycle_unavailable
import com.vitorpamplona.amethyst.commons.resources.lifecycle_unsupported
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DateTimeStyle
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.note.formatDateTime
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.profile.header.DisplayFollowUnfollowButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.highlightMarker
import com.vitorpamplona.amethyst.commons.ui.theme.highlightMarkerText
import com.vitorpamplona.amethyst.commons.ui.theme.isLight
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/**
 * A Namecoin-owner attestation, never an active/safe badge or an automatic migration control.
 * Resolution is shared by the app service; rows only observe the latest signed cached metadata.
 */
@Composable
fun AccountLifecycleBanner(
    user: User,
    accountViewModel: AccountViewModel,
    nav: INav,
    compact: Boolean = false,
    onEvaluation: (AccountLifecycleEvaluation?) -> Unit = {},
) {
    val lifecycle = accountViewModel.host.namecoinLifecycle?.accountLifecycle
    if (lifecycle == null) {
        SideEffect { onEvaluation(null) }
        return
    }
    val userInfo by observeUserInfo(user, accountViewModel)
    val currentEvent = userInfo?.event
    val currentIdentifier = userInfo?.info?.nip05
    val viewerNamespace = accountViewModel.account.userProfile().pubkeyHex
    val invalidationVersion by lifecycle.invalidationVersion.collectAsState()
    var refreshVersion by remember(user.pubkeyHex, viewerNamespace, lifecycle) { mutableIntStateOf(0) }
    val successorEventIds = remember(user.pubkeyHex, lifecycle) { mutableStateMapOf<String, String>() }
    var subjectState by remember(user.pubkeyHex, lifecycle) { mutableStateOf(AccountLifecycleSubjectState()) }

    LaunchedEffect(user.pubkeyHex, viewerNamespace, lifecycle) {
        val sharedState = lifecycle.subjectState(viewerNamespace, user.pubkeyHex)
        sharedState.collect { subjectState = it }
    }

    val metadataProvider =
        remember {
            LifecycleMetadataProvider { pubkey ->
                listOfNotNull(
                    LocalCache
                        .getUserIfExists(pubkey)
                        ?.metadataOrNull()
                        ?.flow
                        ?.value
                        ?.event,
                )
            }
        }

    val successorPubkeys = subjectState.evaluations.mapNotNull { it.result.successorPubkey }.distinct()
    successorPubkeys.forEach { successor ->
        LoadUser(successor) { successorUser ->
            if (successorUser != null) {
                val successorInfo by observeUserInfo(successorUser, accountViewModel)
                val successorEventId = successorInfo?.event?.id
                LaunchedEffect(successor, successorEventId) {
                    if (successorEventId != null) successorEventIds[successor] = successorEventId
                }
            }
        }
    }

    val successorEventIdSnapshot = successorEventIds.toMap()
    LaunchedEffect(
        user.pubkeyHex,
        viewerNamespace,
        currentIdentifier,
        currentEvent?.id,
        successorEventIdSnapshot,
        invalidationVersion,
        lifecycle,
    ) {
        lifecycle.resolveSubject(
            viewerNamespace = viewerNamespace,
            subjectPubkey = user.pubkeyHex,
            currentIdentifier = currentIdentifier,
            oldEvents = listOfNotNull(currentEvent),
            metadataProvider = metadataProvider,
            successorEventIds = successorEventIdSnapshot,
        )
    }

    LaunchedEffect(user.pubkeyHex, viewerNamespace, refreshVersion, lifecycle) {
        if (refreshVersion > 0) {
            // Only this explicit, one-shot recheck forces transport. Successor metadata identity
            // joins shared validation without repeatedly replacing raw lookups on later effects.
            lifecycle.resolveSubject(
                viewerNamespace = viewerNamespace,
                subjectPubkey = user.pubkeyHex,
                currentIdentifier = currentIdentifier,
                oldEvents = listOfNotNull(currentEvent),
                metadataProvider = metadataProvider,
                successorEventIds = successorEventIdSnapshot,
                forceRefresh = true,
            )
        }
    }

    if (!compact) {
        predecessorForSubject(subjectState.evaluations, user.pubkeyHex)?.let { predecessor ->
            PredecessorLink(predecessor, nav)
        }
    }
    val evaluation =
        primaryAccountLifecycleEvaluationForSubject(
            subjectState.evaluations,
            user.pubkeyHex,
            includeErrors = !compact,
        )
    SideEffect { onEvaluation(evaluation) }
    if (evaluation == null) return
    var showDetails by remember(evaluation) { mutableStateOf(false) }
    val result = evaluation.result
    val compromised = result.state == AccountLifecycleState.COMPROMISED
    val containerColor = if (compromised) MaterialTheme.colorScheme.highlightMarker else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (compromised) MaterialTheme.colorScheme.highlightMarkerText else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = if (compact) 3.dp else 6.dp),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text =
                    stringRes(
                        when {
                            compromised && compact -> Res.string.lifecycle_compromised_note
                            compromised -> Res.string.lifecycle_compromised_profile
                            compact -> Res.string.lifecycle_retired_note
                            result.state != null -> Res.string.lifecycle_retired_profile
                            result.status == AccountLifecycleStatus.UNSUPPORTED_TRAVERSAL -> Res.string.lifecycle_unsupported
                            result.status == AccountLifecycleStatus.INVALID ||
                                result.status == AccountLifecycleStatus.UNSUPPORTED_STATE -> Res.string.lifecycle_invalid
                            else -> Res.string.lifecycle_unavailable
                        },
                    ),
                fontWeight = if (compromised) FontWeight.Bold else FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (result.status == AccountLifecycleStatus.STALE) {
                Text(stringRes(Res.string.lifecycle_stale), style = MaterialTheme.typography.bodySmall)
            }
            if (result.status == AccountLifecycleStatus.PROVISIONAL) {
                Text(stringRes(Res.string.lifecycle_provisional), style = MaterialTheme.typography.bodySmall)
            }
            result.since?.let {
                Text(
                    stringRes(
                        Res.string.lifecycle_since_unverified,
                        formatDateTime(it * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.NONE),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (!compact && result.successorPubkey != null) {
                SuccessorConfirmationText(evaluation)
                SuccessorLink(evaluation, nav)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showDetails = true }) {
                    Text(stringRes(Res.string.lifecycle_details))
                }
                if (!compact) {
                    TextButton(onClick = { refreshVersion++ }, enabled = !subjectState.loading) {
                        Text(stringRes(Res.string.lifecycle_recheck))
                    }
                }
            }
            if (!compact && result.isAccountWarning) {
                Text(stringRes(Res.string.lifecycle_no_auto_actions), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(stringRes(Res.string.lifecycle_old_account_actions), style = MaterialTheme.typography.labelSmall)
                        DisplayFollowUnfollowButton(user, accountViewModel)
                    }
                    result.successorPubkey?.let { successor ->
                        LoadUser(successor) { successorUser ->
                            if (successorUser != null) {
                                Column(Modifier.weight(1f)) {
                                    Text(stringRes(Res.string.lifecycle_successor_actions), style = MaterialTheme.typography.labelSmall)
                                    DisplayFollowUnfollowButton(successorUser, accountViewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDetails) {
        AccountLifecycleDetailsDialog(
            evaluation = evaluation,
            nav = nav,
            loading = subjectState.loading,
            onRefresh = { refreshVersion++ },
            onDismiss = { showDetails = false },
        )
    }
}

@Composable
fun SuccessorLink(
    evaluation: AccountLifecycleEvaluation,
    nav: INav,
) {
    val successor = evaluation.result.successorPubkey ?: return
    val confirmed = evaluation.result.successorConfirmation == SuccessorConfirmation.RECIPROCAL_ALIAS_CONFIRMED
    TextButton(onClick = { nav.nav(routeForUser(successor)) }) {
        Text(
            text = stringRes(Res.string.lifecycle_open_successor),
            color =
                if (confirmed) {
                    if (MaterialTheme.colorScheme.isLight) Color(0xFF1B5E20) else Color(0xFF81C784)
                } else {
                    MaterialTheme.colorScheme.placeholderText
                },
        )
    }
}

/** A confirmed NEW alias exposes OLD navigation without transferring OLD's warning onto NEW. */
@Composable
fun PredecessorLink(
    evaluation: AccountLifecycleEvaluation,
    nav: INav,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(stringRes(Res.string.lifecycle_predecessor_profile), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringRes(
                    Res.string.lifecycle_predecessor_confirmation,
                    evaluation.result.confirmationAliases.joinToString(),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            LoadUser(evaluation.subjectPubkey) { predecessor ->
                if (predecessor != null) {
                    TextButton(onClick = { nav.nav(routeForUser(predecessor.pubkeyHex)) }) {
                        Text(stringRes(Res.string.lifecycle_open_predecessor))
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountLifecycleDetailsDialog(
    evaluation: AccountLifecycleEvaluation,
    nav: INav,
    loading: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val result = evaluation.result
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.lifecycle_details)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(stringRes(Res.string.lifecycle_source_name, evaluation.context.name))
                Text(stringRes(Res.string.lifecycle_localpart, evaluation.context.localPart ?: "*"))
                Text(stringRes(Res.string.lifecycle_error_status, result.status.name.lowercase()))
                Text(
                    stringRes(
                        when {
                            evaluation.backlinks.any { it.current } -> Res.string.lifecycle_backlink_current
                            evaluation.backlinks.isNotEmpty() -> Res.string.lifecycle_backlink_historical
                            else -> Res.string.lifecycle_backlink_missing
                        },
                    ),
                )
                result.since?.let {
                    Text(
                        stringRes(
                            Res.string.lifecycle_since_unverified,
                            formatDateTime(it * 1000, DateTimeStyle.MEDIUM, DateTimeStyle.NONE),
                        ),
                    )
                }
                if (result.successorPubkey != null || result.successorConfirmation != SuccessorConfirmation.NOT_APPLICABLE) {
                    SuccessorConfirmationText(evaluation)
                    SuccessorLink(evaluation, nav)
                }
                result.provenance?.let { provenance ->
                    provenance.configuredBackend?.let { Text(stringRes(Res.string.lifecycle_provenance_backend, it)) }
                    provenance.txid?.let { Text(stringRes(Res.string.lifecycle_provenance_txid, it)) }
                    provenance.height?.let { Text(stringRes(Res.string.lifecycle_provenance_height, it)) }
                    provenance.expiresIn?.let { Text(stringRes(Res.string.lifecycle_provenance_expiry, it)) }
                }
                if (result.diagnostics.isNotEmpty()) {
                    Text(result.diagnostics.joinToString(), style = MaterialTheme.typography.bodySmall)
                }
                if (result.status == AccountLifecycleStatus.STALE) {
                    Text(stringRes(Res.string.lifecycle_stale))
                }
                Text(stringRes(Res.string.lifecycle_no_auto_actions), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(Res.string.close)) }
        },
        dismissButton = {
            TextButton(onClick = onRefresh, enabled = !loading) { Text(stringRes(Res.string.lifecycle_recheck)) }
        },
    )
}

@Composable
private fun SuccessorConfirmationText(evaluation: AccountLifecycleEvaluation) {
    val result = evaluation.result
    Text(
        stringRes(
            when (result.successorConfirmation) {
                SuccessorConfirmation.RECIPROCAL_ALIAS_CONFIRMED -> Res.string.lifecycle_confirmation_confirmed
                SuccessorConfirmation.WITHDRAWN -> Res.string.lifecycle_confirmation_withdrawn
                SuccessorConfirmation.STALE -> Res.string.lifecycle_confirmation_stale
                SuccessorConfirmation.INVALID -> Res.string.lifecycle_confirmation_invalid
                SuccessorConfirmation.CONFLICTING -> Res.string.lifecycle_confirmation_conflicting
                SuccessorConfirmation.ABSENT -> Res.string.lifecycle_confirmation_absent
                else -> Res.string.lifecycle_confirmation_unconfirmed
            },
            result.confirmationAliases.joinToString().ifEmpty { result.successorPubkey.orEmpty() },
        ),
    )
}
