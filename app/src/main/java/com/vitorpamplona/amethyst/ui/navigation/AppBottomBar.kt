package com.vitorpamplona.amethyst.ui.navigation

import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

val bottomNavigationItems = listOf(
    Route.Home,
    Route.Message,
    Route.Video,
    Route.Search,
    Route.Notification
)

enum class Keyboard {
    Opened, Closed
}

@Composable
fun keyboardAsState(): State<Keyboard> {
    val keyboardState = remember { mutableStateOf(Keyboard.Closed) }
    val view = LocalView.current
    DisposableEffect(view) {
        val onGlobalListener = ViewTreeObserver.OnGlobalLayoutListener {
            val rect = Rect()
            view.getWindowVisibleDisplayFrame(rect)
            val screenHeight = view.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            val newKeyboardValue = if (keypadHeight > screenHeight * 0.15) {
                Keyboard.Opened
            } else {
                Keyboard.Closed
            }

            if (newKeyboardValue != keyboardState.value) {
                keyboardState.value = newKeyboardValue
            }
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(onGlobalListener)

        onDispose {
            view.viewTreeObserver.removeOnGlobalLayoutListener(onGlobalListener)
        }
    }

    return keyboardState
}

@Composable
fun AppBottomBar(accountViewModel: AccountViewModel, navEntryState: State<NavBackStackEntry?>, nav: (Route, Boolean) -> Unit) {
    val isKeyboardOpen by keyboardAsState()
    if (isKeyboardOpen == Keyboard.Closed) {
        RenderBottomMenu(accountViewModel, navEntryState, nav)
    }
}

@Composable
private fun RenderBottomMenu(
    accountViewModel: AccountViewModel,
    navEntryState: State<NavBackStackEntry?>,
    nav: (Route, Boolean) -> Unit
) {
    Column() {
        Divider(
            thickness = 0.25.dp
        )
        BottomNavigation(
            modifier = Modifier,
            elevation = 0.dp,
            backgroundColor = MaterialTheme.colors.background
        ) {
            bottomNavigationItems.forEach { item ->
                HasNewItemsIcon(item, accountViewModel, navEntryState, nav)
            }
        }
    }
}

@Composable
private fun RowScope.HasNewItemsIcon(
    route: Route,
    accountViewModel: AccountViewModel,
    navEntryState: State<NavBackStackEntry?>,
    nav: (Route, Boolean) -> Unit
) {
    var hasNewItems by remember { mutableStateOf(false) }

    WatchPossibleNotificationChanges(route, accountViewModel) {
        if (it != hasNewItems) {
            hasNewItems = it
        }
    }

    val scope = rememberCoroutineScope()

    BottomIcon(
        icon = route.icon,
        size = if ("Home" == route.base) 25.dp else 23.dp,
        iconSize = if ("Home" == route.base) 24.dp else 20.dp,
        base = route.base,
        hasNewItems = hasNewItems,
        navEntryState = navEntryState
    ) { selected ->
        scope.launch {
            nav(route, selected)
        }
    }
}

@Composable
fun WatchPossibleNotificationChanges(
    route: Route,
    accountViewModel: AccountViewModel,
    onChange: (Boolean) -> Unit
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = remember(accountState) { accountState?.account } ?: return

    val notifState by NotificationCache.live.observeAsState()
    val notif = remember(notifState) { notifState?.cache } ?: return

    LaunchedEffect(key1 = notifState, key2 = accountState) {
        launch(Dispatchers.IO) {
            onChange(route.hasNewItems(account, notif, emptySet()))
        }
    }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            LocalCache.live.newEventBundles.collect {
                launch(Dispatchers.IO) {
                    onChange(route.hasNewItems(account, notif, it))
                }
            }
        }
    }
}

@Composable
private fun RowScope.BottomIcon(
    icon: Int,
    size: Dp,
    iconSize: Dp,
    base: String,
    hasNewItems: Boolean,
    navEntryState: State<NavBackStackEntry?>,
    onClick: (Boolean) -> Unit
) {
    val selected by remember(navEntryState.value) {
        derivedStateOf {
            navEntryState.value?.destination?.route?.substringBefore("?") == base
        }
    }

    NavigationIcon(icon, size, iconSize, selected, hasNewItems, onClick)
}

@Composable
private fun RowScope.NavigationIcon(
    icon: Int,
    size: Dp,
    iconSize: Dp,
    selected: Boolean,
    hasNewItems: Boolean,
    onClick: (Boolean) -> Unit
) {
    BottomNavigationItem(
        icon = {
            NotifiableIcon(
                icon,
                size,
                iconSize,
                selected,
                hasNewItems
            )
        },
        selected = selected,
        onClick = { onClick(selected) }
    )
}

@Composable
private fun NotifiableIcon(icon: Int, size: Dp, iconSize: Dp, selected: Boolean, hasNewItems: Boolean) {
    Box(remember { Modifier.size(size) }) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = remember { Modifier.size(iconSize) },
            tint = if (selected) MaterialTheme.colors.primary else Color.Unspecified
        )

        if (hasNewItems) {
            Box(
                remember {
                    Modifier
                        .width(10.dp)
                        .height(10.dp)
                        .align(Alignment.TopEnd)
                }
            ) {
                Box(
                    modifier = remember {
                        Modifier
                            .width(10.dp)
                            .height(10.dp)
                            .clip(shape = CircleShape)
                    }.background(MaterialTheme.colors.primary),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Text(
                        "",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        modifier = remember {
                            Modifier
                                .wrapContentHeight()
                                .align(Alignment.TopEnd)
                        }
                    )
                }
            }
        }
    }
}
