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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.frontpage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.frontpage.DataStoreFrontPageModelMemory
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageGate
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageModels
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageRun
import com.vitorpamplona.amethyst.commons.frontpage.FrontPageViewModel
import com.vitorpamplona.amethyst.commons.frontpage.MASTHEAD
import com.vitorpamplona.amethyst.commons.frontpage.PrintResult
import com.vitorpamplona.amethyst.commons.frontpage.PrintStep
import com.vitorpamplona.amethyst.commons.frontpage.Violation
import com.vitorpamplona.amethyst.commons.frontpage.WriterFailure
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.cancel
import com.vitorpamplona.amethyst.commons.resources.front_page_cannot_show
import com.vitorpamplona.amethyst.commons.resources.front_page_changes_title
import com.vitorpamplona.amethyst.commons.resources.front_page_connect
import com.vitorpamplona.amethyst.commons.resources.front_page_download
import com.vitorpamplona.amethyst.commons.resources.front_page_edition
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_credit
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_empty
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_http
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_model
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_rate
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_rejected
import com.vitorpamplona.amethyst.commons.resources.front_page_fail_unreachable
import com.vitorpamplona.amethyst.commons.resources.front_page_fixed_once
import com.vitorpamplona.amethyst.commons.resources.front_page_model
import com.vitorpamplona.amethyst.commons.resources.front_page_model_id
import com.vitorpamplona.amethyst.commons.resources.front_page_no_key
import com.vitorpamplona.amethyst.commons.resources.front_page_no_lens
import com.vitorpamplona.amethyst.commons.resources.front_page_not_ready
import com.vitorpamplona.amethyst.commons.resources.front_page_posts_read
import com.vitorpamplona.amethyst.commons.resources.front_page_print
import com.vitorpamplona.amethyst.commons.resources.front_page_print_again
import com.vitorpamplona.amethyst.commons.resources.front_page_quotes_verified
import com.vitorpamplona.amethyst.commons.resources.front_page_refused_body
import com.vitorpamplona.amethyst.commons.resources.front_page_refused_title
import com.vitorpamplona.amethyst.commons.resources.front_page_set_up
import com.vitorpamplona.amethyst.commons.resources.front_page_step_check
import com.vitorpamplona.amethyst.commons.resources.front_page_step_fix
import com.vitorpamplona.amethyst.commons.resources.front_page_step_probe
import com.vitorpamplona.amethyst.commons.resources.front_page_step_pull
import com.vitorpamplona.amethyst.commons.resources.front_page_step_write
import com.vitorpamplona.amethyst.commons.resources.front_page_tagline
import com.vitorpamplona.amethyst.commons.resources.front_page_title
import com.vitorpamplona.amethyst.commons.resources.front_page_tokens
import com.vitorpamplona.amethyst.commons.resources.front_page_unsupported
import com.vitorpamplona.amethyst.commons.ui.components.rememberFileBytesAccess
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppPlatform
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/** The house stylesheet's `--paper`: the newsprint the paper sits on, whatever the app's theme. */
private val Newsprint = Color(0xFFFAF8F3)

/**
 * Your front page: the Nostr Observer's daily paper, written by the reader's own AI key from the
 * last 24 hours of their web of trust, checked, and shown on the same sandboxed surface as an nSite.
 * Nothing is stored or published.
 */
