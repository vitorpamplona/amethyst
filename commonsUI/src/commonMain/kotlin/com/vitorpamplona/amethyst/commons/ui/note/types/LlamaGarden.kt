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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.cartridges.fetchMedia
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.llamagarden.LlamaGardenActions
import com.vitorpamplona.amethyst.commons.llamagarden.LlamaGardenDefaults
import com.vitorpamplona.amethyst.commons.llamagarden.TorrentFileCheck
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.llama_garden_approve
import com.vitorpamplona.amethyst.commons.resources.llama_garden_base_model
import com.vitorpamplona.amethyst.commons.resources.llama_garden_bits_per_weight
import com.vitorpamplona.amethyst.commons.resources.llama_garden_bits_per_weight_value
import com.vitorpamplona.amethyst.commons.resources.llama_garden_checking
import com.vitorpamplona.amethyst.commons.resources.llama_garden_curated
import com.vitorpamplona.amethyst.commons.resources.llama_garden_download
import com.vitorpamplona.amethyst.commons.resources.llama_garden_format
import com.vitorpamplona.amethyst.commons.resources.llama_garden_hugging_face
import com.vitorpamplona.amethyst.commons.resources.llama_garden_infohash
import com.vitorpamplona.amethyst.commons.resources.llama_garden_kind_all
import com.vitorpamplona.amethyst.commons.resources.llama_garden_kind_base
import com.vitorpamplona.amethyst.commons.resources.llama_garden_kind_fine_tune
import com.vitorpamplona.amethyst.commons.resources.llama_garden_kind_not_said
import com.vitorpamplona.amethyst.commons.resources.llama_garden_kind_quant
import com.vitorpamplona.amethyst.commons.resources.llama_garden_lab
import com.vitorpamplona.amethyst.commons.resources.llama_garden_links
import com.vitorpamplona.amethyst.commons.resources.llama_garden_listing
import com.vitorpamplona.amethyst.commons.resources.llama_garden_listing_link
import com.vitorpamplona.amethyst.commons.resources.llama_garden_match_no
import com.vitorpamplona.amethyst.commons.resources.llama_garden_match_unknown
import com.vitorpamplona.amethyst.commons.resources.llama_garden_match_yes
import com.vitorpamplona.amethyst.commons.resources.llama_garden_matches_hf
import com.vitorpamplona.amethyst.commons.resources.llama_garden_mining
import com.vitorpamplona.amethyst.commons.resources.llama_garden_mining_title
import com.vitorpamplona.amethyst.commons.resources.llama_garden_mirrors
import com.vitorpamplona.amethyst.commons.resources.llama_garden_model_request
import com.vitorpamplona.amethyst.commons.resources.llama_garden_more_models
import com.vitorpamplona.amethyst.commons.resources.llama_garden_no_link
import com.vitorpamplona.amethyst.commons.resources.llama_garden_quant
import com.vitorpamplona.amethyst.commons.resources.llama_garden_reject
import com.vitorpamplona.amethyst.commons.resources.llama_garden_release
import com.vitorpamplona.amethyst.commons.resources.llama_garden_release_subtitle
import com.vitorpamplona.amethyst.commons.resources.llama_garden_request_seeders
import com.vitorpamplona.amethyst.commons.resources.llama_garden_seeders_wanted
import com.vitorpamplona.amethyst.commons.resources.llama_garden_sha256
import com.vitorpamplona.amethyst.commons.resources.llama_garden_size
import com.vitorpamplona.amethyst.commons.resources.llama_garden_submitted
import com.vitorpamplona.amethyst.commons.resources.llama_garden_torrent_mismatch
import com.vitorpamplona.amethyst.commons.resources.llama_garden_torrent_mismatch_title
import com.vitorpamplona.amethyst.commons.resources.llama_garden_vram
import com.vitorpamplona.amethyst.commons.resources.llama_garden_vram_value
import com.vitorpamplona.amethyst.commons.resources.llama_garden_wants
import com.vitorpamplona.amethyst.commons.resources.llama_garden_web_seeds
import com.vitorpamplona.amethyst.commons.resources.torrent_failure
import com.vitorpamplona.amethyst.commons.resources.torrent_no_apps
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.util.countToHumanReadableBytes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.llamagarden.modelRequest.ModelRequestEvent
import com.vitorpamplona.quartz.llamagarden.modelRequest.tags.RequestTypeTag
import com.vitorpamplona.quartz.llamagarden.release.ClientReleaseEvent
import com.vitorpamplona.quartz.llamagarden.seederRequest.SeederRequestEvent
import com.vitorpamplona.quartz.llamagarden.torrent.ModelTorrentEvent
import com.vitorpamplona.quartz.llamagarden.torrent.tags.FileClassTag
import com.vitorpamplona.quartz.llamagarden.torrent.tags.HfMatchTag
import com.vitorpamplona.quartz.llamagarden.torrent.tags.ModelKindTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * A llama.garden model torrent (kind 30099): the model, its format and size, and what llama.garden's
 * viewer offers: download (the magnet, with the `.torrent` mirror as exact source only once its bytes
 * hash to the signed `x`), the HuggingFace page, a seeder request, and approve/reject for curators.
 */
