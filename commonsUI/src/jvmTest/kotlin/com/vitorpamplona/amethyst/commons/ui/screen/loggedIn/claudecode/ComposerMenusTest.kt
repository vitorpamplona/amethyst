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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.claudecode

import com.vitorpamplona.agents.ModelChoice
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposerMenusTest {
    private fun model(
        value: String,
        name: String,
        description: String = "",
        resolved: String? = null,
    ) = ModelChoice(value, name, description, resolved)

    private val default = model("default", "Default (recommended)", "Opus 5.5 · Best for everyday, complex tasks", "claude-opus-5-5")

    @Test
    fun theDefaultIsNamedByTheModelItStandsFor() {
        // Another choice names the same model.
        assertEquals("Opus 5.5", modelName(default, listOf(default, model("opus", "Opus 5.5", resolved = "claude-opus-5-5"))))
        // Else its description does.
        assertEquals("Opus 5.5", modelName(default, listOf(default)))
        assertEquals("Haiku 5.5", modelName(model("haiku", "Haiku 5.5"), listOf(default)))
        // A description that isn't a name gives none.
        assertEquals(null, modelName(model("default", "Default", "The model your plan uses"), emptyList()))
    }

    @Test
    fun aLongListLeadsWithTheAliases() {
        val few = listOf(default, model("opus", "Opus"), model("claude-opus-4-5", "Opus 4.5"))
        assertEquals(few, leadingModels(few, "default"))
        val many =
            listOf(default, model("opus", "Opus"), model("sonnet", "Sonnet"), model("haiku", "Haiku")) +
                (1..6).map { model("claude-sonnet-4-$it", "Sonnet 4.$it") }
        assertEquals(listOf("default", "opus", "sonnet", "haiku"), leadingModels(many, "default").map { it.value })
        // The model in use stays in sight, even a dated one.
        assertEquals("claude-sonnet-4-3", leadingModels(many, "claude-sonnet-4-3").last().value)
        // With no aliases at all, the first few.
        val versioned = (1..9).map { model("gpt-5.$it", "GPT-5.$it") }
        assertEquals(6, leadingModels(versioned, "gpt-5.1").size)
    }
}
