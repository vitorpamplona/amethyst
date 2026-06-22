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

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.napplet_card_permissions
import com.vitorpamplona.amethyst.commons.resources.napplet_card_title
import com.vitorpamplona.amethyst.commons.resources.nsite_open
import com.vitorpamplona.amethyst.commons.resources.nsite_root_site
import com.vitorpamplona.amethyst.commons.resources.nsite_servers
import com.vitorpamplona.amethyst.commons.resources.nsite_source
import com.vitorpamplona.amethyst.commons.resources.nsite_title
import org.jetbrains.compose.resources.stringResource

// Card chrome inlined so the shared component carries no app-theme dependency (matches Amethyst's
// QuoteBorder / subtleBorder / spacing tokens).
private val CardShape = RoundedCornerShape(15.dp)
private val CardPadding = 10.dp
private val RowSpacing = 5.dp
private val DividerThickness = 0.25.dp

/**
 * The inert, host-agnostic preview card for a NIP-5A static site or NIP-5D napplet event. It only
 * displays manifest metadata (title, description, source, Blossom servers, declared permissions) and
 * an **Open** button — it never executes applet code. Launching runs in the platform host's sandbox,
 * supplied by the caller via [onOpen]; a null [onOpen] (no paths) hides the button.
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
    onOpen: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .clip(shape = CardShape)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    CardShape,
                ).padding(CardPadding),
    ) {
        Column {
            val displayTitle = title ?: identifier ?: stringResource(Res.string.nsite_root_site)
            val header = if (isNapplet) Res.string.napplet_card_title else Res.string.nsite_title

            Text(
                text = stringResource(header, displayTitle),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            description?.let {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth().padding(vertical = RowSpacing),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            HorizontalDivider(thickness = DividerThickness)

            source?.let {
                Row(Modifier.fillMaxWidth().padding(top = RowSpacing)) {
                    Text(
                        text = stringResource(Res.string.nsite_source),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(RowSpacing))
                    ClickableUrl(it)
                }
            }

            if (servers.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = RowSpacing)) {
                    Text(
                        text = stringResource(Res.string.nsite_servers),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(RowSpacing))
                    Column {
                        servers.forEach { server -> ClickableUrl(server) }
                    }
                }
            }

            if (requires.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = RowSpacing)) {
                    Text(
                        text = stringResource(Res.string.napplet_card_permissions),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(RowSpacing))
                    Text(
                        text = requires.joinToString(", "),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            onOpen?.let {
                Button(
                    onClick = it,
                    modifier = Modifier.fillMaxWidth().padding(top = RowSpacing),
                ) {
                    Text(stringResource(Res.string.nsite_open))
                }
            }
        }
    }
}

/** A primary-colored, single-line clickable URL that opens in the platform's default handler. */
@Composable
private fun ClickableUrl(url: String) {
    val uriHandler = LocalUriHandler.current
    Text(
        text = url.removePrefix("https://").removePrefix("http://"),
        color = MaterialTheme.colorScheme.primary,
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
