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
package com.vitorpamplona.amethyst.commons.ui.note

import androidx.compose.foundation.layout.width
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.quartz.nip34Git.patch.UnifiedDiffParser
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** A diff that starts folded shows each file's header only, and opens on a click. Density 1, so dp == px. */
class GitDiffViewFoldTest {
    private var scene: ImageComposeScene? = null

    @AfterTest
    fun close() {
        scene?.close()
    }

    private val patch =
        """
        diff --git a/a.kt b/a.kt
        --- a/a.kt
        +++ b/a.kt
        @@ -1,3 +1,5 @@
         fun main() {
        -    old()
        +    new()
        +    added()
        +    more()
         }
        """.trimIndent() + "\n"

    private fun heightOf(startFolded: Boolean): Pair<ImageComposeScene, () -> Int> {
        var height = -1
        val s =
            ImageComposeScene(width = 400, height = 600, density = Density(1f)) {
                GitDiffView(UnifiedDiffParser.parse(patch), Modifier.width(400.dp).onGloballyPositioned { height = it.size.height }, showSummary = false, startFolded = startFolded)
            }
        scene = s
        return s to { height }
    }

    private fun shot(
        s: ImageComposeScene,
        name: String,
    ) {
        val image = s.render()
        System.getenv("DIFF_SHOT")?.let { File(it, name).writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes) }
    }

    @Test
    fun aFoldedDiffShowsOnlyTheFileUntilClicked() {
        val (open, openHeight) = heightOf(startFolded = false)
        shot(open, "open.png")
        val full = openHeight()
        open.close()

        val (folded, foldedHeight) = heightOf(startFolded = true)
        shot(folded, "folded.png")
        val header = foldedHeight()
        assertTrue(header in 1 until full / 2, "folded ($header px) is just the header, open is $full px")

        // A click on the header opens it.
        folded.sendPointerEvent(PointerEventType.Press, Offset(100f, header / 2f))
        folded.sendPointerEvent(PointerEventType.Release, Offset(100f, header / 2f))
        shot(folded, "clicked.png")
        assertTrue(foldedHeight() == full, "clicked, it opens to the full diff (${foldedHeight()} of $full px)")
    }
}
