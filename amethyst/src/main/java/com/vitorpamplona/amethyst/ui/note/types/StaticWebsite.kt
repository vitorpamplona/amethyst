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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.viewmodels.IAccountViewModel
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import com.vitorpamplona.quartz.nip5aStaticWebsites.NamedSiteEvent
import com.vitorpamplona.quartz.nip5aStaticWebsites.RootSiteEvent

@Composable
fun RenderRootSiteEvent(
    baseNote: Note,
    accountViewModel: IAccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? RootSiteEvent ?: return

    RenderStaticWebsite(
        title = event.title(),
        description = event.description(),
        source = event.source(),
        servers = event.servers(),
        identifier = null,
    )
}

@Composable
fun RenderNamedSiteEvent(
    baseNote: Note,
    accountViewModel: IAccountViewModel,
    nav: INav,
) {
    val event = baseNote.event as? NamedSiteEvent ?: return

    RenderStaticWebsite(
        title = event.title(),
        description = event.description(),
        source = event.source(),
        servers = event.servers(),
        identifier = event.identifier(),
    )
}

@Composable
private fun RenderStaticWebsite(
    title: String?,
    description: String?,
    source: String?,
    servers: List<String>,
    identifier: String?,
) {
    Row(
        modifier =
            Modifier
                .clip(shape = QuoteBorder)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.subtleBorder,
                    QuoteBorder,
                ).padding(Size10dp),
    ) {
        Column {
            val displayTitle =
                title
                    ?: identifier
                    ?: stringRes(id = R.string.nsite_root_site)

            Text(
                text = stringRes(id = R.string.nsite_title, displayTitle),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            description?.let {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Size5dp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            HorizontalDivider(thickness = DividerThickness)

            source?.let {
                Row(Modifier.fillMaxWidth().padding(top = Size5dp)) {
                    Text(
                        text = stringRes(id = R.string.nsite_source),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = StdHorzSpacer)
                    ClickableUrl(
                        url = it,
                        urlText = it.removePrefix("https://").removePrefix("http://"),
                    )
                }
            }

            if (servers.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = Size5dp)) {
                    Text(
                        text = stringRes(id = R.string.nsite_servers),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = StdHorzSpacer)
                    Column {
                        servers.forEach { server ->
                            ClickableUrl(
                                url = server,
                                urlText = server.removePrefix("https://").removePrefix("http://"),
                            )
                        }
                    }
                }
            }
        }
    }
}
