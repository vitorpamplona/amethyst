package com.vitorpamplona.amethyst.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

val bottomNavigationItems = listOf(
    Route.Home,
    Route.Message,
    Route.Search,
    Route.Notification
)

@Composable
fun AppBottomBar(navController: NavHostController) {
    val currentRoute = currentRoute(navController)

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
                    icon = {
                        Icon(
                            painter = painterResource(id = item.icon),
                            null,
                            modifier = Modifier.size(if ("Home" == item.route) 24.dp else 20.dp),
                            tint = if (currentRoute == item.route) MaterialTheme.colors.primary else Color.Unspecified
                        )
                    },
                    selected = currentRoute == item.route,
                    onClick = {
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
                )
            }
        }
    }
}
