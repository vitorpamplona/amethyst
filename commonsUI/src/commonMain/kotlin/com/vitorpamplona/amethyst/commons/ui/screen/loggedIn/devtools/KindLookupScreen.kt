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
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.vitorpamplona.amethyst.commons.devtools.KindSamplerViewModel
import com.vitorpamplona.amethyst.commons.devtools.KindVariant
import com.vitorpamplona.amethyst.commons.devtools.KindVariantGrouping
import com.vitorpamplona.amethyst.commons.devtools.TagUsage
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.devtools_event_unknown_kind
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
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_lookup_explainer
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_lookup_field
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_lookup_title
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_more
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_nip
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_no_client
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_rare_tags
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_sampled
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_shape_n
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_show_example
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_tag_values
import com.vitorpamplona.amethyst.commons.resources.devtools_kind_usage_title
import com.vitorpamplona.amethyst.commons.resources.devtools_probe_discovering
import com.vitorpamplona.amethyst.commons.resources.devtools_probe_include_discovered
import com.vitorpamplona.amethyst.commons.resources.devtools_probe_none_found
import com.vitorpamplona.amethyst.commons.resources.devtools_probe_start
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
 * Type a kind number and see every way it is written in the wild: recent events sampled from
 * many relays, grouped by the client that wrote them or by shape (tag names and content format),
 * each with its tag usage and a real example. Named when the registry knows the kind.
 */
@Composable
fun KindLookupScreen(
    initialKind: Int?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    // One sampler for the screen: looking up another kind cancels the crawl in flight.
    val viewModel =
        rememberViewModel(key = "KindSampler") {
            KindSamplerViewModel(devToolsSeedRelays(accountViewModel), accountViewModel::buildCrawlClient, accountViewModel.host.websocketBuilder::canConnect)
        }

    Scaffold(
        topBar = { TopBarWithBackButton(stringRes(Res.string.devtools_kind_lookup_title), nav) },
    ) { padding ->
        val current = kind
        if (current == null) {
            Column(Modifier.padding(padding).padding(horizontal = 16.dp, vertical = 12.dp)) {
                KindLookupHeader(null) { kind = it }
            }
        } else {
            KindVariantsList(padding, current, viewModel) { kind = it }
        }
    }
}

@Composable
private fun KindVariantsList(
    padding: PaddingValues,
    kind: Int,
    viewModel: KindSamplerViewModel,
    onLookUp: (Int) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var includeDiscovered by rememberSaveable { mutableStateOf(true) }
    var grouping by rememberSaveable { mutableStateOf(KindVariantGrouping.CLIENT) }

    LaunchedEffect(kind) {
        if (viewModel.state.value.kind != kind) viewModel.start(kind, includeDiscovered)
    }

    val running = state.phase == KindSamplerPhase.DISCOVERING || state.phase == KindSamplerPhase.SAMPLING
    val variants = if (grouping == KindVariantGrouping.CLIENT) state.byClient else state.byShape

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(padding),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KindLookupHeader(kind, onLookUp)

                Text(
                    stringRes(Res.string.devtools_kind_lookup_explainer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringRes(Res.string.devtools_probe_include_discovered), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = includeDiscovered, onCheckedChange = { includeDiscovered = it }, enabled = !running)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (running) {
                        OutlinedButton(onClick = viewModel::cancel) { Text(stringRes(Res.string.devtools_probe_stop)) }
                    } else {
                        Button(onClick = { viewModel.start(kind, includeDiscovered) }) { Text(stringRes(Res.string.devtools_probe_start)) }
                    }
                    Text(
                        stringRes(Res.string.devtools_kind_sampled, state.events, state.relaysWithEvents, state.relaysAnswered, state.relaysTotal),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }

                when {
                    state.phase == KindSamplerPhase.DISCOVERING -> {
                        Text(stringRes(Res.string.devtools_probe_discovering), style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }

                    running && state.relaysTotal > 0 ->
                        LinearProgressIndicator(
                            progress = { state.relaysAnswered / state.relaysTotal.toFloat() },
                            modifier = Modifier.fillMaxWidth(),
                        )

                    state.phase == KindSamplerPhase.DONE && state.events == 0 ->
                        Text(stringRes(Res.string.devtools_probe_none_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

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
        }

        itemsIndexed(variants, key = { _, it -> grouping.name + it.key }) { index, variant ->
            VariantCard(index, variant, state.events, grouping)
        }
    }
}

/** The kind field, and what the registry knows about the kind being shown. */
@Composable
private fun KindLookupHeader(
    kind: Int?,
    onLookUp: (Int) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(kind?.toString() ?: "") }
    val parsed = text.trim().toIntOrNull()?.takeIf { it in 0..65535 }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(5) },
                label = { Text(stringRes(Res.string.devtools_kind_lookup_field)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { parsed?.let(onLookUp) }),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { parsed?.let(onLookUp) }, enabled = parsed != null && parsed != kind) {
                Text(stringRes(Res.string.devtools_kind_lookup_button))
            }
        }

        if (kind != null) {
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
            if (!known) {
                Text(
                    stringRes(Res.string.devtools_event_unknown_kind, kind),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
            Text(
                if (grouping == KindVariantGrouping.CLIENT) variant.client ?: noClient else stringRes(Res.string.devtools_kind_shape_n, index + 1),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                listOf(
                    pluralStringRes(Res.plurals.devtools_kind_events, variant.count, variant.count),
                    pluralStringRes(Res.plurals.devtools_kind_authors, variant.authors, variant.authors),
                    percent(variant.count, total),
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
            if (rare > 0) {
                TextButton(onClick = { showRare = !showRare }) {
                    Text(if (showRare) stringRes(Res.string.devtools_kind_hide_rare) else pluralStringRes(Res.plurals.devtools_kind_rare_tags, rare, rare))
                }
            }

            TextButton(onClick = { showExample = !showExample }) {
                Text(stringRes(if (showExample) Res.string.devtools_kind_hide_example else Res.string.devtools_kind_show_example))
            }
            if (showExample) {
                val json = remember(variant.example) { variant.example.toPrettyJson() }
                DevToolsCodeBlock(json, maxHeight = 320)
            }
        }
    }
}

/** `title` · 100% · 1 value, `p` · 40% · 1/3 values: how often a tag appears and how long it is. */
@Composable
private fun TagUsageChip(
    usage: TagUsage,
    groupSize: Int,
    showShare: Boolean,
) {
    val values = stringRes(Res.string.devtools_kind_tag_values, usage.lengths.joinToString("/") { (it - 1).toString() })
    val detail = if (showShare) percent(usage.events, groupSize) + " · " + values else values
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(usage.name, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            Text(
                " $detail",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
