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
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * The find-in-page bar for the full-screen surfaces, docked at the bottom edge (the top edge belongs to the
 * pill's grabber, and the bottom keeps it off the page's own header). Drives [WebView.findAllAsync] /
 * [WebView.findNext] on [webView] and shows "n/m". Hidden until [show].
 */
@SuppressLint("ViewConstructor")
class BrowserFindBar(
    context: Context,
    private val webViewProvider: () -> WebView?,
    private val onClosed: () -> Unit = {},
) : LinearLayout(context) {
    private val onSurface = color(android.R.attr.textColorPrimary)
    private val dimmed = color(android.R.attr.textColorSecondary)
    private val glyphs = BrowserGlyphs.typeface(context)

    private val field: EditText
    private val count: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
        elevation = dp(8).toFloat()
        background =
            GradientDrawable().apply {
                cornerRadii = floatArrayOf(dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat(), 0f, 0f, 0f, 0f)
                setColor(color(android.R.attr.colorBackground))
            }
        setPadding(dp(12), dp(4), dp(4), dp(4))
        field =
            EditText(context).apply {
                hint = context.getString(CommonsR.string.browser_find_hint)
                setTextColor(onSurface)
                setHintTextColor(dimmed)
                background = null
                isSingleLine = true
                textSize = 15f
                inputType = InputType.TYPE_CLASS_TEXT
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                addTextChangedListener(
                    object : TextWatcher {
                        override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int,
                        ) = Unit

                        override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int,
                        ) = Unit

                        override fun afterTextChanged(s: Editable?) = search(s?.toString().orEmpty())
                    },
                )
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        webViewProvider()?.findNext(true)
                        true
                    } else {
                        false
                    }
                }
            }
        count =
            TextView(context).apply {
                setTextColor(dimmed)
                textSize = 13f
                setPadding(dp(8), 0, dp(4), 0)
            }
        addView(field)
        addView(count)
        addView(button(MaterialSymbols.KeyboardArrowUp, CommonsR.string.browser_find_previous) { webViewProvider()?.findNext(false) })
        addView(button(MaterialSymbols.KeyboardArrowDown, CommonsR.string.browser_find_next) { webViewProvider()?.findNext(true) })
        addView(button(MaterialSymbols.Close, CommonsR.string.browser_find_close) { hide() })
    }

    val isShowing: Boolean get() = visibility == View.VISIBLE

    fun show() {
        webViewProvider()?.setFindListener { active, total, _ ->
            count.text =
                if (total > 0) {
                    context.getString(CommonsR.string.browser_find_count, active + 1, total)
                } else if (field.text.isNullOrEmpty()) {
                    ""
                } else {
                    "0/0"
                }
        }
        visibility = View.VISIBLE
        field.requestFocus()
        field.selectAll()
        context.getSystemService(InputMethodManager::class.java)?.showSoftInput(field, 0)
    }

    fun hide() {
        if (!isShowing) return
        visibility = View.GONE
        context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(field.windowToken, 0)
        webViewProvider()?.apply {
            clearMatches()
            setFindListener(null)
        }
        count.text = ""
        onClosed()
    }

    private fun search(query: String) {
        val wv = webViewProvider() ?: return
        if (query.isEmpty()) {
            wv.clearMatches()
            count.text = ""
        } else {
            wv.findAllAsync(query)
        }
    }

    private fun button(
        symbol: MaterialSymbol,
        label: Int,
        onClick: () -> Unit,
    ): View =
        TextView(context).apply {
            text = symbol.glyph
            typeface = glyphs
            setTextColor(onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22f)
            gravity = Gravity.CENTER
            minWidth = dp(44)
            minHeight = dp(44)
            contentDescription = context.getString(label)
            tooltipText = contentDescription
            isClickable = true
            background =
                TypedValue().let { tv ->
                    context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
                    ContextCompat.getDrawable(context, tv.resourceId)
                }
            setOnClickListener { onClick() }
        }

    private fun color(attr: Int): Int {
        val tv = TypedValue()
        context.theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ContextCompat.getColor(context, tv.resourceId) else tv.data.takeIf { it != 0 } ?: Color.GRAY
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
