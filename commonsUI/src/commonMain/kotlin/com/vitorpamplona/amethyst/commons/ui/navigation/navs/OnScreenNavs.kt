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
package com.vitorpamplona.amethyst.commons.ui.navigation.navs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * The navigations that show screens now: the window's, and each deck column's. The assistant's
 * `what_is_on_screen` reads them to say what the user has open. Touched on the main thread only.
 */
object OnScreenNavs {
    class Entry(
        val label: String,
        val nav: Nav,
    )

    private val entries = LinkedHashMap<Any, Entry>()

    fun register(
        key: Any,
        label: String,
        nav: Nav,
    ) {
        entries[key] = Entry(label, nav)
    }

    fun unregister(key: Any) {
        entries.remove(key)
    }

    /** In the order they were opened: the window first, then its columns. */
    fun all(): List<Entry> = entries.values.toList()
}

/** Lists [nav] as [label] while this is composed. */
@Composable
fun RegisterOnScreen(
    label: String,
    nav: Nav,
) {
    DisposableEffect(nav, label) {
        val key = Any()
        OnScreenNavs.register(key, label, nav)
        onDispose { OnScreenNavs.unregister(key) }
    }
}
