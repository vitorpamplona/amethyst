package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.vitorpamplona.amethyst.NotificationCache
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.note.NewItemsBubble
import kotlinx.coroutines.launch

val bottomNavigationItems = listOf(
    Route.Home,
    Route.Message,
    Route.Search,
    Route.Notification
)

@Composable
fun AppBottomBar(navController: NavHostController) {
    val currentRoute = currentRoute(navController)
    val coroutineScope = rememberCoroutineScope()

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
                    icon = { NotifiableIcon(item, currentRoute) },
                    selected = currentRoute == item.route,
                    onClick = {
                        coroutineScope.launch {
                            if (currentRoute != item.route) {
                                navController.navigate(item.route){
                                    navController.graph.startDestinationRoute?.let { start ->
                                        popUpTo(start)
                                        restoreState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } else {
                                // TODO: Make it scrool to the top
                                navController.navigate(item.route){
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

@Composable
private fun NotifiableIcon(item: Route, currentRoute: String?) {
    Box(Modifier.size(if ("Home" == item.route) 25.dp else 23.dp)) {
        Icon(
            painter = painterResource(id = item.icon),
            null,
            modifier = Modifier.size(if ("Home" == item.route) 24.dp else 20.dp),
            tint = if (currentRoute == item.route) MaterialTheme.colors.primary else Color.Unspecified
        )

        // Notification
        val dbState = LocalCache.live.observeAsState()
        val notifState = NotificationCache.live.observeAsState()

        val db = dbState.value
        val notif = notifState.value

        if (db != null && notif != null) {
            if (item.hasNewItems(db.cache, notif.cache)) {
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
}
