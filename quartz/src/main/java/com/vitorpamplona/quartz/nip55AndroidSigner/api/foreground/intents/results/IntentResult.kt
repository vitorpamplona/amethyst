/**
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
package com.vitorpamplona.quartz.nip55AndroidSigner.api.foreground.intents.results

import android.content.Intent
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.nip01Core.jackson.JsonMapper

data class IntentResult(
    val `package`: String? = null,
    val result: String? = null,
    val event: String? = null,
    val id: String? = null,
) {
    fun toJson(): String = JsonMapper.mapper.writeValueAsString(this)

    fun toIntent(): Intent {
        val intent = Intent()
        intent.putExtra("id", id)
        intent.putExtra("result", result)
        intent.putExtra("event", event)
        intent.putExtra("package", `package`)
        return intent
    }

    companion object {
        fun fromIntent(data: Intent): IntentResult =
            IntentResult(
                id = data.getStringExtra("id"),
                result = data.getStringExtra("result"),
                event = data.getStringExtra("event"),
                `package` = data.getStringExtra("package"),
            )

        fun fromJson(json: String): IntentResult = JsonMapper.mapper.readValue<IntentResult>(json)

        fun fromJsonArray(json: String): List<IntentResult> = JsonMapper.mapper.readValue<List<IntentResult>>(json)
    }
}
