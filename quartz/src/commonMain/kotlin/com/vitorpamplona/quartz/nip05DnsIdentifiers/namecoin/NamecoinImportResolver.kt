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
package com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Resolves the [`import`][ifa-0001] item of a Namecoin Domain Name Object,
 * recursively merging values from the imported names into the importing
 * object before the caller extracts fields like `nostr`.
 *
 * Per [ifa-0001](https://github.com/namecoin/proposals/blob/master/ifa-0001.md)
 * §"import":
 *
 *  - The importing object's items take precedence over the imported items.
 *    `null` items in the importer are still considered "present" and so
 *    nullify the corresponding imported item (semantic suppression).
 *  - The `"import"` value is an array of arrays. Each inner array has at
 *    least one element, the name to import (e.g. `"d/example2"`), and an
 *    optional second element, a Subdomain Selector (DNS-format, dotted).
 *    Selector labels are resolved via the imported value's `map` tree
 *    before merging.
 *  - Three short-hand value forms are accepted alongside the canonical
 *    array-of-arrays. Real-world Namecoin records frequently write the
 *    bare-string form, so we MUST handle it:
 *      - `"import": "d/foo"`            ↔  `[["d/foo"]]`
 *      - `"import": ["d/foo"]`          ↔  `[["d/foo"]]`
 *      - `"import": ["d/foo","sub"]`    ↔  `[["d/foo","sub"]]`
 *  - Recursion: the spec mandates that implementations support **at least
 *    a recursion depth of four**. We default to that limit; deeper chains
 *    are silently truncated (the importing object's own items still apply).
 *  - Cycles are broken by a visited-set keyed on `name|selector`.
 *  - Maps are merged shallow-per-spec: a key in the importing object
 *    replaces the imported value for that key wholesale; there is no
 *    recursive merge of nested objects (none of the items consumed by
 *    Quartz today require it).
 *  - A failed lookup (name not found, malformed JSON, network error)
 *    MAY cause the whole importing record to fail per spec. To preserve
 *    Quartz's existing best-effort namecoin behaviour (where transient
 *    ElectrumX hiccups don't kill resolution outright), this
 *    implementation treats a failed import as if the imported value
 *    were the empty object `{}`. The importing object's own items still
 *    apply.
 *
 * Use [expandImports] in any flow that has a parsed root [JsonObject]
 * and needs the post-import view before extracting record-specific fields.
 */
internal object NamecoinImportResolver {
    /** The minimum recursion depth ifa-0001 requires implementations to support. */
    const val DEFAULT_MAX_DEPTH: Int = 4

    private val SHARED_JSON =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    /**
     * Async name lookup callback. Returns the raw value JSON string of the
     * named record, or `null` if the name does not exist / is expired /
     * could not be fetched. Failures are absorbed: the returned object is
     * always usable.
     *
     * The callback is called once per `import` target (deduplicated within
     * a single recursive expansion path).
     */
    typealias NameValueFetcher = suspend (namecoinName: String) -> String?

    /**
     * Expand all `import` items in [root] (and recursively in imported
     * objects) up to [maxDepth] levels deep, returning a single merged
     * [JsonObject] with no `import` key.
     *
     * The merged object preserves the importing object's items unchanged;
     * imported items only fill in keys the importing object did not declare
     * (including keys whose value is `null` — those remain suppressed).
     *
     * If [root] has no `import` key, it is returned unchanged.
     */
    suspend fun expandImports(
        root: JsonObject,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        fetcher: NameValueFetcher,
    ): JsonObject = expandRecursive(root, fetcher, maxDepth, mutableSetOf())

    // ────────────────────────────────────────────────────────────────────

    private suspend fun expandRecursive(
        obj: JsonObject,
        fetcher: NameValueFetcher,
        budgetRemaining: Int,
        visited: MutableSet<String>,
    ): JsonObject {
        val importItem = obj["import"] ?: return obj
        val operations = parseImportItem(importItem) ?: return removeImportKey(obj)
        if (operations.isEmpty() || budgetRemaining <= 0) return removeImportKey(obj)

        // Walk imports left-to-right. Spec is silent on multiple-import
        // precedence; we follow the common-sense rule that LATER imports
        // override EARLIER ones in the same array (otherwise listing two
        // libraries would silently ignore the second). The whole accumulator
        // still loses to the importing object on top of all of it.
        var accumulator: JsonObject = JsonObject(emptyMap())
        for (op in operations) {
            val visitKey = visitKeyFor(op)
            if (!visited.add(visitKey)) continue // cycle / duplicate within this chain
            try {
                val importedRaw = fetcher(op.name) ?: continue
                val importedRoot = tryParseObject(importedRaw) ?: continue
                val selectorView = applySelector(importedRoot, op.selector) ?: continue
                val expanded =
                    expandRecursive(
                        selectorView,
                        fetcher,
                        budgetRemaining - 1,
                        visited,
                    )
                accumulator = mergeImporterWins(importer = expanded, imported = accumulator)
            } finally {
                visited.remove(visitKey)
            }
        }

        // Finally merge the importing object on top, removing its `import` key.
        val withoutImport = removeImportKey(obj)
        return mergeImporterWins(importer = withoutImport, imported = accumulator)
    }

    /**
     * Merge two objects with importer-wins semantics: every key in [importer]
     * stays as-is (including `null` values, which suppress the imported
     * counterpart per ifa-0001); keys present only in [imported] are added.
     */
    private fun mergeImporterWins(
        importer: JsonObject,
        imported: JsonObject,
    ): JsonObject {
        if (imported.isEmpty()) return importer
        if (importer.isEmpty()) return imported
        val out = LinkedHashMap<String, JsonElement>(importer.size + imported.size)
        // Imported first so we can overwrite with importer.
        for ((k, v) in imported) out[k] = v
        for ((k, v) in importer) out[k] = v
        return JsonObject(out)
    }

    /**
     * Walk the imported object's `map` tree to the node addressed by
     * [selector] (DNS dotted, e.g. `relay`, `a.b.c`). Empty selector
     * returns [root] unchanged.
     *
     * Resolution rules per ifa-0001 §"map":
     *   - Exact label match wins.
     *   - Wildcard `*` matches any single label.
     *   - Empty key `""` is the default for the current level when no other
     *     match applies.
     *   - A non-object child terminates the walk with `null`.
     */
    private fun applySelector(
        root: JsonObject,
        selector: String,
    ): JsonObject? {
        if (selector.isEmpty()) return root
        // Selector is DNS-dotted: leftmost label is the most-specific. The
        // `map` tree is rooted at the parent and nests inwards toward the
        // leaf, so we walk labels right-to-left (the rightmost label is
        // the immediate child of the parent's `map`).
        val labels =
            selector
                .split('.')
                .filter { it.isNotEmpty() }
                .asReversed()
        if (labels.isEmpty()) return root

        var current: JsonObject = root
        for (label in labels) {
            val map = current["map"] as? JsonObject ?: return null
            val child =
                (map[label] as? JsonObject)
                    ?: (map["*"] as? JsonObject)
                    ?: (map[""] as? JsonObject)
                    ?: return null
            current = child
        }
        return current
    }

    private fun tryParseObject(rawJson: String): JsonObject? = runCatching { SHARED_JSON.parseToJsonElement(rawJson).jsonObject }.getOrNull()

    private fun removeImportKey(obj: JsonObject): JsonObject {
        if (!obj.containsKey("import")) return obj
        val out = LinkedHashMap<String, JsonElement>(obj.size - 1)
        for ((k, v) in obj) if (k != "import") out[k] = v
        return JsonObject(out)
    }

    private fun visitKeyFor(op: ImportOp): String = "${op.name}|${op.selector}"

    /**
     * Parse the value of an `import` item into a flat list of [ImportOp]
     * descriptors. Returns `null` if the value is malformed.
     *
     * Accepted shapes (in order of preference):
     *   - canonical: `[ ["d/foo"], ["d/bar","sub"] ]`
     *   - shorthand string: `"d/foo"` → one op with no selector
     *   - shorthand single-array: `["d/foo"]` → one op with no selector
     *   - shorthand pair-array: `["d/foo","sub"]` → one op with selector
     *
     * Anything else is treated as malformed and the import is skipped.
     */
    private fun parseImportItem(item: JsonElement): List<ImportOp>? {
        // Shorthand: bare string.
        if (item is JsonPrimitive && item.isString) {
            return listOf(ImportOp(item.content, ""))
        }
        // Array shapes.
        val arr = (item as? JsonArray) ?: return null
        if (arr.isEmpty()) return emptyList()

        // Distinguish: array-of-arrays (canonical) vs array-of-strings (shorthand).
        val firstIsArray = arr.first() is JsonArray
        if (firstIsArray) {
            return arr.mapNotNull { entry ->
                val inner = (entry as? JsonArray) ?: return@mapNotNull null
                opFromArray(inner)
            }
        }
        // Shorthand: ["name"] or ["name","selector"]. All elements must be
        // strings; anything else makes the whole item malformed.
        return listOfNotNull(opFromArray(arr))
    }

    private fun opFromArray(arr: JsonArray): ImportOp? {
        if (arr.isEmpty()) return null
        val name = (arr[0] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim() ?: return null
        if (name.isEmpty()) return null
        val selector =
            if (arr.size >= 2) {
                (arr[1] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim() ?: ""
            } else {
                ""
            }
        // Trailing dot is forbidden by spec; treat as malformed → no selector.
        if (selector.endsWith('.')) return null
        return ImportOp(name = name, selector = selector)
    }

    private data class ImportOp(
        val name: String,
        /** DNS dotted, may be empty. Preserved as written. */
        val selector: String,
    )
}
