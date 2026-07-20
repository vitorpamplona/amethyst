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
package com.vitorpamplona.amethyst.connectedApps.consent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.favorites.FavoriteApp
import com.vitorpamplona.amethyst.commons.favorites.FavoriteAppIcon
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.call.CallSessionBridge
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.note.NoteCompose
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.utils.TimeUtils

class SignerConsentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AmethystTheme {
                // The signer services requests concurrently, so more than one may await consent. We never
                // mix accounts in one sheet: render only the requests for the OLDEST-pending account as a
                // group. When that account's group clears, the next account's requests render (a fresh
                // per-account sheet). One request → the rich dialog; several → a batched list. When the
                // whole queue empties, close.
                val pending by SignerConsentCoordinator.pending.collectAsStateWithLifecycle()
                val group =
                    run {
                        val account = pending.firstOrNull()?.info?.accountPubKey
                        pending.filter { it.info.accountPubKey == account }
                    }
                LaunchedEffect(pending.isEmpty()) { if (pending.isEmpty()) finish() }
                when {
                    group.isEmpty() -> Unit
                    group.size == 1 -> {
                        val p = group.first()
                        SignerConsentDialog(
                            info = p.info,
                            onGrant = { SignerConsentCoordinator.complete(p.token, it) },
                            onDismiss = { SignerConsentCoordinator.complete(p.token, SignerOpGrant.DenyOnce) },
                        )
                    }
                    else ->
                        BatchedConsentDialog(
                            pending = group,
                            onResolve = { tokens, grant -> SignerConsentCoordinator.completeAll(tokens, grant) },
                            // Dismissing denies only THIS account's group; other accounts' requests stay
                            // pending and render next as their own sheet.
                            onDismiss = { SignerConsentCoordinator.completeAll(group.map { it.token }, SignerOpGrant.DenyOnce) },
                        )
                }
            }
        }
    }
    // Dismissal is failed-closed at the source: each dialog's onDismissRequest (back / tap-outside)
    // denies its own request(s). We deliberately do NOT deny-all in onDestroy — a request arriving as
    // this Activity finishes is owned by a freshly-launched instance, and denying it here would race
    // that instance and reject a legitimate request. A process kill falls back to the bridge's 120s
    // timeout, which also fails closed.
}

