package com.vitorpamplona.amethyst.ui.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.service.notifications.PushDistributorHandler
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.SettingsRow
import com.vitorpamplona.amethyst.ui.screen.loggedIn.SpinnerSelectionDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.TitleExplainer
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Composable
fun SelectNotificationProvider(sharedPreferencesViewModel: SharedPreferencesViewModel) {
    if (!sharedPreferencesViewModel.sharedPrefs.dontShowPushNotificationSelector) {
        val context = LocalContext.current
        var distributorPresent by remember {
            mutableStateOf(PushDistributorHandler.savedDistributorExists())
        }
        if (!distributorPresent) {
            LoadDistributors() { currentDistributor, list, readableListWithExplainer ->
                if (!readableListWithExplainer.isEmpty()) {
                    SpinnerSelectionDialog(
                        title = stringResource(id = R.string.select_push_server),
                        options = readableListWithExplainer,
                        onSelect = { index ->
                            if (list[index] == "None") {
                                PushDistributorHandler.forceRemoveDistributor(context)
                                sharedPreferencesViewModel.dontShowPushNotificationSelector()
                            } else {
                                val fullDistributorName = list[index]
                                PushDistributorHandler.saveDistributor(fullDistributorName)
                            }
                            distributorPresent = true
                            Log.d("Amethyst", "NotificationScreen: Distributor registered.")
                        },
                        onDismiss = {
                            distributorPresent = true
                            Log.d("Amethyst", "NotificationScreen: Distributor dialog dismissed.")
                        }
                    )
                }
            }
        } else {
            val currentDistributor = PushDistributorHandler.getSavedDistributor()
            PushDistributorHandler.saveDistributor(currentDistributor)
        }
    }
}

@Composable
fun LoadDistributors(
    onInner: @Composable (String, ImmutableList<String>, ImmutableList<TitleExplainer>) -> Unit
) {
    val currentDistributor = PushDistributorHandler.getSavedDistributor().ifBlank { null } ?: "None"

    val list = remember {
        PushDistributorHandler.getInstalledDistributors().plus("None").toImmutableList()
    }

    val readableListWithExplainer =
        PushDistributorHandler.formattedDistributorNames()
            .mapIndexed { index, name ->
                TitleExplainer(
                    name,
                    stringResource(id = R.string.push_server_uses_app_explainer, list[index])
                )
            }
            .plus(
                TitleExplainer(
                    stringResource(id = R.string.push_server_none),
                    stringResource(id = R.string.push_server_none_explainer)
                )
            )
            .toImmutableList()

    onInner(
        currentDistributor,
        list,
        readableListWithExplainer
    )
}

@Composable
fun PushNotificationSettingsRow(sharedPreferencesViewModel: SharedPreferencesViewModel) {
    val context = LocalContext.current

    LoadDistributors() { currentDistributor, list, readableListWithExplainer ->
        SettingsRow(
            R.string.push_server_title,
            R.string.push_server_explainer,
            selectedItens = readableListWithExplainer,
            selectedIndex = list.indexOf(currentDistributor)
        ) { index ->
            if (list[index] == "None") {
                sharedPreferencesViewModel.dontShowPushNotificationSelector()
                PushDistributorHandler.forceRemoveDistributor(context)
            } else {
                PushDistributorHandler.saveDistributor(list[index])
            }
        }
    }
}
