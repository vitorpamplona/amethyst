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
package com.vitorpamplona.amethyst.commons.llamagarden.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.llamagarden.LlamaGardenActions
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.llama_garden_add_torrent
import com.vitorpamplona.amethyst.commons.resources.llama_garden_add_torrent_intro
import com.vitorpamplona.amethyst.commons.resources.llama_garden_cancel
import com.vitorpamplona.amethyst.commons.resources.llama_garden_file_class
import com.vitorpamplona.amethyst.commons.resources.llama_garden_hf_url
import com.vitorpamplona.amethyst.commons.resources.llama_garden_kind_all
import com.vitorpamplona.amethyst.commons.resources.llama_garden_kind_base
import com.vitorpamplona.amethyst.commons.resources.llama_garden_kind_fine_tune
import com.vitorpamplona.amethyst.commons.resources.llama_garden_kind_not_said
import com.vitorpamplona.amethyst.commons.resources.llama_garden_kind_quant
import com.vitorpamplona.amethyst.commons.resources.llama_garden_lab
import com.vitorpamplona.amethyst.commons.resources.llama_garden_links_hint
import com.vitorpamplona.amethyst.commons.resources.llama_garden_match_no
import com.vitorpamplona.amethyst.commons.resources.llama_garden_match_unknown
import com.vitorpamplona.amethyst.commons.resources.llama_garden_match_yes
import com.vitorpamplona.amethyst.commons.resources.llama_garden_matches_hf_question
import com.vitorpamplona.amethyst.commons.resources.llama_garden_mining
import com.vitorpamplona.amethyst.commons.resources.llama_garden_mining_title
import com.vitorpamplona.amethyst.commons.resources.llama_garden_model_kind
import com.vitorpamplona.amethyst.commons.resources.llama_garden_model_name
import com.vitorpamplona.amethyst.commons.resources.llama_garden_quant_format
import com.vitorpamplona.amethyst.commons.resources.llama_garden_request_model
import com.vitorpamplona.amethyst.commons.resources.llama_garden_request_model_intro
import com.vitorpamplona.amethyst.commons.resources.llama_garden_size_gb
import com.vitorpamplona.amethyst.commons.resources.llama_garden_submit
import com.vitorpamplona.amethyst.commons.resources.llama_garden_torrent_tool
import com.vitorpamplona.amethyst.commons.resources.llama_garden_torrent_url
import com.vitorpamplona.amethyst.commons.resources.llama_garden_vram_hint
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.llamagarden.modelRequest.ModelRequestEvent
import com.vitorpamplona.quartz.llamagarden.modelRequest.tags.RequestTypeTag
import com.vitorpamplona.quartz.llamagarden.torrent.ModelTorrentEvent
import com.vitorpamplona.quartz.llamagarden.torrent.tags.FileClassTag
import com.vitorpamplona.quartz.llamagarden.torrent.tags.HfMatchTag
import com.vitorpamplona.quartz.llamagarden.torrent.tags.ModelKindTag
import org.jetbrains.compose.resources.StringResource

/**
 * The llama.garden actions that are not about one listing, for the page of a llama.garden kind
 * (30099 listings, 30103 requests): request a model, list a torrent. Nothing for other kinds or a
 * read-only account.
 */
@Composable
fun LlamaGardenKindActions(
    kind: Int,
    accountViewModel: AccountViewModel,
) {
    if (kind != ModelTorrentEvent.KIND && kind != ModelRequestEvent.KIND) return
    if (!accountViewModel.account.signer.isWriteable()) return

    var requesting by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = { requesting = true }) { Text(stringRes(Res.string.llama_garden_request_model)) }
        OutlinedButton(onClick = { adding = true }) { Text(stringRes(Res.string.llama_garden_add_torrent)) }
    }

    if (requesting) RequestModelDialog(accountViewModel) { requesting = false }
    if (adding) AddTorrentDialog(accountViewModel) { adding = false }
}

