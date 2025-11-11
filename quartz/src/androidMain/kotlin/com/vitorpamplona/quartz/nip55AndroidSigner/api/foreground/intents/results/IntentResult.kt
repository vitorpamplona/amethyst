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
import com.vitorpamplona.quartz.nip01Core.core.OptimizedSerializable
import com.vitorpamplona.quartz.nip55AndroidSigner.JsonMapperNip55
import kotlinx.serialization.Serializable

@Serializable
data class IntentResult(
    val `package`: String? = null,
    val result: String? = null,
    val event: String? = null,
    val id: String? = null,
    val rejected: Boolean? = false,
) : OptimizedSerializable {
    fun toJson(): String = JsonMapperNip55.toJson(this)

    fun toIntent(): Intent {
        val intent = Intent()
        if (id != null) intent.putExtra("id", id)
        if (result != null) intent.putExtra("result", result)
        if (event != null) intent.putExtra("event", event)
        if (`package` != null) intent.putExtra("package", `package`)
        if (rejected != null) intent.putExtra("rejected", rejected)
        return intent
    }

    companion object {
        fun fromIntent(data: Intent): IntentResult =
            IntentResult(
                id = data.getStringExtra("id"),
                result = data.getStringExtra("result"),
                event = data.getStringExtra("event"),
                `package` = data.getStringExtra("package"),
                rejected =
                    if (data.extras?.containsKey("rejected") == true) {
                        data.getBooleanExtra("rejected", false)
                    } else {
                        null
                    },
            )

        fun fromJson(json: String): IntentResult = JsonMapperNip55.fromJsonTo<IntentResult>(json)

        fun fromJsonArray(json: String): List<IntentResult> = JsonMapperNip55.fromJsonTo<List<IntentResult>>(json)
    }
}
