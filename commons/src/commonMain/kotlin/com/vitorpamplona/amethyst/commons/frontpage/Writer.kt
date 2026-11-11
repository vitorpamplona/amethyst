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
package com.vitorpamplona.amethyst.commons.frontpage

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.util.formatDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.math.round

/*
 * The writer call: one fixed system prompt, the digest as data, one HTML document back,
 * sent with the reader's own AI key (kind 12000, NIP-FA) to an OpenAI Chat Completions
 * endpoint. The prompt is the Observer's editorial brief and house stylesheet
 * (MIT, see ./LICENSE.observer); the helpers match Brainstorm's writer.ts.
 */

/** The paper's standing name. There is no continuity: it is the same every day. */
const val MASTHEAD = "The Nostr Observer"

fun systemPrompt(): String = "$EDITORIAL_BRIEF\n\n## The house stylesheet\n\nInline this in a <style> block, as given:\n\n```css\n$HOUSE_CSS\n```"

/**
 * The digest in a delimited block; the reader's name and language go outside it, because they
 * are ours. The language goes here, never into the fixed brief: the brief says what to do when
 * one is named (correction 9), this names it, as a BCP 47 tag ("pt-BR").
 */
fun userMessage(
    digest: String,
    readerName: String,
    language: String? = null,
): String =
    listOfNotNull(
        "Masthead: $MASTHEAD",
        "Reader: $readerName",
        language?.trim()?.ifEmpty { null }?.let { "Language: $it" },
        "",
        "<corpus>",
        digest,
        "</corpus>",
        "",
        "Write today's front page. Return the complete HTML document and nothing else.",
    ).joinToString("\n")

/** One repair round: the page the boundary refused, and why. */
fun repairMessage(violations: List<Violation>): String =
    buildList {
        add("The boundary check refused that page. Every item below must be fixed:")
        add("")
        violations.take(40).forEach { add("- ${it.kind}: ${it.detail}\n  ${it.excerpt.replace(Regex("\\s+"), " ")}") }
        add("")
        add("QUOTE: copy the words exactly from one event, or paraphrase without <q>/<blockquote>.")
        add("IMAGE: use <img src=\"art-N\"> ids from the shortlist only.")
        add("LINK: only the citation, byline, watch, listing and calendar URL forms the brief allows.")
        add("MARKUP: no scripts, forms, frames or handlers.")
        add("")
        add("Return the corrected complete HTML document and nothing else.")
    }.joinToString("\n")

class ChatMessage(
    val role: String,
    val content: String,
)

class Usage(
    val promptTokens: Long?,
    val completionTokens: Long?,
) {
    val total get() = if (promptTokens == null && completionTokens == null) null else (promptTokens ?: 0) + (completionTokens ?: 0)

    operator fun plus(other: Usage?): Usage =
        if (other == null) {
            this
        } else {
            Usage(sum(promptTokens, other.promptTokens), sum(completionTokens, other.completionTokens))
        }

    private fun sum(
        a: Long?,
        b: Long?,
    ) = if (a == null && b == null) null else (a ?: 0) + (b ?: 0)
}

class Written(
    val text: String,
    val usage: Usage?,
)

/** Anthropic's compatibility layer caps a reply far below a page unless told otherwise. */
private const val ANTHROPIC_MAX_TOKENS = 32_000

fun isAnthropic(url: String) =
    url
        .substringAfter("://", "")
        .substringBefore('/')
        .substringBefore(':')
        .lowercase() == "api.anthropic.com"

fun chatRequestBody(
    url: String,
    model: String,
    messages: List<ChatMessage>,
    stream: Boolean,
): String =
    buildJsonObject {
        put("model", model)
        put(
            "messages",
            buildJsonArray {
                messages.forEach {
                    add(
                        buildJsonObject {
                            put("role", it.role)
                            put("content", it.content)
                        },
                    )
                }
            },
        )
        put("stream", stream)
        if (stream) put("stream_options", buildJsonObject { put("include_usage", true) })
        if (isAnthropic(url)) put("max_tokens", ANTHROPIC_MAX_TOKENS)
    }.toString()

