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
package com.vitorpamplona.amethyst.service.payments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import com.vitorpamplona.amethyst.commons.model.payments.PaymentTargetTypes
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTarget
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the device can do with one `payto` target type. */
@Immutable
data class PayToAppInfo(
    /** An installed activity accepts the hand-off URI. */
    val resolves: Boolean,
    /** The chosen app's own name, null when no single default app applies. */
    val label: String? = null,
    /** The chosen app's launcher icon, already masked and sized. */
    val icon: ImageBitmap? = null,
)

/**
 * Answers "can anything on this phone open this payment target, and what does it
 * look like?" for the zap picker's hand-off chip.
 *
 * **Package visibility.** `targetSdk` is 37, so on Android 11+ every query here
 * returns nothing unless `AndroidManifest.xml`'s `<queries>` block declares the
 * scheme. The declarations are deliberately `<intent>` filters rather than
 * `QUERY_ALL_PACKAGES`, which is policy-restricted on Play.
 *
 * **Why this is not a per-post lookup.** The chip only ever appears for
 * protocols the *sender themself* publishes, so [warm] probes the sender's own
 * target list — a handful of entries, refreshed when that list changes or the
 * app returns to the foreground. Feed rendering never triggers a probe; it only
 * reads [peek].
 *
 * The result is a [StateFlow] rather than a plain map because a bare map write
 * is invisible to Compose: the chip would stay missing until some unrelated
 * recomposition happened to run.
 */
object PayToAppAvailability {
    /** A host no registrar can delegate (RFC 2606), so only catch-all browsers match it. */
    private const val CONTROL_URL = "https://probe.invalid/"

    private val state = MutableStateFlow<Map<String, PayToAppInfo>>(emptyMap())
    val flow: StateFlow<Map<String, PayToAppInfo>> = state.asStateFlow()

    /** Synchronous read for `RailCapabilityResolver.peek`, which runs inside `remember {}`. */
    fun peek(rawType: String): PayToAppInfo? = state.value[PaymentTargetTypes.probeKeyFor(rawType)]

    /**
     * Probes every distinct type in [myTargets] and replaces the cache.
     *
     * Blocking: `loadIcon` reads the target APK's resources. Call from `Dispatchers.IO`.
     * [iconPx] is the size the chip draws at — decoding once here is what keeps
     * the icon out of the composition path.
     */
    fun warm(
        context: Context,
        myTargets: List<PaymentTarget>,
        iconPx: Int,
    ) {
        val pm = context.packageManager
        val keys =
            myTargets
                .asSequence()
                .map { it.type }
                .filterNot { PaymentTargetTypes.isWalletCovered(it) }
                .distinctBy { PaymentTargetTypes.probeKeyFor(it) }
                .toList()

        if (keys.isEmpty()) {
            state.value = emptyMap()
            return
        }

        val browsers = browserPackages(pm)
        state.value =
            keys.associate { type ->
                PaymentTargetTypes.probeKeyFor(type) to probe(pm, type, browsers, iconPx)
            }
    }

    /** Drops everything, so the next [warm] re-reads a changed set of installed apps. */
    fun clear() {
        state.value = emptyMap()
    }

    private fun probe(
        pm: PackageManager,
        rawType: String,
        browsers: Set<String>,
        iconPx: Int,
    ): PayToAppInfo {
        val intent = viewIntent(PaymentTargetTypes.probeKeyFor(rawType))
        val handlers = queryActivities(pm, intent)
        val isWeb = PaymentTargetTypes.isWebTarget(rawType)

        // A browser resolves any https:// URI, so for web targets "something
        // resolves" is trivially true and tells us nothing. Gate them open, but
        // only claim an app — and therefore an icon — when a handler exists that
        // is not merely a browser. Chrome's icon on a Venmo chip is worse than none.
        val appHandlers = if (isWeb) handlers.filterNot { it.packageName() in browsers } else handlers
        val resolves = isWeb || handlers.isNotEmpty()
        if (appHandlers.isEmpty()) return PayToAppInfo(resolves = resolves)

        val chosen = defaultActivity(pm, intent, appHandlers) ?: return PayToAppInfo(resolves = resolves)

        return PayToAppInfo(
            resolves = resolves,
            label = runCatching { chosen.loadLabel(pm).toString() }.getOrNull(),
            icon = loadIcon(pm, chosen, iconPx),
        )
    }

    /**
     * The single app the hand-off would open, or null when the system would show
     * a chooser instead. With several handlers and no user default, Android hands
     * back its own `ResolverActivity` — there is no app to name there, so the chip
     * falls back to the brand-coloured glyph.
     */
    private fun defaultActivity(
        pm: PackageManager,
        intent: Intent,
        handlers: List<ResolveInfo>,
    ): ResolveInfo? {
        if (handlers.size == 1) return handlers.first()
        val preferred =
            runCatching { pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) }.getOrNull()
                ?: return null
        val pkg = preferred.packageName()
        if (pkg == "android" || preferred.activityInfo?.name?.contains("ResolverActivity") == true) return null
        return handlers.firstOrNull { it.packageName() == pkg }
    }

    /**
     * minSdk is 26, so any icon may be an `AdaptiveIconDrawable`: a 108x108 canvas
     * whose outer margin the launcher masks away. Rasterising it at the chip's own
     * size — rather than at its intrinsic size — is what stops the logo from
     * arriving as a speck floating in that bleed. The circular mask is applied by
     * the composable, matching how a launcher presents the same icon.
     */
    private fun loadIcon(
        pm: PackageManager,
        info: ResolveInfo,
        px: Int,
    ): ImageBitmap? =
        runCatching {
            info.loadIcon(pm).toBitmap(px, px).asImageBitmap()
        }.onFailure {
            Log.w("PayToAppAvailability") { "Could not load icon for ${info.packageName()}: ${it.message}" }
        }.getOrNull()

    @SuppressLint("QueryPermissionsNeeded")
    private fun queryActivities(
        pm: PackageManager,
        intent: Intent,
    ): List<ResolveInfo> = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())

    /** Packages that answer a URL nobody can own — i.e. general-purpose browsers. */
    @SuppressLint("QueryPermissionsNeeded")
    private fun browserPackages(pm: PackageManager): Set<String> = queryActivities(pm, viewIntent(CONTROL_URL)).mapNotNull { it.packageName() }.toSet()

    private fun viewIntent(uri: String) =
        Intent(Intent.ACTION_VIEW, uri.toUri()).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

    private fun ResolveInfo.packageName(): String? = activityInfo?.packageName
}
