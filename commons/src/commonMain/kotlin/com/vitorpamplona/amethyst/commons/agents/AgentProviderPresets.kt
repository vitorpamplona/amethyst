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
package com.vitorpamplona.amethyst.commons.agents

/**
 * A provider the setup screen offers. Only [url] reaches the event; the rest is UI.
 * Every one serves the OpenAI Chat Completions format at `{url}/chat/completions`.
 */
data class AgentProviderPreset(
    val id: String,
    val name: String,
    val url: String,
    /** Local runners take no key. */
    val needsKey: Boolean = true,
    /** The provider's page for creating a key, opened by the screen's "Get a key". */
    val keyPage: String? = null,
    /** Runs on the user's own machine: only reachable from that device. */
    val isLocal: Boolean = false,
    /**
     * The cheapest call, under [url], that fails on a bad key. `/models` for most providers;
     * null where every free call answers without checking the key (checked 2026-11-10).
     */
    val keyCheckPath: String? = "/models",
)

object AgentProviderPresets {
    // OpenRouter lists models without a key; `/key` describes the key and 401s on a bad one.
    val OPENROUTER = AgentProviderPreset("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", keyPage = "https://openrouter.ai/keys", keyCheckPath = "/key")
    val CUSTOM = AgentProviderPreset("custom", "Custom", "", needsKey = false)

    /** OpenRouter first: one key reaches every major model and can be capped. Custom last. */
    val ALL =
        listOf(
            OPENROUTER,
            AgentProviderPreset("anthropic", "Anthropic", "https://api.anthropic.com/v1", keyPage = "https://console.anthropic.com/settings/keys"),
            AgentProviderPreset("openai", "OpenAI", "https://api.openai.com/v1", keyPage = "https://platform.openai.com/api-keys"),
            AgentProviderPreset("google", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", keyPage = "https://aistudio.google.com/apikey"),
            AgentProviderPreset("routstr", "Routstr", "https://api.routstr.com/v1"),
            AgentProviderPreset("ppq", "PPQ.ai", "https://api.ppq.ai/v1", keyPage = "https://ppq.ai", keyCheckPath = null),
            AgentProviderPreset("venice", "Venice", "https://api.venice.ai/api/v1", keyPage = "https://venice.ai/settings/api", keyCheckPath = "/api_keys/rate_limits"),
            AgentProviderPreset("nearai", "NEAR AI", "https://cloud-api.near.ai/v1", keyPage = "https://cloud.near.ai", keyCheckPath = "/users/me"),
            AgentProviderPreset("mistral", "Mistral", "https://api.mistral.ai/v1", keyPage = "https://console.mistral.ai/api-keys"),
            AgentProviderPreset("groq", "Groq", "https://api.groq.com/openai/v1", keyPage = "https://console.groq.com/keys"),
            AgentProviderPreset("xai", "xAI", "https://api.x.ai/v1", keyPage = "https://console.x.ai"),
            AgentProviderPreset("deepseek", "DeepSeek", "https://api.deepseek.com/v1", keyPage = "https://platform.deepseek.com/api_keys"),
            AgentProviderPreset("together", "Together", "https://api.together.xyz/v1", keyPage = "https://api.together.ai/settings/api-keys"),
            AgentProviderPreset("fireworks", "Fireworks", "https://api.fireworks.ai/inference/v1", keyPage = "https://fireworks.ai/account/api-keys"),
            AgentProviderPreset("cerebras", "Cerebras", "https://api.cerebras.ai/v1", keyPage = "https://cloud.cerebras.ai"),
            AgentProviderPreset("ollama", "Ollama", "http://localhost:11434/v1", needsKey = false, isLocal = true),
            AgentProviderPreset("lmstudio", "LM Studio", "http://localhost:1234/v1", needsKey = false, isLocal = true),
            CUSTOM,
        )

    /** The preset whose URL this is, ignoring a trailing slash and case; null for a custom endpoint. */
    fun forUrl(url: String): AgentProviderPreset? {
        val normalized = normalizeUrl(url)
        return ALL.firstOrNull { it.url.isNotEmpty() && normalizeUrl(it.url) == normalized }
    }

    /** A name for a saved key: the preset's, or the URL's host. */
    fun displayName(url: String): String = forUrl(url)?.name ?: hostOf(url) ?: url

    fun normalizeUrl(url: String) = url.trim().trimEnd('/').lowercase()

    fun hostOf(url: String): String? =
        url
            .trim()
            .substringAfter("://", "")
            .substringBefore('/')
            .substringBefore('?')
            .ifBlank { null }

    /** An http(s) URL with a host; the only shape the event accepts from this screen. */
    fun isValidUrl(url: String): Boolean {
        val trimmed = url.trim()
        val scheme = trimmed.substringBefore("://", "").lowercase()
        return (scheme == "https" || scheme == "http") && hostOf(trimmed) != null && trimmed.none { it.isWhitespace() }
    }

    /** `sk-or-…a1b2`: enough to tell keys apart, never enough to use one. */
    fun maskKey(key: String): String {
        if (key.isEmpty()) return ""
        if (key.length <= 10) return "…" + key.takeLast(2)
        val prefix = key.substringBeforeLast('-', "").let { if (it.isNotEmpty() && it.length <= 8) "$it-" else key.take(3) }
        return prefix + "…" + key.takeLast(4)
    }
}
