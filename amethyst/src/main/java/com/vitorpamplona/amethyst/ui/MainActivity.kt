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
package com.vitorpamplona.amethyst.ui

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.debugState
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.lang.LanguageTranslatorService
import com.vitorpamplona.amethyst.service.playback.composable.DEFAULT_MUTED_SETTING
import com.vitorpamplona.amethyst.service.playback.pip.BackgroundMedia
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.navigation.routeFor
import com.vitorpamplona.amethyst.ui.screen.AccountScreen
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.screen.prepareSharedViewModel
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEmbed
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        Log.d("ActivityLifecycle", "MainActivity.onCreate $this")

        setContent {
            StringResSetup()

            val sharedPreferencesViewModel = prepareSharedViewModel()
            AmethystTheme(sharedPreferencesViewModel) {
                val accountStateViewModel: AccountStateViewModel = viewModel()

                LaunchedEffect(key1 = Unit) {
                    accountStateViewModel.tryLoginExistingAccountAsync()
                }

                AccountScreen(accountStateViewModel, sharedPreferencesViewModel)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onResume() {
        super.onResume()

        Log.d("ActivityLifecycle", "MainActivity.onResume $this")

        // starts muted every time
        DEFAULT_MUTED_SETTING.value = true
    }

    override fun onPause() {
        Log.d("ActivityLifecycle", "MainActivity.onPause $this")

        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            LanguageTranslatorService.clear()
        }

        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            debugState(this@MainActivity)
            Amethyst.instance.sources.printCounters()
        }

        super.onPause()
    }

    override fun onStop() {
        super.onStop()

        // Graph doesn't completely clear.
        // @OptIn(DelicateCoroutinesApi::class)
        // GlobalScope.launch(Dispatchers.Default) {
        //    serviceManager.trimMemory()
        // }

        Log.d("ActivityLifecycle", "MainActivity.onStop $this")
    }

    override fun onDestroy() {
        Log.d("ActivityLifecycle", "MainActivity.onDestroy $this")

        BackgroundMedia.removeBackgroundControllerAndReleaseIt()

        super.onDestroy()
    }

    companion object {
        fun createIntent(callbackUri: String): PendingIntent =
            PendingIntent.getActivity(
                Amethyst.instance,
                0,
                Intent(Intent.ACTION_VIEW, callbackUri.toUri(), Amethyst.instance, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
    }
}

fun uriToRoute(
    uri: String?,
    account: Account,
): Route? =
    if (uri?.startsWith("notifications", true) == true || uri?.startsWith("nostr:notifications", true) == true) {
        Route.Notification
    } else {
        if (uri?.startsWith("hashtag?id=") == true || uri?.startsWith("nostr:hashtag?id=") == true) {
            Route.Hashtag(uri.removePrefix("nostr:").removePrefix("hashtag?id="))
        } else {
            val nip19 = Nip19Parser.uriToRoute(uri)?.entity
            if (nip19 != null) {
                LocalCache.consume(nip19)
            }
            when (nip19) {
                is NPub -> Route.Profile(nip19.hex)
                is NProfile -> Route.Profile(nip19.hex)
                is NNote -> Route.Note(nip19.hex)
                is NEvent -> {
                    routeFor(
                        note = LocalCache.getOrCreateNote(nip19.hex),
                        loggedIn = account.userProfile(),
                    ) ?: Route.EventRedirect(nip19.hex)
                }

                is NAddress -> {
                    routeFor(
                        note = LocalCache.getOrCreateAddressableNote(nip19.address()),
                        loggedIn = account.userProfile(),
                    ) ?: Route.EventRedirect(nip19.aTag())
                }

                is NEmbed -> {
                    val noteEvent = nip19.event
                    if (noteEvent is AddressableEvent) {
                        routeFor(
                            note = LocalCache.getOrCreateAddressableNote(noteEvent.address()),
                            loggedIn = account.userProfile(),
                        ) ?: Route.EventRedirect(noteEvent.addressTag())
                    } else {
                        routeFor(
                            note = LocalCache.getOrCreateNote(nip19.event.id),
                            loggedIn = account.userProfile(),
                        ) ?: Route.EventRedirect(nip19.event.id)
                    }
                }

                else -> null
            }
        }
            ?: try {
                uri?.let {
                    Nip47WalletConnect.parse(it)
                    Route.Nip47NWCSetup(it)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
    }
