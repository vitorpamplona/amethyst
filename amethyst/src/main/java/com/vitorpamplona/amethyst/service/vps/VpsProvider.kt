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
package com.vitorpamplona.amethyst.service.vps

data class VpsRegion(
    val id: String,
    val city: String,
    val country: String,
    val continent: String,
)

data class VpsPlan(
    val id: String,
    val name: String,
    val vcpuCount: Int,
    val ramMb: Int,
    val diskGb: Int,
    val bandwidthTb: Double,
    val monthlyPriceCents: Int,
    val regions: List<String>,
) {
    val monthlyPriceDisplay: String
        get() = "$${monthlyPriceCents / 100}.${"%02d".format(monthlyPriceCents % 100)}/mo"

    val specsDisplay: String
        get() = "${vcpuCount}vCPU, ${if (ramMb >= 1024) "${ramMb / 1024}GB" else "${ramMb}MB"} RAM, ${diskGb}GB SSD"
}

enum class VpsServerStatus {
    PENDING,
    ACTIVE,
    SUSPENDED,
    RESIZING,
    UNKNOWN,
}

data class VpsServer(
    val id: String,
    val label: String,
    val status: VpsServerStatus,
    val ipv4: String,
    val ipv6: String,
    val region: String,
    val plan: String,
    val os: String,
    val relayUrl: String?,
)

data class VpsProviderInfo(
    val id: String,
    val name: String,
    val description: String,
    val website: String,
    val acceptsCrypto: Boolean,
)

interface VpsProvider {
    val info: VpsProviderInfo

    suspend fun validateApiKey(apiKey: String): Boolean

    suspend fun listRegions(apiKey: String): Result<List<VpsRegion>>

    suspend fun listPlans(apiKey: String): Result<List<VpsPlan>>

    suspend fun createServer(
        apiKey: String,
        region: String,
        plan: String,
        label: String,
        userData: String,
    ): Result<VpsServer>

    suspend fun getServer(
        apiKey: String,
        serverId: String,
    ): Result<VpsServer>

    suspend fun listServers(apiKey: String): Result<List<VpsServer>>

    suspend fun deleteServer(
        apiKey: String,
        serverId: String,
    ): Result<Unit>
}
