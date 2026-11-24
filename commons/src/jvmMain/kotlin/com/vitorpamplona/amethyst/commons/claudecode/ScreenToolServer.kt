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

import com.vitorpamplona.agents.HostTool
import com.vitorpamplona.agents.HostToolResult
import com.vitorpamplona.agents.HostToolServer
import com.vitorpamplona.agents.ImageInput
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * What the user has open in Amethyst, for the assistant: which screens each part of the window
 * shows ([onScreen], read without asking), and a picture of the window ([capture], asked every
 * time: it sends whatever is on screen to the model). The UI supplies both; null [capture] where
 * the platform can't take one.
 */
class ScreenToolServer(
    private val onScreen: suspend () -> String,
    private val capture: (suspend () -> List<ImageInput>)? = null,
) : HostToolServer {
    override val name = NAME
    override val tools = TOOLS

    override fun alwaysAsks(tool: String): Boolean = tool == SCREENSHOT

    override suspend fun call(
        tool: String,
        arguments: JsonObject,
    ): HostToolResult =
        when (tool) {
            ON_SCREEN -> HostToolResult(onScreen())
            SCREENSHOT -> {
                val take = capture ?: return HostToolResult("Pictures of the screen aren't available on this device.", isError = true)
                val images = take()
                if (images.isEmpty()) {
                    HostToolResult("The window couldn't be captured.", isError = true)
                } else {
                    HostToolResult("A picture of each Amethyst window (${images.size}).", images = images)
                }
            }
            else -> HostToolResult("No tool named $tool", isError = true)
        }

    override fun describe(
        tool: String,
        arguments: JsonObject,
    ): String? =
        when (tool) {
            SCREENSHOT -> "Send a picture of the Amethyst window to the AI. Everything on screen goes with it, messages included."
            else -> null
        }

    companion object {
        const val NAME = "amethyst-screen"
        const val ON_SCREEN = "what_is_on_screen"
        const val SCREENSHOT = "screenshot"

        val READ_ONLY_TOOLS = listOf(ON_SCREEN)
        val ALWAYS_ASK = setOf(SCREENSHOT)

        private fun schema(json: String) = Json.parseToJsonElement(json).jsonObject

        val TOOLS =
            listOf(
                HostTool(
                    ON_SCREEN,
                    "Says what the user has open in Amethyst now: each tab, column and window, with the note, person, " +
                        "hashtag or search it shows. Use it when the user says \"this\", \"here\" or \"on my screen\", then read " +
                        "what it names with get_note, read_thread, search_people or read_profile.",
                    schema("""{"type":"object","properties":{}}"""),
                ),
                HostTool(
                    SCREENSHOT,
                    "Takes a picture of the Amethyst window, for when the layout, images or what something looks like matter. " +
                        "The user approves each one, since it sends everything on screen. Prefer what_is_on_screen for what is open.",
                    schema("""{"type":"object","properties":{}}"""),
                ),
            )
    }
}
