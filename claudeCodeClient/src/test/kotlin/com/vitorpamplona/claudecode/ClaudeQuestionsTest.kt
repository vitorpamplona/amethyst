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
package com.vitorpamplona.claudecode

import com.vitorpamplona.agents.PermissionDecision
import com.vitorpamplona.agents.QuestionAnswer
import com.vitorpamplona.agents.UserQuestion
import com.vitorpamplona.claudecode.process.ClaudeCodeConnection
import com.vitorpamplona.claudecode.protocol.ClaudeMessage
import com.vitorpamplona.claudecode.protocol.ClaudeQuestions
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClaudeQuestionsTest {
    private fun obj(line: String) = Json.parseToJsonElement(line) as JsonObject

    // As Claude Code 2.1.331 sends it: the card's input, kinds filled in by the extended form.
    private val askLine =
        """{"type":"control_request","request_id":"req-1","request":{"subtype":"can_use_tool","tool_name":"AskUserQuestion","display_name":"AskUserQuestion",""" +
            """"input":{"title":"Before I start","questions":[""" +
            """{"question":"Which database?","header":"Database","kind":"choice","multiSelect":false,"options":[{"label":"Postgres","description":"Relational","preview":"```sql\nselect 1\n```"},{"label":"SQLite"}]},""" +
            """{"question":"Which platforms?","header":"Platforms","kind":"choice","multiSelect":true,"options":[{"label":"Android","description":"Phones"},{"label":"Desktop","description":"JVM"}]},""" +
            """{"question":"What should it be called?","header":"Name","kind":"text","placeholder":"my-app","options":[]},""" +
            """{"question":"How many workers?","header":"Workers","kind":"number","min":1,"max":16,"step":1,"defaultValue":4,"unit":"threads","options":[]}""" +
            """]},"tool_use_id":"toolu_q1"}}"""

    private val initLine = """{"type":"system","subtype":"init","session_id":"s1","tools":[],"capabilities":["interrupt_receipt_v1","ask_user_question_unanswered_v1"]}"""

    @Test
    fun theQuestionsAreReadFromThePrompt() {
        val prompt = (ClaudeMessage.parse(askLine) as ClaudeMessage.ControlRequest).permission!!
        val questions = assertNotNull(prompt.questions)
        assertEquals("Before I start", questions.title)
        assertTrue(questions.acceptsAskMore)
        val (db, platforms, name, workers) = questions.questions
        assertEquals("Which database?", db.id)
        assertEquals("Database", db.header)
        assertEquals(UserQuestion.Kind.CHOICE, db.kind)
        assertEquals(listOf("Postgres", "SQLite"), db.options.map { it.label })
        assertEquals("```sql\nselect 1\n```", db.options[0].preview)
        assertNull(db.options[1].description)
        assertTrue(platforms.multiSelect)
        assertEquals(UserQuestion.Kind.TEXT, name.kind)
        assertEquals("my-app", name.placeholder)
        assertEquals(UserQuestion.Kind.NUMBER, workers.kind)
        assertEquals(1.0, workers.min)
        assertEquals(16.0, workers.max)
        assertEquals(4.0, workers.defaultValue)
        assertEquals("threads", workers.unit)
    }

    @Test
    fun aPlainPromptHasNoQuestions() {
        val line = """{"type":"control_request","request_id":"r","request":{"subtype":"can_use_tool","tool_name":"Bash","input":{"command":"ls"}}}"""
        assertNull((ClaudeMessage.parse(line) as ClaudeMessage.ControlRequest).permission!!.questions)
    }

    @Test
    fun theBaseFormTakesNoFollowUp() {
        val input = obj("""{"questions":[{"question":"A or B?","header":"Pick","multiSelect":false,"options":[{"label":"A","description":"a"},{"label":"B","description":"b"}]}]}""")
        val questions = ClaudeQuestions.parse(input)!!
        assertFalse(questions.acceptsAskMore)
        val sent = ClaudeQuestions.answeredInput(input, questions, PermissionDecision.Answer(mapOf("A or B?" to QuestionAnswer(listOf("A"))), askMore = true), unansweredNotes = false)
        assertNull(sent["followUp"])
    }

    @Test
    fun answersGoBackAddedToTheInputUnchanged() =
        runTest {
            val transport = FakeTransport()
            val connection = ClaudeCodeConnection(transport, backgroundScope)
            transport.emit(initLine)
            transport.emit(askLine)
            transport.end()
            val prompt = connection.messages.toList().firstNotNullOf { (it as? ClaudeMessage.ControlRequest)?.permission }

            connection.respond(
                prompt,
                PermissionDecision.Answer(
                    mapOf(
                        "Which database?" to QuestionAnswer(listOf("Postgres"), notes = "  keep it small "),
                        "Which platforms?" to QuestionAnswer(listOf("Desktop", "Android"), text = "iOS later"),
                        "What should it be called?" to QuestionAnswer(text = "amethyst-lite"),
                    ),
                    askMore = true,
                ),
            )

            val decision = obj(transport.written.single())["response"]!!.jsonObject["response"]!!.jsonObject
            assertEquals("allow", decision["behavior"]!!.jsonPrimitive.content)
            assertEquals("toolu_q1", decision["toolUseID"]!!.jsonPrimitive.content)
            val sent = decision["updatedInput"]!!.jsonObject
            // What the CLI sent comes back as it was: it refuses an answer that changes it.
            prompt.input.forEach { (key, value) -> assertEquals(value, sent[key], key) }
            val answers = sent["answers"]!!.jsonObject
            assertEquals("Postgres", answers["Which database?"]!!.jsonPrimitive.content)
            // A multi-select is an array, in the options' order, with the typed answer last.
            assertEquals(JsonArray(listOf("Android", "Desktop", "iOS later").map(::JsonPrimitive)), answers["Which platforms?"])
            assertEquals("amethyst-lite", answers["What should it be called?"]!!.jsonPrimitive.content)
            assertNull(answers["How many workers?"])
            val annotations = sent["annotations"]!!.jsonObject
            val db = annotations["Which database?"]!!.jsonObject
            assertEquals("keep it small", db["notes"]!!.jsonPrimitive.content)
            assertEquals("```sql\nselect 1\n```", db["preview"]!!.jsonPrimitive.content)
            // The CLI advertised it: the question left out says it was skipped.
            assertEquals("skipped", annotations["How many workers?"]!!.jsonObject["unanswered"]!!.jsonPrimitive.content)
            assertEquals(true, sent["followUp"]!!.jsonPrimitive.content.toBoolean())
            assertNull(sent["response"])
        }

    @Test
    fun aReplyInsteadOfAnswersIsTheResponse() =
        runTest {
            val transport = FakeTransport()
            val connection = ClaudeCodeConnection(transport, backgroundScope)
            transport.emit(askLine)
            transport.end()
            val prompt = connection.messages.toList().firstNotNullOf { (it as? ClaudeMessage.ControlRequest)?.permission }

            connection.respond(prompt, PermissionDecision.Answer(emptyMap(), reply = "Let's talk about this first"))

            val sent = obj(transport.written.single())["response"]!!.jsonObject["response"]!!.jsonObject["updatedInput"]!!.jsonObject
            assertEquals("Let's talk about this first", sent["response"]!!.jsonPrimitive.content)
            assertEquals(JsonObject(emptyMap()), sent["answers"])
            // Without the capability, an older CLI would refuse the whole answer over the note.
            assertNull(sent["annotations"])
        }

    @Test
    fun dismissingIsADeny() =
        runTest {
            val transport = FakeTransport()
            val connection = ClaudeCodeConnection(transport, backgroundScope)
            transport.emit(askLine)
            transport.end()
            val prompt = connection.messages.toList().firstNotNullOf { (it as? ClaudeMessage.ControlRequest)?.permission }
            connection.respond(prompt, PermissionDecision.Deny("No"))
            assertEquals(
                "deny",
                obj(transport.written.single())["response"]!!
                    .jsonObject["response"]!!
                    .jsonObject["behavior"]!!
                    .jsonPrimitive.content,
            )
        }

    @Test
    fun aLongAnswerStaysWithinWhatTheCliTakesLabelsIncluded() {
        // The CLI refuses the whole answer when answers, notes and the reply pass 32,768 characters,
        // the picked labels counted with the typed text.
        val label = "L".repeat(1_000)
        val input =
            obj(
                """{"questions":[{"question":"Which?","header":"Pick","multiSelect":true,"options":[{"label":"$label","description":"a"},{"label":"B","description":"b"}]},""" +
                    """{"question":"Why?","header":"Why","multiSelect":false,"options":[{"label":"X","description":"x"},{"label":"Y","description":"y"}]}]}""",
            )
        val questions = ClaudeQuestions.parse(input)!!
        val sent =
            ClaudeQuestions.answeredInput(
                input,
                questions,
                PermissionDecision.Answer(
                    mapOf(
                        "Which?" to QuestionAnswer(listOf(label, "B"), text = "t".repeat(40_000), notes = "n".repeat(9_000)),
                        "Why?" to QuestionAnswer(listOf("X"), notes = "m".repeat(9_000)),
                    ),
                    reply = "r".repeat(5_000),
                ),
                unansweredNotes = false,
            )
        val answers = sent["answers"]!!.jsonObject
        // As the CLI counts it: an array answer joined with ", ".
        val answerChars =
            answers.values.sumOf { value ->
                if (value is JsonArray) value.joinToString(", ") { it.jsonPrimitive.content }.length else value.jsonPrimitive.content.length
            }
        val noteChars =
            sent["annotations"]!!.jsonObject.values.sumOf {
                it.jsonObject["notes"]
                    ?.jsonPrimitive
                    ?.content
                    ?.length ?: 0
            }
        val replyChars = sent["response"]?.jsonPrimitive?.content?.length ?: 0
        assertTrue(answerChars + noteChars + replyChars <= 32_768, "${answerChars + noteChars + replyChars}")
        // The picks themselves always go: only typed text is cut.
        assertEquals(label, (answers["Which?"] as JsonArray)[0].jsonPrimitive.content)
        assertEquals("X", answers["Why?"]!!.jsonPrimitive.content)
    }

    @Test
    fun theHostAsksForTheWholeForm() {
        assertEquals("markdown", ClaudeQuestions.HOST_ENVIRONMENT["CLAUDE_CODE_QUESTION_PREVIEW_FORMAT"])
        assertEquals("1", ClaudeQuestions.HOST_ENVIRONMENT["CLAUDE_CODE_QUESTION_EXTENDED"])
    }
}