private val lenient = Json { ignoreUnknownKeys = true }

private fun parse(text: String): JsonElement? =
    try {
        lenient.parseToJsonElement(text)
    } catch (e: IllegalArgumentException) {
        null
    }

fun readUsage(raw: JsonElement?): Usage? {
    val usage = raw as? JsonObject ?: return null

    fun n(key: String) = (usage[key] as? JsonPrimitive)?.longOrNull
    return Usage(n("prompt_tokens"), n("completion_tokens"))
}

class StreamChunk(
    val text: String,
    val usage: Usage?,
    val done: Boolean,
    /** The choice's `finish_reason`, on the chunk that ends it. */
    val finishReason: String? = null,
    /** An `{"error": …}` frame's message: the provider gave up mid-answer. */
    val error: String? = null,
)

/** One `data:` line of a Chat Completions stream: the text it adds and any usage it carried. */
fun parseStreamLine(line: String): StreamChunk {
    val trimmed = line.trim()
    if (!trimmed.startsWith("data:")) return StreamChunk("", null, false)
    val data = trimmed.removePrefix("data:").trim()
    if (data == "[DONE]") return StreamChunk("", null, true)
    val json = parse(data) as? JsonObject ?: return StreamChunk("", null, false)
    errorOf(json)?.let { return StreamChunk("", null, false, error = it) }
    val choice = (json["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
    val delta = choice?.get("delta") as? JsonObject
    val content = (delta?.get("content") as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
    return StreamChunk(content, readUsage(json["usage"]), false, finishReason = finishReasonOf(choice))
}

private fun errorOf(json: JsonObject): String? {
    val error = json["error"] ?: return null
    if (error is JsonNull) return null
    val message = ((error as? JsonObject)?.get("message") as? JsonPrimitive)?.content ?: (error as? JsonPrimitive)?.content
    return message?.replace(Regex("\\s+"), " ")?.trim()?.take(160) ?: "error"
}

private fun finishReasonOf(choice: JsonObject?) = (choice?.get("finish_reason") as? JsonPrimitive)?.takeIf { it.isString }?.content

/**
 * A whole stream's lines, as one answer: stops at `[DONE]`; an error frame, a `finish_reason`
 * other than `stop`, or a stream that ends with neither `[DONE]` nor `stop` is a failure, never a
 * paper (a cut-off page could still pass the boundary, missing its second half).
 */
class StreamReader(
    private val onText: (Int) -> Unit,
) {
    private val text = StringBuilder()
    private var usage: Usage? = null
    private var finish: String? = null
    private var done = false

    /** Reads one line; true when the stream has ended. */
    fun read(line: String): Boolean {
        val chunk = parseStreamLine(line)
        chunk.error?.let { throw WriterException(WriterFailure.CUT_OFF, providerSaid = it) }
        if (chunk.text.isNotEmpty()) {
            text.append(chunk.text)
            onText(text.length)
        }
        if (chunk.usage != null) usage = chunk.usage
        chunk.finishReason?.let { finish = it }
        if (chunk.done) done = true
        return done
    }

    fun finish(): Written {
        val reason = finish
        if (reason != null && reason != "stop") throw WriterException(WriterFailure.CUT_OFF, providerSaid = reason)
        if (reason == null && !done) throw WriterException(WriterFailure.CUT_OFF)
        return Written(text.toString(), usage)
    }
}

/**
 * A non-streamed answer's text. An error body or a `finish_reason` other than `stop` is a failure;
 * a missing `finish_reason` is accepted here, as some providers leave it out of whole answers.
 */
fun readCompletion(body: String): Written {
    val json = parse(body) as? JsonObject
    json?.let { errorOf(it) }?.let { throw WriterException(WriterFailure.CUT_OFF, providerSaid = it) }
    val choice = (json?.get("choices") as? JsonArray)?.firstOrNull() as? JsonObject
    finishReasonOf(choice)?.takeIf { it != "stop" }?.let { throw WriterException(WriterFailure.CUT_OFF, providerSaid = it) }
    val message = choice?.get("message") as? JsonObject
    val content = (message?.get("content") as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
    return Written(content, readUsage(json?.get("usage")))
}

private val FENCE = Regex("^```(?:html)?\\s*\\n([\\s\\S]*?)\\n?```\\s*$", RegexOption.IGNORE_CASE)
private val DOC_START = Regex("<!doctype html|<html[\\s>]", RegexOption.IGNORE_CASE)

/** The document without the wrapping a model adds despite being told not to. */
fun extractDocument(text: String): String {
    var out = text.trim()
    FENCE.find(out)?.let { out = it.groupValues[1].trim() }
    val start = DOC_START.find(out)?.range?.first ?: -1
    if (start > 0) out = out.substring(start)
    val end = out.lowercase().lastIndexOf("</html>")
    if (end >= 0) out = out.substring(0, end + "</html>".length)
    return out
}

// --- choosing a model --------------------------------------------------------

/**
 * Families a front page is worth writing with, best first: a page is a long, careful
 * document, so long-form writers lead and small/fast variants trail.
 */
private val PREFERENCE =
    listOf(
        Regex("claude[-.\\w]*sonnet|sonnet[-.\\w]*claude", RegexOption.IGNORE_CASE),
        Regex("claude[-.\\w]*opus|opus", RegexOption.IGNORE_CASE),
        Regex("gpt-5(?![-.\\w]*(mini|nano))", RegexOption.IGNORE_CASE),
        Regex("gemini[-.\\w]*pro", RegexOption.IGNORE_CASE),
        Regex("gpt-4\\.1(?![-.\\w]*(mini|nano))|gpt-4o(?![-.\\w]*mini)", RegexOption.IGNORE_CASE),
        Regex("deepseek[-.\\w]*(chat|v3|r1)", RegexOption.IGNORE_CASE),
        Regex("qwen[-.\\w]*(max|72b|235b)|llama[-.\\w]*(405b|70b)|mistral[-.\\w]*large|grok", RegexOption.IGNORE_CASE),
        Regex("claude|gpt|gemini|llama|qwen|mistral|deepseek", RegexOption.IGNORE_CASE),
    )

private val NOT_CHAT = Regex("embed|whisper|tts|transcri|audio|realtime|image|dall-?e|moderation|rerank|vision-preview|search-preview", RegexOption.IGNORE_CASE)
private val SMALL = Regex("mini|nano|haiku|flash|lite|small|8b|7b|3b|1b", RegexOption.IGNORE_CASE)
private val NUMBER = Regex("\\d+(?:\\.\\d+)?")

private fun versionKey(id: String) = NUMBER.findAll(id).map { it.value.toDouble() }.toList()

private fun compareVersionsDesc(
    a: String,
    b: String,
): Int {
    val va = versionKey(a)
    val vb = versionKey(b)
    for (i in 0 until maxOf(va.size, vb.size)) {
        val d = (vb.getOrNull(i) ?: -1.0).compareTo(va.getOrNull(i) ?: -1.0)
        if (d != 0) return d
    }
    return a.compareTo(b)
}

/** Every listed model a page could be written with, best guess first. */
fun rankModels(ids: List<String>): List<String> {
    fun tier(id: String): Double {
        val at = PREFERENCE.indexOfFirst { it.containsMatchIn(id) }
        return (if (at == -1) PREFERENCE.size else at) + if (SMALL.containsMatchIn(id)) 0.5 else 0.0
    }
    return ids
        .distinct()
        .filter { it.isNotEmpty() && !NOT_CHAT.containsMatchIn(it) }
        .sortedWith { a, b -> tier(a).compareTo(tier(b)).takeIf { it != 0 } ?: compareVersionsDesc(a, b) }
}

/**
 * A model the endpoint lists, with its prices when the endpoint publishes them (OpenRouter
 * does, per token, as decimal strings). Null prices mean "not said", never "free".
 */
@Immutable
class AgentModel(
    val id: String,
    val promptPerToken: Double? = null,
    val completionPerToken: Double? = null,
) {
    val priced get() = promptPerToken != null && completionPerToken != null
}

/** `GET {url}/models` to models, with OpenRouter's `pricing` read where it is there. */
fun readModels(body: String): List<AgentModel> {
    val json = parse(body) as? JsonObject ?: return emptyList()
    val list = (json["data"] as? JsonArray) ?: (json["models"] as? JsonArray) ?: return emptyList()
    return list.mapNotNull { entry ->
        when (entry) {
            is JsonPrimitive ->
                entry
                    .takeIf { it.isString }
                    ?.content
                    ?.removePrefix("models/")
                    ?.ifEmpty { null }
                    ?.let { AgentModel(it) }
            is JsonObject -> {
                val id = ((entry["id"] as? JsonPrimitive)?.content ?: (entry["name"] as? JsonPrimitive)?.content)?.removePrefix("models/")?.ifEmpty { null }
                val pricing = entry["pricing"] as? JsonObject

                fun price(name: String) = (pricing?.get(name) as? JsonPrimitive)?.content?.toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }
                id?.let { AgentModel(it, price("prompt"), price("completion")) }
            }
            else -> null
        }
    }
}

// --- cost --------------------------------------------------------------------

/** What a page usually takes to write: a full HTML document with inline CSS. A repair round is not counted. */
const val EXPECTED_OUTPUT_TOKENS = 14_000

/** A rough token count: about four characters a token, for English-heavy text. */
fun approxTokens(chars: Int): Long = (chars + 3L) / 4

/**
 * The cost of one print before it runs: the brief and a digest of [digestChars] in, a page out.
 * Null when the endpoint publishes no prices: an estimate is never invented.
 */
fun estimateCost(
    model: AgentModel?,
    digestChars: Int,
    briefChars: Int = systemPrompt().length,
): Double? {
    val prompt = model?.promptPerToken ?: return null
    val completion = model.completionPerToken ?: return null
    return approxTokens(digestChars + briefChars) * prompt + EXPECTED_OUTPUT_TOKENS * completion
}

/** What a print cost, from the usage the provider reported and its own prices. */
fun actualCost(
    model: AgentModel?,
    usage: Usage?,
): Double? {
    val prompt = model?.promptPerToken ?: return null
    val completion = model.completionPerToken ?: return null
    val inTokens = usage?.promptTokens ?: return null
    val outTokens = usage.completionTokens ?: return null
    return inTokens * prompt + outTokens * completion
}

/**
 * `$0.08`, `$1.20`, or `<$0.01`: dollars to the cent, which is what a reader budgets in, with the
 * reader's decimal mark and grouping (`$1.234,50` in German).
 */
fun formatUsd(amount: Double): String {
    if (amount < 0.01) return "<$" + formatDecimal(0.01, maxFractionDigits = 2, minFractionDigits = 2)
    return "$" + formatDecimal(round(amount * 100) / 100, maxFractionDigits = 2, minFractionDigits = 2)
}

/** `GET {url}/models` to ids, whichever of the two common shapes it comes in. */
fun readModelIds(body: String): List<String> {
    val json = parse(body) as? JsonObject ?: return emptyList()
    val list = (json["data"] as? JsonArray) ?: (json["models"] as? JsonArray) ?: return emptyList()
    return list
        .mapNotNull { entry ->
            when (entry) {
                is JsonPrimitive -> entry.takeIf { it.isString }?.content
                is JsonObject -> (entry["id"] as? JsonPrimitive)?.content ?: (entry["name"] as? JsonPrimitive)?.content
                else -> null
            }
        }.map { it.removePrefix("models/") }
        .filter { it.isNotEmpty() }
}
