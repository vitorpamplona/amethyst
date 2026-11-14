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
package com.vitorpamplona.amethyst.commons.settings.registry

import com.vitorpamplona.amethyst.commons.model.navigation.Route

/** Whether a setting belongs to the app on this device or to the logged-in account. */
enum class SettingScope { APP, ACCOUNT }

/**
 * How careful a change must be. Every change is the user's to approve; [CAREFUL] ones change
 * who the user talks to or what others see, and an assistant should say so before proposing.
 */
enum class SettingSensitivity { NORMAL, CAREFUL }

/** One allowed value of a choice setting, with what it means. */
data class SettingOption(
    val value: String,
    val meaning: String,
)

sealed interface SettingType {
    data object OnOff : SettingType

    data class Choice(
        val options: List<SettingOption>,
    ) : SettingType

    data class Text(
        val maxLength: Int,
    ) : SettingType
}

/**
 * One setting the app lets people (and an assistant acting for them, with their approval) read
 * and change: what it is, in words, where its screen is, and how to read and write it. Values
 * travel as strings: `true`/`false`, a [SettingOption.value], or the text.
 *
 * [title] and [description] are written for the assistant, which answers in the user's words
 * and language; the settings screens keep their own translated labels.
 */
class SettingDefinition(
    val id: String,
    val title: String,
    val description: String,
    val scope: SettingScope,
    val type: SettingType,
    val route: Route?,
    val sensitivity: SettingSensitivity = SettingSensitivity.NORMAL,
    val keywords: List<String> = emptyList(),
    private val read: () -> String,
    private val write: (String) -> Unit,
) {
    fun current(): String = read()

    /** Why [value] can't be set, or null when it can. */
    fun problemWith(value: String): String? =
        when (val t = type) {
            SettingType.OnOff -> if (value == "true" || value == "false") null else "Use true or false."
            is SettingType.Choice -> if (t.options.any { it.value == value }) null else "Use one of: ${t.options.joinToString { it.value }}."
            is SettingType.Text -> if (value.length <= t.maxLength) null else "At most ${t.maxLength} characters."
        }

    /** Sets [value]; throws [IllegalArgumentException] when [problemWith] names a problem. */
    fun set(value: String) {
        problemWith(value)?.let { throw IllegalArgumentException(it) }
        write(value)
    }

    /** [value] in words: an option's meaning, on/off, or the text. */
    fun describe(value: String): String =
        when (val t = type) {
            SettingType.OnOff -> if (value == "true") "on" else "off"
            is SettingType.Choice -> t.options.firstOrNull { it.value == value }?.let { "${it.value} (${it.meaning})" } ?: value
            is SettingType.Text -> if (value.isEmpty()) "(empty)" else "\"$value\""
        }
}

/** Every setting an assistant may explain and propose changes to. */
class SettingsRegistry(
    val settings: List<SettingDefinition>,
) {
    private val byId = settings.associateBy { it.id }

    fun find(id: String): SettingDefinition? = byId[id]

    /**
     * The settings that match [query], best first. Any word may match: an assistant asks in whole
     * phrases ("text size font size too small to read"), and a setting must not drop out because
     * one word of the phrase isn't in it. A word in the id, title or keywords counts three times
     * one in the description or an option; matching more of the words ranks higher.
     */
    fun search(
        query: String,
        limit: Int = 12,
    ): List<SettingDefinition> {
        val words =
            query
                .lowercase()
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.length > 1 && it !in STOP_WORDS }
                .distinct()
        if (words.isEmpty()) return settings.take(limit)
        return settings
            .mapNotNull { setting ->
                val strong = (listOf(setting.id.replace('_', ' ').replace('.', ' '), setting.title) + setting.keywords).joinToString(" ").lowercase()
                val weak =
                    buildString {
                        append(setting.description)
                        (setting.type as? SettingType.Choice)?.options?.forEach { append(' ').append(it.value).append(' ').append(it.meaning) }
                    }.lowercase()
                val score =
                    words.sumOf { word ->
                        when {
                            strong.contains(word) -> 3
                            weak.contains(word) -> 1
                            else -> 0
                        }
                    }
                if (score == 0) null else setting to score
            }.sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    private companion object {
        /** Words that say nothing about which setting is meant. */
        val STOP_WORDS =
            setOf(
                "the",
                "to",
                "too",
                "is",
                "it",
                "my",
                "me",
                "and",
                "or",
                "of",
                "in",
                "on",
                "for",
                "a",
                "an",
                "be",
                "can",
                "how",
                "what",
                "why",
                "do",
                "does",
                "i",
                "want",
                "make",
                "change",
                "turn",
                "set",
                "setting",
                "settings",
                "so",
                "much",
            )
    }
}
