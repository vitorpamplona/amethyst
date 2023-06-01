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
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
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
            keyboardState.value = if (keypadHeight > screenHeight * 0.15) {
                Keyboard.Opened
            } else {
                Keyboard.Closed
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
fun AppBottomBar(navController: NavHostController, accountViewModel: AccountViewModel) {
    val isKeyboardOpen by keyboardAsState()
    if (isKeyboardOpen == Keyboard.Closed) {
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
                    HasNewItemsIcon(item, accountViewModel, navController)
                }
            }
        }
    }
}

@Composable
private fun RowScope.HasNewItemsIcon(
    route: Route,
    accountViewModel: AccountViewModel,
    navController: NavHostController
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = remember(accountState) { accountState?.account } ?: return

    val notifState by NotificationCache.live.observeAsState()
    val notif = remember(notifState) { notifState?.cache } ?: return

    var hasNewItems by remember { mutableStateOf<Boolean>(false) }

    LaunchedEffect(key1 = notifState, key2 = accountState) {
        launch(Dispatchers.IO) {
            val newHasNewItems = route.hasNewItems(account, notif, emptySet())
            if (newHasNewItems != hasNewItems) {
                hasNewItems = newHasNewItems
            }
        }
    }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            LocalCache.live.newEventBundles.collect {
                val newHasNewItems = route.hasNewItems(account, notif, it)
                if (newHasNewItems != hasNewItems) {
                    hasNewItems = newHasNewItems
                }
            }
        }
    }

    val scope = rememberCoroutineScope()

    BottomIcon(
        icon = route.icon,
        size = if ("Home" == route.base) 25.dp else 23.dp,
        iconSize = if ("Home" == route.base) 24.dp else 20.dp,
        base = route.base,
        hasNewItems = hasNewItems,
        navController
    ) { selected ->
        scope.launch {
            if (!selected) {
                navController.navigate(route.base) {
                    popUpTo(Route.Home.route)
                    launchSingleTop = true
                    restoreState = true
                }
            } else {
                val newRoute = route.route.replace("{scrollToTop}", "true")
                navController.navigate(newRoute) {
                    popUpTo(Route.Home.route)
                    launchSingleTop = true
                    restoreState = true
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
    navController: NavHostController,
    onClick: (Boolean) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    navBackStackEntry?.let {
        val selected = remember(it) {
            it.destination.route?.substringBefore("?") == base
        }

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
