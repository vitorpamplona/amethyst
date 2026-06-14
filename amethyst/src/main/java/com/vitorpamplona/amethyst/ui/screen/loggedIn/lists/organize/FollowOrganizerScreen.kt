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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.organize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.followOrganizer.OrganizeStrategy
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.pluralStringRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.ui.theme.grayText

@Composable
fun FollowOrganizerScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val organizer: FollowOrganizerViewModel = viewModel()
    organizer.init(accountViewModel)

    FollowOrganizerScreen(organizer, accountViewModel, nav)
}

@Composable
fun FollowOrganizerScreen(
    organizer: FollowOrganizerViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val context = LocalContext.current

    val strategy by organizer.strategy.collectAsStateWithLifecycle()
    val groups by organizer.groups.collectAsStateWithLifecycle()
    val isAnalyzing by organizer.isAnalyzing.collectAsStateWithLifecycle()
    val isCreating by organizer.isCreating.collectAsStateWithLifecycle()
    val makePrivate by organizer.makePrivate.collectAsStateWithLifecycle()
    val totalFollows by organizer.totalFollows.collectAsStateWithLifecycle()
    val followsWithData by organizer.followsWithData.collectAsStateWithLifecycle()
    val isBackfilling by organizer.isBackfilling.collectAsStateWithLifecycle()
    val backfillDone by organizer.backfillDone.collectAsStateWithLifecycle()
    val backfillTotal by organizer.backfillTotal.collectAsStateWithLifecycle()

    val createdTitle = stringRes(R.string.follow_organizer_title)

    androidx.compose.runtime.LaunchedEffect(Unit) {
        organizer.analyze()
    }

    androidx.compose.runtime.LaunchedEffect(organizer) {
        organizer.created.collect { count ->
            accountViewModel.toastManager.toast(
                createdTitle,
                pluralStringRes(context, R.plurals.follow_organizer_created_n_lists, count, count),
            )
            nav.popBack()
        }
    }

    val selectedCount = groups.count { it.selected }

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(R.string.follow_organizer_title), nav)
        },
        bottomBar = {
            Button(
                onClick = {
                    accountViewModel.launchSigner { organizer.createSelected() }
                },
                enabled = selectedCount > 0 && !isCreating && !isAnalyzing,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
            ) {
                if (isCreating) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text(pluralStringResource(R.plurals.follow_organizer_create_n_lists, selectedCount, selectedCount))
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding(),
                ),
        ) {
            Text(
                text = stringRes(R.string.follow_organizer_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                horizontalArrangement = SpacedBy5dp,
            ) {
                StrategyChip(
                    label = stringRes(R.string.follow_organizer_by_activity),
                    selected = strategy == OrganizeStrategy.LAST_SEEN,
                    onClick = { organizer.setStrategy(OrganizeStrategy.LAST_SEEN) },
                    modifier = Modifier.weight(1f),
                )
                StrategyChip(
                    label = stringRes(R.string.follow_organizer_by_content),
                    selected = strategy == OrganizeStrategy.CONTENT_TYPE,
                    onClick = { organizer.setStrategy(OrganizeStrategy.CONTENT_TYPE) },
                    modifier = Modifier.weight(1f),
                )
                StrategyChip(
                    label = stringRes(R.string.follow_organizer_by_topic),
                    selected = strategy == OrganizeStrategy.TOPICS,
                    onClick = { organizer.setStrategy(OrganizeStrategy.TOPICS) },
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringRes(R.string.follow_organizer_keep_private),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = makePrivate,
                    onCheckedChange = { organizer.makePrivate.value = it },
                )
            }

            if (totalFollows > 0) {
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.follow_organizer_data_coverage,
                            totalFollows,
                            followsWithData,
                            totalFollows,
                        ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.grayText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (isBackfilling) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = SpacedBy5dp,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text(
                        text =
                            stringRes(
                                R.string.follow_organizer_backfilling,
                                backfillDone,
                                backfillTotal,
                            ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.grayText,
                    )
                }
            }

            HorizontalDivider()

            if (isAnalyzing) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringRes(R.string.follow_organizer_analyzing),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            } else if (groups.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringRes(R.string.follow_organizer_empty),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(groups, key = { it.title }) { group ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = group.selected,
                                onCheckedChange = { organizer.toggle(group.title) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 1,
                                )
                                Text(
                                    text =
                                        pluralStringResource(
                                            R.plurals.follow_organizer_member_count,
                                            group.memberCount,
                                            group.memberCount,
                                        ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.grayText,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StrategyChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
    )
}
