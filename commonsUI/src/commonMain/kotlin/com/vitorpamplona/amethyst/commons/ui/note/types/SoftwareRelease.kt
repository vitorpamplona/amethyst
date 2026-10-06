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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nip82_commit_label
import com.vitorpamplona.amethyst.commons.resources.nip82_download
import com.vitorpamplona.amethyst.commons.resources.nip82_downloads_count
import com.vitorpamplona.amethyst.commons.resources.nip82_for_this_device
import com.vitorpamplona.amethyst.commons.resources.nip82_min_android_api
import com.vitorpamplona.amethyst.commons.resources.nip82_min_platform_version
import com.vitorpamplona.amethyst.commons.resources.nip82_new_release
import com.vitorpamplona.amethyst.commons.resources.nip82_not_from_developer
import com.vitorpamplona.amethyst.commons.resources.nip82_os_android
import com.vitorpamplona.amethyst.commons.resources.nip82_os_freebsd
import com.vitorpamplona.amethyst.commons.resources.nip82_os_ios
import com.vitorpamplona.amethyst.commons.resources.nip82_os_linux
import com.vitorpamplona.amethyst.commons.resources.nip82_os_macos
import com.vitorpamplona.amethyst.commons.resources.nip82_os_other
import com.vitorpamplona.amethyst.commons.resources.nip82_os_wasm
import com.vitorpamplona.amethyst.commons.resources.nip82_os_windows
import com.vitorpamplona.amethyst.commons.resources.nip82_section_downloads
import com.vitorpamplona.amethyst.commons.resources.nip82_version_label
import com.vitorpamplona.amethyst.commons.softwareapps.SoftwareAssetDownloads
import com.vitorpamplona.amethyst.commons.softwareapps.SoftwareOs
import com.vitorpamplona.amethyst.commons.softwareapps.SoftwarePlatforms
import com.vitorpamplona.amethyst.commons.softwareapps.SoftwareReleases
import com.vitorpamplona.amethyst.commons.ui.components.ExpandableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.formatLongDate
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.util.devicePlatformIds
import com.vitorpamplona.amethyst.commons.util.prettyMime
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.isNip82SoftwareRelease
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip51Lists.releaseArtifactSet.ReleaseArtifactSetEvent
import com.vitorpamplona.quartz.nipB7Blossom.BlossomServerUrl
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The NIP-82 releases of [app], newest version first, kept live from the cache. Only releases
 * [SoftwareReleases.isReleaseOf] accepts are returned: signed by the app's publisher or a
 * maintainer it credits, and pointing at the app through their `a` tag.
 *
 * The cache index narrows on the release's `i` tag and its trusted signers, so an author with
 * many apps does not pull every release they ever published into the observer.
 */
@Composable
fun produceNip82Releases(app: SoftwareApplicationEvent): State<List<ReleaseArtifactSetEvent>> {
    val flow =
        remember(app.id) {
            LocalCache
                .observeNotes(SoftwareReleases.filter(app))
                .map { notes ->
                    SoftwareReleases.sorted(
                        notes.mapNotNull { note ->
                            (note.event as? ReleaseArtifactSetEvent)?.takeIf { SoftwareReleases.isReleaseOf(it, app) }
                        },
                    )
                }.distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }
    return flow.collectAsStateWithLifecycle(initialValue = emptyList())
}

