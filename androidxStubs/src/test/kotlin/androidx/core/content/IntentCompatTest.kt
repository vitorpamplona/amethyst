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
package androidx.core.content

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.core.util.Consumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The share path reads its attachments back through IntentCompat, so a read
 * that quietly returned null would look like "the user shared nothing" — a
 * dropped image with no error anywhere.
 */
class IntentCompatTest {
    private val uri = Uri.parse("content://media/external/images/1")
    private val other = Uri.parse("content://media/external/images/2")

    @Test
    fun `a single stream extra round-trips`() {
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri)
        assertEquals(uri, IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
    }

    @Test
    fun `a missing extra is null, not an exception`() {
        assertNull(IntentCompat.getParcelableExtra(Intent(Intent.ACTION_SEND), Intent.EXTRA_STREAM, Uri::class.java))
        assertNull(
            IntentCompat.getParcelableArrayListExtra(Intent(Intent.ACTION_SEND), Intent.EXTRA_STREAM, Uri::class.java),
        )
    }

    @Test
    fun `an extra of the wrong type reads as absent, like the platform`() {
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, "not a uri")
        assertNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
    }

    @Test
    fun `a multi-share keeps every uri, in order`() {
        val intent =
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(uri, other))

        val read: List<Uri>? = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        assertEquals(listOf(uri, other), read?.toList())
    }

    @Test
    fun `the component name survives, because share routing reads it`() {
        val intent =
            Intent(Intent.ACTION_SEND)
                .setClassName("com.vitorpamplona.amethyst", "com.vitorpamplona.amethyst.ShareImageTarget")
        assertEquals("com.vitorpamplona.amethyst.ShareImageTarget", intent.component?.className)
    }

    @Test
    fun `new intents reach every registered listener and stop at the removed one`() {
        val activity = ComponentActivity()
        val seen = mutableListOf<String>()
        val first = Consumer<Intent> { seen.add("first:" + it.action) }
        val second = Consumer<Intent> { seen.add("second:" + it.action) }

        activity.addOnNewIntentListener(first)
        activity.addOnNewIntentListener(second)
        activity.dispatchNewIntent(Intent(Intent.ACTION_VIEW))

        assertEquals(listOf("first:" + Intent.ACTION_VIEW, "second:" + Intent.ACTION_VIEW), seen)

        seen.clear()
        activity.removeOnNewIntentListener(first)
        activity.dispatchNewIntent(Intent(Intent.ACTION_SEND))
        assertEquals(listOf("second:" + Intent.ACTION_SEND), seen)
    }

    @Test
    fun `a listener may unregister itself while being dispatched to`() {
        // The composer's DisposableEffect does exactly this when a shared
        // intent navigates away; a plain ArrayList here would throw.
        val activity = ComponentActivity()
        var calls = 0
        lateinit var listener: Consumer<Intent>
        listener =
            Consumer<Intent> {
                calls++
                activity.removeOnNewIntentListener(listener)
            }
        activity.addOnNewIntentListener(listener)

        activity.dispatchNewIntent(Intent(Intent.ACTION_VIEW))
        activity.dispatchNewIntent(Intent(Intent.ACTION_VIEW))

        assertEquals(1, calls)
    }

    @Test
    fun `dispatching with nothing registered is harmless`() {
        ComponentActivity().dispatchNewIntent(Intent(Intent.ACTION_VIEW))
        assertTrue(true)
    }
}