/** llama.garden's "Request a model" form: name, links, base or quant, and the requester's VRAM. */
@Composable
fun RequestModelDialog(
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var links by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(RequestTypeTag.ALL) }
    var vram by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.llama_garden_request_model)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringRes(Res.string.llama_garden_request_model_intro), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(name, { name = it }, label = { Text(stringRes(Res.string.llama_garden_model_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(links, { links = it }, label = { Text(stringRes(Res.string.llama_garden_links_hint)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Choices(
                    selected = type,
                    options = listOf(RequestTypeTag.ALL to Res.string.llama_garden_kind_all, RequestTypeTag.BASE to Res.string.llama_garden_kind_base, RequestTypeTag.QUANT to Res.string.llama_garden_kind_quant),
                ) { type = it }
                OutlinedTextField(
                    vram,
                    { vram = it.filter(Char::isDigit) },
                    label = { Text(stringRes(Res.string.llama_garden_vram_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val linkList = links.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    val vramGb = vram.toIntOrNull()
                    val requested = name
                    val wants = type
                    accountViewModel.toastManager.toast(Res.string.llama_garden_mining_title, Res.string.llama_garden_mining)
                    accountViewModel.launchSigner { LlamaGardenActions.requestModel(accountViewModel.account, requested, linkList, wants, vramGb) }
                    onDismiss()
                },
            ) { Text(stringRes(Res.string.llama_garden_submit)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.llama_garden_cancel)) } },
    )
}

/** llama.garden's "Add a torrent" form: an uploaded `.torrent`'s URL and what the user knows about the model. */
@Composable
fun AddTorrentDialog(
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var hf by remember { mutableStateOf("") }
    var sizeGb by remember { mutableStateOf("") }
    var fileClass by remember { mutableStateOf(FileClassTag.NOT_SAID) }
    var modelKind by remember { mutableStateOf(ModelKindTag.NOT_SAID) }
    var lab by remember { mutableStateOf("") }
    var quantType by remember { mutableStateOf("") }
    var tool by remember { mutableStateOf("") }
    var hfMatch by remember { mutableStateOf(HfMatchTag.YES) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.llama_garden_add_torrent)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringRes(Res.string.llama_garden_add_torrent_intro), style = MaterialTheme.typography.bodySmall)
                Field(url, { url = it }, Res.string.llama_garden_torrent_url, KeyboardType.Uri)
                Field(name, { name = it }, Res.string.llama_garden_model_name)
                Field(hf, { hf = it }, Res.string.llama_garden_hf_url, KeyboardType.Uri)
                Field(sizeGb, { sizeGb = it.filter { c -> c.isDigit() || c == '.' } }, Res.string.llama_garden_size_gb, KeyboardType.Decimal)
                Text(stringRes(Res.string.llama_garden_file_class), style = MaterialTheme.typography.labelMedium)
                Choices(
                    selected = fileClass,
                    options =
                        listOf(
                            FileClassTag.BASE to Res.string.llama_garden_kind_base,
                            FileClassTag.FINE_TUNE to Res.string.llama_garden_kind_fine_tune,
                            FileClassTag.QUANT to Res.string.llama_garden_kind_quant,
                            FileClassTag.NOT_SAID to Res.string.llama_garden_kind_not_said,
                        ),
                ) { fileClass = it }
                Text(stringRes(Res.string.llama_garden_model_kind), style = MaterialTheme.typography.labelMedium)
                Choices(
                    selected = modelKind,
                    options =
                        listOf(
                            ModelKindTag.BASE to Res.string.llama_garden_kind_base,
                            ModelKindTag.FINE_TUNE to Res.string.llama_garden_kind_fine_tune,
                            ModelKindTag.NOT_SAID to Res.string.llama_garden_kind_not_said,
                        ),
                ) { modelKind = it }
                Field(lab, { lab = it }, Res.string.llama_garden_lab)
                Field(quantType, { quantType = it }, Res.string.llama_garden_quant_format)
                Field(tool, { tool = it }, Res.string.llama_garden_torrent_tool)
                Text(stringRes(Res.string.llama_garden_matches_hf_question), style = MaterialTheme.typography.labelMedium)
                Choices(
                    selected = hfMatch,
                    options = listOf(HfMatchTag.YES to Res.string.llama_garden_match_yes, HfMatchTag.NO to Res.string.llama_garden_match_no, HfMatchTag.UNKNOWN to Res.string.llama_garden_match_unknown),
                ) { hfMatch = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank() && name.isNotBlank(),
                onClick = {
                    // Read once here: the dialog and its state are gone by the time the signer runs.
                    val torrentUrl = url
                    val title = name
                    val huggingFaceUrl = hf
                    val size = sizeGb.toDoubleOrNull()
                    val chosenClass = fileClass
                    val chosenKind = modelKind
                    val chosenLab = lab
                    val chosenQuant = quantType
                    val chosenTool = tool
                    val chosenMatch = hfMatch
                    accountViewModel.toastManager.toast(Res.string.llama_garden_mining_title, Res.string.llama_garden_mining)
                    accountViewModel.launchSigner {
                        LlamaGardenActions.submitTorrent(
                            account = accountViewModel.account,
                            torrentUrl = torrentUrl,
                            name = title,
                            approximateSizeGb = size,
                            huggingFaceUrl = huggingFaceUrl,
                            fileClass = chosenClass,
                            modelKind = chosenKind,
                            lab = chosenLab,
                            quantType = chosenQuant,
                            torrentTool = chosenTool,
                            hfMatch = chosenMatch,
                        )
                    }
                    onDismiss()
                },
            ) { Text(stringRes(Res.string.llama_garden_submit)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.llama_garden_cancel)) } },
    )
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: StringResource,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value,
        onChange,
        label = { Text(stringRes(label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Choices(
    selected: String,
    options: List<Pair<String, StringResource>>,
    onSelect: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(stringRes(label)) })
        }
    }
}
