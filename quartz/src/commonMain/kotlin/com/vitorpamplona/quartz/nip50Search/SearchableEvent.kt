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
package com.vitorpamplona.quartz.nip50Search

/**
 * Receives one indexable field at a time. Returning false stops the walk.
 *
 * This is a `fun interface` rather than a lambda parameter on purpose: an interface method cannot
 * be `inline`, so a lambda written at the call site would allocate on every event. One instance
 * of this is built per scan and reused for every event in it, carrying whatever the walk needs —
 * the query terms, a running hit flag — on itself. That is what makes a full-cache scan
 * allocation-free.
 */
fun interface IndexableFieldVisitor {
    /**
     * @param field one field's text, or null where the event does not carry it — implementors
     *   pass their optional fields straight through rather than filtering them first.
     * @return false to stop the walk. A hit on the title need not build the body.
     */
    fun visit(field: String?): Boolean
}

/**
 * An event whose human-authored text is worth indexing for NIP-50 search.
 *
 * There are two ways to read that text, and they exist for opposite cost profiles:
 *
 * - [indexableContent] is the **write path**. The stores call it once per event on insert, so it
 *   is free to build a joined string; the SQLite store hands the result to FTS5 and the
 *   filesystem store tokenizes it. Its exact output per kind is mirrored by external search
 *   engines — see `references/searchable-kinds.md` in the `searchable-events` skill — so changing
 *   what it returns is a breaking change that needs a reindex.
 *
 * - [forEachIndexableField] is the **read path**. Matching a query against the whole cache calls
 *   it once per event per keystroke, where building a string per event is not affordable. It
 *   hands over the fields the event already holds, allocating nothing, and stops early on a hit.
 *
 * The default [forEachIndexableField] falls back to [indexableContent], which is correct for
 * every kind and free for the ~28 whose indexable content is `content` itself. Kinds that join
 * several fields — the majority — should override it so the read path stops paying for a join
 * the reader may not even need.
 */
interface SearchableEvent {
    fun indexableContent(): String

    fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        visitor.visit(indexableContent())
    }
}
