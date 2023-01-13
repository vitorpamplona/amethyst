package com.vitorpamplona.amethyst.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.exoplayer2.util.Util
import com.vitorpamplona.amethyst.KeyStorage
import com.vitorpamplona.amethyst.service.NostrAccountDataSource
import com.vitorpamplona.amethyst.service.NostrGlobalDataSource
import com.vitorpamplona.amethyst.service.NostrHomeDataSource
import com.vitorpamplona.amethyst.service.NostrNotificationDataSource
import com.vitorpamplona.amethyst.service.NostrSingleEventDataSource
import com.vitorpamplona.amethyst.service.NostrSingleUserDataSource
import com.vitorpamplona.amethyst.service.NostrThreadDataSource
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.ui.screen.AccountScreen
import com.vitorpamplona.amethyst.ui.screen.AccountStateViewModel
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent {
      AmethystTheme {
        // A surface container using the 'background' color from the theme
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {

          val accountViewModel: AccountStateViewModel = viewModel {
            AccountStateViewModel(KeyStorage().encryptedPreferences(applicationContext))
          }

          AccountScreen(accountViewModel)
        }
      }
    }

    Client.lenient = true
  }

  override fun onResume() {
    super.onResume()
    Client.connect()
  }

  override fun onPause() {
    NostrAccountDataSource.stop()
    NostrHomeDataSource.stop()

    NostrGlobalDataSource.stop()
    NostrNotificationDataSource.stop()
    NostrSingleEventDataSource.stop()
    NostrSingleUserDataSource.stop()
    NostrThreadDataSource.stop()
    Client.disconnect()
    super.onPause()
  }
}