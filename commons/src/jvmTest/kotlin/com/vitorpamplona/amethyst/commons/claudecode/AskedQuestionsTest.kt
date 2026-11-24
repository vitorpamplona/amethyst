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
package com.vitorpamplona.amethyst.commons.claudecode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AskedQuestionsTest {
    private val input =
        """{"questions":[{"question":"Which database?","header":"Database","multiSelect":false,"options":[{"label":"Postgres","description":"a"},{"label":"SQLite","description":"b"}]},""" +
            """{"question":"Which platforms?","header":"Platforms","multiSelect":true,"options":[{"label":"Android","description":"a"},{"label":"Desktop","description":"b"}]},""" +
            """{"question":"Anything else?","header":"Notes","multiSelect":false,"options":[{"label":"No","description":"a"},{"label":"Yes","description":"b"}]}]}"""

    @Test
    fun readsTheAnswersAsClaudeCodeWordsThem() {
        val questions = AskedQuestions.questions("AskUserQuestion", input)!!.questions
        // As Claude Code 2.1.331 writes the tool result.
        val result =
            "Your questions have been answered: \"Which database?\"=\"Postgres\" selected preview:\n```sql\nselect 1\n``` notes: keep it small, " +
                "\"Which platforms?\"=\"Android, Desktop\". You can now continue with these answers in mind."
        val answers = AskedQuestions.answers(result, questions)
        assertEquals("Postgres", answers["Which database?"])
        assertEquals("Android, Desktop", answers["Which platforms?"])
        assertNull(answers["Anything else?"])
    }

    @Test
    fun aQuestionWithOnlyNotesHasNoAnswer() {
        val questions = AskedQuestions.questions("AskUserQuestion", input)!!.questions
        val answers = AskedQuestions.answers("The user answered: \"Which database?\"=(no option selected) notes: either. Read the answers carefully.", questions)
        assertNull(answers["Which database?"])
    }

    @Test
    fun otherToolsHaveNoQuestions() {
        assertNull(AskedQuestions.questions("Bash", """{"command":"ls"}"""))
        assertNull(AskedQuestions.questions("AskUserQuestion", "not json"))
    }
}
