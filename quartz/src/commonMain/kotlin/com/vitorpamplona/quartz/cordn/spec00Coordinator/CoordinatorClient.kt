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
package com.vitorpamplona.quartz.cordn.spec00Coordinator

import com.vitorpamplona.quartz.contextvm.mcp.CvmMcpClient
import com.vitorpamplona.quartz.contextvm.transport.CvmTransport
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * The cordn coordinator's eleven tools, typed.
 *
 * ## Identity is not a parameter
 *
 * Each method's signing identity comes from [CoordinatorMethod], not from the
 * call site. That is the point: `msg_post` under the stable key would tie every
 * message you send to your real npub, forever, on a coordinator that keeps
 * ordered history — and it would look exactly like a working call. There is no
 * override, so the mistake is not available.
 *
 * Read [CoordinatorMethod]'s KDoc before assuming the split buys more than it
 * does. Admission (`join_request_store`, `welcome_store`/`welcome_take`) names
 * real keys on both ends by design.
 *
 * ## Encryption is pinned
 *
 * The transport must be configured to require encryption. The ContextVM SDK
 * defaults to `OPTIONAL`, which resolves from negotiated session state, so a
 * coordinator that simply does not announce `support_encryption` gets
 * plaintext JSON-RPC on public relays — `gid`s, target pubkeys, KeyPackages and
 * cursors, readable by any relay operator. `:contextvm`'s `CvmGiftWrap`
 * defaults to `REQUIRED` and fails closed; this class does not undo that.
 */
