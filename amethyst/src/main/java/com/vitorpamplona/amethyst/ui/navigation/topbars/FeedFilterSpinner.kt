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
package com.vitorpamplona.amethyst.ui.navigation.topbars

import android.Manifest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.model.TopFilter
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.ui.components.LoadingAnimation
import com.vitorpamplona.amethyst.ui.note.creators.location.LoadCityName
import com.vitorpamplona.amethyst.ui.screen.CommunityName
import com.vitorpamplona.amethyst.ui.screen.FavoriteAlgoFeedName
import com.vitorpamplona.amethyst.ui.screen.FeedDefinition
import com.vitorpamplona.amethyst.ui.screen.GeoHashName
import com.vitorpamplona.amethyst.ui.screen.HashtagName
import com.vitorpamplona.amethyst.ui.screen.InterestSetName
import com.vitorpamplona.amethyst.ui.screen.Name
import com.vitorpamplona.amethyst.ui.screen.PeopleListName
import com.vitorpamplona.amethyst.ui.screen.RelayName
import com.vitorpamplona.amethyst.ui.screen.ResourceName
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Font12SP
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun FeedFilterSpinner(
    placeholderCode: TopFilter,
    explainer: String,
    options: ImmutableList<FeedDefinition>,
    onSelect: (FeedDefinition) -> Unit,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
) {
    var optionsShowing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val selectAnOption = stringRes(id = R.string.select_an_option)

    val selected =
        remember(placeholderCode, options) {
            // Match by both subclass and code string to avoid collisions between
            // TopFilter variants that derive `code` from the same Address (e.g.
            // PeopleList vs MuteList).
            options.firstOrNull {
                it.code::class == placeholderCode::class && it.code.code == placeholderCode.code
            }
        }

    val currentText = selected?.name?.name(context) ?: selectAnOption

    val accessibilityDescription =
        if (selected != null) {
            stringRes(R.string.feed_filter_selected, currentText)
        } else {
            stringRes(R.string.feed_filter_select_an_option, selectAnOption)
        }

    val openDropdownLabel = stringRes(R.string.open_dropdown_menu)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Size20Modifier)

            // Bound the Column so long filter names (e.g. DVM titles) get truncated
            // instead of wrapping to multiple lines and shoving the expand icon out.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f, fill = false),
            ) {
                val filter = selected?.code
                if (filter is TopFilter.Geohash) {
                    LoadCityName(
                        geohashStr = filter.tag,
                        onLoading = {
                            Row {
                                Text(
                                    text = filter.tag,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = StdHorzSpacer)
                                LoadingAnimation(indicatorSize = 12.dp, circleWidth = 2.dp)
                            }
                        },
                    ) { cityName ->
                        Text(
                            text = cityName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Text(
                        text = currentText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (filter is TopFilter.AroundMe) {
                    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (!locationPermissionState.status.isGranted) {
                        LaunchedEffect(locationPermissionState) { locationPermissionState.launchPermissionRequest() }

                        Text(
                            text = stringRes(R.string.lack_location_permissions),
                            fontSize = Font12SP,
                            lineHeight = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        val location by Amethyst.instance.locationManager.geohashStateFlow
                            .collectAsStateWithLifecycle()

                        when (val myLocation = location) {
                            is LocationState.LocationResult.Success -> {
                                LoadCityName(
                                    geohashStr = myLocation.geoHash.toString(),
                                    onLoading = {
                                        Row {
                                            Text(
                                                text = "(${myLocation.geoHash})",
                                                fontSize = Font12SP,
                                                lineHeight = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Spacer(modifier = StdHorzSpacer)
                                            LoadingAnimation(indicatorSize = 12.dp, circleWidth = 2.dp)
                                        }
                                    },
                                ) { cityName ->
                                    Text(
                                        text = "($cityName)",
                                        fontSize = Font12SP,
                                        lineHeight = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            LocationState.LocationResult.LackPermission -> {
                                Text(
                                    text = stringRes(R.string.lack_location_permissions),
                                    fontSize = Font12SP,
                                    lineHeight = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            LocationState.LocationResult.Loading -> {
                                Text(
                                    text = stringRes(R.string.loading_location),
                                    fontSize = Font12SP,
                                    lineHeight = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Icon(
                symbol = MaterialSymbols.ExpandMore,
                contentDescription = explainer,
                modifier = Size20Modifier,
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        }
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        optionsShowing = true
                    }.semantics {
                        role = Role.DropdownList
                        stateDescription = accessibilityDescription
                        onClick(label = openDropdownLabel) {
                            optionsShowing = true
                            return@onClick true
                        }
                    },
        )
    }

    if (optionsShowing && options.isNotEmpty()) {
        GroupedFeedFilterDialog(
            title = explainer,
            options = options,
            onDismiss = { optionsShowing = false },
            onSelect = { definition ->
                optionsShowing = false
                onSelect(definition)
            },
        ) {
            RenderOption(it.name, accountViewModel)
        }
    }
}

@Composable
fun RenderOption(
    option: Name,
    accountViewModel: AccountViewModel,
) {
    val context = LocalContext.current
    when (option) {
        is GeoHashName -> {
            LoadCityName(option.geoHashTag) {
                Text(text = "/g/$it", fontSize = Font14SP, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        // Note-backed names: subscribe to the note so the displayed title updates as
        // the corresponding event arrives from relays. The displayed string itself is
        // produced by Name.name(), which already has the right precedence rules.
        is PeopleListName -> {
            val noteState by observeNote(option.note, accountViewModel)
            val name = remember(noteState) { option.name(context) }
            Text(text = name, fontSize = Font14SP, color = MaterialTheme.colorScheme.onSurface)
        }

        is CommunityName -> {
            val noteState by observeNote(option.note, accountViewModel)
            val name = remember(noteState) { option.name(context) }
            Text(text = name, fontSize = Font14SP, color = MaterialTheme.colorScheme.onSurface)
        }

        is FavoriteAlgoFeedName -> {
            val noteState by observeNote(option.note, accountViewModel)
            val name = remember(noteState) { option.name(context) }
            Text(text = name, fontSize = Font14SP, color = MaterialTheme.colorScheme.onSurface)
        }

        // Pure names: no relay subscription needed.
        is HashtagName,
        is ResourceName,
        is RelayName,
        is InterestSetName,
        -> {
            Text(
                text = option.name(context),
                fontSize = Font14SP,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private enum class FeedGroup(
    @param:androidx.annotation.StringRes val labelRes: Int,
) {
    FEEDS(R.string.feed_group_feeds),
    HASHTAGS(R.string.feed_group_hashtags),
    INTEREST_SETS(R.string.feed_group_interest_sets),
    COMMUNITIES(R.string.feed_group_communities),
    LOCATIONS(R.string.feed_group_locations),
    LISTS(R.string.feed_group_lists),
    DVMS(R.string.feed_group_dvms),
    RELAYS(R.string.feed_group_relays),
}

private fun FeedDefinition.group(): FeedGroup =
    when (name) {
        is HashtagName -> {
            FeedGroup.HASHTAGS
        }

        is CommunityName -> {
            FeedGroup.COMMUNITIES
        }

        is PeopleListName -> {
            FeedGroup.LISTS
        }

        is RelayName -> {
            FeedGroup.RELAYS
        }

        is GeoHashName -> {
            FeedGroup.LOCATIONS
        }

        is FavoriteAlgoFeedName -> {
            FeedGroup.DVMS
        }

        is InterestSetName -> {
            FeedGroup.INTEREST_SETS
        }

        is ResourceName -> {
            when (code) {
                is TopFilter.AroundMe -> FeedGroup.LOCATIONS
                is TopFilter.Global -> FeedGroup.RELAYS
                is TopFilter.AllFavoriteAlgoFeeds -> FeedGroup.DVMS
                else -> FeedGroup.FEEDS
            }
        }
    }

private fun groupFeedDefinitions(options: ImmutableList<FeedDefinition>): List<Pair<FeedGroup, List<FeedDefinition>>> {
    val grouped = options.groupBy { it.group() }
    return FeedGroup.entries.mapNotNull { group ->
        grouped[group]?.takeIf { it.isNotEmpty() }?.let { group to it }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupedFeedFilterDialog(
    title: String,
    options: ImmutableList<FeedDefinition>,
    onSelect: (FeedDefinition) -> Unit,
    onDismiss: () -> Unit,
    onRenderItem: @Composable (FeedDefinition) -> Unit,
) {
    val grouped = remember(options) { groupFeedDefinitions(options) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            LazyColumn(
                modifier = Modifier.padding(vertical = 20.dp),
            ) {
                item {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }

                grouped.forEach { (group, items) ->
                    item(key = group) {
                        GroupSection(
                            label = stringRes(group.labelRes),
                            items = items,
                            isChipLayout = group == FeedGroup.HASHTAGS,
                            onSelect = onSelect,
                            onRenderItem = onRenderItem,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupSection(
    label: String,
    items: List<FeedDefinition>,
    isChipLayout: Boolean,
    onSelect: (FeedDefinition) -> Unit,
    onRenderItem: @Composable (FeedDefinition) -> Unit,
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Text(
                text = label.uppercase(),
                fontSize = Font12SP,
                letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
            )

            if (isChipLayout) {
                FlowRow(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items.forEach { entry ->
                        Surface(
                            modifier = Modifier.clickable { onSelect(entry) },
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            color = Color.Transparent,
                        ) {
                            Text(
                                text = entry.name.name(context),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            } else {
                items.forEach { entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(entry) }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        FeedIcon(
                            item = entry,
                            modifier = Size20Modifier,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) { onRenderItem(entry) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedIcon(
    item: FeedDefinition,
    modifier: Modifier = Modifier,
) {
    val icon =
        when (item.code) {
            is TopFilter.Global -> {
                MaterialSymbols.Public
            }

            is TopFilter.AroundMe -> {
                MaterialSymbols.LocationOn
            }

            is TopFilter.AllFollows -> {
                MaterialSymbols.Groups
            }

            is TopFilter.AllUserFollows -> {
                MaterialSymbols.Person
            }

            is TopFilter.DefaultFollows -> {
                MaterialSymbols.Groups
            }

            is TopFilter.MuteList -> {
                MaterialSymbols.AutoMirrored.VolumeOff
            }

            is TopFilter.PeopleList -> {
                MaterialSymbols.AutoMirrored.ViewList
            }

            is TopFilter.FavoriteAlgoFeed -> {
                MaterialSymbols.AutoAwesome
            }

            is TopFilter.AllFavoriteAlgoFeeds -> {
                MaterialSymbols.AutoAwesome
            }

            else -> {
                when (item.name) {
                    is GeoHashName -> MaterialSymbols.LocationOn
                    is RelayName -> MaterialSymbols.Storage
                    is CommunityName -> MaterialSymbols.Groups
                    is PeopleListName -> MaterialSymbols.AutoMirrored.ViewList
                    is FavoriteAlgoFeedName -> MaterialSymbols.AutoAwesome
                    else -> MaterialSymbols.Person
                }
            }
        }
    Icon(
        symbol = icon,
        contentDescription = null,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
