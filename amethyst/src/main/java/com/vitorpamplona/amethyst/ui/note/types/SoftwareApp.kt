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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteEvent
import com.vitorpamplona.amethyst.ui.components.ClickableTextPrimary
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.LinkIcon
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

/**
 * NIP-82 kind 32267 — Software Application card. Renders icon, name, summary,
 * a horizontal screenshot strip, hashtag/platform chips, and quick links.
 */
@Composable
fun RenderSoftwareApplication(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = note.event as? SoftwareApplicationEvent ?: return

    val uri = LocalUriHandler.current
    val icon = remember(event) { event.icon() }
    val name = remember(event) { event.name() ?: event.appId().orEmpty() }
    val summary = remember(event) { event.summary() }
    val images = remember(event) { event.images() }
    val topics = remember(event) { event.topics() }
    val platforms = remember(event) { event.platforms() }
    val website = remember(event) { event.url() }
    val repo = remember(event) { event.repository() }
    val license = remember(event) { event.license() }

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
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.subtleBorder, RoundedCornerShape(12.dp)),
            ) {
                icon?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }

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
                summary?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (images.isNotEmpty()) {
            Spacer(StdVertSpacer)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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

        if (platforms.isNotEmpty() || topics.isNotEmpty() || license != null) {
            Spacer(StdVertSpacer)
            ChipFlowRow(platforms = platforms, topics = topics, license = license)
        }

        if (website != null || repo != null) {
            Spacer(StdVertSpacer)
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
                repo?.let {
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
    }
}

@Composable
private fun ChipFlowRow(
    platforms: List<String>,
    topics: List<String>,
    license: String?,
) {
    // FlowRow is in compose foundation but we use a simple LazyRow to keep this compatible.
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(platforms) { Chip(it) }
        items(topics) { Chip("#$it") }
        license?.let { item { Chip(it, tint = MaterialTheme.colorScheme.secondaryContainer) } }
    }
}

@Composable
private fun Chip(
    text: String,
    tint: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Box(
        Modifier
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

    val appId = remember(event) { event.appId() }
    val version = remember(event) { event.version() }
    val channel = remember(event) { event.channel() }
    val assets = remember(event) { event.assets() }
    val notes = remember(event) { event.content }

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
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(platforms) { Chip(it) }
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                mimeType?.let { item { Chip(prettyMime(it)) } }
                items(platforms) { Chip(it) }
            }
        }
    }
}

private fun prettyMime(mime: String): String =
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

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024
    return "%.2f GB".format(gb)
}
