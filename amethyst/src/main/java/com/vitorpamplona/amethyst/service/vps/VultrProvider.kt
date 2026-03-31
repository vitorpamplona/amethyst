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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync
import java.util.concurrent.TimeUnit

class VultrProvider(
    private val httpClientProvider: () -> OkHttpClient =
        {
            OkHttpClient
                .Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        },
) : VpsProvider {
    companion object {
        private const val BASE_URL = "https://api.vultr.com/v2"
        private val JSON_TYPE = "application/json".toMediaType()
        private val mapper = jacksonObjectMapper()

        // Ubuntu 24.04 LTS
        private const val UBUNTU_OS_ID = 2284
    }

    override val info =
        VpsProviderInfo(
            id = "vultr",
            name = "Vultr",
            description = "Cloud hosting with global data centers. Accepts cryptocurrency payments.",
            website = "https://www.vultr.com",
            acceptsCrypto = true,
        )

    private fun authRequest(
        apiKey: String,
        url: String,
    ): Request.Builder =
        Request
            .Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")

    override suspend fun validateApiKey(apiKey: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val request = authRequest(apiKey, "$BASE_URL/account").get().build()
                httpClientProvider().newCall(request).executeAsync().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                false
            }
        }

    override suspend fun listRegions(apiKey: String): Result<List<VpsRegion>> =
        withContext(Dispatchers.IO) {
            try {
                val request = authRequest(apiKey, "$BASE_URL/regions").get().build()
                httpClientProvider().newCall(request).executeAsync().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${response.code}"))
                    }
                    val body = response.body.string()
                    val tree = mapper.readTree(body)
                    val regions =
                        tree["regions"]?.map { node ->
                            VpsRegion(
                                id = node["id"].asText(),
                                city = node["city"].asText(),
                                country = node["country"].asText(),
                                continent = node["continent"].asText(),
                            )
                        } ?: emptyList()
                    Result.success(regions)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun listPlans(apiKey: String): Result<List<VpsPlan>> =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    authRequest(apiKey, "$BASE_URL/plans?type=vc2&per_page=100")
                        .get()
                        .build()
                httpClientProvider().newCall(request).executeAsync().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${response.code}"))
                    }
                    val body = response.body.string()
                    val tree = mapper.readTree(body)
                    val plans =
                        tree["plans"]
                            ?.filter { node ->
                                // Only plans with at least 1GB RAM suitable for strfry
                                node["ram"].asInt() >= 1024
                            }?.map { node ->
                                VpsPlan(
                                    id = node["id"].asText(),
                                    name =
                                        node["id"].asText().replace("vc2-", "").uppercase(),
                                    vcpuCount = node["vcpu_count"].asInt(),
                                    ramMb = node["ram"].asInt(),
                                    diskGb = node["disk"].asInt(),
                                    bandwidthTb = node["bandwidth"].asDouble() / 1024.0,
                                    monthlyPriceCents = (node["monthly_cost"].asDouble() * 100).toInt(),
                                    regions = node["locations"]?.map { it.asText() } ?: emptyList(),
                                )
                            }?.sortedBy { it.monthlyPriceCents } ?: emptyList()
                    Result.success(plans)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun createServer(
        apiKey: String,
        region: String,
        plan: String,
        label: String,
        userData: String,
    ): Result<VpsServer> =
        withContext(Dispatchers.IO) {
            try {
                val payload =
                    mapper.writeValueAsString(
                        mapOf(
                            "region" to region,
                            "plan" to plan,
                            "os_id" to UBUNTU_OS_ID,
                            "label" to label,
                            "hostname" to label.replace(Regex("[^a-zA-Z0-9-]"), "-").take(63),
                            "user_data" to android.util.Base64.encodeToString(userData.toByteArray(), android.util.Base64.NO_WRAP),
                            "backups" to "disabled",
                            "enable_ipv6" to true,
                        ),
                    )
                val request =
                    authRequest(apiKey, "$BASE_URL/instances")
                        .post(payload.toRequestBody(JSON_TYPE))
                        .build()
                httpClientProvider().newCall(request).executeAsync().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body.string()
                        return@withContext Result.failure(Exception("HTTP ${response.code}: $errorBody"))
                    }
                    val body = response.body.string()
                    val node = mapper.readTree(body)["instance"]
                    Result.success(parseServer(node))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getServer(
        apiKey: String,
        serverId: String,
    ): Result<VpsServer> =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    authRequest(apiKey, "$BASE_URL/instances/$serverId")
                        .get()
                        .build()
                httpClientProvider().newCall(request).executeAsync().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${response.code}"))
                    }
                    val body = response.body.string()
                    val node = mapper.readTree(body)["instance"]
                    Result.success(parseServer(node))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun listServers(apiKey: String): Result<List<VpsServer>> =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    authRequest(apiKey, "$BASE_URL/instances?label=strfry-relay&per_page=100")
                        .get()
                        .build()
                httpClientProvider().newCall(request).executeAsync().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${response.code}"))
                    }
                    val body = response.body.string()
                    val tree = mapper.readTree(body)
                    val servers =
                        tree["instances"]?.map { node ->
                            parseServer(node)
                        } ?: emptyList()
                    Result.success(servers)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun deleteServer(
        apiKey: String,
        serverId: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    authRequest(apiKey, "$BASE_URL/instances/$serverId")
                        .delete()
                        .build()
                httpClientProvider().newCall(request).executeAsync().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("HTTP ${response.code}"))
                    }
                    Result.success(Unit)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun parseServer(node: com.fasterxml.jackson.databind.JsonNode): VpsServer {
        val mainIp = node["main_ip"]?.asText() ?: ""
        return VpsServer(
            id = node["id"].asText(),
            label = node["label"]?.asText() ?: "",
            status = parseStatus(node["status"]?.asText(), node["power_status"]?.asText()),
            ipv4 = mainIp,
            ipv6 = node["v6_main_ip"]?.asText() ?: "",
            region = node["region"]?.asText() ?: "",
            plan = node["plan"]?.asText() ?: "",
            os = node["os"]?.asText() ?: "",
            relayUrl = if (mainIp.isNotEmpty() && mainIp != "0.0.0.0") "wss://$mainIp" else null,
        )
    }

    private fun parseStatus(
        status: String?,
        powerStatus: String?,
    ): VpsServerStatus =
        when {
            status == "active" && powerStatus == "running" -> VpsServerStatus.ACTIVE
            status == "pending" -> VpsServerStatus.PENDING
            status == "suspended" -> VpsServerStatus.SUSPENDED
            status == "resizing" -> VpsServerStatus.RESIZING
            else -> VpsServerStatus.UNKNOWN
        }
}
