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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.commons.cartridges.fetchMedia
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.llamagarden.LlamaGardenActions
import com.vitorpamplona.amethyst.commons.llamagarden.LlamaGardenDefaults
import com.vitorpamplona.amethyst.commons.llamagarden.TorrentFileCheck
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nipsOnNostr.ui.LocalEventKindPage
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.llama_garden_all_models
import com.vitorpamplona.amethyst.commons.resources.llama_garden_approve
import com.vitorpamplona.amethyst.commons.resources.llama_garden_base_model
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
import com.vitorpamplona.amethyst.commons.resources.llama_garden_listing
import com.vitorpamplona.amethyst.commons.resources.llama_garden_match_no
import com.vitorpamplona.amethyst.commons.resources.llama_garden_match_unknown
import com.vitorpamplona.amethyst.commons.resources.llama_garden_match_yes
import com.vitorpamplona.amethyst.commons.resources.llama_garden_matches_hf
import com.vitorpamplona.amethyst.commons.resources.llama_garden_mirrors
import com.vitorpamplona.amethyst.commons.resources.llama_garden_model_request
import com.vitorpamplona.amethyst.commons.resources.llama_garden_no_link
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.StringResource
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
    val view = remember(event) { ModelTorrentView(event) }
    val bpw = view.bitsPerWeight?.let { stringRes(Res.string.llama_garden_bits_per_weight_value, it) }
    val chips = remember(view, bpw) { listOfNotNull(view.size, view.quant, bpw).distinct() }

    KindCard(
        icon = MaterialSymbols.DownloadForOffline,
        title = view.title ?: stringRes(Res.string.llama_garden_listing),
        subtitle = view.repoId,
        // A curator's own listing, or a user's submission (a .torrent URL, no infohash yet). Another
        // builder's catalog listing gets no pill: whether a curator approved it is in their labels.
        status =
            when {
                view.curated -> stringRes(Res.string.llama_garden_curated)
                !view.isCatalogListing -> stringRes(Res.string.llama_garden_submitted)
                else -> null
            },
        statusTone = if (view.curated) KindTone.Positive else KindTone.Neutral,
    ) {
        KindCardChips(chips)
        KindCardField(stringRes(Res.string.llama_garden_format), fileClassLabel(event.fileClass(), event.effectiveModelKind()))
        KindCardField(stringRes(Res.string.llama_garden_lab), event.lab())
        KindCardField(stringRes(Res.string.llama_garden_base_model), event.baseModel())
        KindCardField(stringRes(Res.string.llama_garden_matches_hf), hfMatchLabel(event.hfMatch()))
        KindCardField(stringRes(Res.string.llama_garden_infohash), view.infoHash, monospace = true, maxLines = 1)
        ModelTorrentActions(baseNote, event, view, accountViewModel, nav)
    }
}

/** What the card reads from a listing, worked out once per event rather than on every recomposition. */
@Immutable
private class ModelTorrentView(
    event: ModelTorrentEvent,
) {
    val title = event.title()
    val repoId = event.repoId()
    val infoHash = event.infoHash()
    val isCatalogListing = event.isCatalogListing()
    val curated = LlamaGardenDefaults.isCurator(event.pubKey)
    val huggingFaceUrl = event.huggingFaceUrl()?.let(::openableUrl)
    val size = event.totalSize()?.let { countToHumanReadableBytes(it) }
    val quant = event.quantDetail() ?: event.quantType()?.uppercase()

    /** `6.6`, or `5` rather than `5.0`. */
    val bitsPerWeight = event.quantBpw()?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }
}

/**
 * [url] as a link to open: as is when it is http(s), with `https://` when it names no scheme, and
 * null for any other scheme (`javascript:`, `intent:`, `file:`), which an event has no business
 * handing to the platform.
 */
private fun openableUrl(url: String): String? {
    val trimmed = url.trim()
    return when {
        trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true) -> trimmed
        trimmed.contains(':') -> null
        trimmed.isNotEmpty() -> "https://$trimmed"
        else -> null
    }
}

