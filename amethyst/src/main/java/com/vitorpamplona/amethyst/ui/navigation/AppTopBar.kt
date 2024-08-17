/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.navigation

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Debug
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.map
import androidx.navigation.NavBackStackEntry
import coil.Coil
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.NostrAccountDataSource
import com.vitorpamplona.amethyst.service.NostrChannelDataSource
import com.vitorpamplona.amethyst.service.NostrChatroomDataSource
import com.vitorpamplona.amethyst.service.NostrChatroomListDataSource
import com.vitorpamplona.amethyst.service.NostrCommunityDataSource
import com.vitorpamplona.amethyst.service.NostrDiscoveryDataSource
import com.vitorpamplona.amethyst.service.NostrGeohashDataSource
import com.vitorpamplona.amethyst.service.NostrHashtagDataSource
import com.vitorpamplona.amethyst.service.NostrHomeDataSource
import com.vitorpamplona.amethyst.service.NostrSearchEventOrUserDataSource
import com.vitorpamplona.amethyst.service.NostrSingleChannelDataSource
import com.vitorpamplona.amethyst.service.NostrSingleEventDataSource
import com.vitorpamplona.amethyst.service.NostrSingleUserDataSource
import com.vitorpamplona.amethyst.service.NostrThreadDataSource
import com.vitorpamplona.amethyst.service.NostrUserProfileDataSource
import com.vitorpamplona.amethyst.service.NostrVideoDataSource
import com.vitorpamplona.amethyst.ui.components.LoadNote
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.note.AmethystIcon
import com.vitorpamplona.amethyst.ui.note.ArrowBackIcon
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.note.LoadChannel
import com.vitorpamplona.amethyst.ui.note.LoadCityName
import com.vitorpamplona.amethyst.ui.note.LoadUser
import com.vitorpamplona.amethyst.ui.note.NonClickableUserPictures
import com.vitorpamplona.amethyst.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.ui.note.SearchIcon
import com.vitorpamplona.amethyst.ui.note.UserCompose
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.types.LongCommunityHeader
import com.vitorpamplona.amethyst.ui.note.types.ShortCommunityHeader
import com.vitorpamplona.amethyst.ui.screen.CodeName
import com.vitorpamplona.amethyst.ui.screen.CodeNameType
import com.vitorpamplona.amethyst.ui.screen.CommunityName
import com.vitorpamplona.amethyst.ui.screen.FollowListState
import com.vitorpamplona.amethyst.ui.screen.GeoHashName
import com.vitorpamplona.amethyst.ui.screen.HashtagName
import com.vitorpamplona.amethyst.ui.screen.Name
import com.vitorpamplona.amethyst.ui.screen.PeopleListName
import com.vitorpamplona.amethyst.ui.screen.ResourceName
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.DislayGeoTagHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.GeoHashActionOptions
import com.vitorpamplona.amethyst.ui.screen.loggedIn.HashtagActionOptions
import com.vitorpamplona.amethyst.ui.screen.loggedIn.SpinnerSelectionDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chatrooms.LoadRoom
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chatrooms.LoadRoomByAuthor
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chatrooms.LongChannelHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chatrooms.LongRoomHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chatrooms.RoomNameOnlyDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chatrooms.ShortChannelHeader
import com.vitorpamplona.amethyst.ui.screen.loggedIn.discover.observeAppDefinition
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.BottomTopHeight
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.DoubleHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.HeaderPictureModifier
import com.vitorpamplona.amethyst.ui.theme.Size20Modifier
import com.vitorpamplona.amethyst.ui.theme.Size22Modifier
import com.vitorpamplona.amethyst.ui.theme.Size34dp
import com.vitorpamplona.amethyst.ui.theme.Size40dp
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.ammolite.relays.Client
import com.vitorpamplona.ammolite.relays.RelayPool
import com.vitorpamplona.quartz.events.ChatroomKey
import com.vitorpamplona.quartz.events.PeopleListEvent
import kotlinx.collections.immutable.ImmutableList

@Composable
fun AppTopBar(
    navEntryState: State<NavBackStackEntry?>,
    openDrawer: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    navPopBack: () -> Unit,
) {
    val currentRoute by
        remember(navEntryState.value) {
            derivedStateOf {
                navEntryState.value
                    ?.destination
                    ?.route
                    ?.substringBefore("?")
            }
        }

    val id by
        remember(navEntryState.value) {
            derivedStateOf { navEntryState.value?.arguments?.getString("id") }
        }

    RenderTopRouteBar(currentRoute, id, accountViewModel.feedStates.feedListOptions, openDrawer, accountViewModel, nav, navPopBack)
}

