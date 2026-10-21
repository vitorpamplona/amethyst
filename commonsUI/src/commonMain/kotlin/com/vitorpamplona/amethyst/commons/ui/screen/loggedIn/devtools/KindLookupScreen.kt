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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.devtools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.devtools.ContentFormat
import com.vitorpamplona.amethyst.commons.devtools.KindSamplerPhase
import com.vitorpamplona.amethyst.commons.devtools.KindSamplerState
import com.vitorpamplona.amethyst.commons.devtools.KindSamplerViewModel
import com.vitorpamplona.amethyst.commons.devtools.KindVariant
import com.vitorpamplona.amethyst.commons.devtools.KindVariantGrouping
import com.vitorpamplona.amethyst.commons.devtools.TagUsage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.devtools_format_empty
import com.vitorpamplona.amethyst.commons.resources.devtools_format_encrypted
import com.vitorpamplona.amethyst.commons.resources.devtools_format_json_array
import com.vitorpamplona.amethyst.commons.resources.devtools_format_json_object
import com.vitorpamplona.amethyst.commons.resources.devtools_format_text
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_authors
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_by_client
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_by_shape
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_content
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_events
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_from_clients
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_hide_example
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_hide_rare
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_lookup_button
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_lookup_field
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_lookup_title
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_more
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_nip
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_no_client
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_rare_tags
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_sample_status
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_shape_n
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_show_example
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_tag_values
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_usage_title
import com.vitorpamplona.amethyst.commons.resources.devtools_probe_none_found
import com.vitorpamplona.amethyst.commons.resources.devtools_probe_stop
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindDisplayName
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.toPrettyJson
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.kinds.KindNames
import org.jetbrains.compose.resources.StringResource

/**
 * Type a kind number and see every way it is written: recent events sampled from the connected
 * relays and the search relays, grouped by the client that wrote them or by shape (tag names and
 * content format), each with its tag usage and a real example. Named when the registry knows the kind.
 */
@Composable
fun KindLookupScreen(
    initialKind: Int?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    var grouping by rememberSaveable { mutableStateOf(KindVariantGrouping.CLIENT) }
    // One sampler for the screen: looking up another kind cancels the one in flight.
    val viewModel =
        rememberViewModel(key = "KindSampler") {
            KindSamplerViewModel(
                relays = {
                    accountViewModel.account.client
                        .connectedRelaysFlow()
                        .value + accountViewModel.account.searchOnlyRelays()
                },
                clientBuilder = accountViewModel::buildCrawlClient,
                canDial = accountViewModel.host.websocketBuilder::canConnect,
            )
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val running = state.phase == KindSamplerPhase.SAMPLING

    // Opened on a kind (from the inspector or the kind list): look it up right away, once.
    LaunchedEffect(Unit) {
        val opened = kind
        if (opened != null && viewModel.state.value.kind != opened) viewModel.start(opened)
    }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.devtools_kind_lookup_title), nav) },
    ) { padding ->
        val variants = if (grouping == KindVariantGrouping.CLIENT) state.byClient else state.byShape

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindField(
                        kind = kind,
                        running = running,
                        onLookUp = {
                            kind = it
                            viewModel.start(it)
                        },
                        onStop = viewModel::cancel,
                    )
                    kind?.let { KindTitle(it) }
                    if (kind != null) SampleStatus(state, running)
                }
            }

            if (state.events > 0) {
                item {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = grouping == KindVariantGrouping.CLIENT,
                            onClick = { grouping = KindVariantGrouping.CLIENT },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) { Text(stringRes(Res.string.devtools_kind_by_client) + " (${state.byClient.size})") }
                        SegmentedButton(
                            selected = grouping == KindVariantGrouping.SHAPE,
                            onClick = { grouping = KindVariantGrouping.SHAPE },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) { Text(stringRes(Res.string.devtools_kind_by_shape) + " (${state.byShape.size})") }
                    }
                }

                itemsIndexed(variants, key = { _, it -> grouping.name + it.key }) { index, variant ->
                    VariantCard(index, variant, state.events, grouping)
                }
            }
        }
    }
}

/**
 * The kind number with a single action at its end: search, or stop while the number shown is
 * being looked up. Searching the kind already shown runs it again.
 */
