package com.vitorpamplona.amethyst.ui.navigation

import android.graphics.Rect
import android.view.ViewTreeObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import kotlinx.coroutines.launch

val bottomNavigationItems = listOf(
    Route.Home,
    Route.Message,
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
    val currentRoute = currentRoute(navController)
    val coroutineScope = rememberCoroutineScope()

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
                    BottomNavigationItem(
                        icon = { NotifiableIcon(item, currentRoute, accountViewModel) },
                        selected = currentRoute == item.route,
                        onClick = {
                            coroutineScope.launch {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        navController.graph.startDestinationRoute?.let { start ->
                                            popUpTo(start)
                                            restoreState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                } else {
                                    // TODO: Make it scrool to the top
                                    navController.navigate(item.route) {
                                        navController.graph.startDestinationRoute?.let { start ->
                                            popUpTo(start) { inclusive = item.route == Route.Home.route }
                                            restoreState = true
                                        }

                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotifiableIcon(item: Route, currentRoute: String?, accountViewModel: AccountViewModel) {
    Box(Modifier.size(if ("Home" == item.route) 25.dp else 23.dp)) {
        Icon(
            painter = painterResource(id = item.icon),
            null,
            modifier = Modifier.size(if ("Home" == item.route) 24.dp else 20.dp),
            tint = if (currentRoute == item.route) MaterialTheme.colors.primary else Color.Unspecified
        )

        val accountState by accountViewModel.accountLiveData.observeAsState()
        val account = accountState?.account ?: return

        // Notification
        val dbState = LocalCache.live.observeAsState()
        val db = dbState.value ?: return

        val notifState = NotificationCache.live.observeAsState()
        val notif = notifState.value ?: return

        var hasNewItems by remember { mutableStateOf<Boolean>(false) }

        val context = LocalContext.current.applicationContext

        LaunchedEffect(key1 = notif) {
            hasNewItems = item.hasNewItems(account, notif.cache, context)
        }

        LaunchedEffect(key1 = db) {
            hasNewItems = item.hasNewItems(account, notif.cache, context)
        }

        if (hasNewItems) {
            Box(
                Modifier
                    .width(10.dp)
                    .height(10.dp)
                    .align(Alignment.TopEnd)
            ) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(10.dp)
                        .clip(shape = CircleShape)
                        .background(MaterialTheme.colors.primary),
                    contentAlignment = Alignment.TopEnd
                ) {
                    Text(
                        "",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .wrapContentHeight()
                            .align(Alignment.TopEnd)
                    )
                }
            }
        }
    }
}
