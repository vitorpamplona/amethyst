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
package com.vitorpamplona.amethyst.ui.navigation

/**
 * Distinguishes the extra share targets ("Send as DM", "New Highlight") from the default
 * "New Post" share target. Every SEND intent-filter resolves to MainActivity; they are told
 * apart by the component class name of the launching intent (the activity-alias name).
 */
object ShareIntentRouting {
    /**
     * Simple class name of the `<activity-alias>` declared in AndroidManifest.xml
     * (android:name=".ui.ShareAsDMAlias"). MUST stay in sync with the manifest —
     * renaming the alias there without updating this constant silently routes
     * "Send as DM" shares to the New Post composer (no build error).
     */
    const val SHARE_AS_DM_ALIAS_SIMPLE_NAME = "ShareAsDMAlias"

    /**
     * Simple class name of the `<activity-alias>` declared in AndroidManifest.xml
     * (android:name=".ui.ShareAsHighlightAlias"). MUST stay in sync with the manifest —
     * see the caveat on [SHARE_AS_DM_ALIAS_SIMPLE_NAME].
     */
    const val SHARE_AS_HIGHLIGHT_ALIAS_SIMPLE_NAME = "ShareAsHighlightAlias"

    fun isShareAsDm(componentClassName: String?): Boolean = componentClassName?.endsWith(".$SHARE_AS_DM_ALIAS_SIMPLE_NAME") == true

    fun isShareAsHighlight(componentClassName: String?): Boolean = componentClassName?.endsWith(".$SHARE_AS_HIGHLIGHT_ALIAS_SIMPLE_NAME") == true
}
