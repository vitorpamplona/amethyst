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
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.commons.R as CommonsR

/**
 * The full-screen browser's **bottom pull-up sheet** for JavaScript console output. Collapsed it's
 * a small grabber at the bottom edge, symmetric to [NappletControlSheet]'s top grabber. Pull it up
 * (or tap) to reveal a scrollable log of [console.log / warn / error / debug] messages captured
 * from the page via `WebChromeClient.onConsoleMessage`. Capped at [MAX_ENTRIES] entries (oldest
 * dropped on overflow). Built in plain Views like [NappletControlSheet] — no Compose/Material.
 */
@SuppressLint("UseSwitchCompatOrMaterialCode")
class NappletConsolePanel(
    context: Context,
) : LinearLayout(context) {
    private val onSurface = resolveThemeColor(android.R.attr.textColorPrimary)
    private val dimmed = resolveThemeColor(android.R.attr.textColorSecondary)
    private val surface = resolveThemeColor(android.R.attr.colorBackground)

    private var expanded = false
    private val panel: LinearLayout
    private lateinit var logContainer: LinearLayout
    private lateinit var scrollView: ScrollView

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
                    cornerRadii = floatArrayOf(dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat(), 0f, 0f, 0f, 0f)
                    setColor(surface)
                }
            setPadding(dp(8), dp(10), dp(8), dp(6))
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(220))

            val headerRow =
                LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), 0, dp(4), dp(4))
                    addView(
                        TextView(context).apply {
                            text = context.getString(CommonsR.string.browser_console_title_short)
                            setTextColor(dimmed)
                            textSize = 12f
                            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                        },
                    )
                    addView(
                        TextView(context).apply {
                            text = context.getString(CommonsR.string.browser_console_clear)
                            setTextColor(dimmed)
                            textSize = 12f
                            setPadding(dp(12), dp(6), dp(12), dp(6))
                            isClickable = true
                            setOnClickListener { clearLogs() }
                        },
                    )
                }

            val container =
                LinearLayout(context).apply {
                    orientation = VERTICAL
                }
            logContainer = container

            val sv =
                ScrollView(context).apply {
                    addView(container, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
                }
            scrollView = sv

            addView(headerRow)
            addView(sv)
        }

    /** Number of log entries currently stored (used to update the control sheet count label). */
    var entryCount: Int = 0
        private set

    /** Toggles the panel's expanded/collapsed state; used by the control sheet's Console row. */
    fun toggle() {
        if (expanded) collapse() else expand()
    }

    fun appendLog(
        level: ConsoleMessage.MessageLevel,
        message: String,
        source: String,
        lineNumber: Int,
    ) {
        if (entryCount >= MAX_ENTRIES && logContainer.childCount > 0) {
            logContainer.removeViewAt(0)
        } else {
            entryCount++
        }

        val levelChar =
            when (level) {
                ConsoleMessage.MessageLevel.ERROR -> "E"
                ConsoleMessage.MessageLevel.WARNING -> "W"
                ConsoleMessage.MessageLevel.TIP -> "T"
                ConsoleMessage.MessageLevel.DEBUG -> "D"
                else -> "I"
            }
        val levelColor =
            when (level) {
                ConsoleMessage.MessageLevel.ERROR -> Color.RED
                ConsoleMessage.MessageLevel.WARNING -> Color.rgb(255, 152, 0)
                ConsoleMessage.MessageLevel.TIP -> Color.CYAN
                ConsoleMessage.MessageLevel.DEBUG -> dimmed
                else -> onSurface
            }

        val srcShort =
            source
                .substringAfterLast("/")
                .substringAfterLast("\\")
                .let { if (it.isBlank()) source.takeLast(20) else it }
        val annotation = if (srcShort.isNotBlank()) " ($srcShort:$lineNumber)" else ""

        val entry =
            TextView(context).apply {
                text = "$levelChar $message$annotation"
                setTextColor(levelColor)
                textSize = 11f
                typeface = Typeface.MONOSPACE
                setPadding(dp(4), dp(2), dp(4), dp(2))
            }
        logContainer.addView(entry)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun clearLogs() {
        logContainer.removeAllViews()
        entryCount = 0
        // Return a callback so the activity can update the control sheet count after clearing.
        onClearCallback?.invoke()
    }

    var onClearCallback: (() -> Unit)? = null

    /** The grabber: a small rounded bar centered at the bottom edge. Tap toggles, vertical drag opens/closes. */
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
            layoutParams =
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            setPadding(dp(16), dp(7), dp(16), dp(7))
            background =
                GradientDrawable().apply {
                    cornerRadii = floatArrayOf(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), 0f, 0f, 0f, 0f)
                    setColor(withAlpha(surface, 0.6f))
                }
            isClickable = true
            contentDescription = context.getString(CommonsR.string.browser_console_title_short)
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
                        if (dy < -dp(8)) {
                            expand()
                            dragged = true
                        } else if (dy > dp(8)) {
                            collapse()
                            dragged = true
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragged) {
                            if (expanded) collapse() else expand()
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

    private companion object {
        private const val MAX_ENTRIES = 200
    }
}
