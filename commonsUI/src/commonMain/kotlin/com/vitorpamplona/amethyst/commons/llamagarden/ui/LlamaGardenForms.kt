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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.llamagarden.LlamaGardenActions
import com.vitorpamplona.amethyst.commons.llamagarden.TorrentSubmissionForm
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.llama_garden_add_torrent
import com.vitorpamplona.amethyst.commons.resources.llama_garden_add_torrent_intro
import com.vitorpamplona.amethyst.commons.resources.llama_garden_file_class
import com.vitorpamplona.amethyst.commons.resources.llama_garden_file_class_help
import com.vitorpamplona.amethyst.commons.resources.llama_garden_gb
import com.vitorpamplona.amethyst.commons.resources.llama_garden_hf_help
import com.vitorpamplona.amethyst.commons.resources.llama_garden_hf_label
import com.vitorpamplona.amethyst.commons.resources.llama_garden_hf_placeholder
import com.vitorpamplona.amethyst.commons.resources.llama_garden_kind_all
import com.vitorpamplona.amethyst.commons.resources.llama_garden_kind_base
import com.vitorpamplona.amethyst.commons.resources.llama_garden_kind_fine_tune
import com.vitorpamplona.amethyst.commons.resources.llama_garden_kind_not_said
import com.vitorpamplona.amethyst.commons.resources.llama_garden_kind_quant
import com.vitorpamplona.amethyst.commons.resources.llama_garden_lab
import com.vitorpamplona.amethyst.commons.resources.llama_garden_lab_placeholder
import com.vitorpamplona.amethyst.commons.resources.llama_garden_links_help
import com.vitorpamplona.amethyst.commons.resources.llama_garden_links_label
import com.vitorpamplona.amethyst.commons.resources.llama_garden_links_placeholder
import com.vitorpamplona.amethyst.commons.resources.llama_garden_match_no
import com.vitorpamplona.amethyst.commons.resources.llama_garden_match_unknown
import com.vitorpamplona.amethyst.commons.resources.llama_garden_match_yes
import com.vitorpamplona.amethyst.commons.resources.llama_garden_matches_hf_label
import com.vitorpamplona.amethyst.commons.resources.llama_garden_model_kind
import com.vitorpamplona.amethyst.commons.resources.llama_garden_model_kind_help
import com.vitorpamplona.amethyst.commons.resources.llama_garden_model_name
import com.vitorpamplona.amethyst.commons.resources.llama_garden_model_name_placeholder
import com.vitorpamplona.amethyst.commons.resources.llama_garden_pow_notice
import com.vitorpamplona.amethyst.commons.resources.llama_garden_quant_format_label
import com.vitorpamplona.amethyst.commons.resources.llama_garden_quant_format_placeholder
import com.vitorpamplona.amethyst.commons.resources.llama_garden_request
import com.vitorpamplona.amethyst.commons.resources.llama_garden_request_model
import com.vitorpamplona.amethyst.commons.resources.llama_garden_request_model_intro
import com.vitorpamplona.amethyst.commons.resources.llama_garden_required
import com.vitorpamplona.amethyst.commons.resources.llama_garden_section_model
import com.vitorpamplona.amethyst.commons.resources.llama_garden_section_torrent
import com.vitorpamplona.amethyst.commons.resources.llama_garden_section_wants
import com.vitorpamplona.amethyst.commons.resources.llama_garden_size_label
import com.vitorpamplona.amethyst.commons.resources.llama_garden_size_placeholder
import com.vitorpamplona.amethyst.commons.resources.llama_garden_tool_label
import com.vitorpamplona.amethyst.commons.resources.llama_garden_tool_placeholder
import com.vitorpamplona.amethyst.commons.resources.llama_garden_torrent_url
import com.vitorpamplona.amethyst.commons.resources.llama_garden_torrent_url_help
import com.vitorpamplona.amethyst.commons.resources.llama_garden_torrent_url_placeholder
import com.vitorpamplona.amethyst.commons.resources.llama_garden_version_label
import com.vitorpamplona.amethyst.commons.resources.llama_garden_vram
import com.vitorpamplona.amethyst.commons.resources.llama_garden_vram_help
import com.vitorpamplona.amethyst.commons.resources.llama_garden_vram_placeholder
import com.vitorpamplona.amethyst.commons.resources.publish
import com.vitorpamplona.amethyst.commons.ui.components.FormTextField
import com.vitorpamplona.amethyst.commons.ui.components.LabeledField
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.ActionTopBar
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.llamagarden.modelRequest.ModelRequestEvent
import com.vitorpamplona.quartz.llamagarden.modelRequest.tags.RequestTypeTag
import com.vitorpamplona.quartz.llamagarden.torrent.ModelTorrentEvent
import com.vitorpamplona.quartz.llamagarden.torrent.tags.FileClassTag
import com.vitorpamplona.quartz.llamagarden.torrent.tags.HfMatchTag
import com.vitorpamplona.quartz.llamagarden.torrent.tags.ModelKindTag
import org.jetbrains.compose.resources.StringResource

