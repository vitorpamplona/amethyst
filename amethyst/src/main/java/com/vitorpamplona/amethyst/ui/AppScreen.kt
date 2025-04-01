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
package com.vitorpamplona.amethyst.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.adaptive.calculateDisplayFeatures
import com.vitorpamplona.amethyst.ui.screen.SharedPreferencesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun prepareSharedViewModel(act: MainActivity): SharedPreferencesViewModel {
    val sharedPreferencesViewModel: SharedPreferencesViewModel = viewModel()

    val displayFeatures = calculateDisplayFeatures(act)
    val windowSizeClass = calculateWindowSizeClass(act)

    LaunchedEffect(key1 = sharedPreferencesViewModel) {
        sharedPreferencesViewModel.init()
    }

    LaunchedEffect(sharedPreferencesViewModel, displayFeatures, windowSizeClass) {
        sharedPreferencesViewModel.updateDisplaySettings(windowSizeClass, displayFeatures)
    }

    ManageConnectivity(sharedPreferencesViewModel)

    return sharedPreferencesViewModel
}

@Composable
fun ManageConnectivity(sharedPreferencesViewModel: SharedPreferencesViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var job = remember<Job?> { null }

    val networkCallback =
        remember {
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    sharedPreferencesViewModel.updateNetworkState(network.networkHandle)
                }

                // Network capabilities have changed for the network
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    super.onCapabilitiesChanged(network, networkCapabilities)
                    sharedPreferencesViewModel.updateNetworkState(network.networkHandle)
                    sharedPreferencesViewModel.updateConnectivityStatusState(networkCapabilities)
                }
            }
        }

    LifecycleResumeEffect(sharedPreferencesViewModel) {
        job?.cancel()
        job =
            scope.launch(Dispatchers.IO) {
                Log.d("ManageConnectivity", "Register network listener from Resume")
                val connectivityManager = context.getConnectivityManager()
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
                connectivityManager.activeNetwork?.let { network ->
                    sharedPreferencesViewModel.updateNetworkState(network.networkHandle)
                    connectivityManager.getNetworkCapabilities(network)?.let {
                        sharedPreferencesViewModel.updateConnectivityStatusState(it)
                    }
                }
            }

        onPauseOrDispose {
            job?.cancel()
            job =
                scope.launch(Dispatchers.IO) {
                    delay(30000) // 30 seconds
                    Log.d("ManageConnectivity", "Unregister network listener from Pause")
                    context.getConnectivityManager().unregisterNetworkCallback(networkCallback)
                }
        }
    }
}

fun SharedPreferencesViewModel.updateConnectivityStatusState(networkCapabilities: NetworkCapabilities) {
    val metered = !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    val mobileData = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    updateConnectivityStatusState(metered || mobileData)
}

fun Context.getConnectivityManager() = getSystemService(ConnectivityManager::class.java) as ConnectivityManager