/**
 * NIP-82 kind 30063 — a software release as a note. Leads with the app it belongs to (icon
 * and name, from the release's `a` tag), then the version, the release notes as markdown and
 * the platforms it ships for.
 *
 * In a feed ([expanded] false) the platforms come from the release's aggregate `f` tags, so
 * the card fetches none of its assets; the app's page lists the downloads. Expanded (the
 * release's own thread) it lists the downloads itself.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderSoftwareRelease(
    note: Note,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    expanded: Boolean = false,
) {
    // kind 30063 is shared between NIP-51 release artifact sets and NIP-82 software
    // releases; only render the NIP-82 form here.
    val event = note.event as? ReleaseArtifactSetEvent ?: return
    if (!event.isNip82SoftwareRelease()) return

    val appAddress = remember(event) { event.appAddress() }
    val appNote = remember(appAddress) { appAddress?.let { LocalCache.getOrCreateAddressableNote(it) } }
    val app = appNote?.let { observeNoteEvent<SoftwareApplicationEvent>(it, accountViewModel).value }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Size5dp)
                .clip(QuoteBorder)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder)
                .padding(12.dp),
    ) {
        ReleaseAppHeader(event, app, appAddress, nav)

        if (app != null && !SoftwareReleases.isReleaseOf(event, app)) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringRes(Res.string.nip82_not_from_developer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (expanded) {
            ReleaseDetails(event, app, backgroundColor, accountViewModel, nav)
        } else {
            ReleaseNotes(event, backgroundColor, expandable = true, accountViewModel, nav)
            ReleaseSummaryFooter(event, appAddress, nav)
        }
    }
}

/**
 * A release on its app's page: no app header (the page is the app), the version and date,
 * the release notes and the downloads.
 */
@Composable
fun RenderSoftwareReleaseBody(
    event: ReleaseArtifactSetEvent,
    app: SoftwareApplicationEvent,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Size5dp)
                .clip(QuoteBorder)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder)
                .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                event.version()?.let {
                    Text(
                        text = stringRes(Res.string.nip82_version_label, it),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = remember(event.createdAt) { formatLongDate(event.createdAt) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
            ChannelChip(event.channel())
        }

        ReleaseDetails(event, app, backgroundColor, accountViewModel, nav)
    }
}

/** Icon, app name and "New release" on the left; version and a non-default channel on the right. */
@Composable
private fun ReleaseAppHeader(
    event: ReleaseArtifactSetEvent,
    app: SoftwareApplicationEvent?,
    appAddress: Address?,
    nav: INav,
) {
    val name = app?.name() ?: event.appId().orEmpty()
    val openApp = appAddress?.let { { nav.nav(Route.SoftwareAppDetail(it)) } }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (openApp != null) Modifier.clickable(onClick = openApp) else Modifier,
    ) {
        AppIcon(icon = app?.icon(), name = name, sizeDp = 40)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringRes(Res.string.nip82_new_release),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 1,
            )
        }
        event.version()?.let {
            Spacer(Modifier.width(8.dp))
            VersionChip(it)
        }
        ChannelChip(event.channel())
    }
}

/** `main` is NIP-82's default channel, so only the others earn a chip. */
@Composable
private fun ChannelChip(channel: String?) {
    if (channel == null || SoftwareReleases.isMainChannel(channel)) return
    Spacer(Modifier.width(6.dp))
    Chip(channel.uppercase(), tint = MaterialTheme.colorScheme.tertiaryContainer)
}

