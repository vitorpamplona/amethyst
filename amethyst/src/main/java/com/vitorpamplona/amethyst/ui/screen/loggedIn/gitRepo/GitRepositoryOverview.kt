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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.gitRepo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.ui.layouts.LocalDisappearingScaffoldPadding
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Font12SP
import com.vitorpamplona.amethyst.ui.theme.Size16dp
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

private val SectionCardShape = RoundedCornerShape(15.dp)
private val SectionSpacing = Arrangement.spacedBy(12.dp)
private val LinkSpacing = Arrangement.spacedBy(8.dp)

@Composable
fun GitRepositoryOverview(
    event: GitRepositoryEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val scaffoldPadding = LocalDisappearingScaffoldPadding.current
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(scaffoldPadding)
                .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = SectionSpacing,
    ) {
        TitleHeader(event)

        val description = event.description()
        if (!description.isNullOrBlank()) {
            SectionCard(title = stringRes(id = R.string.git_repo_section_about)) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        val webs = event.webs()
        val clones = event.clones()
        if (webs.isNotEmpty() || clones.isNotEmpty()) {
            SectionCard(title = stringRes(id = R.string.git_repo_section_links)) {
                Column(verticalArrangement = LinkSpacing) {
                    webs.forEach { LinkLine(symbol = MaterialSymbols.Public, url = it) }
                    clones.forEach { LinkLine(symbol = MaterialSymbols.AutoMirrored.OpenInNew, url = it) }
                }
            }
        }

        val topics = event.hashtags()
        if (topics.isNotEmpty()) {
            SectionCard(title = stringRes(id = R.string.git_repo_section_topics)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    topics.forEach { TopicChip(it) }
                }
            }
        }

        val maintainers = listOfNotNull(event.pubKey).plus(event.maintainers()).distinct()
        if (maintainers.isNotEmpty()) {
            SectionCard(title = stringRes(id = R.string.git_repo_section_maintainers)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    maintainers.forEach { hex ->
                        MaintainerRow(pubKeyHex = hex, accountViewModel = accountViewModel, nav = nav)
                    }
                }
            }
        }
    }
}

@Composable
private fun TitleHeader(event: GitRepositoryEvent) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                symbol = MaterialSymbols.Code,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = event.name() ?: event.dTag(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (event.isPersonalFork()) {
                Spacer(Modifier.height(4.dp))
                TopicChip(stringRes(id = R.string.git_repo_personal_fork))
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(SectionCardShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.grayText,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun LinkLine(
    symbol: MaterialSymbol,
    url: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            modifier = Modifier.size(Size16dp),
            tint = MaterialTheme.colorScheme.grayText,
        )
        ClickableUrl(
            url = url,
            urlText = url.removePrefix("https://").removePrefix("http://"),
        )
    }
}

@Composable
private fun TopicChip(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = Font12SP),
        color = MaterialTheme.colorScheme.grayText,
        modifier =
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun MaintainerRow(
    pubKeyHex: HexKey,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val user = LocalCache.checkGetOrCreateUser(pubKeyHex) ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ClickableUserPicture(
            baseUser = user,
            size = 32.dp,
            accountViewModel = accountViewModel,
            onClick = { nav.nav(Route.Profile(it.pubkeyHex)) },
        )
        UsernameDisplay(
            baseUser = user,
            accountViewModel = accountViewModel,
        )
    }
}