private val NoticeShape = RoundedCornerShape(14.dp)

/**
 * The llama.garden actions that are not about one listing, under the header of a llama.garden kind's
 * page (30099 listings, 30103 requests). Nothing for other kinds or a read-only account.
 */
@Composable
fun LlamaGardenKindActions(
    kind: Int,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (kind != ModelTorrentEvent.KIND && kind != ModelRequestEvent.KIND) return
    if (!accountViewModel.account.signer.isWriteable()) return

    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = { nav.nav(Route.LlamaGardenRequestModel) }) {
            Icon(MaterialSymbols.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringRes(Res.string.llama_garden_request_model), modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(onClick = { nav.nav(Route.LlamaGardenAddTorrent) }) {
            Icon(MaterialSymbols.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringRes(Res.string.llama_garden_add_torrent), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/** llama.garden's "Request a model" form: the model, links to it, base or quant, and the requester's VRAM. */
@Composable
fun RequestModelScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var name by rememberSaveable { mutableStateOfString() }
    var links by rememberSaveable { mutableStateOfString() }
    var type by rememberSaveable { mutableStateOfString(RequestTypeTag.ALL) }
    var vram by rememberSaveable { mutableStateOfString() }

    FormScaffold(
        title = Res.string.llama_garden_request_model,
        postLabel = Res.string.llama_garden_request,
        canPost = { name.isNotBlank() },
        nav = nav,
        onPost = {
            val requested = name
            val linkList = links.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val wants = type
            val vramGb = vram.toIntOrNull()
            accountViewModel.launchSigner { LlamaGardenActions.requestModel(accountViewModel.account, requested, linkList, wants, vramGb) }
        },
    ) {
        Intro(MaterialSymbols.AddCircle, Res.string.llama_garden_request_model_intro)

        Section(Res.string.llama_garden_section_model) {
            LabeledField(Res.string.llama_garden_model_name, helper = stringRes(Res.string.llama_garden_required)) {
                FormTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringRes(Res.string.llama_garden_model_name_placeholder),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                )
            }
            LabeledField(Res.string.llama_garden_links_label, helper = stringRes(Res.string.llama_garden_links_help)) {
                FormTextField(
                    value = links,
                    onValueChange = { links = it },
                    placeholder = stringRes(Res.string.llama_garden_links_placeholder),
                    leadingIcon = MaterialSymbols.Link,
                    singleLine = false,
                    minLines = 2,
                    // Not KeyboardType.Uri: some URL keyboards show Go instead of a newline key, and
                    // this field takes one link per line.
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                )
            }
        }

        Section(Res.string.llama_garden_section_wants) {
            LabeledField(Res.string.llama_garden_version_label) {
                Choices(
                    selected = type,
                    options =
                        listOf(
                            RequestTypeTag.ALL to Res.string.llama_garden_kind_all,
                            RequestTypeTag.BASE to Res.string.llama_garden_kind_base,
                            RequestTypeTag.QUANT to Res.string.llama_garden_kind_quant,
                        ),
                ) { type = it }
            }
            LabeledField(Res.string.llama_garden_vram, helper = stringRes(Res.string.llama_garden_vram_help)) {
                NumberField(vram, { vram = it.filter(Char::isDigit) }, Res.string.llama_garden_vram_placeholder, imeAction = ImeAction.Done)
            }
        }

        PowNotice()
    }
}

/** llama.garden's "Add a torrent" form: an uploaded `.torrent`'s URL and what the user knows about the model. */
@Composable
fun AddTorrentScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var url by rememberSaveable { mutableStateOfString() }
    var name by rememberSaveable { mutableStateOfString() }
    var sizeGb by rememberSaveable { mutableStateOfString() }
    var tool by rememberSaveable { mutableStateOfString() }
    var hf by rememberSaveable { mutableStateOfString() }
    var lab by rememberSaveable { mutableStateOfString() }
    var fileClass by rememberSaveable { mutableStateOfString(FileClassTag.NOT_SAID) }
    var modelKind by rememberSaveable { mutableStateOfString(ModelKindTag.NOT_SAID) }
    var quantType by rememberSaveable { mutableStateOfString() }
    var hfMatch by rememberSaveable { mutableStateOfString(HfMatchTag.YES) }

    // Built from the fields on each read: the rules for what is published live in the form class.
    val form =
        TorrentSubmissionForm(
            torrentUrl = url,
            name = name,
            sizeGb = sizeGb,
            torrentTool = tool,
            huggingFaceUrl = hf,
            lab = lab,
            fileClass = fileClass,
            modelKind = modelKind,
            quantType = quantType,
            hfMatch = hfMatch,
        )

    FormScaffold(
        title = Res.string.llama_garden_add_torrent,
        postLabel = Res.string.publish,
        canPost = { form.isPublishable },
        nav = nav,
        onPost = { accountViewModel.launchSigner { LlamaGardenActions.submitTorrent(accountViewModel.account, form) } },
    ) {
        Intro(MaterialSymbols.CloudUpload, Res.string.llama_garden_add_torrent_intro)

        Section(Res.string.llama_garden_section_torrent) {
            // Red once something is typed that no client could fetch the .torrent from.
            val badUrl = url.isNotBlank() && !TorrentSubmissionForm.isHttpUrl(url)
            LabeledField(Res.string.llama_garden_torrent_url, helper = stringRes(Res.string.llama_garden_torrent_url_help), isError = badUrl) {
                FormTextField(
                    value = url,
                    isError = badUrl,
                    onValueChange = { url = it },
                    placeholder = stringRes(Res.string.llama_garden_torrent_url_placeholder),
                    leadingIcon = MaterialSymbols.Link,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                )
            }
            LabeledField(Res.string.llama_garden_model_name, helper = stringRes(Res.string.llama_garden_required)) {
                FormTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringRes(Res.string.llama_garden_model_name_placeholder),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                )
            }
            LabeledField(Res.string.llama_garden_size_label) {
                NumberField(sizeGb, { sizeGb = TorrentSubmissionForm.cleanSize(it) }, Res.string.llama_garden_size_placeholder, decimal = true)
            }
            LabeledField(Res.string.llama_garden_tool_label) {
                FormTextField(
                    value = tool,
                    onValueChange = { tool = it },
                    placeholder = stringRes(Res.string.llama_garden_tool_placeholder),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                )
            }
        }

        Section(Res.string.llama_garden_section_model) {
            LabeledField(Res.string.llama_garden_hf_label, helper = stringRes(Res.string.llama_garden_hf_help)) {
                FormTextField(
                    value = hf,
                    onValueChange = { hf = it },
                    placeholder = stringRes(Res.string.llama_garden_hf_placeholder),
                    leadingIcon = MaterialSymbols.Link,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                )
            }
            if (form.hasHuggingFace) {
                LabeledField(Res.string.llama_garden_matches_hf_label) {
                    Choices(
                        selected = hfMatch,
                        options =
                            listOf(
                                HfMatchTag.YES to Res.string.llama_garden_match_yes,
                                HfMatchTag.NO to Res.string.llama_garden_match_no,
                                HfMatchTag.UNKNOWN to Res.string.llama_garden_match_unknown,
                            ),
                    ) { hfMatch = it }
                }
            }
            LabeledField(Res.string.llama_garden_lab) {
                FormTextField(
                    value = lab,
                    onValueChange = { lab = it },
                    placeholder = stringRes(Res.string.llama_garden_lab_placeholder),
                    // The last text field unless the quant format follows it.
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = if (form.isQuant) ImeAction.Next else ImeAction.Done,
                        ),
                )
            }
            LabeledField(Res.string.llama_garden_file_class, helper = stringRes(Res.string.llama_garden_file_class_help)) {
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
            }
            // A quant's format, and what it is a quant of, only matter for a quant.
            if (form.isQuant) {
                LabeledField(Res.string.llama_garden_quant_format_label) {
                    FormTextField(
                        value = quantType,
                        onValueChange = { quantType = it },
                        placeholder = stringRes(Res.string.llama_garden_quant_format_placeholder),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    )
                }
                LabeledField(Res.string.llama_garden_model_kind, helper = stringRes(Res.string.llama_garden_model_kind_help)) {
                    Choices(
                        selected = modelKind,
                        options =
                            listOf(
                                ModelKindTag.BASE to Res.string.llama_garden_kind_base,
                                ModelKindTag.FINE_TUNE to Res.string.llama_garden_kind_fine_tune,
                                ModelKindTag.NOT_SAID to Res.string.llama_garden_kind_not_said,
                            ),
                    ) { modelKind = it }
                }
            }
        }

        PowNotice()
    }
}

