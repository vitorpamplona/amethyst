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
package com.vitorpamplona.androiddevice

import com.vitorpamplona.androiddevice.ui.UiTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiTreeTest {
    private val xml =
        """
        <?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
        <hierarchy rotation="0">
          <node index="0" text="" resource-id="" class="android.widget.FrameLayout" package="com.vitorpamplona.amethyst" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[0,0][1080,2400]">
            <node index="0" text="" resource-id="" class="android.view.View" package="com.vitorpamplona.amethyst" content-desc="New post" checkable="false" checked="false" clickable="true" enabled="true" focusable="true" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[900,2100][1040,2240]">
              <node index="0" text="Post" resource-id="" class="android.widget.TextView" package="com.vitorpamplona.amethyst" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[920,2150][1020,2190]" />
            </node>
            <node index="1" text="" resource-id="com.vitorpamplona.amethyst:id/feed" class="androidx.recyclerview.widget.RecyclerView" package="com.vitorpamplona.amethyst" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="true" focused="false" scrollable="true" long-clickable="false" password="false" selected="false" bounds="[0,200][1080,2000]" />
            <node index="2" text="What's on your mind?" resource-id="" class="android.widget.EditText" package="com.vitorpamplona.amethyst" content-desc="" checkable="false" checked="false" clickable="true" enabled="true" focusable="true" focused="true" scrollable="false" long-clickable="true" password="false" selected="false" bounds="[0,0][1080,180]" />
          </node>
        </hierarchy>
        """.trimIndent()

    @Test
    fun describesWhatSaysOrDoesSomething() {
        val tree = UiTree.parse(xml)
        assertEquals(5, tree.nodes.size)
        val text = tree.describe()
        // The layout frame is left out; the button, its label, the list and the field are in.
        assertFalse(text.contains("FrameLayout"))
        assertTrue(text.contains("#1 View desc=\"New post\" [900,2100][1040,2240] clickable"), text)
        assertTrue(text.contains("  #2 TextView \"Post\" [920,2150][1020,2190]"), text)
        assertTrue(text.contains("#3 RecyclerView id=feed [0,200][1080,2000] scrollable"), text)
        assertTrue(text.contains("editable"), text)
        assertTrue(text.contains("focused"), text)
    }

    @Test
    fun scalesBoundsToTheCallersSpace() {
        val text = UiTree.parse(xml).describe(scale = 0.5)
        assertTrue(text.contains("[450,1050][520,1120]"), text)
    }

    @Test
    fun findsByTextDescriptionOrIdPreferringWhatCanBeTapped() {
        val tree = UiTree.parse(xml)
        assertEquals(1, tree.find("new post")?.id)
        assertEquals(2, tree.find("Post")?.id)
        assertEquals(3, tree.find("feed")?.id)
        // Contains, when nothing matches exactly.
        assertEquals(4, tree.find("your mind")?.id)
        assertTrue(tree.contains("on your"))
        assertEquals(null, tree.find("nothing like this"))
    }
}