@Composable
fun RenderModelTorrent(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? ModelTorrentEvent ?: return
    val curated = remember(event) { LlamaGardenDefaults.isCurator(event.pubKey) }
    val chips =
        remember(event) {
            listOfNotNull(
                event.totalSize()?.let { countToHumanReadableBytes(it) },
                event.quantDetail() ?: event.quantType()?.uppercase(),
            ).distinct()
        }

    KindCard(
        icon = MaterialSymbols.DownloadForOffline,
        title = event.title() ?: stringRes(Res.string.llama_garden_listing),
        subtitle = event.repoId(),
        // A curator's own listing, or a user's submission (a .torrent URL, no infohash yet). Another
        // builder's catalog listing gets no pill: whether a curator approved it is in their labels.
        status =
            when {
                curated -> stringRes(Res.string.llama_garden_curated)
                !event.isCatalogListing() -> stringRes(Res.string.llama_garden_submitted)
                else -> null
            },
        statusTone = if (curated) KindTone.Positive else KindTone.Neutral,
    ) {
        KindCardChips(chips)
        KindCardField(stringRes(Res.string.llama_garden_format), fileClassLabel(event.fileClass(), event.effectiveModelKind()))
        KindCardField(stringRes(Res.string.llama_garden_quant), event.quantDetail() ?: event.quantType())
        event.quantBpw()?.let { KindCardField(stringRes(Res.string.llama_garden_bits_per_weight), stringRes(Res.string.llama_garden_bits_per_weight_value, it.toString())) }
        KindCardField(stringRes(Res.string.llama_garden_lab), event.lab())
        KindCardField(stringRes(Res.string.llama_garden_base_model), event.baseModel())
        KindCardField(
            stringRes(Res.string.llama_garden_web_seeds),
            event
                .webSeeds()
                .size
                .takeIf { it > 0 }
                ?.toString(),
        )
        KindCardField(stringRes(Res.string.llama_garden_matches_hf), hfMatchLabel(event.hfMatch()))
        KindCardField(stringRes(Res.string.llama_garden_infohash), event.infoHash(), monospace = true, maxLines = 1)
        ModelTorrentActions(baseNote, event, accountViewModel, nav)
    }
}

@Composable
private fun ModelTorrentActions(
    baseNote: Note,
    event: ModelTorrentEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    val mismatchTitle = stringRes(Res.string.llama_garden_torrent_mismatch_title)
    val mismatch = stringRes(Res.string.llama_garden_torrent_mismatch)
    val isCurator = LlamaGardenDefaults.isCurator(accountViewModel.account.signer.pubKey)
    val canWrite = accountViewModel.account.signer.isWriteable()

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            enabled = !checking,
            onClick = {
                checking = true
                scope.launch {
                    val check =
                        withContext(Dispatchers.IO) {
                            TorrentFileCheck.check(event) { accountViewModel.httpClientBuilder.fetchMedia(it, LlamaGardenDefaults.MAX_TORRENT_FILE_BYTES) }
                        }
                    checking = false
                    if (check == TorrentFileCheck.Mismatch) accountViewModel.toastManager.toast(mismatchTitle, mismatch)
                    val link = TorrentFileCheck.linkToOpen(event, check)
                    if (link == null) {
                        accountViewModel.toastManager.toast(Res.string.torrent_failure, Res.string.llama_garden_no_link)
                    } else {
                        try {
                            uriHandler.openUri(link)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            accountViewModel.toastManager.toast(Res.string.torrent_failure, Res.string.torrent_no_apps)
                        }
                    }
                }
            },
        ) {
            Text(stringRes(if (checking) Res.string.llama_garden_checking else Res.string.llama_garden_download))
        }

        event.huggingFaceUrl()?.let { url ->
            TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                Text(stringRes(Res.string.llama_garden_hugging_face))
            }
        }

        if (canWrite && event.infoHash() != null) {
            TextButton(
                onClick = {
                    accountViewModel.toastManager.toast(Res.string.llama_garden_mining_title, Res.string.llama_garden_mining)
                    accountViewModel.launchSigner { LlamaGardenActions.requestSeeders(accountViewModel.account, baseNote) }
                },
            ) {
                Text(stringRes(Res.string.llama_garden_request_seeders))
            }
        }

        // The kind's page lists more listings and holds "Request a model" and "Add a torrent".
        TextButton(onClick = { nav.nav(Route.EventKind(ModelTorrentEvent.KIND)) }) {
            Text(stringRes(Res.string.llama_garden_more_models))
        }

        if (canWrite && isCurator) {
            TextButton(onClick = { accountViewModel.launchSigner { LlamaGardenActions.approve(accountViewModel.account, baseNote) } }) {
                Text(stringRes(Res.string.llama_garden_approve))
            }
            TextButton(onClick = { accountViewModel.launchSigner { LlamaGardenActions.reject(accountViewModel.account, baseNote) } }) {
                Text(stringRes(Res.string.llama_garden_reject))
            }
        }
    }
}

