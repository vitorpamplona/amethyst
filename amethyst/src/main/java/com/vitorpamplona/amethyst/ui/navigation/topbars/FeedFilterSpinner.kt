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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.IAccountViewModel
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.TopFilter
import com.vitorpamplona.amethyst.service.call.CallSessionBridge.accountViewModel
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.ui.components.LoadingAnimation
import com.vitorpamplona.amethyst.ui.note.creators.location.LoadCityName
import com.vitorpamplona.amethyst.ui.screen.CommunityName
import com.vitorpamplona.amethyst.ui.screen.FeedDefinition
import com.vitorpamplona.amethyst.ui.screen.GeoHashName
import com.vitorpamplona.amethyst.ui.screen.HashtagName
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
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun FeedFilterSpinner(
    placeholderCode: TopFilter,
    explainer: String,
    options: ImmutableList<FeedDefinition>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accountViewModel: IAccountViewModel,
) {
    @Suppress("NAME_SHADOWING")
    val accountViewModel = accountViewModel as AccountViewModel
    var optionsShowing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val selectAnOption =
        stringRes(
            id = R.string.select_an_option,
        )

    var selected by
        remember(placeholderCode, options) {
            mutableStateOf(
                options.firstOrNull { it.code.code == placeholderCode.code },
            )
        }

    val currentText by
        remember(placeholderCode, options) {
            derivedStateOf {
                selected?.name?.name(context) ?: selectAnOption
            }
        }

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

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val filter = selected?.code
                if (filter is TopFilter.Geohash) {
                    LoadCityName(
                        geohashStr = filter.tag,
                        onLoading = {
                            Row {
                                Text(filter.tag)
                                Spacer(modifier = StdHorzSpacer)
                                LoadingAnimation(indicatorSize = 12.dp, circleWidth = 2.dp)
                            }
                        },
                    ) { cityName ->
                        Text(cityName)
                    }
                } else {
                    Text(currentText)
                }

                if (filter is TopFilter.AroundMe) {
                    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (!locationPermissionState.status.isGranted) {
                        LaunchedEffect(locationPermissionState) { locationPermissionState.launchPermissionRequest() }

                        Text(
                            text = stringRes(R.string.lack_location_permissions),
                            fontSize = Font12SP,
                            lineHeight = 12.sp,
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
                                    )
                                }
                            }

                            LocationState.LocationResult.LackPermission -> {
                                Text(
                                    text = stringRes(R.string.lack_location_permissions),
                                    fontSize = Font12SP,
                                    lineHeight = 12.sp,
                                )
                            }

                            LocationState.LocationResult.Loading -> {
                                Text(
                                    text = stringRes(R.string.loading_location),
                                    fontSize = Font12SP,
                                    lineHeight = 12.sp,
                                )
                            }
                        }
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.ExpandMore,
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
            onSelect = {
                selected = options[it]
                optionsShowing = false
                onSelect(it)
            },
        ) {
            RenderOption(it.name, accountViewModel)
        }
    }
}