@Composable
private fun SignerConsentDialog(
    info: SignerConsentInfo,
    onGrant: (SignerOpGrant) -> Unit,
    onDismiss: () -> Unit,
) {
    var showMoreOptions by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(max = maxHeight),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .verticalScroll(scrollState)
                        .padding(vertical = 24.dp),
            ) {
                // Centered header: icon + title + description
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val isBrowser = info.coordinate.startsWith("browser:")
                    FavoriteAppIcon(
                        app =
                            if (isBrowser) {
                                FavoriteApp.WebApp(info.coordinate.substringAfter(':'), info.appletTitle, 0L, info.iconUrl)
                            } else {
                                FavoriteApp.NostrApp(info.coordinate, info.appletTitle, 0L, info.iconUrl)
                            },
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(56.dp),
                    )
                    Text(
                        info.appletTitle,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.napplet_consent_wants_to, info.operationSummary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    // Show WHICH account would sign/encrypt/decrypt (avatar + name), not the coordinate hex.
                    if (info.accountName != null) {
                        ConnectedAccountRow(info.accountName, info.accountPicture, info.accountPubKey)
                    }
                    // For a decrypt request, WHOSE conversation is being read is the decision. Show
                    // that person as an avatar + name, never as nothing.
                    if (info.counterpartyName != null) {
                        Text(
                            stringResource(R.string.nip46_signer_messages_with),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ConnectedAccountRow(info.counterpartyName, info.counterpartyPicture, info.counterpartyPubKey)
                    }
                }

                Spacer(Modifier.height(12.dp))
                Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                    SignerConsentPreview(info)
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Primary: the NARROWEST "remember" available. For decrypt that is "always allow for
                // Alice" — one broad decrypt grant would otherwise hand over every conversation
                // forever, and scoping the op itself would mean a prompt per conversation.
                val narrowOp = info.narrowOp
                if (narrowOp != null && info.narrowOpLabel != null) {
                    Button(
                        onClick = { onGrant(SignerOpGrant.AllowForOp(narrowOp)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        Text(info.narrowOpLabel)
                    }
                    // The broad grant stays available, but demoted below the scoped one.
                    OutlinedButton(
                        onClick = { onGrant(SignerOpGrant.AllowForOp(info.op)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        Text(stringResource(R.string.napplet_consent_allow_always))
                    }
                } else {
                    Button(
                        onClick = { onGrant(SignerOpGrant.AllowForOp(info.op)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        Text(stringResource(R.string.napplet_consent_allow_always))
                    }
                }

                // Secondary: allow just once
                OutlinedButton(
                    onClick = { onGrant(SignerOpGrant.AllowOnce) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) {
                    Text(stringResource(R.string.napplet_signer_allow_once))
                }

                // "More options" toggle: session and time-bound grants
                TextButton(
                    onClick = { showMoreOptions = !showMoreOptions },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            if (showMoreOptions) {
                                stringResource(R.string.napplet_consent_fewer_options)
                            } else {
                                stringResource(R.string.napplet_consent_more_options)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Icon(
                            if (showMoreOptions) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                if (showMoreOptions) {
                    OutlinedButton(
                        onClick = { onGrant(SignerOpGrant.AllowForSession(info.op)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        Text(stringResource(R.string.napplet_signer_allow_session))
                    }
                    OutlinedButton(
                        onClick = { onGrant(SignerOpGrant.AllowUntil(info.op, TimeUtils.now() + 86_400L)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        Text(stringResource(R.string.napplet_signer_allow_24h))
                    }
                    OutlinedButton(
                        onClick = { onGrant(SignerOpGrant.AllowUntil(info.op, TimeUtils.now() + 30L * 86_400L)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        Text(stringResource(R.string.napplet_signer_allow_30d))
                    }
                    OutlinedButton(
                        onClick = { onGrant(SignerOpGrant.AllowAll) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    ) {
                        Text(stringResource(R.string.napplet_signer_allow_all))
                    }
                }

                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { onGrant(SignerOpGrant.DenyOnce) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.napplet_signer_deny_once))
                }
                OutlinedButton(
                    onClick = { onGrant(SignerOpGrant.DenyForOp(info.op)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.napplet_signer_deny_op, info.operationSummary))
                }
            }
        }
    }
}

/**
 * The "what you're acting on" block: the unsigned event rendered as a real NoteCompose (what it will
 * look like once signed) with a JSON toggle for sign/publish, or the raw content / decrypted plaintext
 * for encrypt/decrypt. Shared by the single-request dialog and each expanded batch row so a user can
 * always inspect exactly what they are signing/encrypting/decrypting. Best-effort: if the main Activity
 * is gone (only the foreground signer service alive) the NoteCompose is skipped and the JSON stands in.
 */
@Composable
private fun SignerConsentPreview(info: SignerConsentInfo) {
    var showRawData by remember(info) { mutableStateOf(false) }
    val accountViewModel = remember { CallSessionBridge.accountViewModel }
    val previewNav = remember { EmptyNav() }
    val previewNote =
        remember(info, accountViewModel) {
            val template = info.previewTemplate
            val author = info.accountPubKey ?: accountViewModel?.account?.signer?.pubKey
            if (template != null && author != null && accountViewModel != null) {
                runCatching {
                    val unsigned = RumorAssembler.assembleRumor<Event>(author, template)
                    accountViewModel.createTempDraftNote(unsigned, LocalCache.getOrCreateUser(author))
                }.getOrNull()
            } else {
                null
            }
        }

    val hasContent = previewNote != null || info.contentPreview.isNotBlank() || info.rawData.isNotBlank()
    if (!hasContent) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (previewNote != null && accountViewModel != null) {
                NoteCompose(
                    baseNote = previewNote,
                    isQuotedNote = true,
                    quotesLeft = 0,
                    accountViewModel = accountViewModel,
                    nav = previewNav,
                )
            } else if (info.contentPreview.isNotBlank()) {
                Text("“${info.contentPreview}”", style = MaterialTheme.typography.bodySmall)
            }
            if (info.rawData.isNotBlank()) {
                if (showRawData) {
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        SelectionContainer {
                            Text(
                                info.rawData,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                softWrap = false,
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { showRawData = !showRawData },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                ) {
                    Text(
                        if (showRawData) {
                            stringResource(R.string.napplet_consent_hide_event)
                        } else {
                            stringResource(R.string.napplet_consent_show_event)
                        },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

/**
 * Shown when more than one request is awaiting consent at once (the signer services requests
 * concurrently). Lists each with a checkbox — all selected by default — and resolves the selected
 * ones together as Allow or Deny. "Remember" makes an Allow persist per-op ([SignerOpGrant.AllowForOp]);
 * off is a one-time [SignerOpGrant.AllowOnce]. Requests left unselected stay pending and re-render
 * (as this list, or the single-request dialog once one remains).
 */
@Composable
private fun BatchedConsentDialog(
    pending: List<PendingConsent>,
    onResolve: (tokens: List<String>, grant: SignerOpGrant) -> Unit,
    onDismiss: () -> Unit,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f
    val tokens = pending.map { it.token }.toSet()
    // Seed all-selected ONCE for the initial batch the user opened. The signer services requests
    // concurrently, so `tokens` can change under an open sheet; reconcile incrementally instead of
    // re-seeding — drop resolved tokens but KEEP the user's deselections, and never auto-select a
    // newly-arrived request. Otherwise a request landing (or resolving) mid-decision would silently
    // re-check everything, and an "Allow selected" tap would grant ops the user deselected or never saw.
    var selected by remember { mutableStateOf(tokens) }
    LaunchedEffect(tokens) { selected = selected intersect tokens }
    var rememberChoice by remember { mutableStateOf(false) }
    // Tokens whose full preview (rendered event + JSON, or encrypt/decrypt plaintext) is expanded.
    var expanded by remember { mutableStateOf(emptySet<String>()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(max = maxHeight),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(vertical = 20.dp)) {
                // The sheet is single-account (grouped upstream), so the account is a header, not a
                // per-row label. It says WHO every request in this sheet would act as.
                val account = pending.first().info
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    account.accountName?.let { name ->
                        RobohashFallbackAsyncImage(
                            robot = account.accountPubKey ?: name,
                            model = account.accountPicture,
                            contentDescription = null,
                            modifier = Modifier.size(34.dp).clip(CircleShape),
                            loadProfilePicture = true,
                            loadRobohash = true,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            pluralStringResource(R.plurals.nip46_signer_batch_title, pending.size, pending.size),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        account.accountName?.let { name ->
                            Text(
                                stringResource(R.string.nip46_signer_batch_signing_as, name),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                TextButton(
                    onClick = {
                        selected = if (selected.size == pending.size) emptySet() else pending.map { it.token }.toSet()
                    },
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
                ) {
                    Text(
                        stringResource(
                            if (selected.size == pending.size) R.string.nip46_signer_batch_select_none else R.string.nip46_signer_batch_select_all,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Column(
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                ) {
                    pending.forEach { p ->
                        val isExpanded = p.token in expanded
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            expanded = if (isExpanded) expanded - p.token else expanded + p.token
                                        }.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                // Checkbox handles its own tap (select); tapping elsewhere on the row expands.
                                Checkbox(
                                    checked = p.token in selected,
                                    onCheckedChange = { on -> selected = if (on) selected + p.token else selected - p.token },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${p.info.appletTitle} · ${p.info.operationSummary}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                    )
                                    if (p.info.contentPreview.isNotBlank()) {
                                        Text(
                                            p.info.contentPreview,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                Icon(
                                    if (isExpanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            if (isExpanded) {
                                Box(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                                    SignerConsentPreview(p.info)
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Switch(checked = rememberChoice, onCheckedChange = { rememberChoice = it })
                    Text(
                        stringResource(R.string.nip46_signer_batch_remember),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        val tokens = pending.filter { it.token in selected }
                        // Per-op remember uses each request's own op; one-time is a single AllowOnce.
                        if (rememberChoice) {
                            tokens.forEach { onResolve(listOf(it.token), SignerOpGrant.AllowForOp(it.info.op)) }
                        } else {
                            onResolve(tokens.map { it.token }, SignerOpGrant.AllowOnce)
                        }
                    },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                ) {
                    Text(stringResource(R.string.nip46_signer_batch_allow, selected.size))
                }
                OutlinedButton(
                    onClick = { onResolve(pending.filter { it.token in selected }.map { it.token }, SignerOpGrant.DenyOnce) },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.nip46_signer_batch_deny, selected.size))
                }
            }
        }
    }
}