/** A llama.garden seeder request (kind 30102): the torrent that needs seeders, and a link to its listing. */
@Composable
fun RenderSeederRequest(
    baseNote: Note,
    nav: INav,
) {
    val event = baseNote.event as? SeederRequestEvent ?: return
    KindCard(
        icon = MaterialSymbols.CloudUpload,
        title = stringRes(Res.string.llama_garden_seeders_wanted),
        subtitle = event.name(),
        tone = KindTone.Warning,
    ) {
        KindCardField(stringRes(Res.string.llama_garden_infohash), event.infoHash(), monospace = true, maxLines = 1)
        event.torrentListingId()?.let { id ->
            TextButton(onClick = { nav.nav(Route.Note(id)) }) {
                Text(stringRes(Res.string.llama_garden_listing_link))
            }
        }
    }
}

/** A llama.garden model request (kind 30103): the model, what format, the requester's VRAM and links. */
@Composable
fun RenderModelRequest(baseNote: Note) {
    val event = baseNote.event as? ModelRequestEvent ?: return
    val uriHandler = LocalUriHandler.current
    KindCard(
        icon = MaterialSymbols.AddCircle,
        title = event.name() ?: stringRes(Res.string.llama_garden_model_request),
        subtitle = stringRes(Res.string.llama_garden_model_request),
    ) {
        KindCardField(stringRes(Res.string.llama_garden_wants), requestTypeLabel(event.requestType()))
        event.vramGb()?.let { KindCardField(stringRes(Res.string.llama_garden_vram), stringRes(Res.string.llama_garden_vram_value, it)) }
        val links = event.links()
        if (links.isNotEmpty()) {
            KindCardSection(stringRes(Res.string.llama_garden_links))
            links.take(5).forEach { url ->
                TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                    Text(url, maxLines = 1)
                }
            }
        }
    }
}

/**
 * A llama.garden viewer release (kind 30100): version, size, hash and mirrors. Shown, not installed:
 * Amethyst does not run the viewer, so nothing here updates or opens the file.
 */
@Composable
fun RenderClientRelease(baseNote: Note) {
    val event = baseNote.event as? ClientReleaseEvent ?: return
    KindCard(
        icon = MaterialSymbols.CloudDownload,
        title = stringRes(Res.string.llama_garden_release, event.version() ?: "?"),
        subtitle = stringRes(Res.string.llama_garden_release_subtitle),
        tone = KindTone.Neutral,
    ) {
        KindCardField(stringRes(Res.string.llama_garden_size), event.size()?.let { countToHumanReadableBytes(it) })
        KindCardField(stringRes(Res.string.llama_garden_sha256), event.sha256(), monospace = true, maxLines = 1)
        KindCardField(stringRes(Res.string.llama_garden_mirrors), event.downloadUrls().size.toString())
    }
}

@Composable
private fun fileClassLabel(
    fileClass: String?,
    modelKind: String?,
): String? {
    val cls = fileClass?.let { classOrKindLabel(it) }
    val kind = modelKind?.let { classOrKindLabel(it) }
    return when {
        cls == null -> kind
        kind == null || kind == cls || fileClass == FileClassTag.NOT_SAID -> cls
        else -> "$cls · $kind"
    }
}

@Composable
private fun classOrKindLabel(value: String): String =
    when (value) {
        FileClassTag.BASE, ModelKindTag.BASE -> stringRes(Res.string.llama_garden_kind_base)
        FileClassTag.FINE_TUNE, ModelKindTag.FINE_TUNE -> stringRes(Res.string.llama_garden_kind_fine_tune)
        FileClassTag.QUANT -> stringRes(Res.string.llama_garden_kind_quant)
        FileClassTag.NOT_SAID, ModelKindTag.NOT_SAID -> stringRes(Res.string.llama_garden_kind_not_said)
        else -> value
    }

@Composable
private fun hfMatchLabel(value: String?): String? =
    when (value) {
        null -> null
        HfMatchTag.YES -> stringRes(Res.string.llama_garden_match_yes)
        HfMatchTag.NO -> stringRes(Res.string.llama_garden_match_no)
        HfMatchTag.UNKNOWN -> stringRes(Res.string.llama_garden_match_unknown)
        else -> value
    }

@Composable
private fun requestTypeLabel(value: String?): String? =
    when (value) {
        null -> null
        RequestTypeTag.ALL -> stringRes(Res.string.llama_garden_kind_all)
        RequestTypeTag.BASE -> stringRes(Res.string.llama_garden_kind_base)
        RequestTypeTag.QUANT -> stringRes(Res.string.llama_garden_kind_quant)
        else -> value
    }