@Composable
fun RenderOption(
    option: Name,
    accountViewModel: IAccountViewModel,
) {
    @Suppress("NAME_SHADOWING")
    val accountViewModel = accountViewModel as AccountViewModel
    when (option) {
        is GeoHashName -> {
            LoadCityName(option.geoHashTag) {
                Text(text = "/g/$it", fontSize = Font14SP, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        is HashtagName -> {
            Text(text = option.name(), fontSize = Font14SP, color = MaterialTheme.colorScheme.onSurface)
        }

        is ResourceName -> {
            Text(
                text = stringRes(id = option.resourceId),
                fontSize = Font14SP,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        is PeopleListName -> {
            val noteState by observeNote(option.note, accountViewModel)

            val noteEvent = noteState.note.event
            val name =
                when (noteEvent) {
                    is PeopleListEvent -> {
                        noteEvent.titleOrName() ?: option.note.dTag()
                    }

                    is FollowListEvent -> {
                        noteEvent.title() ?: option.note.dTag()
                    }

                    else -> {
                        option.note.dTag()
                    }
                }

            Text(text = name, fontSize = Font14SP, color = MaterialTheme.colorScheme.onSurface)
        }

        is CommunityName -> {
            val it by observeNote(option.note, accountViewModel)

            Text(text = "/n/${((it.note as? AddressableNote)?.dTag() ?: "")}", fontSize = Font14SP, color = MaterialTheme.colorScheme.onSurface)
        }

        is RelayName -> {
            Text(
                text = option.name(),
                fontSize = Font14SP,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Immutable
private data class IndexedFeedDefinition(
    val originalIndex: Int,
    val item: FeedDefinition,
)

private enum class FeedGroup(
    @param:androidx.annotation.StringRes val labelRes: Int,
) {
    FEEDS(R.string.feed_group_feeds),
    HASHTAGS(R.string.feed_group_hashtags),
    COMMUNITIES(R.string.feed_group_communities),
    LOCATIONS(R.string.feed_group_locations),
    LISTS(R.string.feed_group_lists),
    RELAYS(R.string.feed_group_relays),
}

private fun groupFeedDefinitions(options: ImmutableList<FeedDefinition>): Map<FeedGroup, List<IndexedFeedDefinition>> {
    val indexed = options.mapIndexed { index, item -> IndexedFeedDefinition(index, item) }
    return indexed.groupBy { entry ->
        when (entry.item.name) {
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

            is ResourceName -> {
                when (entry.item.code) {
                    is TopFilter.AroundMe -> FeedGroup.LOCATIONS
                    is TopFilter.Global -> FeedGroup.RELAYS
                    else -> FeedGroup.FEEDS
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupedFeedFilterDialog(
    title: String,
    options: ImmutableList<FeedDefinition>,
    onSelect: (Int) -> Unit,
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

                FeedGroup.entries.forEach { group ->
                    val items = grouped[group]
                    if (!items.isNullOrEmpty()) {
                        item {
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupSection(
    label: String,
    items: List<IndexedFeedDefinition>,
    isChipLayout: Boolean,
    onSelect: (Int) -> Unit,
    onRenderItem: @Composable (FeedDefinition) -> Unit,
) {
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
                            modifier = Modifier.clickable { onSelect(entry.originalIndex) },
                            shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            color = Color.Transparent,
                        ) {
                            Text(
                                text = entry.item.name.name(),
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
                                .clickable { onSelect(entry.originalIndex) }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        FeedIcon(
                            item = entry.item,
                            modifier = Size20Modifier,
                        )
                        Spacer(modifier = Modifier.padding(start = 12.dp))
                        Column(modifier = Modifier.weight(1f)) { onRenderItem(entry.item) }
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
                Icons.Outlined.Public
            }

            is TopFilter.AroundMe -> {
                Icons.Outlined.LocationOn
            }

            is TopFilter.AllFollows -> {
                Icons.Outlined.Groups
            }

            is TopFilter.AllUserFollows -> {
                Icons.Outlined.Person
            }

            is TopFilter.DefaultFollows -> {
                Icons.Outlined.Groups
            }

            is TopFilter.MuteList -> {
                Icons.AutoMirrored.Outlined.VolumeOff
            }

            is TopFilter.Chess -> {
                Icons.Outlined.Groups
            }

            is TopFilter.PeopleList -> {
                Icons.AutoMirrored.Outlined.ViewList
            }

            else -> {
                when (item.name) {
                    is GeoHashName -> Icons.Outlined.LocationOn
                    is RelayName -> Icons.Outlined.Storage
                    is CommunityName -> Icons.Outlined.Groups
                    is PeopleListName -> Icons.AutoMirrored.Outlined.ViewList
                    else -> Icons.Outlined.Person
                }
            }
        }
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