class CoordinatorClient(
    private val mcp: CvmMcpClient,
    /** Applied to every call. Coordinator work is storage, not computation. */
    private val timeoutMs: Long = CvmTransport.DEFAULT_TIMEOUT_MS,
) : ICoordinator {
    /** Performs the MCP handshake. Optional, but it is where CEP-35 tags ride. */
    suspend fun initialize() = mcp.initialize()

    /**
     * Handshakes and reports what the coordinator says about itself.
     *
     * Null when the server answered but named nothing, which MCP allows. Every
     * field is a claim the coordinator makes about itself — see
     * [CoordinatorServerInfo]; the pubkey is the identity (§8.5).
     */
    suspend fun serverInfo(): CoordinatorServerInfo? = CoordinatorServerInfo.from(initialize())?.takeIf { !it.isEmpty }

    // ---- stable identity -------------------------------------------------

    /**
     * Publishes a KeyPackage.
     *
     * The signed publication payload §7 requires IS this call's request event,
     * which the coordinator stores verbatim and serves back from [takeKeyPackage].
     * Nothing extra is signed here, and nothing else can be: the transport owns
     * the event.
     */
    override suspend fun publishKeyPackage(
        keyPackageRef: String,
        keyPackageBase64: String,
    ): PublishedKeyPackage =
        call(CoordinatorMethod.KP_PUBLISH, KeyPackagePublication.arguments(keyPackageRef, keyPackageBase64)).let {
            PublishedKeyPackage(
                keyPackageRef = it.str(CoordinatorFields.KP_REF),
                lastResort = it.bool(CoordinatorFields.LAST_RESORT),
                at = it.num(CoordinatorFields.AT),
            )
        }

    /** Withdraws published KeyPackages. Authorized by us being their owner (§12). */
    override suspend fun removeKeyPackages(keyPackageRefs: List<String>): List<String> {
        require(keyPackageRefs.isNotEmpty()) { "kp_remove needs at least one ref" }
        val args =
            buildJsonObject {
                put(CoordinatorFields.KP_REFS, buildJsonArray { keyPackageRefs.forEach { add(JsonPrimitive(it)) } })
            }
        return call(CoordinatorMethod.KP_REMOVE, args)[CoordinatorFields.KP_REFS]
            ?.jsonArray
            ?.map { it.jsonPrimitive.content }
            .orEmpty()
    }

    /**
     * Drains this account's pending Welcomes, acknowledging any already joined.
     *
     * [consumed] retires records the coordinator would otherwise keep serving.
     * A last-resort KeyPackage can back several Welcomes, which is why a record
     * is identified by `(kp_ref, at)` rather than by `kp_ref` alone.
     */
    override suspend fun takeWelcomes(consumed: List<ConsumedWelcomeRef>): List<PendingWelcome> {
        val args =
            buildJsonObject {
                if (consumed.isNotEmpty()) {
                    put(
                        CoordinatorFields.CONSUMED,
                        buildJsonArray {
                            consumed.forEach {
                                add(
                                    buildJsonObject {
                                        put(CoordinatorFields.KP_REF, it.keyPackageRef)
                                        put(CoordinatorFields.AT, it.at)
                                    },
                                )
                            }
                        },
                    )
                }
            }
        return call(CoordinatorMethod.WELCOME_TAKE, args).list(CoordinatorFields.WELCOMES) {
            PendingWelcome(
                keyPackageRef = it.str(CoordinatorFields.KP_REF),
                welcomeBase64 = it.str(CoordinatorFields.WELCOME_64),
                at = it.num(CoordinatorFields.AT),
                after = it.numOrNull(CoordinatorFields.AFTER),
            )
        }
    }

    /**
     * Asks to join [gid] with [keyPackageRef].
     *
     * Rides the stable identity because the request is "npub X wants into group
     * G" — there is no version of this call that does not name the asker.
     */
    override suspend fun storeJoinRequest(
        gid: String,
        keyPackageRef: String,
    ): Long =
        call(
            CoordinatorMethod.JOIN_REQUEST_STORE,
            buildJsonObject {
                put(CoordinatorFields.GID, gid)
                put(CoordinatorFields.KP_REF, keyPackageRef)
            },
        ).num(CoordinatorFields.AT)

    // ---- ephemeral identity ----------------------------------------------

    /** Every KeyPackage this coordinator holds. */
    override suspend fun listKeyPackages(): List<AvailableKeyPackage> =
        call(CoordinatorMethod.KP_LIST, buildJsonObject {}).list(CoordinatorFields.KEY_PACKAGES) {
            AvailableKeyPackage(
                pubKey = it.str(CoordinatorFields.PK),
                keyPackageRef = it.str(CoordinatorFields.KP_REF),
                lastResort = it.bool(CoordinatorFields.LAST_RESORT),
                at = it.num(CoordinatorFields.AT),
            )
        }

    /**
     * Takes a KeyPackage by ref or by account hex (`id` accepts either).
     *
     * The result is NOT usable until [KeyPackagePublication.verify] has passed
     * on its publication event. Null means the coordinator holds nothing
     * matching.
     */
    override suspend fun takeKeyPackage(id: String): TakenKeyPackage? {
        val result = call(CoordinatorMethod.KP_TAKE, buildJsonObject { put(CoordinatorFields.ID, id) })
        val entry = result[CoordinatorFields.KEY_PACKAGE]?.takeIf { it is JsonObject }?.jsonObject ?: return null
        val eventJson =
            entry[CoordinatorFields.EVENT]?.jsonObject
                ?: throw CoordinatorException("kp_take result carries no publication event")
        return TakenKeyPackage(
            pubKey = entry.str(CoordinatorFields.PK),
            keyPackageRef = entry.str(CoordinatorFields.KP_REF),
            lastResort = entry.bool(CoordinatorFields.LAST_RESORT),
            at = entry.num(CoordinatorFields.AT),
            publicationEvent = parseEvent(eventJson),
        )
    }

    /**
     * Leaves a Welcome for [targetPubKey].
     *
     * [after] tells the joiner which cursor to start their history from, so
     * they do not replay epochs they cannot decrypt.
     */
    override suspend fun storeWelcome(
        targetPubKey: HexKey,
        keyPackageRef: String,
        welcomeBase64: String,
        after: Long?,
    ): Long =
        call(
            CoordinatorMethod.WELCOME_STORE,
            buildJsonObject {
                put(CoordinatorFields.TARGET_PK, targetPubKey)
                put(CoordinatorFields.KP_REF, keyPackageRef)
                put(CoordinatorFields.WELCOME_64, welcomeBase64)
                after?.let { put(CoordinatorFields.AFTER, it) }
            },
        ).num(CoordinatorFields.AT)

    /** Drains join requests for the groups we administer, acknowledging handled ones. */
    override suspend fun takeJoinRequests(
        gids: List<String>,
        consumed: List<ConsumedJoinRequestRef>,
    ): List<JoinRequest> {
        require(gids.isNotEmpty()) { "join_request_take_many needs at least one group" }
        val args =
            buildJsonObject {
                put(
                    CoordinatorFields.GROUPS,
                    buildJsonArray { gids.forEach { gid -> add(buildJsonObject { put(CoordinatorFields.GID, gid) }) } },
                )
                if (consumed.isNotEmpty()) {
                    put(
                        CoordinatorFields.CONSUMED,
                        buildJsonArray {
                            consumed.forEach {
                                add(
                                    buildJsonObject {
                                        put(CoordinatorFields.GID, it.gid)
                                        put(CoordinatorFields.PK, it.pubKey)
                                        put(CoordinatorFields.AT, it.at)
                                    },
                                )
                            }
                        },
                    )
                }
            }
        return call(CoordinatorMethod.JOIN_REQUEST_TAKE_MANY, args).list(CoordinatorFields.REQUESTS) {
            JoinRequest(
                gid = it.str(CoordinatorFields.GID),
                pubKey = it.str(CoordinatorFields.PK),
                keyPackageRef = it.str(CoordinatorFields.KP_REF),
                at = it.num(CoordinatorFields.AT),
            )
        }
    }

    /** Posts one sealed payload to a group's stream. */
    override suspend fun postMessage(
        gid: String,
        sealedBase64: String,
    ): PostedMessage =
        call(
            CoordinatorMethod.MSG_POST,
            buildJsonObject {
                put(CoordinatorFields.GID, gid)
                put(CoordinatorFields.MSG_64, sealedBase64)
            },
        ).let {
            PostedMessage(
                gid = it.str(CoordinatorFields.GID),
                cursor = it.num(CoordinatorFields.CURSOR),
                at = it.num(CoordinatorFields.AT),
            )
        }

    /**
     * Fetches history for several groups, each after its own cursor.
     *
     * One page. The coordinator decides how many it returns, so a caller
     * catching up loops until a page comes back empty — see
     * [com.vitorpamplona.quartz.cordn.sync.GroupSync].
     */
    override suspend fun fetchMessages(cursors: Map<String, Long?>): List<GroupMessage> {
        require(cursors.isNotEmpty()) { "msg_fetch_many needs at least one group" }
        val args = buildJsonObject { put(CoordinatorFields.GROUPS, groupsArray(cursors)) }
        return call(CoordinatorMethod.MSG_FETCH_MANY, args).list(CoordinatorFields.MESSAGES, ::groupMessage)
    }

    /**
     * Subscribes to live delivery.
     *
     * Each message arrives as a CEP-41 open-stream fragment whose payload is
     * one `GroupMessage` JSON object. The call does not return until the
     * coordinator closes the stream, so run it in its own coroutine; `close`
     * does not complete the request, so the returned list is the whole run's
     * traffic, not a partial view.
     */
    override suspend fun subscribeMessages(
        cursors: Map<String, Long?>,
        timeoutMs: Long,
        onMessage: (GroupMessage) -> Unit,
    ) {
        require(cursors.isNotEmpty()) { "msg_sub_many needs at least one group" }
        val args = buildJsonObject { put(CoordinatorFields.GROUPS, groupsArray(cursors)) }
        val result =
            mcp.callTool(
                name = CoordinatorMethod.MSG_SUB_MANY.wire,
                arguments = args,
                identity = CoordinatorMethod.MSG_SUB_MANY.identity,
                timeoutMs = timeoutMs,
                onStreamFragment = { fragment ->
                    // A malformed frame is the coordinator's problem, not a
                    // reason to tear down a live subscription over other groups.
                    runCatching { groupMessage(Json.parseToJsonElement(fragment).jsonObject) }
                        .getOrNull()
                        ?.let(onMessage)
                },
            )
        if (result.isError) throw CoordinatorException("msg_sub_many failed: ${result.error?.message}")
    }

    // ---- plumbing --------------------------------------------------------

    private fun groupsArray(cursors: Map<String, Long?>) =
        buildJsonArray {
            cursors.forEach { (gid, after) ->
                add(
                    buildJsonObject {
                        put(CoordinatorFields.GID, gid)
                        // The schema types `after` as a positive int, so a
                        // first-ever fetch omits it rather than sending 0.
                        after?.takeIf { it > 0 }?.let { put(CoordinatorFields.AFTER, it) }
                    },
                )
            }
        }

    private fun groupMessage(json: JsonObject) =
        GroupMessage(
            gid = json.str(CoordinatorFields.GID),
            cursor = json.num(CoordinatorFields.CURSOR),
            sealedBase64 = json.str(CoordinatorFields.MSG_64),
            at = json.num(CoordinatorFields.AT),
        )

    private fun parseEvent(json: JsonObject): Event =
        try {
            OptimizedJsonMapper.fromJson(json.toString())
        } catch (e: Exception) {
            throw CoordinatorException("coordinator served a publication event we cannot parse", e)
        }

    private suspend fun call(
        method: CoordinatorMethod,
        arguments: JsonObject,
    ): JsonObject {
        val result = mcp.callTool(method.wire, arguments, method.identity, timeoutMs)
        if (result.isError) {
            throw CoordinatorException("${method.wire} failed: ${result.error?.message ?: "unknown error"}")
        }
        val body = result.result as? JsonObject ?: throw CoordinatorException("${method.wire} returned no result object")
        return body[CoordinatorFields.STRUCTURED_CONTENT]?.jsonObject
            ?: throw CoordinatorException("${method.wire} returned no structuredContent")
    }

    private fun <T> JsonObject.list(
        key: String,
        map: (JsonObject) -> T,
    ): List<T> = this[key]?.jsonArray?.map { map(it.jsonObject) }.orEmpty()

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.content ?: throw CoordinatorException("coordinator result is missing `$key`")

    private fun JsonObject.num(key: String) = this[key]?.jsonPrimitive?.long ?: throw CoordinatorException("coordinator result is missing `$key`")

    private fun JsonObject.numOrNull(key: String) = this[key]?.jsonPrimitive?.long

    private fun JsonObject.bool(key: String) = this[key]?.jsonPrimitive?.content?.toBoolean() ?: false
}
