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
package com.vitorpamplona.quartz.nip96FileStorage.info

import com.fasterxml.jackson.annotation.JsonProperty

typealias PlanName = String

typealias MimeType = String

data class ServerInfo(
    @field:JsonProperty("api_url") val apiUrl: String,
    @field:JsonProperty("download_url") val downloadUrl: String? = null,
    @field:JsonProperty("delegated_to_url") val delegatedToUrl: String? = null,
    @field:JsonProperty("supported_nips") val supportedNips: ArrayList<Int> = arrayListOf(),
    @field:JsonProperty("tos_url") val tosUrl: String? = null,
    @field:JsonProperty("content_types") val contentTypes: ArrayList<MimeType> = arrayListOf(),
    @field:JsonProperty("plans") val plans: Map<PlanName, Plan> = mapOf(),
)

data class Plan(
    @field:JsonProperty("name") val name: String? = null,
    @field:JsonProperty("is_nip98_required") val isNip98Required: Boolean? = null,
    @field:JsonProperty("url") val url: String? = null,
    @field:JsonProperty("max_byte_size") val maxByteSize: Long? = null,
    @field:JsonProperty("file_expiration") val fileExpiration: ArrayList<Int> = arrayListOf(),
    @field:JsonProperty("media_transformations") val mediaTransformations: Map<MimeType, Array<String>> = emptyMap(),
)