@Composable
private fun RenderTopRouteBar(
    currentRoute: String?,
    id: String?,
    followLists: FollowListState,
    openDrawer: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    navPopBack: () -> Unit,
) {
    when (currentRoute) {
        Route.Home.base -> HomeTopBar(followLists, openDrawer, accountViewModel, nav)
        Route.Video.base -> StoriesTopBar(followLists, openDrawer, accountViewModel, nav)
        Route.Discover.base -> DiscoveryTopBar(followLists, openDrawer, accountViewModel, nav)
        Route.Notification.base -> NotificationTopBar(followLists, openDrawer, accountViewModel, nav)
        Route.Settings.base -> TopBarWithBackButton(stringRes(id = R.string.application_preferences), navPopBack)
        Route.Bookmarks.base -> TopBarWithBackButton(stringRes(id = R.string.bookmarks), navPopBack)
        Route.Drafts.base -> TopBarWithBackButton(stringRes(id = R.string.drafts), navPopBack)

        else -> {
            if (id != null) {
                when (currentRoute) {
                    Route.Channel.base -> ChannelTopBar(id, accountViewModel, nav, navPopBack)
                    Route.RoomByAuthor.base -> RoomByAuthorTopBar(id, accountViewModel, nav, navPopBack)
                    Route.Room.base -> RoomTopBar(id, accountViewModel, nav, navPopBack)
                    Route.Community.base -> CommunityTopBar(id, accountViewModel, nav, navPopBack)
                    Route.Hashtag.base -> HashTagTopBar(id, accountViewModel, navPopBack)
                    Route.Geohash.base -> GeoHashTopBar(id, accountViewModel, navPopBack)
                    Route.Note.base -> ThreadTopBar(id, accountViewModel, navPopBack)
                    Route.ContentDiscovery.base -> DvmTopBar(id, accountViewModel, nav, navPopBack)
                    else -> MainTopBar(openDrawer, accountViewModel, nav)
                }
            } else {
                MainTopBar(openDrawer, accountViewModel, nav)
            }
        }
    }
}

@Composable
private fun ThreadTopBar(
    id: String,
    accountViewModel: AccountViewModel,
    navPopBack: () -> Unit,
) {
    FlexibleTopBarWithBackButton(
        title = { Text(stringRes(id = R.string.thread_title)) },
        popBack = navPopBack,
    )
}

@Composable
private fun GeoHashTopBar(
    tag: String,
    accountViewModel: AccountViewModel,
    navPopBack: () -> Unit,
) {
    FlexibleTopBarWithBackButton(
        title = {
            DislayGeoTagHeader(tag, remember { Modifier.weight(1f) })
            GeoHashActionOptions(tag, accountViewModel)
        },
        popBack = navPopBack,
    )
}

@Composable
private fun HashTagTopBar(
    tag: String,
    accountViewModel: AccountViewModel,
    navPopBack: () -> Unit,
) {
    FlexibleTopBarWithBackButton(
        title = {
            Text(
                remember(tag) { "#$tag" },
                modifier = Modifier.weight(1f),
            )

            HashtagActionOptions(tag, accountViewModel)
        },
        popBack = navPopBack,
    )
}

@Composable
private fun CommunityTopBar(
    id: String,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    navPopBack: () -> Unit,
) {
    LoadAddressableNote(aTagHex = id, accountViewModel) { baseNote ->
        if (baseNote != null) {
            FlexibleTopBarWithBackButton(
                title = { ShortCommunityHeader(baseNote, accountViewModel, nav) },
                extendableRow = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        LongCommunityHeader(baseNote = baseNote, accountViewModel = accountViewModel, nav = nav)
                    }
                },
                popBack = navPopBack,
            )
        } else {
            Spacer(BottomTopHeight)
        }
    }
}

@Composable
private fun RoomTopBar(
    id: String,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    navPopBack: () -> Unit,
) {
    LoadRoom(roomId = id, accountViewModel) { room ->
        if (room != null) {
            RenderRoomTopBar(room, accountViewModel, nav, navPopBack)
        } else {
            Spacer(BottomTopHeight)
        }
    }
}

