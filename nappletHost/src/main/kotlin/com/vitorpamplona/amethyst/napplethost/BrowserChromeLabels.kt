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
package com.vitorpamplona.amethyst.napplethost

import android.content.Context
import android.graphics.Typeface
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome.Action
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome.Security
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.napplet.NappletWebContract
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * How each [BrowserChrome.Action] looks: its Material Symbol (or, for Tor, the onion drawable) and its
 * label. Shared by both renderers of the top pill — the Compose sheet over embedded tabs and the
 * plain-View sheet in the full-screen browser — so an action has one icon and one name everywhere.
 */
object BrowserChromeLabels {
    /** The glyph for [action], or null when it is drawn from [drawableFor] instead. */
    fun symbolFor(action: Action): MaterialSymbol? =
        when (action) {
            Action.BACK -> MaterialSymbols.AutoMirrored.ArrowBack
            Action.FORWARD -> MaterialSymbols.AutoMirrored.ArrowForward
            Action.RELOAD -> MaterialSymbols.Refresh
            Action.STOP -> MaterialSymbols.Close
            Action.FAVORITE -> MaterialSymbols.Star
            Action.SHARE -> MaterialSymbols.Share
            Action.BACK_TO_APP -> MaterialSymbols.Home
            Action.COPY_LINK -> MaterialSymbols.ContentCopy
            Action.EDIT_ADDRESS -> MaterialSymbols.Edit
            Action.FIND_IN_PAGE -> MaterialSymbols.Search
            Action.TEXT_SIZE -> MaterialSymbols.FormatSize
            Action.DESKTOP_SITE -> MaterialSymbols.DesktopWindows
            Action.ADD_TO_HOME_SCREEN -> MaterialSymbols.AddToHomeScreen
            Action.OPEN_IN_BROWSER_APP -> MaterialSymbols.OpenInBrowser
            Action.OPEN_FULL_SCREEN -> MaterialSymbols.OpenInFull
            Action.TOR -> null
            Action.ACCESS_INFO -> MaterialSymbols.Info
            Action.SITE_SETTINGS -> MaterialSymbols.Tune
            Action.CONSOLE -> MaterialSymbols.Code
        }

    @DrawableRes
    fun drawableFor(action: Action): Int? = if (action == Action.TOR) R.drawable.ic_tor else null

    /** The label for [action]; [isFavorite] and [torOn] pick the stateful wording. */
    @StringRes
    fun labelFor(
        action: Action,
        isFavorite: Boolean = false,
        torOn: Boolean = false,
    ): Int =
        when (action) {
            Action.BACK -> CommonsR.string.browser_action_back
            Action.FORWARD -> CommonsR.string.browser_action_forward
            Action.RELOAD -> CommonsR.string.browser_action_reload
            Action.STOP -> CommonsR.string.browser_action_stop
            Action.FAVORITE -> if (isFavorite) CommonsR.string.browser_action_favorite_remove else CommonsR.string.browser_action_favorite_add
            Action.SHARE -> CommonsR.string.browser_action_share
            Action.BACK_TO_APP -> CommonsR.string.browser_action_back_to_app
            Action.COPY_LINK -> CommonsR.string.browser_action_copy_link
            Action.EDIT_ADDRESS -> CommonsR.string.browser_action_edit_address
            Action.FIND_IN_PAGE -> CommonsR.string.browser_action_find_in_page
            Action.TEXT_SIZE -> CommonsR.string.browser_action_text_size
            Action.DESKTOP_SITE -> CommonsR.string.browser_action_desktop_site
            Action.ADD_TO_HOME_SCREEN -> CommonsR.string.browser_action_add_to_home
            Action.OPEN_IN_BROWSER_APP -> CommonsR.string.browser_action_open_in_browser_app
            Action.OPEN_FULL_SCREEN -> CommonsR.string.browser_action_open_full_screen
            Action.TOR -> if (torOn) CommonsR.string.browser_action_tor_on else CommonsR.string.browser_action_tor_off
            Action.ACCESS_INFO -> CommonsR.string.browser_action_access_info
            Action.SITE_SETTINGS -> CommonsR.string.browser_action_site_settings
            Action.CONSOLE -> CommonsR.string.browser_console_title_short
        }

    /** Actions whose row carries an on/off switch. */
    fun isToggle(action: Action): Boolean = action == Action.TOR || action == Action.DESKTOP_SITE || action == Action.CONSOLE

    /** Actions that keep the sheet open when used (the text-size stepper, the address editor). */
    fun keepsSheetOpen(action: Action): Boolean = action == Action.TEXT_SIZE || action == Action.EDIT_ADDRESS

    fun securitySymbol(security: Security): MaterialSymbol? =
        when (security) {
            Security.TOR -> null
            Security.HTTPS -> MaterialSymbols.Lock
            Security.HTTP -> MaterialSymbols.NoEncryption
            Security.SANDBOX -> MaterialSymbols.Shield
        }

    @DrawableRes
    fun securityDrawable(security: Security): Int? = if (security == Security.TOR) R.drawable.ic_tor else null

    @StringRes
    fun securityLabel(security: Security): Int =
        when (security) {
            Security.TOR -> CommonsR.string.browser_security_tor
            Security.HTTPS -> CommonsR.string.browser_security_https
            Security.HTTP -> CommonsR.string.browser_security_http
            Security.SANDBOX -> CommonsR.string.browser_security_sandbox
        }
}

/**
 * The Material Symbols font the Compose UI draws its icons from, loaded for plain Views. It ships inside
 * `:commonsUI`'s compose resources (the same APK assets the napplet shim is read from), so the full-screen
 * browser's controls use the very same glyphs as the embedded tab's.
 */
object BrowserGlyphs {
    private const val FONT_PATH = "font/material_symbols_outlined.ttf"

    @Volatile private var cached: Typeface? = null

    @Volatile private var cachedFilled: Typeface? = null

    /** The same font with its FILL axis at 1 — a pinned page's solid star, as the Compose icon draws it. */
    fun filledTypeface(context: Context): Typeface =
        cachedFilled ?: synchronized(this) {
            cachedFilled ?: (
                runCatching {
                    Typeface
                        .Builder(context.assets, NappletWebContract.RESOURCE_ASSET_ROOT + FONT_PATH)
                        .setFontVariationSettings("'FILL' 1")
                        .build()
                }.getOrNull() ?: typeface(context)
            ).also { cachedFilled = it }
        }

    fun typeface(context: Context): Typeface =
        cached ?: synchronized(this) {
            cached ?: runCatching { Typeface.createFromAsset(context.assets, NappletWebContract.RESOURCE_ASSET_ROOT + FONT_PATH) }
                .onFailure { Log.w("BrowserGlyphs", "Material Symbols font missing from assets", it) }
                .getOrDefault(Typeface.DEFAULT)
                .also { cached = it }
        }
}
