/**
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.service.location.LocationState
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.ui.components.LoadingAnimation
import com.vitorpamplona.amethyst.ui.components.SpinnerSelectionDialog
import com.vitorpamplona.amethyst.ui.note.creators.location.LoadCityName
import com.vitorpamplona.amethyst.ui.screen.AroundMeFeedDefinition
import com.vitorpamplona.amethyst.ui.screen.CommunityName
import com.vitorpamplona.amethyst.ui.screen.FeedDefinition
import com.vitorpamplona.amethyst.ui.screen.GeoHashName
import com.vitorpamplona.amethyst.ui.screen.HashtagName
import com.vitorpamplona.amethyst.ui.screen.Name
import com.vitorpamplona.amethyst.ui.screen.PeopleListName
import com.vitorpamplona.amethyst.ui.screen.ResourceName
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip51Lists.followList.FollowListEvent
import com.vitorpamplona.quartz.nip51Lists.peopleList.PeopleListEvent
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun FeedFilterSpinner(
    placeholderCode: String,
    explainer: String,
    options: ImmutableList<FeedDefinition>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accountViewModel: AccountViewModel,
) {
    var optionsShowing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val selectAnOption =
        stringRes(
            id = R.string.select_an_option,
        )

    var selected by
        remember(placeholderCode, options) {
            mutableStateOf(
                options.firstOrNull { it.code == placeholderCode },
            )
        }

    val currentText by
        remember(placeholderCode, options) {
            derivedStateOf {
                selected?.name?.name(context) ?: selectAnOption
            }
        }

    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_COARSE_LOCATION)

    LaunchedEffect(locationPermissionState.status.isGranted) {
        Amethyst.instance.locationManager.setLocationPermission(locationPermissionState.status.isGranted)
    }

    val accessibilityDescription =
        if (selected != null) {
            stringRes(R.string.feed_filter_selected, currentText)
        } else {
            stringRes(R.string.feed_filter_select_an_option, selectAnOption)
        }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Size20Modifier)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(currentText)

                if (selected is AroundMeFeedDefinition) {
                    if (!locationPermissionState.status.isGranted) {
                        LaunchedEffect(locationPermissionState) { locationPermissionState.launchPermissionRequest() }

                        Text(
                            text = stringRes(R.string.lack_location_permissions),
                            fontSize = 12.sp,
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
                                                fontSize = 12.sp,
                                                lineHeight = 12.sp,
                                            )
                                            Spacer(modifier = StdHorzSpacer)
                                            LoadingAnimation(indicatorSize = 12.dp, circleWidth = 2.dp)
                                        }
                                    },
                                ) { cityName ->
                                    Text(
                                        text = "($cityName)",
                                        fontSize = 12.sp,
                                        lineHeight = 12.sp,
                                    )
                                }
                            }

                            LocationState.LocationResult.LackPermission -> {
                                Text(
                                    text = stringRes(R.string.lack_location_permissions),
                                    fontSize = 12.sp,
                                    lineHeight = 12.sp,
                                )
                            }
                            LocationState.LocationResult.Loading -> {
                                Text(
                                    text = stringRes(R.string.loading_location),
                                    fontSize = 12.sp,
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
                        onClick(label = "Open feed filter menu") {
                            optionsShowing = true
                            return@onClick true
                        }
                    },
        )
    }

    if (optionsShowing) {
        options.isNotEmpty().also {
            SpinnerSelectionDialog(
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
}

@Composable
fun RenderOption(
    option: Name,
    accountViewModel: AccountViewModel,
) {
    when (option) {
        is GeoHashName -> {
            LoadCityName(option.geoHashTag) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "/g/$it", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        is HashtagName -> {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = option.name(), color = MaterialTheme.colorScheme.onSurface)
            }
        }
        is ResourceName -> {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringRes(id = option.resourceId),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        is PeopleListName -> {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val noteState by observeNote(option.note, accountViewModel)

                val noteEvent = noteState.note.event
                val name =
                    when (noteEvent) {
                        is PeopleListEvent -> {
                            noteEvent.nameOrTitle() ?: option.note.dTag()
                        }

                        is FollowListEvent -> {
                            noteEvent.title() ?: option.note.dTag()
                        }

                        else -> {
                            option.note.dTag()
                        }
                    }

                Text(text = name, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        is CommunityName -> {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val it by observeNote(option.note, accountViewModel)

                Text(text = "/n/${((it.note as? AddressableNote)?.dTag() ?: "")}", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
