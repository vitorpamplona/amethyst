package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.Material3RichText
import com.halilibo.richtext.ui.resolveDefaults
import com.vitorpamplona.amethyst.service.notifications.PushDistributorHandler
import com.vitorpamplona.amethyst.ui.note.UserReactionsViewModel
import com.vitorpamplona.amethyst.ui.screen.NotificationViewModel
import com.vitorpamplona.amethyst.ui.theme.HalfPadding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Composable
fun CustomNotificationScreen(
    notifFeedViewModel: NotificationViewModel,
    userReactionsStatsModel: UserReactionsViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    val pushHandler = PushDistributorHandler
    var distributorPresent by remember {
        mutableStateOf(pushHandler.savedDistributorExists())
    }
    val list = pushHandler.getInstalledDistributors()
    val readableList = pushHandler.formattedDistributorNames()
    if (!distributorPresent) {
        SelectPushDistributor(
            distrbutorList = readableList.toImmutableList(),
            onDistributorSelected = { index, name ->
                val fullDistributorName = list[index]
                pushHandler.saveDistributor(fullDistributorName)
                Log.d("Amethyst", "NotificationScreen: Distributor registered.")
            },
            onDismiss = {
                distributorPresent = true
                Log.d("Amethyst", "NotificationScreen: Distributor dialog dismissed.")
            }
        )
    } else {
        val currentDistributor = pushHandler.getSavedDistributor()
        pushHandler.saveDistributor(currentDistributor)
    }

    NotificationScreen(
        notifFeedViewModel = notifFeedViewModel,
        userReactionsStatsModel = userReactionsStatsModel,
        accountViewModel = accountViewModel,
        nav = nav
    )
}

@Composable
fun SelectPushDistributor(
    distrbutorList: ImmutableList<String>,
    onDistributorSelected: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .border(
                    width = Dp.Hairline,
                    color = MaterialTheme.colorScheme.background,
                    shape = CircleShape
                )
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Box(modifier = Modifier.align(CenterHorizontally)) {
                    Material3RichText(
                        style = RichTextStyle().resolveDefaults()
                    ) {
                        Markdown(content = "### Select a distributor")
                    }
                }
                distrbutorList.forEachIndexed { index, distributor ->
                    TextButton(
                        onClick = {
                            onDistributorSelected(index, distributor)
                            onDismiss()
                        },
                        modifier = HalfPadding.fillMaxWidth()
                    ) {
                        Material3RichText(
                            style = RichTextStyle().resolveDefaults()
                        ) {
                            Markdown(content = distributor)
                        }
                    }
                }
            }
        }
    }
}
