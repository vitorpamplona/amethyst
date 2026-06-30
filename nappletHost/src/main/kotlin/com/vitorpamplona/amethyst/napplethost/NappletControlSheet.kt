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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * The full-screen sandbox surfaces' **top pull-down sheet** — the native-View twin of the embedded
 * tabs' Compose `TopControlSheet`. Collapsed it's just a small grabber centered at the very top edge,
 * out of the corner where a site puts its own avatar/menu. Pull it down (or tap) to reveal the page's
 * controls: route over Tor, reload, and "what it can access" (sandboxed apps).
 *
 * Built in code (no XML) because `:nappletHost` hosts plain Android `View`s, not Compose, and must stay
 * dependency-light. Add it to a `FrameLayout` parent at `Gravity.TOP` filling the width; it manages its
 * own expand/collapse.
 */
@SuppressLint("UseSwitchCompatOrMaterialCode") // plain framework Switch: :nappletHost is Compose/Material-free
class NappletControlSheet(
    context: Context,
    private val title: String,
    private val isSandbox: Boolean,
    private val onReload: () -> Unit,
    torInitiallyOn: Boolean?,
    private val onToggleTor: (Boolean) -> Unit = {},
    // When non-null, the Tor row taps through to this (e.g. a confirm dialog that relaunches) instead of
    // toggling inline — used by the nSite host, where switching routing rebuilds the whole session.
    private val onNetworkTap: (() -> Unit)? = null,
    private val onInfo: (() -> Unit)? = null,
    // The live URL of a plain-website browser. Non-null only for the direct-WebView browser (never an
    // nsite/napplet), where it renders an editable address row; [onNavigate] loads what the user types.
    liveUrl: String? = null,
    private val onNavigate: ((String) -> Unit)? = null,
    // When non-null, a "Console" toggle row is added to the pull-down sheet. The callback is invoked with
    // the new visibility each time the user flips it; the count label is updated via [updateConsoleCount].
    private val onConsole: ((Boolean) -> Unit)? = null,
    // When non-null, a favorite toggle row is shown; called with the current URL and new isFavorite state.
    isFavoriteInitially: Boolean = false,
    private val onFavoriteToggle: ((url: String, isFavorite: Boolean) -> Unit)? = null,
) : LinearLayout(context) {
    private val onSurface = resolveThemeColor(android.R.attr.textColorPrimary)
    private val dimmed = resolveThemeColor(android.R.attr.textColorSecondary)
    private val surface = resolveThemeColor(android.R.attr.colorBackground)

    private var expanded = false
    private var torOn = torInitiallyOn
    private var currentUrl = liveUrl
    private var isFavorite = isFavoriteInitially
    private var consoleShowing = false

    private val panel: LinearLayout
    private var torLabel: TextView? = null
    private var torSwitch: Switch? = null
    private var addressField: EditText? = null
    private var securityGlyph: TextView? = null
    private var consoleLabel: TextView? = null
    private var consoleSwitch: Switch? = null
    private var favoriteLabel: TextView? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL

        panel = buildPanel().also { addView(it) }
        addView(buildGrabber())
    }

    private fun buildPanel(): LinearLayout =
        LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = View.GONE
            elevation = dp(6).toFloat()
            background =
                GradientDrawable().apply {
                    cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat())
                    setColor(surface)
                }
            setPadding(dp(8), dp(6), dp(8), dp(10))

            addView(titleRow())
            // Browser only: an editable address bar showing the live URL + a security glyph. nsite/napplet
            // hosts pass no navigate callback, so they never get one.
            onNavigate?.let { addView(addressRow(currentUrl.orEmpty(), it)) }
            addView(divider())
            if (torOn != null) addView(torRow())
            addView(
                actionRow("↻", context.getString(R.string.napplet_chrome_reload)) {
                    collapse()
                    onReload()
                },
            )
            onInfo?.let { info ->
                addView(
                    actionRow("ⓘ", context.getString(R.string.napplet_chrome_permissions_desc)) {
                        collapse()
                        info()
                    },
                )
            }
            onConsole?.let {
                val label =
                    TextView(context).apply {
                        text = context.getString(CommonsR.string.browser_console_title_short)
                        setTextColor(onSurface)
                        textSize = 15f
                        setPadding(dp(8), 0, 0, 0)
                        // Weight 1 so the label fills and shoves the Switch to the end, like the Tor row.
                        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                    }
                consoleLabel = label
                // Display-only switch (the whole row is the touch target), matching the Tor row + Compose twin.
                val toggle =
                    Switch(context).apply {
                        isChecked = consoleShowing
                        isClickable = false
                        isFocusable = false
                    }
                consoleSwitch = toggle
                addView(
                    LinearLayout(context).apply {
                        orientation = HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(8), dp(10), dp(8), dp(10))
                        isClickable = true
                        setOnClickListener { toggleConsole() }
                        addView(
                            TextView(context).apply {
                                text = ">"
                                setTextColor(dimmed)
                                textSize = 18f
                                width = dp(28)
                                gravity = Gravity.CENTER
                                typeface = Typeface.MONOSPACE
                            },
                        )
                        addView(label)
                        addView(toggle)
                    },
                )
            }
            onFavoriteToggle?.let {
                val label =
                    TextView(context).apply {
                        text = context.getString(if (isFavorite) R.string.browser_favorite_remove else R.string.browser_favorite_add)
                        setTextColor(onSurface)
                        textSize = 15f
                        setPadding(dp(8), 0, 0, 0)
                    }
                favoriteLabel = label
                addView(
                    LinearLayout(context).apply {
                        orientation = HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(8), dp(10), dp(8), dp(10))
                        isClickable = true
                        setOnClickListener { toggleFavorite() }
                        addView(
                            TextView(context).apply {
                                text = "★"
                                setTextColor(dimmed)
                                textSize = 18f
                                width = dp(28)
                                gravity = Gravity.CENTER
                            },
                        )
                        addView(label)
                    },
                )
            }
        }

    private fun titleRow(): View =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(
                TextView(context).apply {
                    text = if (isSandbox) "🛡" else "🌐"
                    textSize = 16f
                },
            )
            addView(
                TextView(context).apply {
                    text = title
                    setTextColor(onSurface)
                    textSize = 16f
                    maxLines = 1
                    setPadding(dp(10), 0, 0, 0)
                },
            )
        }

    /**
     * The browser address bar: a security glyph (🧅 Tor / 🔒 https / 🌐 plain) + an editable URL field.
     * Pressing Go hands the trimmed text to [onNavigate] (normalized by the caller) and collapses the sheet.
     */
    private fun addressRow(
        initial: String,
        onNavigate: (String) -> Unit,
    ): View {
        val glyph =
            TextView(context).apply {
                text = securityGlyphFor(initial)
                textSize = 15f
                width = dp(28)
                gravity = Gravity.CENTER
            }
        securityGlyph = glyph
        val field =
            EditText(context).apply {
                setText(initial)
                setTextColor(onSurface)
                setHintTextColor(dimmed)
                hint = context.getString(CommonsR.string.browser_address_hint)
                contentDescription = context.getString(CommonsR.string.browser_address_hint)
                textSize = 15f
                isSingleLine = true
                setSelectAllOnFocus(true)
                background = null
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                imeOptions = EditorInfo.IME_ACTION_GO
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                setOnEditorActionListener { v, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_GO) {
                        val text =
                            v.text
                                ?.toString()
                                ?.trim()
                                .orEmpty()
                        if (text.isNotEmpty()) {
                            clearFocus()
                            collapse()
                            onNavigate(text)
                        }
                        true
                    } else {
                        false
                    }
                }
            }
        addressField = field
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            addView(glyph)
            addView(field)
        }
    }

    /** Updates the count shown in the Console row label so the user sees how many messages are waiting. */
    fun updateConsoleCount(count: Int) {
        consoleLabel?.text =
            if (count > 0) {
                context.getString(CommonsR.string.browser_console_title, count)
            } else {
                context.getString(CommonsR.string.browser_console_title_short)
            }
    }

    /** Refreshes the address bar + security glyph as the page navigates. No-op without an address row. */
    fun updateUrl(url: String) {
        currentUrl = url
        // Don't fight the user while they're editing the field.
        addressField?.takeIf { !it.hasFocus() }?.setText(url)
        securityGlyph?.text = securityGlyphFor(url)
        // Reset favorite state for the new URL (we don't know if it's a favorite without a round-trip).
        if (onFavoriteToggle != null) {
            isFavorite = false
            favoriteLabel?.text = context.getString(R.string.browser_favorite_add)
        }
    }

    private fun toggleFavorite() {
        val url = currentUrl?.takeIf { it.isNotBlank() } ?: return
        isFavorite = !isFavorite
        favoriteLabel?.text = context.getString(if (isFavorite) R.string.browser_favorite_remove else R.string.browser_favorite_add)
        collapse()
        onFavoriteToggle?.invoke(url, isFavorite)
    }

    private fun securityGlyphFor(url: String): String =
        when {
            torOn == true -> "🧅" // 🧅 routed over Tor
            url.startsWith("https://", ignoreCase = true) -> "🔒" // 🔒 secure
            else -> "🌐" // 🌐 plain http
        }

    private fun torRow(): View {
        // Steady, muted icon (the Switch carries the on/off state) — matches the Compose twin, where the
        // lock icon is a constant onSurfaceVariant tint and the Switch is the state indicator.
        val icon =
            ImageView(context).apply {
                setImageResource(R.drawable.ic_tor)
                setColorFilter(dimmed)
                layoutParams = LayoutParams(dp(22), dp(22))
            }
        val label =
            TextView(context).apply {
                text = context.getString(if (torOn == true) R.string.napplet_net_tor_label else R.string.napplet_net_open_label)
                setTextColor(onSurface)
                textSize = 15f
                setPadding(dp(14), 0, 0, 0)
                // Weight 1 so the label fills and shoves the Switch to the end, like the Compose row.
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            }
        // Display-only: the whole row is the touch target (parity with the Compose row, whose Switch and
        // row both route to the same onToggle), so the Switch itself doesn't take clicks.
        val toggle =
            Switch(context).apply {
                isChecked = torOn == true
                isClickable = false
                isFocusable = false
            }
        torLabel = label
        torSwitch = toggle
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            isClickable = true
            setOnClickListener {
                if (onNetworkTap != null) {
                    collapse()
                    onNetworkTap.invoke()
                } else {
                    toggleTor()
                }
            }
            addView(icon)
            addView(label)
            addView(toggle)
        }
    }

    private fun toggleTor() {
        val next = !(torOn ?: return)
        torOn = next
        torSwitch?.isChecked = next
        torLabel?.text = context.getString(if (next) R.string.napplet_net_tor_label else R.string.napplet_net_open_label)
        securityGlyph?.text = securityGlyphFor(currentUrl.orEmpty())
        onToggleTor(next)
    }

    private fun toggleConsole() {
        consoleShowing = !consoleShowing
        consoleSwitch?.isChecked = consoleShowing
        // Collapse the top sheet on toggle, like the Compose twin, so the bottom console isn't hidden behind it.
        collapse()
        onConsole?.invoke(consoleShowing)
    }

    private fun actionRow(
        glyph: String,
        label: String,
        onClick: () -> Unit,
    ): View =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // Same vertical rhythm as the Tor row and the Compose twin's rows.
            setPadding(dp(8), dp(10), dp(8), dp(10))
            isClickable = true
            setOnClickListener { onClick() }
            addView(
                TextView(context).apply {
                    text = glyph
                    setTextColor(dimmed)
                    textSize = 18f
                    width = dp(28)
                    gravity = Gravity.CENTER
                },
            )
            addView(
                TextView(context).apply {
                    text = label
                    setTextColor(onSurface)
                    textSize = 15f
                    setPadding(dp(8), 0, 0, 0)
                },
            )
        }

    private fun divider(): View =
        View(context).apply {
            setBackgroundColor(dimmed and 0x33FFFFFF.toInt())
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(1))
        }

    /** The grabber: a small rounded bar centered at the top edge; tap toggles, vertical drag opens/closes. */
    @SuppressLint("ClickableViewAccessibility")
    private fun buildGrabber(): View {
        val bar =
            View(context).apply {
                background =
                    GradientDrawable().apply {
                        cornerRadius = dp(3).toFloat()
                        setColor(dimmed and 0x99FFFFFF.toInt())
                    }
                layoutParams = LayoutParams(dp(36), dp(5))
            }
        return LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            // Wrap the grabber (a vertical LinearLayout defaults its children to MATCH_PARENT width, which
            // would stretch this chip's background across the whole screen) and center it under the parent.
            layoutParams =
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            setPadding(dp(16), dp(7), dp(16), dp(7))
            background =
                GradientDrawable().apply {
                    cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat())
                    setColor(withAlpha(surface, 0.6f))
                }
            isClickable = true
            contentDescription = title
            addView(bar)

            var downY = 0f
            var dragged = false
            setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downY = ev.rawY
                        dragged = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = ev.rawY - downY
                        if (dy > dp(8)) {
                            expand()
                            dragged = true
                        } else if (dy < -dp(8)) {
                            collapse()
                            dragged = true
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragged) {
                            if (expanded) {
                                collapse()
                            } else {
                                expand()
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun expand() {
        if (expanded) return
        expanded = true
        panel.visibility = View.VISIBLE
    }

    private fun collapse() {
        if (!expanded) return
        expanded = false
        panel.visibility = View.GONE
    }

    private fun withAlpha(
        color: Int,
        alpha: Float,
    ): Int = (color and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)

    private fun resolveThemeColor(attr: Int): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ContextCompat.getColor(context, tv.resourceId) else tv.data.takeIf { it != 0 } ?: Color.GRAY
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