private fun mutableStateOfString(initial: String = "") = mutableStateOf(initial)

@Composable
private fun FormScaffold(
    title: StringResource,
    postLabel: StringResource,
    canPost: () -> Boolean,
    nav: INav,
    onPost: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            ActionTopBar(
                postRes = postLabel,
                titleRes = title,
                isActive = canPost,
                onCancel = { nav.popBack() },
                onPost = {
                    onPost()
                    nav.popBack()
                },
            )
        },
    ) { pad ->
        Surface(modifier = Modifier.padding(pad).consumeWindowInsets(pad).imePaddingSafe()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun Intro(
    icon: MaterialSymbol,
    text: StringResource,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(stringRes(text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.grayText)
    }
}

@Composable
private fun Section(
    title: StringResource,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringRes(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

/** A single choice among a few short options: segmented, as the app's other single-choice settings. */
@Composable
private fun Choices(
    selected: String,
    options: List<Pair<String, StringResource>>,
    onSelect: (String) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(stringRes(label), maxLines = 1)
            }
        }
    }
}

/** A number in GB: the unit sits at the end of the field rather than in its label. */
@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: StringResource,
    decimal: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
) {
    FormTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = stringRes(placeholder),
        trailingIcon = { Text(stringRes(Res.string.llama_garden_gb), color = MaterialTheme.colorScheme.grayText, modifier = Modifier.padding(end = 12.dp)) },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number, imeAction = imeAction),
    )
}

/** What the viewer tells people before they publish: the wait is proof of work, and why. */
@Composable
private fun PowNotice() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(NoticeShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(MaterialSymbols.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(stringRes(Res.string.llama_garden_pow_notice), style = MaterialTheme.typography.bodySmall)
    }
}