@Composable
private fun DvmTopBar(
    appDefinitionId: String,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    navPopBack: () -> Unit,
) {
    FlexibleTopBarWithBackButton(
        title = {
            LoadNote(baseNoteHex = appDefinitionId, accountViewModel = accountViewModel) { appDefinitionNote ->
                if (appDefinitionNote != null) {
                    val card = observeAppDefinition(appDefinitionNote)

                    card.cover?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(Size34dp)
                                    .clip(shape = CircleShape),
                        )
                    } ?: run { NoteAuthorPicture(baseNote = appDefinitionNote, size = Size34dp, accountViewModel = accountViewModel) }

                    Spacer(modifier = DoubleHorzSpacer)

                    Text(
                        text = card.name,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        popBack = navPopBack,
    )
}

@Composable
private fun RoomByAuthorTopBar(
    id: String,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    navPopBack: () -> Unit,
) {
    LoadRoomByAuthor(authorPubKeyHex = id, accountViewModel) { room ->
        if (room != null) {
            RenderRoomTopBar(room, accountViewModel, nav, navPopBack)
        } else {
            Spacer(BottomTopHeight)
        }
    }
}

@Composable
private fun RenderRoomTopBar(
    room: ChatroomKey,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    navPopBack: () -> Unit,
) {
    if (room.users.size == 1) {
        FlexibleTopBarWithBackButton(
            title = {
                LoadUser(baseUserHex = room.users.first(), accountViewModel) { baseUser ->
                    if (baseUser != null) {
                        ClickableUserPicture(
                            baseUser = baseUser,
                            accountViewModel = accountViewModel,
                            size = Size34dp,
                        )

                        Spacer(modifier = DoubleHorzSpacer)

                        UsernameDisplay(baseUser, Modifier.weight(1f), fontWeight = FontWeight.Normal, accountViewModel = accountViewModel)
                    }
                }
            },
            extendableRow = {
                LoadUser(baseUserHex = room.users.first(), accountViewModel) {
                    if (it != null) {
                        UserCompose(
                            baseUser = it,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            },
            popBack = navPopBack,
        )
    } else {
        FlexibleTopBarWithBackButton(
            title = {
                NonClickableUserPictures(
                    room = room,
                    accountViewModel = accountViewModel,
                    size = Size34dp,
                )

                RoomNameOnlyDisplay(
                    room,
                    Modifier
                        .padding(start = 10.dp)
                        .weight(1f),
                    fontWeight = FontWeight.Normal,
                    accountViewModel,
                )
            },
            extendableRow = {
                LongRoomHeader(room = room, accountViewModel = accountViewModel, nav = nav)
            },
            popBack = navPopBack,
        )
    }
}

@Composable
private fun ChannelTopBar(
    id: String,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    navPopBack: () -> Unit,
) {
    LoadChannel(baseChannelHex = id, accountViewModel) { baseChannel ->
        FlexibleTopBarWithBackButton(
            title = {
                ShortChannelHeader(
                    baseChannel = baseChannel,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    showFlag = true,
                )
            },
            extendableRow = {
                LongChannelHeader(baseChannel = baseChannel, accountViewModel = accountViewModel, nav = nav)
            },
            popBack = navPopBack,
        )
    }
}

@Composable
fun StoriesTopBar(
    followLists: FollowListState,
    openDrawer: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    GenericMainTopBar(openDrawer, accountViewModel, nav) {
        val list by accountViewModel.account.defaultStoriesFollowList.collectAsStateWithLifecycle()

        FollowListWithRoutes(
            followListsModel = followLists,
            listName = list,
        ) { listName ->
            accountViewModel.account.changeDefaultStoriesFollowList(listName.code)
        }
    }
}

@Composable
fun HomeTopBar(
    followLists: FollowListState,
    openDrawer: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    GenericMainTopBar(openDrawer, accountViewModel, nav) {
        val list by accountViewModel.account.defaultHomeFollowList.collectAsStateWithLifecycle()

        FollowListWithRoutes(
            followListsModel = followLists,
            listName = list,
        ) { listName ->
            if (listName.type == CodeNameType.ROUTE) {
                nav(listName.code)
            } else {
                accountViewModel.account.changeDefaultHomeFollowList(listName.code)
            }
        }
    }
}

@Composable
fun NotificationTopBar(
    followLists: FollowListState,
    openDrawer: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    GenericMainTopBar(openDrawer, accountViewModel, nav) {
        val list by accountViewModel.account.defaultNotificationFollowList.collectAsStateWithLifecycle()

        FollowListWithoutRoutes(
            followListsModel = followLists,
            listName = list,
        ) { listName ->
            accountViewModel.account.changeDefaultNotificationFollowList(listName.code)
        }
    }
}

@Composable
fun DiscoveryTopBar(
    followLists: FollowListState,
    openDrawer: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    GenericMainTopBar(openDrawer, accountViewModel, nav) {
        val list by accountViewModel.account.defaultDiscoveryFollowList.collectAsStateWithLifecycle()

        FollowListWithoutRoutes(
            followListsModel = followLists,
            listName = list,
        ) { listName ->
            accountViewModel.account.changeDefaultDiscoveryFollowList(listName.code)
        }
    }
}

@Composable
fun MainTopBar(
    openDrawer: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    GenericMainTopBar(openDrawer, accountViewModel, nav) { AmethystClickableIcon() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenericMainTopBar(
    openDrawer: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = BottomTopHeight) {
        TopAppBar(
            title = {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    content()
                }
            },
            navigationIcon = {
                LoggedInUserPictureDrawer(accountViewModel, openDrawer)
            },
            actions = { SearchButton { nav(Route.Search.route) } },
        )
        HorizontalDivider(thickness = DividerThickness)
    }
}

@Composable
private fun SearchButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
    ) {
        SearchIcon(modifier = Size22Modifier, MaterialTheme.colorScheme.placeholderText)
    }
}

@Composable
private fun LoggedInUserPictureDrawer(
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    val profilePicture by
        accountViewModel.account
            .userProfile()
            .live()
            .profilePictureChanges
            .observeAsState()

    IconButton(onClick = onClick) {
        RobohashFallbackAsyncImage(
            robot = accountViewModel.userProfile().pubkeyHex,
            model = profilePicture,
            contentDescription = stringRes(id = R.string.your_profile_image),
            modifier = HeaderPictureModifier,
            contentScale = ContentScale.Crop,
            loadProfilePicture = accountViewModel.settings.showProfilePictures.value,
            loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
        )
    }
}

@Composable
fun FollowListWithRoutes(
    followListsModel: FollowListState,
    listName: String,
    onChange: (CodeName) -> Unit,
) {
    val allLists by followListsModel.kind3GlobalPeopleRoutes.collectAsStateWithLifecycle()

    SimpleTextSpinner(
        placeholderCode = listName,
        options = allLists,
        onSelect = { onChange(allLists.getOrNull(it) ?: followListsModel.kind3Follow) },
    )
}

@Composable
fun FollowListWithoutRoutes(
    followListsModel: FollowListState,
    listName: String,
    onChange: (CodeName) -> Unit,
) {
    val allLists by followListsModel.kind3GlobalPeople.collectAsStateWithLifecycle()

    SimpleTextSpinner(
        placeholderCode = listName,
        options = allLists,
        onSelect = { onChange(allLists.getOrNull(it) ?: followListsModel.kind3Follow) },
    )
}

@Composable
fun SimpleTextSpinner(
    placeholderCode: String,
    options: ImmutableList<CodeName>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var optionsShowing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val selectAnOption =
        stringRes(
            id = R.string.select_an_option,
        )

    var currentText by
        remember(placeholderCode, options) {
            mutableStateOf(
                options.firstOrNull { it.code == placeholderCode }?.name?.name(context) ?: selectAnOption,
            )
        }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Size20Modifier)
            Text(currentText)
            Icon(
                imageVector = Icons.Default.ExpandMore,
                null,
                modifier = Size20Modifier,
                tint = MaterialTheme.colorScheme.placeholderText,
            )
        }
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                    ) {
                        optionsShowing = true
                    },
        )
    }

    if (optionsShowing) {
        options.isNotEmpty().also {
            SpinnerSelectionDialog(
                options = options,
                onDismiss = { optionsShowing = false },
                onSelect = {
                    currentText = options[it].name.name(context)
                    optionsShowing = false
                    onSelect(it)
                },
            ) {
                RenderOption(it.name)
            }
        }
    }
}