@Composable
private fun ReleaseNotes(
    event: ReleaseArtifactSetEvent,
    backgroundColor: MutableState<Color>,
    expandable: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val notes = event.releaseNotes()
    if (notes.isBlank()) return

    val tags = remember(event) { event.tags.toImmutableListOfLists() }
    Spacer(StdVertSpacer)
    if (expandable) {
        ExpandableRichTextViewer(
            content = notes,
            canPreview = true,
            quotesLeft = 1,
            modifier = Modifier.fillMaxWidth(),
            tags = tags,
            backgroundColor = backgroundColor,
            id = event.id,
            authorPubKey = event.pubKey,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    } else {
        TranslatableRichTextViewer(
            content = notes,
            canPreview = true,
            quotesLeft = 1,
            modifier = Modifier.fillMaxWidth(),
            tags = tags,
            backgroundColor = backgroundColor,
            id = event.id,
            authorPubKey = event.pubKey,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

/** The OSes behind the release's aggregate `f` tags, and how many downloads its app page lists. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReleaseSummaryFooter(
    event: ReleaseArtifactSetEvent,
    appAddress: Address?,
    nav: INav,
) {
    val oses = remember(event) { SoftwarePlatforms.osesOfPlatforms(event.platforms()) }
    val assetCount = remember(event) { event.assets().size }
    if (oses.isEmpty() && assetCount == 0) return

    Spacer(StdVertSpacer)
    Row(verticalAlignment = Alignment.CenterVertically) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            oses.forEach { Chip(osLabel(it)) }
        }
        if (assetCount > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = pluralStringRes(Res.plurals.nip82_downloads_count, assetCount, assetCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    if (appAddress != null) {
                        Modifier.clickable { nav.nav(Route.SoftwareAppDetail(appAddress)) }
                    } else {
                        Modifier
                    },
            )
        }
    }
}

/** Commit, full release notes and the downloads grouped by OS. */
@Composable
private fun ReleaseDetails(
    event: ReleaseArtifactSetEvent,
    app: SoftwareApplicationEvent?,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val assetIds = remember(event) { event.assets().map { it.eventId } }
    // One observer per asset: each fetches its event from the relays when missing.
    val assets = assetIds.map { id -> key(id) { rememberAssetEvent(id, accountViewModel) } }.filterNotNull()

    // NIP-82 puts `commit` on assets; some publishers put it on the release instead.
    val commit = assets.firstNotNullOfOrNull { it.commit() } ?: event.commit()
    commit?.let { CommitLine(it, app, nav) }

    ReleaseNotes(event, backgroundColor, expandable = false, accountViewModel, nav)

    if (assetIds.isNotEmpty()) {
        Spacer(StdVertSpacer)
        DownloadsList(assets, assetIds.size, event.version(), accountViewModel)
    }
}

@Composable
private fun rememberAssetEvent(
    eventId: HexKey,
    accountViewModel: AccountViewModel,
): SoftwareAssetEvent? {
    val note by produceState(LocalCache.getNoteIfExists(eventId), eventId) {
        if (value == null) value = LocalCache.checkGetOrCreateNote(eventId)
    }
    val loaded = note ?: return null
    return observeNoteEvent<SoftwareAssetEvent>(loaded, accountViewModel).value
}

/** "Commit 821591a", opening the app's NIP-34 repository when the app links one. */
@Composable
private fun CommitLine(
    commit: String,
    app: SoftwareApplicationEvent?,
    nav: INav,
) {
    val repo = remember(app) { app?.appLinks()?.firstOrNull { it.kind == GitRepositoryEvent.KIND } }
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringRes(Res.string.nip82_commit_label, commit.take(7)),
        style = MaterialTheme.typography.bodySmall,
        color = if (repo != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.grayText,
        modifier =
            if (repo != null) {
                Modifier.clickable { nav.nav(Route.GitRepository(repo.kind, repo.pubKeyHex, repo.dTag)) }
            } else {
                Modifier
            },
    )
}

/**
 * The release's downloads, grouped by OS in [SoftwareOs] order, with the best fit for this
 * device repeated on top.
 */
@Composable
private fun DownloadsList(
    assets: List<SoftwareAssetEvent>,
    expectedCount: Int,
    releaseVersion: String?,
    accountViewModel: AccountViewModel,
) {
    val devicePlatforms = remember { devicePlatformIds() }
    val forThisDevice =
        remember(assets) {
            assets
                .mapNotNull { asset -> SoftwarePlatforms.deviceFit(asset.platforms(), asset.mimeType(), devicePlatforms)?.let { asset to it } }
                .minByOrNull { it.second }
                ?.first
        }
    val groups =
        remember(assets) {
            SoftwareOs.entries.mapNotNull { os ->
                assets.filter { os in SoftwarePlatforms.osesOf(it) }.takeIf { it.isNotEmpty() }?.let { os to it }
            }
        }

    Text(
        text = stringRes(Res.string.nip82_section_downloads) + " · " + pluralStringRes(Res.plurals.nip82_downloads_count, expectedCount, expectedCount),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.grayText,
        fontWeight = FontWeight.SemiBold,
    )

    forThisDevice?.let { asset ->
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringRes(Res.string.nip82_for_this_device),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        DownloadRow(asset, SoftwarePlatforms.osesOf(asset).first(), releaseVersion, highlighted = true, accountViewModel)
    }

    groups.forEach { (os, list) ->
        Spacer(Modifier.height(8.dp))
        Text(
            text = osLabel(os),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            list.forEach { asset ->
                key(asset.id) {
                    DownloadRow(asset, os, releaseVersion, highlighted = false, accountViewModel)
                }
            }
        }
    }
}

/**
 * One download: its format and variant ("EXE · Installer"), then the CPU, size, minimum OS
 * version, and the asset's own version when it differs from the release's.
 */
@Composable
private fun DownloadRow(
    asset: SoftwareAssetEvent,
    os: SoftwareOs,
    releaseVersion: String?,
    highlighted: Boolean,
    accountViewModel: AccountViewModel,
) {
    val format = remember(asset) { asset.mimeType()?.let(::prettyMime) }
    val variant = remember(asset) { asset.variant()?.replaceFirstChar { it.uppercaseChar() } }
    val archs =
        remember(asset, os) {
            asset
                .platforms()
                .filter { SoftwarePlatforms.os(it) == os }
                .mapNotNull(SoftwarePlatforms::arch)
                .distinct()
                .joinToString(", ")
        }
    val size = remember(asset) { asset.sizeInBytes()?.let { formatBytes(it.toLong()) } }
    val assetVersion = remember(asset, releaseVersion) { asset.version()?.takeIf { it != releaseVersion } }
    val minVersion = asset.minPlatformVersion()?.let { minPlatformLabel(os, it) }

    val title = listOfNotNull(format, variant).joinToString(" · ").ifEmpty { asset.filename() ?: osLabel(os) }
    val details =
        listOfNotNull(
            archs.ifEmpty { null },
            size,
            minVersion,
            assetVersion?.let { stringRes(Res.string.nip82_version_label, it) },
        ).joinToString(" · ")

    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .then(
                    if (highlighted) {
                        Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.subtleBorder, shape)
                    },
                ).padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        DownloadButton(asset, accountViewModel)
    }
}

/**
 * Opens the asset's url. Without one, NIP-82 has clients find the file by its `x` hash on
 * Blossom: the publisher's servers first, then the viewer's default server.
 */
@Composable
private fun DownloadButton(
    asset: SoftwareAssetEvent,
    accountViewModel: AccountViewModel,
) {
    val uri = LocalUriHandler.current
    val finder = LocalAppServices.current.blossomServerFinder
    val scope = rememberCoroutineScope()
    val directUrl = remember(asset) { SoftwareAssetDownloads.url(asset) }
    val hash = remember(asset) { asset.hash() }
    if (directUrl == null && hash == null) return

    val label = stringRes(Res.string.nip82_download)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    if (directUrl != null) {
                        runCatching { uri.openUri(directUrl) }
                    } else if (hash != null) {
                        scope.launch {
                            val server = accountViewModel.account.settings.defaultFileServer.baseUrl
                            val extension = SoftwareAssetDownloads.extension(asset)
                            val blossomUri =
                                BlossomUri(
                                    sha256 = hash,
                                    extension = extension ?: "bin",
                                    servers = listOf(server),
                                    authors = listOf(asset.pubKey),
                                    size = asset.sizeInBytes()?.toLong(),
                                ).toUriString()
                            val url = finder.findServerUrl(blossomUri) ?: BlossomServerUrl.blob(server, hash, extension.orEmpty())
                            runCatching { uri.openUri(url) }
                        }
                    }
                }.padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.Download,
            contentDescription = label,
            modifier = Size16Modifier,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun minPlatformLabel(
    os: SoftwareOs,
    version: String,
): String =
    if (os == SoftwareOs.ANDROID) {
        stringRes(Res.string.nip82_min_android_api, version)
    } else {
        stringRes(Res.string.nip82_min_platform_version, osLabel(os), version)
    }

@Composable
fun osLabel(os: SoftwareOs): String =
    stringRes(
        when (os) {
            SoftwareOs.ANDROID -> Res.string.nip82_os_android
            SoftwareOs.IOS -> Res.string.nip82_os_ios
            SoftwareOs.MACOS -> Res.string.nip82_os_macos
            SoftwareOs.WINDOWS -> Res.string.nip82_os_windows
            SoftwareOs.LINUX -> Res.string.nip82_os_linux
            SoftwareOs.FREEBSD -> Res.string.nip82_os_freebsd
            SoftwareOs.WASM -> Res.string.nip82_os_wasm
            SoftwareOs.OTHER -> Res.string.nip82_os_other
        },
    )
