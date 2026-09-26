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
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome.Action
import com.vitorpamplona.amethyst.commons.browser.BrowserChrome.SectionKind
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * The full-screen surfaces' **top pull-down pill** — the plain-View twin of the embedded tabs' Compose
 * `TopControlSheet`. Collapsed it's a small grabber centered at the top edge, out of the corner where a site
 * puts its own avatar/menu. Pulled down (or tapped) it shows, like a Chrome PWA's app menu:
 *
 * - a header with the page title, its origin + connection badge (tap: page info; long-press: copy link),
 *   and a close button;
 * - the icon row (back · forward · reload/stop · star · share);
 * - the menu rows, then Privacy and Developer groups.
 *
 * *Which* actions appear, and in what order, comes from [BrowserChrome] — the same source the Compose sheet
 * uses — and each action's icon/label from [BrowserChromeLabels]. This class only draws them. The page
 * can't draw over it. Built in code (no XML, no Compose/Material) because `:nappletHost` stays light.
 */
@SuppressLint("UseSwitchCompatOrMaterialCode", "ViewConstructor")
class NappletControlSheet(
    context: Context,
    initialState: BrowserChrome.State,
    title: String,
    private val listener: Listener,
    isFavoriteInitially: Boolean = false,
    /** Shows the header's close button (finishing the window). */
    private val showClose: Boolean = true,
) : LinearLayout(context) {
    /** What the sheet asks its host to do. Every row and icon ends up in [onAction]. */
    interface Listener {
        fun onAction(action: Action)

        /** The user typed an address into "Edit address" and pressed Go. */
        fun onNavigate(text: String) {}

        /** The text-size stepper moved to [percent]. */
        fun onTextZoom(percent: Int) {}

        /** The origin chip was tapped: show page info (or the access summary for sandboxed apps). */
        fun onOriginTap() {}

        fun onClose() {}
    }

    private val onSurface = resolveThemeColor(android.R.attr.textColorPrimary)
    private val dimmed = resolveThemeColor(android.R.attr.textColorSecondary)
    private val surface = resolveThemeColor(android.R.attr.colorBackground)
    private val accent = resolveThemeColor(android.R.attr.colorPrimary)
    private val glyphs: Typeface = BrowserGlyphs.typeface(context)

    var state: BrowserChrome.State = initialState
        private set
    private var title: String = title
    private var isFavorite = isFavoriteInitially
    private var desktopSite = false
    private var textZoom = BrowserChrome.DEFAULT_TEXT_ZOOM
    private var consoleShowing = false
    private var consoleCount = 0
    private var editingAddress = false

    private var expanded = false
    private val panel: LinearLayout
    private val grabber: View

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        panel =
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
            }
        addView(panel)
        grabber = buildGrabber()
        addView(grabber)
    }

    // ---- state updates from the host ----

    /**
     * The page navigated. Moving to another site swaps the title for that site's host until its page title
     * arrives ([updateTitle]), and marks the pin state unknown until the host answers ([setFavorite]).
     */
    fun updateUrl(url: String) {
        val previous = state.url
        if (url == previous) return
        state = state.copy(url = url)
        if (BrowserChrome.displayHost(url) != BrowserChrome.displayHost(previous)) title = BrowserChrome.displayHost(url)
        // Unknown until the host answers; "Add" meanwhile. The toggle sends an explicit target state, so a
        // tap in that window can only add (idempotent), never silently remove an existing pin.
        isFavorite = false
        refresh()
    }

    /** Shows the page's `<title>`, falling back to the host (WebView reports the URL for untitled pages). */
    fun updateTitle(pageTitle: String?) {
        val real = pageTitle?.trim()?.takeIf { it.isNotEmpty() && it != state.url }
        title = real ?: BrowserChrome.displayHost(state.url)
        refresh()
    }

    /** Applies the registry's answer for [url]; ignored once the user has moved on to another page. */
    fun setFavorite(
        url: String,
        favorite: Boolean,
    ) {
        if (url != state.url) return
        isFavorite = favorite
        refresh()
    }

    fun setNavigation(
        canGoBack: Boolean,
        canGoForward: Boolean,
    ) = update(state.copy(canGoBack = canGoBack, canGoForward = canGoForward))

    fun setLoading(loading: Boolean) = update(state.copy(isLoading = loading))

    fun setTor(on: Boolean) = update(state.copy(torOn = on))

    fun setDesktopSite(on: Boolean) {
        desktopSite = on
        refresh()
    }

    fun setTextZoom(percent: Int) {
        textZoom = percent
        refresh()
    }

    fun setConsoleShowing(showing: Boolean) {
        consoleShowing = showing
        refresh()
    }

    fun updateConsoleCount(count: Int) {
        consoleCount = count
        refresh()
    }

    private fun update(next: BrowserChrome.State) {
        if (next == state) return
        state = next
        refresh()
    }

    /** Rebuilds the open panel. Collapsed, nothing is drawn, so it waits for the next [expand]. */
    private fun refresh() {
        grabber.contentDescription = title
        if (expanded) render()
    }

    // ---- drawing ----

    private fun render() {
        panel.removeAllViews()
        panel.addView(header())
        panel.addView(iconRow())
        panel.addView(divider())
        val sections =
            LinearLayout(context).apply {
                orientation = VERTICAL
                BrowserChrome.sections(state).forEachIndexed { index, section ->
                    if (index > 0) addView(divider())
                    sectionTitle(section.kind)?.let { addView(sectionLabel(it)) }
                    section.actions.forEach { addView(row(it)) }
                }
            }
        panel.addView(
            MaxHeightScrollView(context, (resources.displayMetrics.heightPixels * 0.6f).toInt()).apply {
                isVerticalScrollBarEnabled = false
                addView(sections)
            },
        )
    }

    private fun sectionTitle(kind: SectionKind): String? =
        when (kind) {
            SectionKind.PAGE -> null
            SectionKind.PRIVACY -> context.getString(CommonsR.string.browser_section_privacy)
            SectionKind.DEVELOPER -> context.getString(CommonsR.string.browser_section_developer)
        }

    private fun header(): View =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(4), dp(6))
            val security = BrowserChrome.security(state)
            addView(iconView(BrowserChromeLabels.securitySymbol(security), BrowserChromeLabels.securityDrawable(security), dimmed, 20))
            if (editingAddress) {
                addView(addressField())
            } else {
                addView(
                    LinearLayout(context).apply {
                        orientation = VERTICAL
                        setPadding(dp(12), 0, dp(8), 0)
                        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                        isClickable = true
                        setOnClickListener {
                            collapse()
                            listener.onOriginTap()
                        }
                        if (!state.isSandbox) {
                            setOnLongClickListener {
                                listener.onAction(Action.COPY_LINK)
                                true
                            }
                        }
                        addView(
                            TextView(context).apply {
                                text = title
                                setTextColor(onSurface)
                                textSize = 16f
                                typeface = Typeface.DEFAULT_BOLD
                                maxLines = 1
                                ellipsize = TextUtils.TruncateAt.END
                            },
                        )
                        addView(
                            TextView(context).apply {
                                text =
                                    if (state.isSandbox) {
                                        context.getString(BrowserChromeLabels.securityLabel(security))
                                    } else {
                                        BrowserChrome.displayHost(state.url) + "  ·  " + context.getString(BrowserChromeLabels.securityLabel(security))
                                    }
                                setTextColor(dimmed)
                                textSize = 13f
                                maxLines = 1
                                ellipsize = TextUtils.TruncateAt.MIDDLE
                            },
                        )
                    },
                )
            }
            if (showClose) {
                addView(
                    glyphButton(BrowserChromeLabels.symbolFor(Action.STOP)!!, context.getString(CommonsR.string.browser_action_close), onSurface) {
                        collapse()
                        listener.onClose()
                    },
                )
            }
        }

    /** The rarely used editable address, swapped in for the origin chip by the "Edit address" row. */
    private fun addressField(): View {
        val field =
            EditText(context).apply {
                setText(state.url)
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
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) }
                setOnEditorActionListener { v, actionId, _ ->
                    if (actionId != EditorInfo.IME_ACTION_GO) return@setOnEditorActionListener false
                    val text =
                        v.text
                            ?.toString()
                            ?.trim()
                            .orEmpty()
                    if (text.isNotEmpty()) {
                        hideKeyboard(v)
                        editingAddress = false
                        collapse()
                        listener.onNavigate(text)
                    }
                    true
                }
            }
        field.post {
            field.requestFocus()
            context.getSystemService(InputMethodManager::class.java)?.showSoftInput(field, 0)
        }
        return field
    }

    private fun iconRow(): View =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(4))
            BrowserChrome.iconRow(state).forEach { action ->
                val enabled = BrowserChrome.isEnabled(state, action)
                val tint = if (action == Action.FAVORITE && isFavorite) accent else onSurface
                val button =
                    glyphButton(
                        BrowserChromeLabels.symbolFor(action)!!,
                        context.getString(BrowserChromeLabels.labelFor(action, isFavorite = isFavorite)),
                        tint,
                    ) {
                        collapse()
                        onRowAction(action)
                    }
                if (action == Action.FAVORITE && isFavorite) button.typeface = BrowserGlyphs.filledTypeface(context)
                button.isEnabled = enabled
                button.alpha = if (enabled) 1f else 0.35f
                button.layoutParams = LayoutParams(0, dp(48), 1f)
                addView(button)
            }
        }

    private fun row(action: Action): View {
        val label =
            when (action) {
                Action.BACK_TO_APP -> context.getString(CommonsR.string.browser_action_back_to_app, BrowserChrome.displayHost(state.startUrl))
                else -> context.getString(BrowserChromeLabels.labelFor(action, isFavorite = isFavorite, torOn = state.torOn == true))
            }
        val consoleLabel = if (action == Action.CONSOLE && consoleCount > 0) context.getString(CommonsR.string.browser_console_title, consoleCount) else label
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(10), dp(8), dp(10))
            addView(iconView(BrowserChromeLabels.symbolFor(action), BrowserChromeLabels.drawableFor(action), dimmed, 22))
            addView(
                TextView(context).apply {
                    text = consoleLabel
                    setTextColor(onSurface)
                    textSize = 15f
                    setPadding(dp(14), 0, 0, 0)
                    layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                },
            )
            if (action == Action.TEXT_SIZE) {
                addView(textSizeStepper())
            } else {
                isClickable = true
                setOnClickListener {
                    if (!BrowserChromeLabels.keepsSheetOpen(action)) collapse()
                    onRowAction(action)
                }
                if (BrowserChromeLabels.isToggle(action)) {
                    // Display-only: the whole row is the touch target, as in the Compose twin.
                    addView(
                        Switch(context).apply {
                            isChecked =
                                when (action) {
                                    Action.TOR -> state.torOn == true
                                    Action.DESKTOP_SITE -> desktopSite
                                    else -> consoleShowing
                                }
                            isClickable = false
                            isFocusable = false
                        },
                    )
                }
            }
        }
    }

    private fun textSizeStepper(): View =
        LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                glyphButton(MaterialSymbols.Remove, context.getString(CommonsR.string.browser_action_text_smaller), onSurface) {
                    textZoom = BrowserChrome.stepTextZoom(textZoom, larger = false)
                    listener.onTextZoom(textZoom)
                    refresh()
                },
            )
            addView(
                TextView(context).apply {
                    text = context.getString(CommonsR.string.browser_action_text_size_value, textZoom)
                    setTextColor(onSurface)
                    textSize = 14f
                    gravity = Gravity.CENTER
                    minWidth = dp(48)
                },
            )
            addView(
                glyphButton(MaterialSymbols.Add, context.getString(CommonsR.string.browser_action_text_larger), onSurface) {
                    textZoom = BrowserChrome.stepTextZoom(textZoom, larger = true)
                    listener.onTextZoom(textZoom)
                    refresh()
                },
            )
        }

    private fun onRowAction(action: Action) {
        when (action) {
            Action.EDIT_ADDRESS -> {
                editingAddress = true
                refresh()
            }
            Action.FAVORITE -> {
                isFavorite = !isFavorite
                listener.onAction(action)
            }
            Action.DESKTOP_SITE -> {
                desktopSite = !desktopSite
                listener.onAction(action)
            }
            Action.CONSOLE -> {
                consoleShowing = !consoleShowing
                listener.onAction(action)
            }
            else -> listener.onAction(action)
        }
    }

    /** The favorite state the user is asking for (read by the host right after a FAVORITE action). */
    fun wantsFavorite(): Boolean = isFavorite

    private fun sectionLabel(text: String): View =
        TextView(context).apply {
            this.text = text
            setTextColor(dimmed)
            textSize = 12f
            isAllCaps = true
            letterSpacing = 0.06f
            setPadding(dp(8), dp(10), dp(8), dp(2))
        }

    private fun iconView(
        symbol: MaterialSymbol?,
        drawable: Int?,
        tint: Int,
        sizeDp: Int,
    ): View =
        if (drawable != null) {
            ImageView(context).apply {
                setImageResource(drawable)
                setColorFilter(tint)
                layoutParams = LayoutParams(dp(sizeDp), dp(sizeDp))
            }
        } else {
            glyphText(symbol?.glyph.orEmpty(), tint, sizeDp).apply { layoutParams = LayoutParams(dp(sizeDp + 4), LayoutParams.WRAP_CONTENT) }
        }

    private fun glyphText(
        glyph: String,
        tint: Int,
        sizeDp: Int,
    ): TextView =
        TextView(context).apply {
            text = glyph
            typeface = glyphs
            setTextColor(tint)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp.toFloat())
            gravity = Gravity.CENTER
            includeFontPadding = false
        }

    private fun glyphButton(
        symbol: MaterialSymbol,
        description: String,
        tint: Int,
        onClick: () -> Unit,
    ): TextView =
        glyphText(symbol.glyph, tint, 22).apply {
            contentDescription = description
            tooltipText = description
            minWidth = dp(44)
            minHeight = dp(44)
            isClickable = true
            isFocusable = true
            background = selectableBackground()
            // Mirrored glyphs (back/forward) flip for right-to-left layouts, as Compose's autoMirror does.
            if (symbol.autoMirror && resources.configuration.layoutDirection == LAYOUT_DIRECTION_RTL) scaleX = -1f
            setOnClickListener { onClick() }
        }

    private fun selectableBackground() =
        TypedValue().let { tv ->
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
            ContextCompat.getDrawable(context, tv.resourceId)
        }

    private fun divider(): View =
        View(context).apply {
            setBackgroundColor(dimmed and 0x33FFFFFF)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(1)).apply { setMargins(0, dp(2), 0, dp(2)) }
        }

    private fun hideKeyboard(view: View) {
        context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(view.windowToken, 0)
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
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL }
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

    val isExpanded: Boolean get() = expanded

    fun expand() {
        if (expanded) return
        expanded = true
        render()
        panel.visibility = View.VISIBLE
    }

    fun collapse() {
        if (!expanded) return
        expanded = false
        if (editingAddress) {
            editingAddress = false
            hideKeyboard(panel)
        }
        panel.visibility = View.GONE
        panel.removeAllViews()
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

    /** A [ScrollView] that stops growing at [maxHeightPx], so a long menu scrolls instead of covering the page. */
    private class MaxHeightScrollView(
        context: Context,
        private val maxHeightPx: Int,
    ) : ScrollView(context) {
        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST))
        }
    }
}
