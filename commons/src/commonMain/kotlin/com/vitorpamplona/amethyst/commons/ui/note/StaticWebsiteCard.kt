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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.napplet_card_kind
import com.vitorpamplona.amethyst.commons.resources.napplet_card_permissions
import com.vitorpamplona.amethyst.commons.resources.nsite_open
import com.vitorpamplona.amethyst.commons.resources.nsite_root_site
import com.vitorpamplona.amethyst.commons.resources.nsite_servers
import com.vitorpamplona.amethyst.commons.resources.nsite_source
import com.vitorpamplona.amethyst.commons.resources.nsite_website_kind
import org.jetbrains.compose.resources.stringResource

private val CardShape = RoundedCornerShape(16.dp)
private val IconShape = RoundedCornerShape(14.dp)
private val CardPadding = 12.dp
private val IconSize = 56.dp

/**
 * The inert, host-agnostic preview card for a NIP-5A static site or NIP-5D napplet event, styled like
 * an app-store entry: square [icon] (with a colored monogram fallback), name, a type label, a short
 * description, and an **Open** button. The technical bits a typical user doesn't care about — the
 * declared capabilities, Blossom servers, and source URL — are tucked behind a "What it can access"
 * disclosure; capabilities are also re-confirmed at the consent prompt when the napplet actually uses
 * one. It never executes applet code; launching runs in the platform host's sandbox via [onOpen]
 * (a null [onOpen] — no paths — hides the button).
 *
 * Both the Android feed and a future desktop feed render this identical card so the two can't drift.
 */
@Composable
fun StaticWebsiteCard(
    title: String?,
    description: String?,
    source: String?,
    servers: List<String>,
    identifier: String?,
    isNapplet: Boolean,
    requires: List<String> = emptyList(),
    icon: String? = null,
    onOpen: (() -> Unit)? = null,
) {
    val displayTitle = title?.ifBlank { null } ?: identifier?.ifBlank { null } ?: stringResource(Res.string.nsite_root_site)
    val kindLabel = stringResource(if (isNapplet) Res.string.napplet_card_kind else Res.string.nsite_website_kind)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
                .padding(CardPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(icon, displayTitle)

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = kindLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            onOpen?.let {
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = it) { Text(stringResource(Res.string.nsite_open)) }
            }
        }

        description?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (requires.isNotEmpty() || !source.isNullOrBlank() || servers.isNotEmpty()) {
            DetailsDisclosure(requires = requires, source = source, servers = servers)
        }
    }
}

/** Square app icon: the manifest [iconUrl] when present, otherwise a colored monogram from [title]. */
@Composable
private fun AppIcon(
    iconUrl: String?,
    title: String,
) {
    if (!iconUrl.isNullOrBlank()) {
        AsyncImage(
            model = iconUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(IconSize).clip(IconShape),
        )
    } else {
        Box(
            modifier =
                Modifier
                    .size(IconSize)
                    .clip(IconShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title.trim().take(1).uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Tap-to-expand "What it can access": capability rows (icon + name) plus source/servers details. */
@Composable
private fun DetailsDisclosure(
    requires: List<String>,
    source: String?,
    servers: List<String>,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            MaterialSymbols.Shield,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(Res.string.napplet_card_permissions),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }

    AnimatedVisibility(visible = expanded) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            requires.forEach { capability ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        capabilitySymbol(capability),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = capability.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            source?.takeIf { it.isNotBlank() }?.let {
                LabeledUrl(stringResource(Res.string.nsite_source), it)
            }

            if (servers.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(Res.string.nsite_servers),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    servers.forEach { ClickableUrl(it) }
                }
            }
        }
    }
}

@Composable
private fun LabeledUrl(
    label: String,
    url: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        ClickableUrl(url)
    }
}

/** Best-effort capability → icon map for the disclosure (host-agnostic; unknown domains fall back). */
private fun capabilitySymbol(domain: String): MaterialSymbol =
    when (domain.lowercase()) {
        "identity" -> MaterialSymbols.AccountCircle
        "keys" -> MaterialSymbols.Key
        "relay" -> MaterialSymbols.Public
        "storage" -> MaterialSymbols.Storage
        "value" -> MaterialSymbols.Bolt
        "resource" -> MaterialSymbols.Language
        "upload" -> MaterialSymbols.Upload
        "shell" -> MaterialSymbols.Tune
        else -> MaterialSymbols.Lock
    }

/** A primary-colored, single-line clickable URL that opens in the platform's default handler. */
@Composable
private fun ClickableUrl(url: String) {
    val uriHandler = LocalUriHandler.current
    Text(
        text = url.removePrefix("https://").removePrefix("http://"),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier.clickable {
                runCatching {
                    uriHandler.openUri(if (url.contains("://")) url else "https://$url")
                }
            },
    )
}
