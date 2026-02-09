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
package com.vitorpamplona.quartz.nip96FileStorage.info

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias PlanName = String

typealias MimeType = String

@Serializable
data class ServerInfo(
    @SerialName("api_url") val apiUrl: String,
    @SerialName("download_url") val downloadUrl: String? = null,
    @SerialName("delegated_to_url") val delegatedToUrl: String? = null,
    @SerialName("supported_nips") val supportedNips: ArrayList<Int> = arrayListOf(),
    @SerialName("tos_url") val tosUrl: String? = null,
    @SerialName("content_types") val contentTypes: ArrayList<MimeType> = arrayListOf(),
    @SerialName("plans") val plans: Map<PlanName, Plan> = mapOf(),
)

@Serializable
data class Plan(
    @SerialName("name") val name: String? = null,
    @SerialName("is_nip98_required") val isNip98Required: Boolean? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("max_byte_size") val maxByteSize: Long? = null,
    @SerialName("file_expiration") val fileExpiration: ArrayList<Int> = arrayListOf(),
    @SerialName("media_transformations") val mediaTransformations: Map<MimeType, Array<String>> = emptyMap(),
)
