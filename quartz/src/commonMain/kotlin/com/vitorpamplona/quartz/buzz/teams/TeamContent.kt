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
package com.vitorpamplona.quartz.buzz.teams

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The JSON body stored in a Buzz Team event ([TeamEvent], NIP-AP `kind:30176`).
 *
 * Explicit opt-in projection of the public team fields, published by the workspace owner.
 * Ground truth is `TeamEventContent` in Buzz's
 * `desktop/src-tauri/src/managed_agents/team_events.rs`. Local-only install fields
 * (`source_dir`, `is_symlink`, `is_builtin`, timestamps) are intentionally never published.
 *
 * [name] is required. Wire field names are snake_case (the Rust struct carries no
 * `rename_all`). Unknown JSON fields are ignored for forward compatibility.
 *
 * WIRE-SEMANTICS CAVEAT (see [TeamEvent] KDoc and FLAG in the interop report): upstream
 * models both [instructions] and [personaIds] with an extra "absent vs. explicitly-null"
 * layer (`Option<Option<String>>` / `Option<Vec<String>>`) so a reconcile can tell "publisher
 * predates always-publish" (absent -> preserve local) from "explicitly cleared" (null/`[]`).
 * kotlinx.serialization cannot express that third state, so [instructions] collapses absent
 * and JSON-`null` into `null`, and [personaIds] collapses absent and JSON-`null` into `null`
 * (an explicit empty list still round-trips as `emptyList()`). Read/serialize only; if this
 * model is ever used to author reconcile-merge writes, restore the tri-state first.
 */
@Serializable
data class TeamContent(
    val name: String,
    val description: String? = null,
    val instructions: String? = null,
    @SerialName("persona_ids") val personaIds: List<String>? = null,
) {
    fun encodeToJson(): String = JSON.encodeToString(this)

    companion object {
        val JSON =
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            }

        fun decodeFromJson(json: String): TeamContent = JSON.decodeFromString(json)
    }
}