@Composable
private fun ModelTorrentActions(
    baseNote: Note,
    event: ModelTorrentEvent,
    view: ModelTorrentView,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val uriHandler = LocalUriHandler.current
    var checking by remember { mutableStateOf(false) }
    val isCurator = LlamaGardenDefaults.isCurator(accountViewModel.account.signer.pubKey)
    val canWrite = accountViewModel.account.signer.isWriteable()

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalButton(
            enabled = !checking,
            onClick = {
                checking = true
                // On the account's scope, not the card's: scrolling the card away must not drop a
                // download the user asked for.
                accountViewModel.viewModelScope.launch {
                    val check =
                        withTimeoutOrNull(DOWNLOAD_CHECK_TIMEOUT_MS) {
                            TorrentFileCheck.check(event) { accountViewModel.httpClientBuilder.fetchMedia(it, LlamaGardenDefaults.MAX_TORRENT_FILE_BYTES) }
                        } ?: TorrentFileCheck.Unreachable
                    checking = false
                    openDownload(event, check, uriHandler::openUri, accountViewModel)
                }
            },
        ) {
            if (checking) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(MaterialSymbols.Download, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(
                stringRes(if (checking) Res.string.llama_garden_checking else Res.string.llama_garden_download),
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        view.huggingFaceUrl?.let { url ->
            CardAction(MaterialSymbols.AutoMirrored.OpenInNew, Res.string.llama_garden_hugging_face) { runCatching { uriHandler.openUri(url) } }
        }

        // The account's mining queue shows its own progress while the request is mined.
        if (canWrite && view.infoHash != null) {
            CardAction(MaterialSymbols.CloudUpload, Res.string.llama_garden_request_seeders) {
                accountViewModel.launchSigner { LlamaGardenActions.requestSeeders(accountViewModel.account, baseNote) }
            }
        }

        if (canWrite && isCurator) {
            CardAction(MaterialSymbols.Check, Res.string.llama_garden_approve) {
                accountViewModel.launchSigner { LlamaGardenActions.approve(accountViewModel.account, baseNote) }
            }
            CardAction(MaterialSymbols.Block, Res.string.llama_garden_reject, tint = MaterialTheme.colorScheme.error) {
                accountViewModel.launchSigner { LlamaGardenActions.reject(accountViewModel.account, baseNote) }
            }
        }
    }

    // The kind's page lists more listings and holds "Request a model" and "Add a torrent"; on that
    // page the link would only open it again.
    if (LocalEventKindPage.current != ModelTorrentEvent.KIND) {
        CardLink(Res.string.llama_garden_all_models) { nav.nav(Route.EventKind(ModelTorrentEvent.KIND)) }
    }
}

/**
 * Hands the BitTorrent client what [check] allows: the magnet (with `xs=` only for a verified
 * mirror), or a submission's `.torrent`. Warns only when a mirror served a different file and there is
 * still something to open; an unreachable mirror is not news, the magnet works without it.
 */
private fun openDownload(
    event: ModelTorrentEvent,
    check: TorrentFileCheck,
    openUri: (String) -> Unit,
    accountViewModel: AccountViewModel,
) {
    val link = TorrentFileCheck.linkToOpen(event, check)
    if (link == null) {
        accountViewModel.toastManager.toast(Res.string.torrent_failure, Res.string.llama_garden_no_link)
        return
    }
    if (check == TorrentFileCheck.Mismatch) {
        accountViewModel.toastManager.toast(Res.string.llama_garden_torrent_mismatch_title, Res.string.llama_garden_torrent_mismatch)
    }
    try {
        openUri(link)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        accountViewModel.toastManager.toast(Res.string.torrent_failure, Res.string.torrent_no_apps)
    }
}

/** Every mirror tried in turn can wait on its own timeout; past this the download goes ahead without one. */
private const val DOWNLOAD_CHECK_TIMEOUT_MS = 30_000L

/** A secondary card action: an icon and a label, lighter than the card's one tonal button. */
@Composable
private fun CardAction(
    icon: MaterialSymbol,
    label: StringResource,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = tint),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringRes(label), modifier = Modifier.padding(start = 6.dp))
    }
}

/** A quiet link to somewhere else, aligned with the card's facts rather than its buttons. */
@Composable
private fun CardLink(
    label: StringResource,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringRes(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Icon(MaterialSymbols.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

/** A URL someone gave, one line, in a row like the card's facts. */
@Composable
private fun CardUrl(url: String) {
    val uriHandler = LocalUriHandler.current
    val target = remember(url) { openableUrl(url) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = target != null) { target?.let { runCatching { uriHandler.openUri(it) } } }
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(MaterialSymbols.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(
            text = url.removePrefix("https://").removePrefix("http://"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A llama.garden seeder request (kind 30102): the torrent that needs seeders, with its listing quoted. */
@Composable
fun RenderSeederRequest(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
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
        event.torrentListingId()?.let { KindCardQuoteEvent(it, quotesLeft, backgroundColor, accountViewModel, nav) }
    }
}

/** A llama.garden model request (kind 30103): the model, what format, the requester's VRAM and links. */
@Composable
fun RenderModelRequest(baseNote: Note) {
    val event = baseNote.event as? ModelRequestEvent ?: return
    KindCard(
        icon = MaterialSymbols.AddCircle,
        title = event.name() ?: stringRes(Res.string.llama_garden_model_request),
        subtitle = stringRes(Res.string.llama_garden_model_request),
    ) {
        KindCardField(stringRes(Res.string.llama_garden_wants), requestTypeLabel(event.requestType()))
        event.vramGb()?.let { KindCardField(stringRes(Res.string.llama_garden_vram), stringRes(Res.string.llama_garden_vram_value, it)) }
        event.links().take(5).forEach { CardUrl(it) }
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
        KindCardField(stringRes(Res.string.llama_garden_size), event.fileSize()?.let { countToHumanReadableBytes(it) })
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