@Composable
fun FrontPageScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val appStores = LocalAppServices.current.appStores
    val viewModel =
        rememberViewModel(key = "front-page:${accountViewModel.account.signer.pubKey}") {
            FrontPageViewModel(accountViewModel.account, accountViewModel.httpClientBuilder, DataStoreFrontPageModelMemory(appStores))
        }
    val gate by viewModel.gate.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val run by viewModel.run.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopBarWithBackButton(stringRes(Res.string.front_page_title), nav) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Hero(gate, models, run, onModel = viewModel::selectModel, onPrint = viewModel::print, onCancel = viewModel::cancel)
            when (gate) {
                FrontPageGate.NO_KEY -> GateCard(Res.string.front_page_no_key, Res.string.front_page_connect) { nav.nav(Route.AgentKeysSettings) }
                FrontPageGate.NO_LENS -> GateCard(Res.string.front_page_no_lens, Res.string.front_page_set_up) { nav.nav(Route.WebOfTrust) }
                FrontPageGate.UNSUPPORTED -> Notice(stringRes(Res.string.front_page_unsupported))
                FrontPageGate.READY -> RunBody(run)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Hero(
    gate: FrontPageGate,
    models: FrontPageModels,
    run: FrontPageRun,
    onModel: (String) -> Unit,
    onPrint: () -> Unit,
    onCancel: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val onHero = scheme.onPrimary
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(onHero.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(MaterialSymbols.AutoMirrored.Article, contentDescription = null, tint = onHero, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringRes(Res.string.front_page_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = onHero)
                    Text(stringRes(Res.string.front_page_tagline), style = MaterialTheme.typography.bodyMedium, color = onHero.copy(alpha = 0.85f))
                }
            }
            if (gate == FrontPageGate.READY) {
                Surface(shape = RoundedCornerShape(16.dp), color = scheme.surface) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModelPicker(models, onModel)
                        if (run is FrontPageRun.Running) {
                            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) {
                                Text(stringRes(Res.string.cancel))
                            }
                        } else {
                            Button(
                                onClick = onPrint,
                                enabled = models.selected != null,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Icon(MaterialSymbols.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp), filled = true)
                                Spacer(Modifier.width(8.dp))
                                Text(stringRes(if (run is FrontPageRun.Done) Res.string.front_page_print_again else Res.string.front_page_print), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPicker(
    models: FrontPageModels,
    onModel: (String) -> Unit,
) {
    if (models.loading) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(stringRes(Res.string.front_page_model), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    if (models.ids.isEmpty()) {
        // The provider lists no models: the reader names one.
        OutlinedTextField(
            value = models.selected.orEmpty(),
            onValueChange = onModel,
            singleLine = true,
            label = { Text(stringRes(Res.string.front_page_model_id)) },
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = models.selected.orEmpty(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringRes(Res.string.front_page_model)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.ids.forEach { id ->
                DropdownMenuItem(text = { Text(id) }, onClick = {
                    onModel(id)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun GateCard(
    message: StringResource,
    action: StringResource,
    onAction: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringRes(message), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Button(onClick = onAction, shape = RoundedCornerShape(14.dp)) { Text(stringRes(action)) }
        }
    }
}

@Composable
private fun Notice(
    text: String,
    error: Boolean = false,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

private fun grouped(n: Long): String =
    n
        .toString()
        .reversed()
        .chunked(3)
        .joinToString(",")
        .reversed()

@Composable
private fun RunBody(run: FrontPageRun) {
    when (run) {
        FrontPageRun.Idle -> {}
        is FrontPageRun.Running -> Progress(run.step)
        is FrontPageRun.Failed -> Notice(failureText(run), error = true)
        is FrontPageRun.Done ->
            when (val result = run.result) {
                is PrintResult.NotReady -> Notice(stringRes(Res.string.front_page_not_ready))
                is PrintResult.Refused -> Refused(result.violations)
                is PrintResult.Printed -> Printed(result)
            }
    }
}

@Composable
private fun Progress(step: PrintStep) {
    val text =
        when (step) {
            PrintStep.Probe -> stringRes(Res.string.front_page_step_probe)
            is PrintStep.Pull -> stringRes(Res.string.front_page_step_pull, step.desksDone, step.desksTotal)
            is PrintStep.Write ->
                stringRes(if (step.round == 1) Res.string.front_page_step_write else Res.string.front_page_step_fix, grouped(step.chars.toLong()))
            is PrintStep.Check -> stringRes(Res.string.front_page_step_check)
        }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(14.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun failureText(run: FrontPageRun.Failed): String =
    when (run.failure) {
        WriterFailure.REJECTED -> stringRes(Res.string.front_page_fail_rejected)
        WriterFailure.NO_CREDIT -> stringRes(Res.string.front_page_fail_credit)
        WriterFailure.RATE_LIMITED -> stringRes(Res.string.front_page_fail_rate)
        WriterFailure.UNKNOWN_MODEL -> stringRes(Res.string.front_page_fail_model)
        WriterFailure.EMPTY -> stringRes(Res.string.front_page_fail_empty)
        WriterFailure.HTTP -> stringRes(Res.string.front_page_fail_http, run.status ?: 0)
        WriterFailure.UNSUPPORTED -> stringRes(Res.string.front_page_unsupported)
        WriterFailure.UNREACHABLE -> stringRes(Res.string.front_page_fail_unreachable)
    }

@Composable
private fun Refused(violations: List<Violation>) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringRes(Res.string.front_page_refused_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(stringRes(Res.string.front_page_refused_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            violations.take(12).forEach {
                Text("${it.kind} · ${it.excerpt.take(90)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Printed(result: PrintResult.Printed) {
    val platform = LocalAppPlatform.current
    val files = rememberFileBytesAccess()
    val scope = rememberCoroutineScope()
    val code = result.corpus.code

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        AssistChip(onClick = {}, label = { Text(stringRes(Res.string.front_page_edition, code)) })
        AssistChip(
            onClick = {},
            label = { Text(pluralStringRes(Res.plurals.front_page_quotes_verified, result.quotes, result.quotes)) },
            leadingIcon = { Icon(MaterialSymbols.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
        val posts = result.corpus.events.size
        AssistChip(onClick = {}, label = { Text(pluralStringRes(Res.plurals.front_page_posts_read, posts, posts)) })
        result.usage?.total?.let { tokens ->
            AssistChip(onClick = {}, label = { Text(pluralStringRes(Res.plurals.front_page_tokens, tokens.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), grouped(tokens))) })
        }
        if (result.rounds == 2) AssistChip(onClick = {}, label = { Text(stringRes(Res.string.front_page_fixed_once)) })
        TextButton(onClick = { scope.launch { files.save("front-page-$code.html", "text/html") { result.html.encodeToByteArray() } } }) {
            Icon(MaterialSymbols.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringRes(Res.string.front_page_download))
        }
    }

    val removed = result.changes.filter { it.noteworthy }
    if (removed.isNotEmpty()) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringRes(Res.string.front_page_changes_title), style = MaterialTheme.typography.titleSmall)
                removed.take(10).forEach { Text(it.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }

    if (platform.showsPapersInline) {
        // The model writes the layout, some pages run edge to edge: a margin of newsprint keeps the
        // rounded corners off the folio. The paper itself is always light.
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Newsprint)
                .padding(8.dp),
        ) {
            platform.InlinePaperSurface(
                html = result.html,
                title = "$MASTHEAD · $code",
                instanceId = "front-page-$code",
                onStatus = {},
                modifier = Modifier.fillMaxWidth().heightIn(min = 640.dp).height(900.dp),
            )
        }
    } else {
        Notice(stringRes(Res.string.front_page_cannot_show))
    }
}
