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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.model.MediaAspectRatioCache
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nip82_by_author
import com.vitorpamplona.amethyst.commons.resources.nip82_download
import com.vitorpamplona.amethyst.commons.resources.nip82_repository_label
import com.vitorpamplona.amethyst.commons.resources.nip82_version_label
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.softwareapps.SoftwareAssetDownloads
import com.vitorpamplona.amethyst.commons.softwareapps.SoftwareReleases
import com.vitorpamplona.amethyst.commons.ui.components.ClickableTextPrimary
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LinkIcon
import com.vitorpamplona.amethyst.commons.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.commons.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.ReactionsRow
import com.vitorpamplona.amethyst.commons.ui.note.elements.MoreOptionsButton
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.Size20dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.util.DecimalPatternFormatter
import com.vitorpamplona.amethyst.commons.util.prettyMime
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.application.SoftwareApplicationEvent
import com.vitorpamplona.quartz.experimental.nip82SoftwareApps.asset.SoftwareAssetEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import kotlinx.collections.immutable.toImmutableList

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
    val images = remember(event) { event.images() }

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
                AppAuthorLine(note, accountViewModel, nav)
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

            Spacer(Modifier.width(4.dp))
            MoreOptionsButton(
                baseNote = note,
                editState = null,
                accountViewModel = accountViewModel,
                nav = nav,
            )
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

        if (images.isNotEmpty()) {
            Spacer(StdVertSpacer)
            ScreenshotsStrip(images, accountViewModel, imageHeight = 200.dp)
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

/** The version of [app]'s current release (see [SoftwareReleases.latest]), kept live. */
@Composable
fun produceLatestReleaseVersion(app: SoftwareApplicationEvent): State<String?> {
    val releases = produceNip82Releases(app)
    return remember(releases) { derivedStateOf { SoftwareReleases.latest(releases.value)?.version() } }
}

@Composable
fun AppIcon(
    icon: String?,
    name: String,
    sizeDp: Int = 56,
) {
    val shape = RoundedCornerShape((sizeDp / 4).dp)
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.subtleBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        // Fallback underneath the image: visible when there is no icon url,
        // while the icon downloads, and when the download fails (AsyncImage
        // draws nothing in those states).
        Text(
            text = (name.firstOrNull() ?: '?').uppercase(),
            fontSize = (sizeDp / 2).sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.grayText,
        )
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

/**
 * "by <author>" line with a small clickable profile picture. Shared between
 * the app feed card and the app detail screen header.
 */
@Composable
fun AppAuthorLine(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringRes(Res.string.nip82_by_author),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.grayText,
        )
        Spacer(Modifier.width(4.dp))
        NoteAuthorPicture(note, Size20dp, accountViewModel = accountViewModel, nav = nav)
        Spacer(Modifier.width(4.dp))
        NoteUsernameDisplay(note, Modifier.weight(1f, fill = false), accountViewModel = accountViewModel)
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
    accountViewModel: AccountViewModel,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    imageHeight: Dp = 180.dp,
) {
    if (images.isEmpty()) return

    val mediaContents =
        remember(images) {
            images.map { MediaUrlImage(url = it) }.toImmutableList()
        }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = contentPadding,
        modifier = Modifier.height(imageHeight),
    ) {
        items(mediaContents) { content ->
            // Fixed-height tile whose width follows the image's aspect ratio
            // once it is known; assumes a portrait phone screenshot before the
            // first load fills the ratio cache.
            val ratio = MediaAspectRatioCache.get(content.url) ?: (9f / 16f)
            Box(
                Modifier
                    .height(imageHeight)
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.subtleBorder, RoundedCornerShape(8.dp)),
            ) {
                ZoomableContentView(
                    content = content,
                    images = mediaContents,
                    roundedCorner = false,
                    contentScale = ContentScale.Crop,
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}

/**
 * Website and source links. A `repository` that is not a web URL (Armada's
 * `nostr://npub…/relay/repo`, which only `git-remote-nostr` can clone) opens the app's NIP-34
 * repository ([gitRepository], its `a 30617` pointer) inside the app when it has one.
 */
@Composable
fun AppLinksColumn(
    website: String?,
    repository: String?,
    gitRepository: Address? = null,
    nav: INav? = null,
) {
    val webRepository = repository?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    val inAppRepository = gitRepository?.takeIf { webRepository == null && nav != null }
    val externalRepository = repository.takeIf { inAppRepository == null }
    if (website == null && externalRepository == null && inAppRepository == null) return

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
        if (inAppRepository != null && nav != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinkIcon(Size16Modifier, MaterialTheme.colorScheme.placeholderText)
                ClickableTextPrimary(
                    text = stringRes(Res.string.nip82_repository_label, inAppRepository.dTag),
                    onClick = { nav.nav(Route.GitRepository(inAppRepository)) },
                    modifier = Modifier.padding(start = 5.dp),
                )
            }
        } else {
            externalRepository?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinkIcon(Size16Modifier, MaterialTheme.colorScheme.placeholderText)
                    ClickableTextPrimary(
                        text = stringRes(Res.string.nip82_repository_label, it.removePrefix("https://").removePrefix("http://")),
                        onClick = { runCatching { uri.openUri(it) } },
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun VersionChip(version: String) {
    Chip(
        text = stringRes(Res.string.nip82_version_label, version),
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
    val downloadUrl = remember(event) { SoftwareAssetDownloads.url(event) }

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
                            text = stringRes(Res.string.nip82_version_label, it),
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
                    text = stringRes(Res.string.nip82_download),
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

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${DecimalPatternFormatter("0.0").format(kb)} KB"
    val mb = kb / 1024
    if (mb < 1024) return "${DecimalPatternFormatter("0.0").format(mb)} MB"
    val gb = mb / 1024
    return "${DecimalPatternFormatter("0.00").format(gb)} GB"
}
