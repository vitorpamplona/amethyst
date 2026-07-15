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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.rooms

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.grayText

/**
 * One selectable conversation type on the [NewConversationScreen] chooser: an icon, a name, a
 * one-line tagline, a "best for" hint, and short pros/cons lists. Tapping the card routes to that
 * type's existing creation (or browse) flow.
 */
private class ConversationType(
    val icon: MaterialSymbol,
    @StringRes val title: Int,
    @StringRes val tagline: Int,
    @StringRes val bestFor: Int,
    val pros: List<Int>,
    val cons: List<Int>,
    val route: Route,
)

private class ConversationSection(
    @StringRes val header: Int,
    val types: List<ConversationType>,
)

// Grouped by intent so the six protocols read as three simple buckets instead of a flat wall of
// options: talk privately, run an encrypted group, or open something to the public.
private val conversationSections =
    listOf(
        ConversationSection(
            header = R.string.new_conversation_section_direct,
            types =
                listOf(
                    ConversationType(
                        icon = MaterialSymbols.Mail,
                        title = R.string.new_conversation_dm_title,
                        tagline = R.string.new_conversation_dm_tagline,
                        bestFor = R.string.new_conversation_dm_best,
                        pros =
                            listOf(
                                R.string.new_conversation_dm_pro_1,
                                R.string.new_conversation_dm_pro_2,
                                R.string.new_conversation_dm_pro_3,
                            ),
                        cons =
                            listOf(
                                R.string.new_conversation_dm_con_1,
                                R.string.new_conversation_dm_con_2,
                            ),
                        route = Route.NewGroupDM(),
                    ),
                ),
        ),
        ConversationSection(
            header = R.string.new_conversation_section_encrypted,
            types =
                listOf(
                    ConversationType(
                        icon = MaterialSymbols.Lock,
                        title = R.string.new_conversation_marmot_title,
                        tagline = R.string.new_conversation_marmot_tagline,
                        bestFor = R.string.new_conversation_marmot_best,
                        pros =
                            listOf(
                                R.string.new_conversation_marmot_pro_1,
                                R.string.new_conversation_marmot_pro_2,
                                R.string.new_conversation_marmot_pro_3,
                            ),
                        cons =
                            listOf(
                                R.string.new_conversation_marmot_con_1,
                                R.string.new_conversation_marmot_con_2,
                            ),
                        route = Route.CreateMarmotGroup,
                    ),
                    ConversationType(
                        icon = MaterialSymbols.Groups,
                        title = R.string.new_conversation_concord_title,
                        tagline = R.string.new_conversation_concord_tagline,
                        bestFor = R.string.new_conversation_concord_best,
                        pros =
                            listOf(
                                R.string.new_conversation_concord_pro_1,
                                R.string.new_conversation_concord_pro_2,
                                R.string.new_conversation_concord_pro_3,
                            ),
                        cons =
                            listOf(
                                R.string.new_conversation_concord_con_1,
                                R.string.new_conversation_concord_con_2,
                            ),
                        route = Route.ConcordCreate,
                    ),
                ),
        ),
        ConversationSection(
            header = R.string.new_conversation_section_public,
            types =
                listOf(
                    ConversationType(
                        icon = MaterialSymbols.Public,
                        title = R.string.new_conversation_public_chat_title,
                        tagline = R.string.new_conversation_public_chat_tagline,
                        bestFor = R.string.new_conversation_public_chat_best,
                        pros =
                            listOf(
                                R.string.new_conversation_public_chat_pro_1,
                                R.string.new_conversation_public_chat_pro_2,
                            ),
                        cons =
                            listOf(
                                R.string.new_conversation_public_chat_con_1,
                                R.string.new_conversation_public_chat_con_2,
                            ),
                        route = Route.ChannelMetadataEdit(),
                    ),
                    ConversationType(
                        icon = MaterialSymbols.Dns,
                        title = R.string.new_conversation_relay_group_title,
                        tagline = R.string.new_conversation_relay_group_tagline,
                        bestFor = R.string.new_conversation_relay_group_best,
                        pros =
                            listOf(
                                R.string.new_conversation_relay_group_pro_1,
                                R.string.new_conversation_relay_group_pro_2,
                            ),
                        cons =
                            listOf(
                                R.string.new_conversation_relay_group_con_1,
                                R.string.new_conversation_relay_group_con_2,
                            ),
                        route = Route.RelayGroupBrowse,
                    ),
                    ConversationType(
                        icon = MaterialSymbols.Timer,
                        title = R.string.new_conversation_ephemeral_title,
                        tagline = R.string.new_conversation_ephemeral_tagline,
                        bestFor = R.string.new_conversation_ephemeral_best,
                        pros =
                            listOf(
                                R.string.new_conversation_ephemeral_pro_1,
                                R.string.new_conversation_ephemeral_pro_2,
                            ),
                        cons =
                            listOf(
                                R.string.new_conversation_ephemeral_con_1,
                                R.string.new_conversation_ephemeral_con_2,
                            ),
                        route = Route.NewEphemeralChat,
                    ),
                ),
        ),
    )

@Composable
fun NewConversationScreen(nav: INav) {
    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(R.string.new_conversation_title), nav)
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = pad.calculateTopPadding() + 4.dp,
                    bottom = pad.calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = stringRes(R.string.new_conversation_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.grayText,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }

            conversationSections.forEach { section ->
                item {
                    Text(
                        text = stringRes(section.header),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                }

                section.types.forEach { type ->
                    item(key = type.title) {
                        ConversationTypeCard(type) { nav.nav(type.route) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationTypeCard(
    type: ConversationType,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            symbol = type.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Spacer(Modifier.size(14.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringRes(type.title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringRes(type.tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.grayText,
                    )
                }

                Icon(
                    symbol = MaterialSymbols.AutoMirrored.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.grayText,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = DoubleVertSpacer)

            Row {
                Text(
                    text = "${stringRes(R.string.new_conversation_best_for)}: ",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringRes(type.bestFor),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }

            Spacer(modifier = StdVertSpacer)

            type.pros.forEach { ProConRow(it, isPro = true) }
            type.cons.forEach { ProConRow(it, isPro = false) }
        }
    }
}

@Composable
private fun ProConRow(
    @StringRes text: Int,
    isPro: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 1.dp),
    ) {
        Icon(
            symbol = if (isPro) MaterialSymbols.Check else MaterialSymbols.Close,
            contentDescription = null,
            tint = if (isPro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.grayText,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringRes(text),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Preview
@Composable
private fun NewConversationScreenPreview() {
    ThemeComparisonColumn {
        NewConversationScreen(nav = EmptyNav())
    }
}
