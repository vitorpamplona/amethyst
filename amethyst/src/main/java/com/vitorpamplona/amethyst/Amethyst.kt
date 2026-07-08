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
package com.vitorpamplona.amethyst

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.Build
import com.vitorpamplona.amethyst.favorites.BrowserHistoryRegistry
import com.vitorpamplona.amethyst.favorites.BrowserIconRegistry
import com.vitorpamplona.amethyst.favorites.FavoriteAppsRegistry
import com.vitorpamplona.amethyst.napplet.WebAppNetworkRegistry
import com.vitorpamplona.amethyst.service.logging.Logging
import com.vitorpamplona.amethyst.service.nests.AppForegroundRecycleHook
import com.vitorpamplona.amethyst.ui.screen.loggedIn.embed.EmbeddedTabHost
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.LogLevel
import java.io.File

/**
 * The Android [Application]. **Heads-up: this app runs in TWO OS processes**, and Android
 * instantiates this same class in each of them:
 *
 * - **main** — the normal app (UI, account, signer, `LocalCache`, relay client). [instance]
 *   ([AppModules]) is built here in [onCreate].
 * - **`:napplet`** — the sandboxed WebView host for NIP-5D napplets / NIP-5A nSites
 *   (`NappletHostActivity`, declared `android:process=":napplet"`). It holds **no** account or keys.
 *   [onCreate] early-returns here, so [instance] is **left unset** and any access throws.
 *
 * There is no per-process Application in Android — `android:name` is one class for the whole package —
 * so the process-name guard ([isNappletSandbox]) is how the two are kept apart.
 *
 * **Processes don't share memory:** every `object`/companion/`static` (e.g. `LocalCache`,
 * `NappletLaunchRegistry`) is a *separate copy per process*. Don't assume [instance] exists off the
 * main process, and don't try to share state via a singleton across the boundary — use Messenger IPC.
 */
class Amethyst : Application() {
    init {
        Log.minLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.ERROR
        Log.d("AmethystApp") { "Creating App $this" }
    }

    companion object {
        lateinit var instance: AppModules
            private set
    }

    /**
     * Android instantiates this single Application class in EVERY process (there's no per-process
     * Application in the manifest). The sandboxed napplet host runs in `:napplet`, where we must not
     * build [AppModules] — so this is computed once and gates every lifecycle callback that would
     * otherwise touch the unset [instance].
     */
    private val isNappletSandbox by lazy { isNappletSandboxProcess() }

    override fun onCreate() {
        super.onCreate()
        Log.d("AmethystApp") { "onCreate $this" }

        // Application.onCreate runs in every process. The sandboxed napplet host
        // (`:napplet`) must NOT build AppModules — that would load the account and
        // construct the signer in the very process we keep secret-free. The host
        // is self-contained (WebView + IPC), so we skip all app init there and
        // leave `instance` unset; any accidental use fails fast.
        if (isNappletSandbox) {
            Log.d("AmethystApp") { "Skipping AppModules init in sandbox process" }
            return
        }

        instance = AppModules(this)

        // Hydrate the device-local favorite-apps list (main process only; the sandbox never reads it).
        FavoriteAppsRegistry.init(this)

        // Hydrate the device-local browser visit history (main process only; feeds the omnibox suggestions).
        BrowserHistoryRegistry.init(this)

        // Index device-local captured favicons (main process only; decorates favorites + suggestions).
        BrowserIconRegistry.init(this)

        // Hydrate the per-web-client Tor routing preferences so a site opted out of Tor (some reject Tor
        // exits) starts on the open web without first flashing a failed Tor load.
        WebAppNetworkRegistry.init(this)

        // After-background foreground recycle: when the app returns to
        // the foreground after spending more than ~5 s in the
        // background, publish a network-change event so every active
        // NestViewModel recycles its underlying QUIC session. Covers
        // the case where Android reclaims our UDP socket FD while
        // backgrounded — the connectivity callback in
        // `NestForegroundService` doesn't fire there because the
        // network itself is still up. See `AppForegroundRecycleHook`'s
        // kdoc for the threshold rationale.
        registerActivityLifecycleCallbacks(AppForegroundRecycleHook())

        if (isDebug) {
            Logging.setup()
            // Auto-enable the Nests session-trace recorder in debug
            // builds so two-phone repros can be captured via
            //   adb logcat -s NestsTraceJsonl:D -v raw > nest-trace.jsonl
            // without rebuilding to flip a flag. Off in release.
            com.vitorpamplona.nestsclient.trace.NestsTrace
                .setRecording(true)
        }

        instance.initiate(this)
    }

    /** True when this process is the isolated `:napplet` WebView host (see AndroidManifest). */
    private fun isNappletSandboxProcess(): Boolean {
        val name =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                getProcessName()
            } else {
                runCatching { File("/proc/self/cmdline").readText().substringBefore('\u0000').trim() }.getOrNull()
            }
        return name?.endsWith(":napplet") == true
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d("AmethystApp") { "onTerminate $this" }
        // The sandbox process never built AppModules; `instance` is unset there.
        if (isNappletSandbox) return
        instance.terminate(this)
    }

    /**
     * Release memory when the UI becomes hidden or when system resources become low.
     *
     * @param level the memory-related event that was raised.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d("AmethystApp") { "onTrimMemory $level" }
        // onTrimMemory IS delivered to the `:napplet` process on real devices, where `instance`
        // was never initialized — guard so a trim callback can't crash the sandbox.
        if (isNappletSandbox) return
        instance.trim(level)
        // Drop warm embedded tab sessions under genuine memory pressure (decision: keep warm until the
        // user or Android reclaims them). Since API 34 the OS only delivers UI_HIDDEN and BACKGROUND:
        // BACKGROUND means the process is on the system LRU list (real reclaim pressure), while UI_HIDDEN
        // fires on every app switch — so evict only at BACKGROUND and above, letting a pinned tab survive
        // a plain backgrounding. R+ only.
        val pressure = level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        if (pressure && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            EmbeddedTabHost.evictAll()
        }
    }
}