@Composable
fun RenderOption(option: Name) {
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
                val noteState by
                    option.note
                        .live()
                        .metadata
                        .observeAsState()

                val name = (noteState?.note?.event as? PeopleListEvent)?.nameOrTitle() ?: option.note.dTag() ?: ""

                Text(text = name, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        is CommunityName -> {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val name by
                    option.note
                        .live()
                        .metadata
                        .map { "/n/" + ((it.note as? AddressableNote)?.dTag() ?: "") }
                        .observeAsState()

                Text(text = name ?: "", color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarWithBackButton(
    caption: String,
    popBack: () -> Unit,
) {
    Column(modifier = BottomTopHeight) {
        TopAppBar(
            title = { Text(caption) },
            navigationIcon = {
                IconButton(
                    onClick = popBack,
                    modifier = Modifier,
                ) {
                    ArrowBackIcon()
                }
            },
            actions = {},
        )
        HorizontalDivider(thickness = DividerThickness)
    }
}

@Composable
fun FlexibleTopBarWithBackButton(
    title: @Composable RowScope.() -> Unit,
    extendableRow: (@Composable () -> Unit)? = null,
    popBack: () -> Unit,
) {
    Column {
        MyExtensibleTopAppBar(
            title = title,
            extendableRow = extendableRow,
            navigationIcon = { IconButton(onClick = popBack) { ArrowBackIcon() } },
            actions = {},
        )
        HorizontalDivider(thickness = DividerThickness)
    }
}

@Composable
fun AmethystClickableIcon() {
    val context = LocalContext.current

    IconButton(
        onClick = { debugState(context) },
    ) {
        AmethystIcon(Size40dp)
    }
}

fun debugState(context: Context) {
    Client
        .allSubscriptions()
        .forEach { Log.d("STATE DUMP", "${it.key} ${it.value.joinToString { it.filter.toDebugJson() }}") }

    NostrAccountDataSource.printCounter()
    NostrChannelDataSource.printCounter()
    NostrChatroomDataSource.printCounter()
    NostrChatroomListDataSource.printCounter()
    NostrCommunityDataSource.printCounter()
    NostrDiscoveryDataSource.printCounter()
    NostrHashtagDataSource.printCounter()
    NostrGeohashDataSource.printCounter()
    NostrHomeDataSource.printCounter()
    NostrSearchEventOrUserDataSource.printCounter()
    NostrSingleChannelDataSource.printCounter()
    NostrSingleEventDataSource.printCounter()
    NostrSingleUserDataSource.printCounter()
    NostrThreadDataSource.printCounter()
    NostrUserProfileDataSource.printCounter()
    NostrVideoDataSource.printCounter()

    val totalMemoryMb = Runtime.getRuntime().totalMemory() / (1024 * 1024)
    val freeMemoryMb = Runtime.getRuntime().freeMemory() / (1024 * 1024)
    val maxMemoryMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)

    val jvmHeapAllocatedMb = totalMemoryMb - freeMemoryMb

    Log.d("STATE DUMP", "Total Heap Allocated: " + jvmHeapAllocatedMb + "/" + maxMemoryMb + " MB")

    val nativeHeap = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
    val maxNative = Debug.getNativeHeapSize() / (1024 * 1024)

    Log.d("STATE DUMP", "Total Native Heap Allocated: " + nativeHeap + "/" + maxNative + " MB")

    val activityManager: ActivityManager? = context.getSystemService()
    if (activityManager != null) {
        val isLargeHeap = (context.applicationInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP) != 0
        val memClass = if (isLargeHeap) activityManager.largeMemoryClass else activityManager.memoryClass

        Log.d("STATE DUMP", "Memory Class " + memClass + " MB (largeHeap $isLargeHeap)")
    }

    Log.d("STATE DUMP", "Connected Relays: " + RelayPool.connectedRelays())

    val imageLoader = Coil.imageLoader(context)
    Log.d(
        "STATE DUMP",
        "Image Disk Cache ${(imageLoader.diskCache?.size ?: 0) / (1024 * 1024)}/${(imageLoader.diskCache?.maxSize ?: 0) / (1024 * 1024)} MB",
    )
    Log.d(
        "STATE DUMP",
        "Image Memory Cache ${(imageLoader.memoryCache?.size ?: 0) / (1024 * 1024)}/${(imageLoader.memoryCache?.maxSize ?: 0) / (1024 * 1024)} MB",
    )

    Log.d(
        "STATE DUMP",
        "Notes: " +
            LocalCache.notes.filter { _, it -> it.liveSet != null }.size +
            " / " +
            LocalCache.notes.filter { _, it -> it.flowSet != null }.size +
            " / " +
            LocalCache.notes.filter { _, it -> it.event != null }.size +
            " / " +
            LocalCache.notes.size(),
    )
    Log.d(
        "STATE DUMP",
        "Addressables: " +
            LocalCache.addressables.filter { _, it -> it.liveSet != null }.size +
            " / " +
            LocalCache.addressables.filter { _, it -> it.flowSet != null }.size +
            " / " +
            LocalCache.addressables.filter { _, it -> it.event != null }.size +
            " / " +
            LocalCache.addressables.size(),
    )
    Log.d(
        "STATE DUMP",
        "Users: " +
            LocalCache.users.filter { _, it -> it.liveSet != null }.size +
            " / " +
            LocalCache.users.filter { _, it -> it.flowSet != null }.size +
            " / " +
            LocalCache.users.filter { _, it -> it.latestMetadata != null }.size +
            " / " +
            LocalCache.users.size(),
    )
    Log.d(
        "STATE DUMP",
        "Deletion Events: " +
            LocalCache.deletionIndex.size(),
    )
    Log.d(
        "STATE DUMP",
        "Observable Events: " +
            LocalCache.observablesByKindAndETag.size +
            " / " +
            LocalCache.observablesByKindAndAuthor.size,
    )

    Log.d(
        "STATE DUMP",
        "Spam: " +
            LocalCache.antiSpam.spamMessages.size() + " / " + LocalCache.antiSpam.recentMessages.size(),
    )

    Log.d(
        "STATE DUMP",
        "Memory used by Events: " +
            LocalCache.notes.sumOfLong { _, note -> note.event?.countMemory() ?: 0L } / (1024 * 1024) +
            " MB",
    )

    val qttNotes = LocalCache.notes.countByGroup { _, it -> it.event?.kind() }
    val qttAddressables = LocalCache.addressables.countByGroup { _, it -> it.event?.kind() }

    val bytesNotes =
        LocalCache.notes
            .sumByGroup(groupMap = { _, it -> it.event?.kind() }, sumOf = { _, it -> it.event?.countMemory() ?: 0L })
    val bytesAddressables =
        LocalCache.addressables
            .sumByGroup(groupMap = { _, it -> it.event?.kind() }, sumOf = { _, it -> it.event?.countMemory() ?: 0L })

    qttNotes.toList().sortedByDescending { bytesNotes.get(it.first) }.forEach { (kind, qtt) ->
        Log.d("STATE DUMP", "Kind ${kind.toString().padStart(5,' ')}:\t${qtt.toString().padStart(6,' ')} elements\t${bytesNotes.get(kind)?.div((1024 * 1024))}MB ")
    }
    qttAddressables.toList().sortedByDescending { bytesNotes.get(it.first) }.forEach { (kind, qtt) ->
        Log.d("STATE DUMP", "Kind ${kind.toString().padStart(5,' ')}:\t${qtt.toString().padStart(6,' ')} elements\t${bytesAddressables.get(kind)?.div((1024 * 1024))}MB ")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyExtensibleTopAppBar(
    title: @Composable RowScope.() -> Unit,
    extendableRow: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val expanded = remember { mutableStateOf(false) }

    Column(
        Modifier.clickable { expanded.value = !expanded.value },
    ) {
        Row(modifier = BottomTopHeight) {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        title()
                    }
                },
                modifier = modifier,
                navigationIcon = {
                    if (navigationIcon == null) {
                        Spacer(TitleInsetWithoutIcon)
                    } else {
                        Row(TitleIconModifier, verticalAlignment = Alignment.CenterVertically) {
                            navigationIcon()
                        }
                    }
                },
                actions = actions,
            )
        }

        if (expanded.value && extendableRow != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column { extendableRow() }
            }
        }
    }
}

// TODO: this should probably be part of the touch target of the start and end icons, clarify this
private val AppBarHorizontalPadding = 4.dp

// Start inset for the title when there is no navigation icon provided
private val TitleInsetWithoutIcon = Modifier.width(16.dp - AppBarHorizontalPadding)

// Start inset for the title when there is a navigation icon provided
private val TitleIconModifier = Modifier.width(48.dp - AppBarHorizontalPadding)
