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
package com.vitorpamplona.quartz.cordn.fixture

import com.vitorpamplona.quartz.contextvm.fixture.CvmRequest
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcError
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcFailure
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcId
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcMessage
import com.vitorpamplona.quartz.contextvm.jsonrpc.JsonRpcSuccess
import com.vitorpamplona.quartz.cordn.spec00Coordinator.CoordinatorFields
import com.vitorpamplona.quartz.cordn.spec00Coordinator.CoordinatorMethod
import com.vitorpamplona.quartz.mls.codec.TlsReader
import com.vitorpamplona.quartz.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * An in-memory cordn coordinator for tests.
 *
 * It implements the eleven tools against maps, and — more usefully — it
 * RECORDS which identity made each call. The privacy claim in `spec/00.md` §8
 * is not about what the coordinator stores, it is about what it learns, so the
 * only way to test it is to be the coordinator and look.
 *
 * Deliberately not hardened; `cordn-rs` exists for real deployments. Like
 * `:contextvm`'s fixture, this one can also be told to misbehave — see
 * [rejectPublication] and [rejectRemoval].
 */
@OptIn(ExperimentalEncodingApi::class)
class CordnFixtureCoordinator(
    /** Serve `kp_publish` back as if it had been stored, keyed by ref. */
    private val publications: MutableMap<String, StoredKeyPackage> = mutableMapOf(),
    /** Reject every publication, as a coordinator enforcing §8 would. */
    var rejectPublication: Boolean = false,
    /** Reject every withdrawal, as a coordinator that is simply down would. */
    var rejectRemoval: Boolean = false,
) {
    data class StoredKeyPackage(
        val pubKey: HexKey,
        val keyPackageRef: String,
        val lastResort: Boolean,
        val at: Long,
        val publicationEvent: Event?,
    )

    data class Call(
        val method: String,
        /** The caller pubkey the coordinator learned, via CEP-16 `_meta`. */
        val callerPubKey: HexKey?,
        /** Exactly the arguments object that arrived, for wire-shape assertions. */
        val arguments: JsonObject = JsonObject(emptyMap()),
    )

    /** Every call seen, with who made it. The privacy surface, made observable. */
    val calls = mutableListOf<Call>()

    /**
     * Answers keyed by tool name that replace this fixture's own bookkeeping,
     * served verbatim as `structuredContent`.
     *
     * For replaying a result recorded from another implementation: the fixture
     * models the coordinator well enough to drive a client, but a result it
     * composed itself only proves we agree with ourselves.
     */
    val scriptedResults = mutableMapOf<String, JsonObject>()

    private val welcomes = mutableListOf<JsonObject>()
    private val joinRequests = mutableListOf<JsonObject>()
    private val messages = mutableMapOf<String, MutableList<JsonObject>>()
    private var nextCursor = 1L
    private var clock = 1_700_000_000L

    /** Messages posted to [gid], oldest first. */
    fun posted(gid: String): List<String> = messages[gid].orEmpty().map { it[CoordinatorFields.MSG_64]!!.jsonPrimitive.content }

    /** Pre-seeds a group's stream, as history a client will catch up on. */
    fun seed(
        gid: String,
        sealedBase64: String,
    ): Long {
        val cursor = nextCursor++
        messages.getOrPut(gid) { mutableListOf() } +=
            buildJsonObject {
                put(CoordinatorFields.GID, gid)
                put(CoordinatorFields.CURSOR, cursor)
                put(CoordinatorFields.MSG_64, sealedBase64)
                put(CoordinatorFields.AT, clock++)
            }
        return cursor
    }

    /** Pre-seeds a Welcome in an account's inbox. */
    fun seedWelcome(
        keyPackageRef: String,
        welcomeBase64: String,
        after: Long? = null,
        /** Who this Welcome is addressed to. A fetch by anyone else will not see it. */
        targetPubKey: HexKey,
    ) {
        welcomes +=
            buildJsonObject {
                put(CoordinatorFields.TARGET_PK, targetPubKey)
                put(CoordinatorFields.KP_REF, keyPackageRef)
                put(CoordinatorFields.WELCOME_64, welcomeBase64)
                put(CoordinatorFields.AT, clock++)
                after?.let { put(CoordinatorFields.AFTER, it) }
            }
    }

    /** How many single-use KeyPackages this coordinator still holds for [pubKey]. */
    fun availableCount(pubKey: HexKey): Int = keyPackagesOf(pubKey).count { !it.lastResort }

    /** Every KeyPackage this coordinator holds for [pubKey], reusable ones included. */
    fun keyPackagesOf(pubKey: HexKey): List<StoredKeyPackage> = publications.values.filter { it.pubKey == pubKey }

    /** Reads the last-resort marker out of a base64 KeyPackage, as a real one would. */
    private fun isLastResort(keyPackageBase64: String): Boolean =
        try {
            MlsKeyPackage.decodeTls(TlsReader(Base64.decode(keyPackageBase64))).isLastResort()
        } catch (e: Exception) {
            // Unparseable bytes are not our problem here: the client verifies
            // the publication payload (§9), and a fixture that threw would hide
            // that check behind a transport error.
            false
        }

    /** Stores a publication event so `kp_take` can serve it back verbatim (§7). */
    fun seedPublication(
        keyPackageRef: String,
        event: Event,
    ) {
        publications[keyPackageRef] =
            StoredKeyPackage(event.pubKey, keyPackageRef, false, clock++, event)
    }

    /** The handler to hand to `CvmFixtureServer`. */
    suspend fun handle(request: CvmRequest): JsonRpcMessage {
        val params = request.params
        val id = request.id
        val name = params?.get("name")?.jsonPrimitive?.content ?: request.method
        val args = params?.get("arguments")?.jsonObject ?: buildJsonObject {}
        val caller =
            params
                ?.get("_meta")
                ?.jsonObject
                ?.get("clientPubkey")
                ?.jsonPrimitive
                ?.content
        calls += Call(name, caller, args)

        scriptedResults[name]?.let { return success(id, it) }

        val structured =
            when (name) {
                CoordinatorMethod.KP_PUBLISH.wire -> {
                    if (rejectPublication) {
                        return JsonRpcFailure(id, JsonRpcError(-32000, "publication rejected"))
                    }
                    val ref = args.str(CoordinatorFields.KP_REF)
                    // `spec/00.md` §7: cordn has no KeyPackage event kind, so the
                    // signed publication payload IS this request event. A real
                    // coordinator reaches back into its transport for it and
                    // stores it verbatim; storing anything else here would make
                    // `kp_take` unverifiable and hide the §9 checks from tests.
                    // Whether it is reusable is a property of the KeyPackage
                    // itself, so a real coordinator reads it out of the bytes
                    // rather than trusting a flag. Ours does the same, which is
                    // why `kp_list` can report it and a take can respect it.
                    val lastResort = isLastResort(args.str(CoordinatorFields.KP_64))
                    publications[ref] = StoredKeyPackage(caller.orEmpty(), ref, lastResort, clock++, request.event)
                    buildJsonObject {
                        put(CoordinatorFields.KP_REF, ref)
                        put(CoordinatorFields.LAST_RESORT, lastResort)
                        put(CoordinatorFields.AT, clock)
                    }
                }

                CoordinatorMethod.KP_LIST.wire ->
                    buildJsonObject {
                        put(
                            CoordinatorFields.KEY_PACKAGES,
                            buildJsonArray {
                                publications.values.forEach {
                                    add(
                                        buildJsonObject {
                                            put(CoordinatorFields.PK, it.pubKey)
                                            put(CoordinatorFields.KP_REF, it.keyPackageRef)
                                            put(CoordinatorFields.LAST_RESORT, it.lastResort)
                                            put(CoordinatorFields.AT, it.at)
                                        },
                                    )
                                }
                            },
                        )
                    }

                CoordinatorMethod.KP_TAKE.wire -> {
                    val id = args.str(CoordinatorFields.ID)
                    val stored = publications[id] ?: publications.values.firstOrNull { it.pubKey == id }
                    // A take is a take: the method is `consumeKeyPackage` and a
                    // single-use KeyPackage is gone once somebody has it, which
                    // is what makes a client's pool drain and need topping up.
                    // A last-resort package survives, by definition -- it can
                    // back several Welcomes.
                    if (stored != null && !stored.lastResort) publications.remove(stored.keyPackageRef)
                    buildJsonObject {
                        if (stored?.publicationEvent == null) {
                            put(CoordinatorFields.KEY_PACKAGE, JsonNull)
                        } else {
                            put(
                                CoordinatorFields.KEY_PACKAGE,
                                buildJsonObject {
                                    put(CoordinatorFields.PK, stored.pubKey)
                                    put(CoordinatorFields.KP_REF, stored.keyPackageRef)
                                    put(CoordinatorFields.LAST_RESORT, stored.lastResort)
                                    put(CoordinatorFields.AT, stored.at)
                                    put(
                                        CoordinatorFields.EVENT,
                                        Json.parseToJsonElement(OptimizedJsonMapper.toJson(stored.publicationEvent)),
                                    )
                                },
                            )
                        }
                    }
                }

                CoordinatorMethod.KP_REMOVE.wire -> {
                    if (rejectRemoval) {
                        return JsonRpcFailure(id, JsonRpcError(-32000, "removal rejected"))
                    }
                    val refs = args[CoordinatorFields.KP_REFS]!!.jsonArray.map { it.jsonPrimitive.content }
                    refs.forEach { publications.remove(it) }
                    buildJsonObject {
                        put(CoordinatorFields.KP_REFS, buildJsonArray { refs.forEach { add(Json.parseToJsonElement("\"$it\"")) } })
                    }
                }

                CoordinatorMethod.WELCOME_TAKE.wire -> {
                    val consumed =
                        args[CoordinatorFields.CONSUMED]
                            ?.jsonArray
                            ?.map {
                                it.jsonObject.str(CoordinatorFields.KP_REF) to it.jsonObject[CoordinatorFields.AT]!!.jsonPrimitive.long
                            }.orEmpty()
                    welcomes.removeAll { w ->
                        consumed.any { it.first == w.str(CoordinatorFields.KP_REF) && it.second == w[CoordinatorFields.AT]!!.jsonPrimitive.long }
                    }
                    // Addressed, not broadcast: `welcome-delivery.md` has the
                    // coordinator store a Welcome "addressed to a specific
                    // invited member" and serve it on that member's fetch.
                    // Returning everyone's would let one account join a group it
                    // was never invited to -- and would make a two-party test
                    // pass for the wrong reason.
                    val mine = welcomes.filter { it.str(CoordinatorFields.TARGET_PK) == caller }
                    buildJsonObject { put(CoordinatorFields.WELCOMES, buildJsonArray { mine.forEach { add(it) } }) }
                }

                CoordinatorMethod.WELCOME_STORE.wire -> {
                    // The stored record is the arguments PLUS the `at` the
                    // coordinator assigns: `welcome_take` reports it, and
                    // `consumed` identifies a record by (kp_ref, at) because one
                    // last-resort KeyPackage can back several Welcomes. Storing
                    // the bare arguments loses it -- which only a real
                    // store-then-fetch round trip notices.
                    val at = clock++
                    welcomes += JsonObject(args + mapOf(CoordinatorFields.AT to JsonPrimitive(at)))
                    buildJsonObject { put(CoordinatorFields.AT, at) }
                }

                CoordinatorMethod.JOIN_REQUEST_STORE.wire -> {
                    joinRequests +=
                        buildJsonObject {
                            put(CoordinatorFields.GID, args.str(CoordinatorFields.GID))
                            put(CoordinatorFields.PK, caller.orEmpty())
                            put(CoordinatorFields.KP_REF, args.str(CoordinatorFields.KP_REF))
                            put(CoordinatorFields.AT, clock++)
                        }
                    buildJsonObject { put(CoordinatorFields.AT, clock) }
                }

                CoordinatorMethod.JOIN_REQUEST_TAKE_MANY.wire -> {
                    val gids = args[CoordinatorFields.GROUPS]!!.jsonArray.map { it.jsonObject.str(CoordinatorFields.GID) }
                    buildJsonObject {
                        put(
                            CoordinatorFields.REQUESTS,
                            buildJsonArray { joinRequests.filter { it.str(CoordinatorFields.GID) in gids }.forEach { add(it) } },
                        )
                    }
                }

                CoordinatorMethod.MSG_POST.wire -> {
                    val gid = args.str(CoordinatorFields.GID)
                    val cursor = seed(gid, args.str(CoordinatorFields.MSG_64))
                    buildJsonObject {
                        put(CoordinatorFields.GID, gid)
                        put(CoordinatorFields.CURSOR, cursor)
                        put(CoordinatorFields.AT, clock)
                    }
                }

                CoordinatorMethod.MSG_FETCH_MANY.wire ->
                    buildJsonObject {
                        put(CoordinatorFields.MESSAGES, buildJsonArray { after(args).forEach { add(it) } })
                    }

                else -> buildJsonObject {}
            }

        return success(id, structured)
    }

    private fun success(
        id: JsonRpcId,
        structured: JsonObject,
    ) = JsonRpcSuccess(
        id,
        buildJsonObject {
            put("content", buildJsonArray {})
            put(CoordinatorFields.STRUCTURED_CONTENT, structured)
        },
    )

    /** The messages each requested group has after its cursor. */
    fun after(args: JsonObject): List<JsonObject> =
        args[CoordinatorFields.GROUPS]!!.jsonArray.flatMap { entry ->
            val group = entry.jsonObject
            val gid = group.str(CoordinatorFields.GID)
            val cursor = group[CoordinatorFields.AFTER]?.jsonPrimitive?.long ?: 0L
            messages[gid].orEmpty().filter { it[CoordinatorFields.CURSOR]!!.jsonPrimitive.long > cursor }
        }

    private fun JsonObject.str(key: String) = this[key]!!.jsonPrimitive.content
}