@Composable
private fun KindField(
    kind: Int?,
    running: Boolean,
    onLookUp: (Int) -> Unit,
    onStop: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(kind?.toString() ?: "") }
    val parsed = text.trim().toIntOrNull()?.takeIf { it in 0..65535 }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it.filter(Char::isDigit).take(5) },
        label = { Text(stringRes(Res.string.devtools_kind_lookup_field)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { parsed?.let(onLookUp) }),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        trailingIcon = {
            if (running && parsed == kind) {
                IconButton(onClick = onStop) {
                    Icon(MaterialSymbols.Stop, contentDescription = stringRes(Res.string.devtools_probe_stop))
                }
            } else {
                IconButton(onClick = { parsed?.let(onLookUp) }, enabled = parsed != null) {
                    Icon(MaterialSymbols.Search, contentDescription = stringRes(Res.string.devtools_kind_lookup_button))
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The kind being shown, named and tagged with its NIP when the registry knows it. */
@Composable
private fun KindTitle(kind: Int) {
    val info = KindNames.infoFor(kind)
    // Name the kind only when the app knows it, not with kindName's "k1234" fallback.
    val known = info != null || kindDisplayName(kind) != null
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringRes(Res.string.devtools_kind_usage_title, kind) + if (known) " · " + kindName(kind) else "",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f, fill = false),
        )
        info?.nip?.let { nip -> SmallChip(stringRes(Res.string.devtools_kind_nip, nip)) }
    }
}

/** One line of counts, with the share of relays that answered as a bar while sampling. */
@Composable
private fun SampleStatus(
    state: KindSamplerState,
    running: Boolean,
) {
    if (state.phase == KindSamplerPhase.DONE && state.events == 0) {
        Text(stringRes(Res.string.devtools_probe_none_found), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    if (state.phase == KindSamplerPhase.IDLE) return

    Text(
        stringRes(Res.string.devtools_kind_sample_status, state.events, state.relaysWithEvents, state.relaysTotal),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (running && state.relaysTotal > 0) {
        LinearProgressIndicator(
            progress = { state.relaysAnswered / state.relaysTotal.toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One way the kind is written: who writes it, which tags and content it carries, an example. */
@Composable
private fun VariantCard(
    index: Int,
    variant: KindVariant,
    total: Int,
    grouping: KindVariantGrouping,
) {
    var showExample by rememberSaveable(variant.key) { mutableStateOf(false) }
    val noClient = stringRes(Res.string.devtools_kind_no_client)

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (grouping == KindVariantGrouping.CLIENT) variant.client ?: noClient else stringRes(Res.string.devtools_kind_shape_n, index + 1),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(percent(variant.count, total), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                listOf(
                    pluralStringRes(Res.plurals.devtools_kind_events, variant.count, variant.count),
                    pluralStringRes(Res.plurals.devtools_kind_authors, variant.authors, variant.authors),
                    timeAgoNoDot(variant.example.createdAt).trim(),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (grouping == KindVariantGrouping.SHAPE) {
                val shown = variant.clients.take(MAX_CLIENTS).joinToString(", ") { (name, count) -> "${name ?: noClient} ($count)" }
                val hidden = variant.clients.size - MAX_CLIENTS
                val clients = if (hidden > 0) shown + ", " + stringRes(Res.string.devtools_kind_more, hidden) else shown
                Text(stringRes(Res.string.devtools_kind_from_clients, clients), style = MaterialTheme.typography.bodySmall)
            }

            val formats = variant.formats.map { (format, count) -> stringRes(format.label()) + if (variant.formats.size > 1) " " + percent(count, variant.count) else "" }
            Text(stringRes(Res.string.devtools_kind_content, formats.joinToString(", ")), style = MaterialTheme.typography.bodySmall)

            // A client group's long tail of one-off tags buries the ones every event carries.
            var showRare by rememberSaveable(variant.key) { mutableStateOf(false) }
            val common = variant.tags.filter { it.events * 100 >= variant.count * COMMON_TAG_PERCENT }
            val rare = variant.tags.size - common.size
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                (if (showRare) variant.tags else common).forEach { TagUsageChip(it, variant.count, showShare = grouping == KindVariantGrouping.CLIENT) }
            }

            Row {
                TextButton(onClick = { showExample = !showExample }) {
                    Text(stringRes(if (showExample) Res.string.devtools_kind_hide_example else Res.string.devtools_kind_show_example))
                }
                if (rare > 0) {
                    TextButton(onClick = { showRare = !showRare }) {
                        Text(if (showRare) stringRes(Res.string.devtools_kind_hide_rare) else pluralStringRes(Res.plurals.devtools_kind_rare_tags, rare, rare))
                    }
                }
            }
            if (showExample) {
                val json = remember(variant.example) { variant.example.toPrettyJson() }
                DevToolsCodeBlock(json, maxHeight = 320)
            }
        }
    }
}

/**
 * `title` 100%, `p` 40% · values: 1/3: how often a tag appears and, when it is not the usual
 * single value, how many values it carries.
 */
@Composable
private fun TagUsageChip(
    usage: TagUsage,
    groupSize: Int,
    showShare: Boolean,
) {
    val values = if (usage.lengths == SINGLE_VALUE) null else stringRes(Res.string.devtools_kind_tag_values, usage.lengths.joinToString("/") { (it - 1).toString() })
    val detail = listOfNotNull(if (showShare) percent(usage.events, groupSize) else null, values).joinToString(" · ")
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(usage.name, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            if (detail.isNotEmpty()) {
                Text(
                    " $detail",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SmallChip(text: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

private const val MAX_CLIENTS = 6

/** A tag that is always `[name, value]`. */
private val SINGLE_VALUE = listOf(2)

/** Tags on fewer than this share of a group's events fold behind "+N rare tags". */
private const val COMMON_TAG_PERCENT = 5

private fun percent(
    part: Int,
    whole: Int,
): String {
    if (whole == 0 || part == 0) return "0%"
    val rounded = (part * 100 + whole / 2) / whole
    return if (rounded == 0) "<1%" else "$rounded%"
}

private fun ContentFormat.label(): StringResource =
    when (this) {
        ContentFormat.EMPTY -> Res.string.devtools_format_empty
        ContentFormat.JSON_OBJECT -> Res.string.devtools_format_json_object
        ContentFormat.JSON_ARRAY -> Res.string.devtools_format_json_array
        ContentFormat.ENCRYPTED -> Res.string.devtools_format_encrypted
        ContentFormat.TEXT -> Res.string.devtools_format_text
    }
