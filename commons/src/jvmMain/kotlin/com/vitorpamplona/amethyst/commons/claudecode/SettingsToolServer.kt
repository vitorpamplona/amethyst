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

import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.settings.registry.SettingDefinition
import com.vitorpamplona.amethyst.commons.settings.registry.SettingScope
import com.vitorpamplona.amethyst.commons.settings.registry.SettingSensitivity
import com.vitorpamplona.amethyst.commons.settings.registry.SettingType
import com.vitorpamplona.amethyst.commons.settings.registry.SettingsRegistry
import com.vitorpamplona.claudecode.mcp.McpTool
import com.vitorpamplona.claudecode.mcp.McpToolResult
import com.vitorpamplona.claudecode.mcp.SdkMcpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Amethyst's settings as tools Claude can use: find a setting, explain it, change it (with the
 * user's approval: the CLI asks before every [CHANGE]) and open its screen. Reads [registry]
 * at each call, so it always sees the current values and the current account.
 */
class SettingsToolServer(
    private val registry: () -> SettingsRegistry,
    private val openScreen: (Route) -> Unit,
) : SdkMcpServer {
    override val name = NAME

    override val tools =
        listOf(
            McpTool(
                SEARCH,
                "Finds Amethyst settings by what the user is asking about (\"dark mode\", \"videos use my data\", \"notifications are noisy\"). " +
                    "Returns each match's id, title and current value. Use it before answering any question about settings.",
                schema("""{"type":"object","properties":{"query":{"type":"string","description":"What the user wants, in a few words"}},"required":["query"]}"""),
            ),
            McpTool(
                GET,
                "Explains one setting: what it does, its current value, every value it can take and what each means, and where its screen is.",
                schema("""{"type":"object","properties":{"id":{"type":"string","description":"The setting's id, from search_settings"}},"required":["id"]}"""),
            ),
            McpTool(
                CHANGE,
                "Changes one setting to a new value. The user is asked to approve each change, so propose one change at a time and say why. " +
                    "Values: true/false for on/off settings, an option's value for choices, the text for text settings.",
                schema(
                    """{"type":"object","properties":{"id":{"type":"string"},"value":{"type":"string"}},"required":["id","value"]}""",
                ),
            ),
            McpTool(
                OPEN,
                "Opens the screen where a setting lives, so the user can see it and change it themselves.",
                schema("""{"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}"""),
            ),
        )

    override suspend fun call(
        tool: String,
        arguments: JsonObject,
    ): McpToolResult {
        val settings = registry()
        return when (tool) {
            SEARCH -> {
                val found = settings.search(arguments.string("query").orEmpty())
                if (found.isEmpty()) {
                    McpToolResult("No setting matches. It may not be something the app lets an assistant change; offer to open the settings screen instead.")
                } else {
                    McpToolResult(found.joinToString("\n") { "${it.id} — ${it.title}: ${it.describe(it.current())}" })
                }
            }
            GET -> {
                val setting = settings.find(arguments.string("id").orEmpty()) ?: return unknown(arguments)
                McpToolResult(explain(setting))
            }
            CHANGE -> {
                val setting = settings.find(arguments.string("id").orEmpty()) ?: return unknown(arguments)
                val value = arguments.string("value") ?: return McpToolResult("Give the new value.", isError = true)
                setting.problemWith(value)?.let { return McpToolResult(it, isError = true) }
                val before = setting.current()
                setting.set(value)
                McpToolResult("${setting.title} is now ${setting.describe(setting.current())} (it was ${setting.describe(before)}).")
            }
            OPEN -> {
                val setting = settings.find(arguments.string("id").orEmpty()) ?: return unknown(arguments)
                val route = setting.route ?: return McpToolResult("${setting.title} has no screen of its own.", isError = true)
                openScreen(route)
                McpToolResult("Opened the screen with ${setting.title}.")
            }
            else -> McpToolResult("No tool named $tool", isError = true)
        }
    }

    /** For the approval prompt: "Theme: SYSTEM (follow the system) → DARK (always dark)". */
    override fun describe(
        tool: String,
        arguments: JsonObject,
    ): String? {
        if (tool != CHANGE) return null
        val setting = registry().find(arguments.string("id").orEmpty()) ?: return null
        val value = arguments.string("value") ?: return null
        return "${setting.title}: ${setting.describe(setting.current())} → ${setting.describe(value)}"
    }

    private fun explain(setting: SettingDefinition): String =
        buildString {
            appendLine("${setting.title} (${setting.id})")
            appendLine(setting.description)
            appendLine("Now: ${setting.describe(setting.current())}")
            when (val type = setting.type) {
                SettingType.OnOff -> appendLine("Values: true (on), false (off)")
                is SettingType.Choice -> {
                    appendLine("Values:")
                    type.options.forEach { appendLine("- ${it.value}: ${it.meaning}") }
                }
                is SettingType.Text -> appendLine("Value: text, at most ${type.maxLength} characters")
            }
            appendLine(if (setting.scope == SettingScope.ACCOUNT) "Applies to this account." else "Applies to the app on this computer.")
            if (setting.sensitivity == SettingSensitivity.CAREFUL) {
                appendLine("Careful: this changes privacy or who the user deals with. Explain the trade-off before proposing a change.")
            }
            if (setting.route != null) append("It has its own screen in Settings.") else append("It has no screen of its own.")
        }

    private fun unknown(arguments: JsonObject) = McpToolResult("No setting with id ${arguments.string("id")}. Use search_settings to find its id.", isError = true)

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    companion object {
        const val NAME = "amethyst-settings"
        const val SEARCH = "search_settings"
        const val GET = "get_setting"
        const val CHANGE = "change_setting"
        const val OPEN = "open_settings_screen"

        private fun schema(json: String) = Json.parseToJsonElement(json).jsonObject

        /**
         * What the settings assistant is for, added to Claude Code's own prompt. It has no
         * other tools: no files, no shell.
         */
        const val SYSTEM_PROMPT =
            "You are the settings assistant inside Amethyst, a Nostr client. You help the user understand what Amethyst's " +
                "settings do and change them. You have no files, shell or web here: only the amethyst-settings tools. " +
                "Look settings up with search_settings and get_setting before answering; never guess what a setting does. " +
                "Explain in plain words, in the user's language, without jargon. Propose one change at a time with change_setting " +
                "and say why; the user approves each one. For settings marked careful, explain the trade-off first. " +
                "When something isn't in the tools (keys, signer, wallets, relays, payments), say so and offer open_settings_screen " +
                "or point the user to Settings. Keep answers short."
    }
}
