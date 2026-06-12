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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.filterIntoSet
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEvent
import com.vitorpamplona.amethyst.ui.components.ClickableTextPrimary
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.LinkIcon
import com.vitorpamplona.amethyst.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.SoftwareReleaseEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.asSoftwareRelease
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.release.isNip82SoftwareRelease
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * NIP-82 kind 32267 — compact feed card. Renders icon, name, latest version
 * chip, summary, description, and platforms/license. Tapping the card opens
 * the dedicated [Route.SoftwareAppDetail] screen with screenshots, full
 * description, links, releases, and comments. The bottom of the card hosts
 * the standard [ReactionsRow] so zaps / likes / replies are visible inline.
 */
@Composable
fun RenderSoftwareApplication(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event as? SoftwareApplicationEvent ?: return

    val icon = remember(event) { event.icon() }
    val name = remember(event) { event.name() ?: event.appId().orEmpty() }
    val summary = remember(event) { event.summary() }
    val description = remember(event) { event.content.trim() }
    val platforms = remember(event) { event.platforms() }
    val license = remember(event) { event.license() }

    val latestVersion by produceLatestReleaseVersion(event)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Size5dp)
                .clip(QuoteBorder)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder)
                .clickable { nav.nav(Route.SoftwareAppDetail(event.kind, event.pubKey, event.dTag())) }
                .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(icon = icon, name = name)

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                if (name.isNotBlank()) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                summary?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            latestVersion?.let { version ->
                Spacer(Modifier.width(8.dp))
                VersionChip(version)
            }
        }

        if (description.isNotBlank()) {
            Spacer(StdVertSpacer)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (platforms.isNotEmpty() || license != null) {
            Spacer(StdVertSpacer)
            PlatformLicenseRow(platforms = platforms, license = license)
        }
    }

    ReactionsRow(
        baseNote = note,
        showReactionDetail = true,
        addPadding = true,
        editState = null,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/**
 * Looks up the latest NIP-82 [SoftwareReleaseEvent] for [app] from
 * [LocalCache] and exposes the version string. Recomputes on event identity
 * change; relay-driven recompositions of the surrounding feed will pick up
 * newer releases via re-keying.
 */
@Composable
fun produceLatestReleaseVersion(app: SoftwareApplicationEvent) =
    produceState<String?>(initialValue = null, key1 = app.id) {
        value =
            withContext(Dispatchers.Default) {
                findLatestNip82Release(app)?.version()
            }
    }

fun findLatestNip82Release(app: SoftwareApplicationEvent): SoftwareReleaseEvent? {
    val prefix = "${app.dTag()}@"
    val notes =
        LocalCache.addressables.filterIntoSet(SoftwareReleaseEvent.KIND, app.pubKey) { _, addr ->
            val ev = addr.event ?: return@filterIntoSet false
            ev.isNip82SoftwareRelease() && ev.dTag().startsWith(prefix)
        }
    return notes
        .mapNotNull {
            when (val ev = it.event) {
                is SoftwareReleaseEvent -> ev
                null -> null
                else -> if (ev.isNip82SoftwareRelease()) ev.asSoftwareRelease() else null
            }
        }.maxByOrNull { it.createdAt }
}

fun findAllNip82Releases(app: SoftwareApplicationEvent): List<SoftwareReleaseEvent> {
    val prefix = "${app.dTag()}@"
    val notes =
        LocalCache.addressables.filterIntoSet(SoftwareReleaseEvent.KIND, app.pubKey) { _, addr ->
            val ev = addr.event ?: return@filterIntoSet false
            ev.isNip82SoftwareRelease() && ev.dTag().startsWith(prefix)
        }
    return notes
        .mapNotNull {
            when (val ev = it.event) {
                is SoftwareReleaseEvent -> ev
                null -> null
                else -> if (ev.isNip82SoftwareRelease()) ev.asSoftwareRelease() else null
            }
        }.sortedByDescending { it.createdAt }
}

@Composable
fun AppIcon(
    icon: String?,
    name: String,
    sizeDp: Int = 56,
) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape((sizeDp / 4).dp))
            .border(1.dp, MaterialTheme.colorScheme.subtleBorder, RoundedCornerShape((sizeDp / 4).dp)),
    ) {
        icon?.let {
            AsyncImage(
                model = it,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(sizeDp.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlatformLicenseRow(
    platforms: List<String>,
    license: String?,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        platforms.forEach { Chip(it) }
        license?.let { Chip(it, tint = MaterialTheme.colorScheme.secondaryContainer) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicChipFlow(
    topics: List<String>,
    nav: INav,
) {
    if (topics.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        topics.forEach { tag ->
            Chip(
                text = "#$tag",
                modifier = Modifier.clickable { nav.nav(Route.Hashtag(tag.lowercase())) },
            )
        }
    }
}

@Composable
fun ScreenshotsStrip(
    images: List<String>,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    if (images.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = contentPadding,
        modifier = Modifier.height(180.dp),
    ) {
        items(images) { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .height(180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.subtleBorder, RoundedCornerShape(8.dp)),
            )
        }
    }
}

@Composable
fun AppLinksColumn(
    website: String?,
    repository: String?,
) {
    if (website == null && repository == null) return
    val uri = LocalUriHandler.current
    Column {
        website?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinkIcon(Size16Modifier, MaterialTheme.colorScheme.placeholderText)
                ClickableTextPrimary(
                    text = it.removePrefix("https://").removePrefix("http://"),
                    onClick = { runCatching { uri.openUri(it) } },
                    modifier = Modifier.padding(start = 5.dp),
                )
            }
        }
        repository?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinkIcon(Size16Modifier, MaterialTheme.colorScheme.placeholderText)
                ClickableTextPrimary(
                    text = stringRes(R.string.nip82_repository_label, it.removePrefix("https://").removePrefix("http://")),
                    onClick = { runCatching { uri.openUri(it) } },
                    modifier = Modifier.padding(start = 5.dp),
                )
            }
        }
    }
}

@Composable
fun VersionChip(version: String) {
    Chip(
        text = stringRes(R.string.nip82_version_label, version),
        tint = MaterialTheme.colorScheme.primaryContainer,
    )
}

@Composable
fun Chip(
    text: String,
    tint: Color = MaterialTheme.colorScheme.surfaceVariant,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tint)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text = text, fontSize = 11.sp, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * NIP-82 kind 30063 — Software Release card. Renders the version and channel as
 * a header, the aggregated platforms as chips, the release notes (markdown), and
 * a count of bundled assets.
 */
@Composable
fun RenderSoftwareRelease(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // kind 30063 is shared between NIP-51 ReleaseArtifactSetEvent and NIP-82 SoftwareReleaseEvent;
    // EventFactory always returns the NIP-51 class, so we re-parse the same tag array as the
    // NIP-82 form when the tag signature matches.
    val rawEvent = note.event ?: return
    val event: SoftwareReleaseEvent =
        if (rawEvent is SoftwareReleaseEvent) {
            rawEvent
        } else if (rawEvent.isNip82SoftwareRelease()) {
            rawEvent.asSoftwareRelease()
        } else {
            return
        }

    RenderSoftwareReleaseBody(event = event, accountViewModel = accountViewModel, nav = nav)
}

@Composable
fun RenderSoftwareReleaseBody(
    event: SoftwareReleaseEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
    showAppId: Boolean = true,
) {
    val appId = remember(event) { event.appId() }
    val version = remember(event) { event.version() }
    val channel = remember(event) { event.channel() }
    val assets = remember(event) { event.assets() }
    val notes = event.content

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
                if (showAppId) {
                    appId?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                version?.let {
                    Text(
                        text = stringRes(R.string.nip82_version_label, it),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            channel?.let {
                Chip(it.uppercase(), tint = MaterialTheme.colorScheme.tertiaryContainer)
            }
        }

        if (notes.isNotBlank()) {
            Spacer(StdVertSpacer)
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (assets.isNotEmpty()) {
            Spacer(StdVertSpacer)
            val n = assets.size
            Text(
                text = pluralStringResource(R.plurals.nip82_assets_count, n, n),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.placeholderText,
            )
            Spacer(Modifier.height(6.dp))
            BundledAssetsList(
                assetIds = assets.map { it.eventId },
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

@Composable
private fun BundledAssetsList(
    assetIds: List<String>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        assetIds.forEach { id ->
            key(id) {
                LoadAssetNote(id, accountViewModel) { assetNote ->
                    if (assetNote != null) {
                        SoftwareAssetRow(assetNote, accountViewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadAssetNote(
    eventId: String,
    accountViewModel: AccountViewModel,
    content: @Composable (Note?) -> Unit,
) {
    val note by produceState<Note?>(initialValue = accountViewModel.getNoteIfExists(eventId), eventId) {
        if (value == null) {
            value = accountViewModel.checkGetOrCreateNote(eventId)
        }
    }
    content(note)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoftwareAssetRow(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    // Subscribe so a missing asset event is fetched from the relay and we recompose when it arrives.
    val eventState = observeNoteEvent<SoftwareAssetEvent>(note, accountViewModel)
    val event = eventState.value ?: return

    val uri = LocalUriHandler.current
    val version = remember(event) { event.version() }
    val mimeType = remember(event) { event.mimeType() }
    val sizeBytes = remember(event) { event.sizeInBytes() }
    val platforms = remember(event) { event.platforms() }
    val downloadUrl = remember(event) { event.url() }
    val variant = remember(event) { event.variant() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                mimeType?.let {
                    Text(
                        text = prettyMime(it),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                version?.let {
                    Text(
                        text = stringRes(R.string.nip82_version_label, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                }
                variant?.let {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "· $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                }
                sizeBytes?.let {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "· ${formatBytes(it.toLong())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                }
            }
            if (platforms.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    platforms.forEach { Chip(it) }
                }
            }
        }
        downloadUrl?.let {
            Spacer(Modifier.width(8.dp))
            ClickableTextPrimary(
                text = stringRes(R.string.nip82_download),
                onClick = { runCatching { uri.openUri(it) } },
            )
        }
    }
}

/**
 * NIP-82 kind 3063 — Software Asset card. A compact descriptor of a single
 * install artifact: MIME type, version, size, platforms, and a download link.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderSoftwareAsset(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event as? SoftwareAssetEvent ?: return

    val uri = LocalUriHandler.current
    val appId = remember(event) { event.appId() }
    val version = remember(event) { event.version() }
    val mimeType = remember(event) { event.mimeType() }
    val sizeBytes = remember(event) { event.sizeInBytes() }
    val platforms = remember(event) { event.platforms() }
    val downloadUrl = remember(event) { event.url() }

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
                appId?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    version?.let {
                        Text(
                            text = stringRes(R.string.nip82_version_label, it),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    sizeBytes?.let {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "· ${formatBytes(it.toLong())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                        )
                    }
                }
            }
            downloadUrl?.let {
                ClickableTextPrimary(
                    text = stringRes(R.string.nip82_download),
                    onClick = { runCatching { uri.openUri(it) } },
                )
            }
        }

        if (mimeType != null || platforms.isNotEmpty()) {
            Spacer(StdVertSpacer)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                mimeType?.let { Chip(prettyMime(it)) }
                platforms.forEach { Chip(it) }
            }
        }
    }
}

internal fun prettyMime(mime: String): String =
    when (mime) {
        "application/vnd.android.package-archive" -> "APK"
        "application/vnd.apple.ipa" -> "IPA"
        "application/x-apple-diskimage" -> "DMG"
        "application/vnd.apple.installer+xml" -> "PKG"
        "application/x-msi" -> "MSI"
        "application/vnd.appimage" -> "AppImage"
        "application/vnd.flatpak" -> "Flatpak"
        "application/vnd.oci.image.manifest.v1+json" -> "OCI"
        "application/x-executable" -> "ELF"
        "application/x-mach-binary" -> "Mach-O"
        "application/vnd.microsoft.portable-executable" -> "EXE"
        "application/vsix" -> "VSIX"
        "application/x-chrome-extension" -> "CRX"
        "application/x-xpinstall" -> "XPI"
        "application/wasm" -> "WASM"
        "application/webbundle" -> "Web Bundle"
        else -> mime
    }

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024
    return "%.2f GB".format(gb)
}
