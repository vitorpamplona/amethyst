package com.vitorpamplona.amethyst.ui

import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.Coil
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import com.vitorpamplona.amethyst.LocalPreferences
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.service.nip19.Nip19
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.ui.screen.AccountScreen
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val nip19 = Nip19.uriToRoute(intent?.data?.toString())
        val startingPage = when (nip19?.type) {
            Nip19.Type.USER -> "User/${nip19.hex}"
            Nip19.Type.NOTE -> "Note/${nip19.hex}"
            else -> null
        }

        Coil.setImageLoader {
            ImageLoader.Builder(this).components {
                if (SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(SvgDecoder.Factory())
            } // .logger(DebugLogger())
                .respectCacheHeaders(false)
                .build()
        }

        setContent {
            AmethystTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
                    val accountStateViewModel: AccountStateViewModel = viewModel {
                        AccountStateViewModel(LocalPreferences(applicationContext))
                    }

                    AccountScreen(accountStateViewModel, startingPage)
                }
            }
        }

        Client.lenient = true
    }

    override fun onResume() {
        super.onResume()

        // Only starts after login
        ServiceManager.start()
    }

    override fun onPause() {
        ServiceManager.pause()

        super.onPause()
    }

    /**
     * Release memory when the UI becomes hidden or when system resources become low.
     * @param level the memory-related event that was raised.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        println("Trim Memory $level")
        ServiceManager.cleanUp()
    }
}
