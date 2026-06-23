/*
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.napplet.SandboxForegroundHold
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.TorToggleButton
import com.vitorpamplona.amethyst.ui.theme.AmethystTheme

/**
 * Full-screen host for a single web client (a [FavoriteApp.WebUrl][com.vitorpamplona.amethyst.commons.favorites.FavoriteApp.WebUrl]
 * or any URL opened from the browser launcher). Runs in the **main** process and embeds the keyless
 * `:napplet` browser surface (see [EmbeddedBrowserSurface]) — so the page's JS still executes in the
 * sandbox, never here.
 *
 * Its own task + recents entry (declared `singleTask` / `autoRemoveFromRecents` in the manifest) so the
 * user swaps between open apps the normal Android way. Deliberately **no editable address bar**: a
 * host instance is locked to the app it was opened with, which keeps each instance to a single NIP-07
 * trust context. The chrome is just back/reload/Tor + a read-only title.
 */
class BrowserHostActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val startUrl = intent.getStringExtra(EXTRA_URL)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || startUrl.isNullOrBlank()) {
            finish()
            return
        }

        setContent {
            AmethystTheme {
                BrowserHostScreen(startUrl = startUrl, onClose = { finish() })
            }
        }
    }

    // This full-screen host runs in the main process but backgrounds MainActivity, stopping its
    // resource collectors. Hold the main process resumed (Tor/relays/AUTH) while it's in front so the
    // embedded web client keeps its network up, then release on background to resume normal scaling.
    override fun onResume() {
        super.onResume()
        SandboxForegroundHold.acquire()
    }

    override fun onPause() {
        SandboxForegroundHold.release()
        super.onPause()
    }

    companion object {
        private const val EXTRA_URL = "url"

        fun intent(
            context: Context,
            url: String,
        ): Intent =
            Intent(context, BrowserHostActivity::class.java)
                .putExtra(EXTRA_URL, url)
                // The data Uri gives each app a distinct task identity for documentLaunchMode=intoExisting:
                // re-opening the same URL returns to its existing task; a different URL spawns a new one.
                .setData(Uri.parse(url))
    }
}

@RequiresApi(Build.VERSION_CODES.R)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserHostScreen(
    startUrl: String,
    onClose: () -> Unit,
) {
    var currentUrl by remember { mutableStateOf(startUrl) }
    var canGoBack by remember { mutableStateOf(false) }

    val proxyAvailable = remember { Amethyst.instance.torManager.activePortOrNull.value != null }
    var torOn by remember { mutableStateOf(proxyAvailable) }

    val controller =
        rememberBrowserController(startUrl = startUrl) { url, back ->
            if (url != "about:blank") currentUrl = url
            canGoBack = back
        }

    // In-page back first; once there's no page history, the system back closes the activity.
    BackHandler(enabled = canGoBack) { controller.back() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(MaterialSymbols.AutoMirrored.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                title = {
                    Text(
                        text = hostOf(currentUrl),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (proxyAvailable) {
                        TorToggleButton(torOn) {
                            torOn = !torOn
                            controller.setTor(torOn)
                        }
                    }
                    IconButton(onClick = { controller.reload() }) {
                        Icon(MaterialSymbols.Refresh, contentDescription = stringResource(R.string.browser_reload))
                    }
                },
            )
        },
    ) { padding ->
        EmbeddedBrowserSurface(
            controller = controller,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        )
    }
}

/** The host of [url] for the read-only title, falling back to the raw string. */
private fun hostOf(url: String): String = runCatching { Uri.parse(url).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: url
