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
package com.vitorpamplona.quartz.cordn.appMultiDevice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * The two sealed documents of `spec/applications/multi-device.md` §4.
 *
 * ## What this is for here
 *
 * The spec's subject is a fleet of devices sharing one MLS leaf and converging
 * continuously. Amethyst uses the same documents for the narrow case that has
 * no convergence problem: **moving an account to a new phone**. One device
 * writes a snapshot, one device reads it, and the writer then stops (see the
 * handoff lock). Nothing here implements §8.5 `prev` chains, §10 sibling-Commit
 * convergence or §10.5's publish-on-every-Commit — those exist to keep two
 * *live* devices in step, which migration does not ask for.
 *
 * ## The one field that is not interoperable
 *
 * `clientState` is `base64(serialized MLS ClientState)`, and §4.2 says outright
 * that it is *library-serialized and intentionally not pinned to a wire format*
 * — TLS is pinned only for the meta document's key package. So the field holds
 * whatever the writing implementation's engine produces: ts-mls for the
 * reference client, [CLIENT_STATE_FORMAT] for us. The two cannot read each
 * other, by the spec's design rather than by omission.
 *
 * Because the spec gives no place to say which one a document carries, a reader
 * would otherwise discover the mismatch as a deserialization crash deep inside
 * its MLS engine. [CordnGroupDocument.clientStateFormat] is an additive field
 * that lets a reader check first and refuse with a reason. Unknown fields are
 * ignored by both sides, so it costs a foreign reader nothing.
 */
object CordnDeviceDocument {
    const val SCHEMA_VERSION = 1

    /** §4.1 `type`. */
    const val TYPE_GROUP = "group"

    /** §4.2 `type`. */
    const val TYPE_META = "meta"

    /**
     * What Amethyst puts in `clientState`: our own `MlsGroupState` encoding.
     *
     * Not a spec field. See the class KDoc for why it exists.
     */
    const val CLIENT_STATE_FORMAT = "amethyst.MlsGroupState.v1"

    private const val SCHEMA_VERSION_FIELD = "schemaVersion"
    private const val TYPE = "type"
    private const val ISSUED_AT = "issuedAt"
    private const val PREV = "prev"
    private const val GID = "gid"
    private const val COORDINATOR = "coordinator"
    private const val CLIENT_STATE = "clientState"
    private const val CLIENT_STATE_FORMAT_FIELD = "clientStateFormat"
    private const val CURSOR = "cursor"
    private const val ROOM_STATE = "amethystRoomState"
    private const val ECHO_STATE = "amethystEchoState"
    private const val JOINED_VIA_REQUEST = "amethystJoinedViaRequest"
    private const val MESSAGES = "amethystMessages"
    private const val COORDINATOR_RELAYS = "amethystCoordinatorRelays"
    private const val KEY_PACKAGES = "amethystKeyPackages"
    private const val COORDINATOR_PUBKEY = "coordinator"
    private const val REF = "ref"
    private const val BUNDLE = "bundle"
    private const val REMOVED = "removed"
    private const val EPOCH = "epoch"
    private const val LAST_RESORT_KEY_PACKAGE = "lastResortKeyPackage"
    private const val KEY_PACKAGE = "keyPackage"
    private const val PRIVATE_KEY_PACKAGE = "privateKeyPackage"

    private val json = Json { ignoreUnknownKeys = true }

    /** §5: the plaintext is UTF-8 JSON, with no canonical form required. */
    fun encode(document: CordnDeviceDoc): String =
        when (document) {
            is CordnGroupDocument -> encodeGroup(document)
            is CordnMetaDocument -> encodeMeta(document)
        }

    /**
     * Reads a decrypted document.
     *
     * Throws [CordnDocumentException] rather than returning null: by the time a
     * caller has a plaintext it has already fetched a blob whose hash matched
     * the tip's advertised address, so a document that does not parse is a real
     * fault worth a reason, not one candidate among many to skip.
     */
    fun decode(plaintext: String): CordnDeviceDoc {
        val root =
            try {
                json.parseToJsonElement(plaintext) as? JsonObject
            } catch (e: IllegalArgumentException) {
                throw CordnDocumentException("document is not valid JSON: ${e.message}")
            } ?: throw CordnDocumentException("document is not a JSON object")

        val version = root.intOrNull(SCHEMA_VERSION_FIELD)
        // §4.1/§4.2: "Clients MUST reject documents with an unknown schema version."
        if (version != SCHEMA_VERSION) {
            throw CordnDocumentException("unsupported document schemaVersion: $version")
        }

        return when (val type = root.stringOrNull(TYPE)) {
            TYPE_GROUP -> decodeGroup(root)
            TYPE_META -> decodeMeta(root)
            else -> throw CordnDocumentException("unknown document type: $type")
        }
    }

    private fun encodeGroup(document: CordnGroupDocument) =
        buildJsonObject {
            put(SCHEMA_VERSION_FIELD, SCHEMA_VERSION)
            put(TYPE, TYPE_GROUP)
            put(ISSUED_AT, document.issuedAt)
            document.prev?.let { put(PREV, it) }
            put(GID, document.gid)
            put(COORDINATOR, document.coordinator)
            put(CLIENT_STATE, document.clientState)
            put(CLIENT_STATE_FORMAT_FIELD, document.clientStateFormat)
            put(CURSOR, document.cursor)
            document.roomState?.let { put(ROOM_STATE, it) }
            document.messages
                ?.takeIf { it.isNotEmpty() }
                ?.let { entries -> put(MESSAGES, buildJsonArray { entries.forEach { add(JsonPrimitive(it)) } }) }
            document.echoState?.let { put(ECHO_STATE, it) }
            if (document.joinedViaRequest) put(JOINED_VIA_REQUEST, true)
            if (document.coordinatorRelays.isNotEmpty()) {
                put(COORDINATOR_RELAYS, buildJsonArray { document.coordinatorRelays.forEach { add(JsonPrimitive(it)) } })
            }
        }.toString()

    private fun decodeGroup(root: JsonObject): CordnGroupDocument =
        CordnGroupDocument(
            gid = root.stringOrNull(GID) ?: throw CordnDocumentException("group document requires a gid"),
            coordinator =
                root.stringOrNull(COORDINATOR)
                    ?: throw CordnDocumentException("group document requires a coordinator"),
            clientState =
                root.stringOrNull(CLIENT_STATE)
                    ?: throw CordnDocumentException("group document requires a clientState"),
            cursor = root.longOrNull(CURSOR) ?: throw CordnDocumentException("group document requires a cursor"),
            issuedAt = root.longOrNull(ISSUED_AT) ?: 0L,
            prev = root.stringOrNull(PREV),
            // Absent means a writer that predates the marker, or the reference
            // client. Either way it is not ours; say so rather than guess.
            clientStateFormat = root.stringOrNull(CLIENT_STATE_FORMAT_FIELD),
            roomState = root.stringOrNull(ROOM_STATE),
            messages = (root[MESSAGES] as? JsonArray)?.map { (it as JsonPrimitive).content },
            echoState = root.stringOrNull(ECHO_STATE),
            joinedViaRequest = root.boolOrNull(JOINED_VIA_REQUEST) ?: false,
            coordinatorRelays =
                (root[COORDINATOR_RELAYS] as? JsonArray)
                    ?.filterIsInstance<JsonPrimitive>()
                    ?.filter { it.isString }
                    ?.map { it.content }
                    .orEmpty(),
        )

    private fun encodeMeta(document: CordnMetaDocument) =
        buildJsonObject {
            put(SCHEMA_VERSION_FIELD, SCHEMA_VERSION)
            put(TYPE, TYPE_META)
            put(ISSUED_AT, document.issuedAt)
            if (document.removed.isNotEmpty()) {
                put(
                    REMOVED,
                    buildJsonArray {
                        document.removed.forEach {
                            add(
                                buildJsonObject {
                                    put(GID, it.gid)
                                    put(EPOCH, it.epoch)
                                },
                            )
                        }
                    },
                )
            }
            if (document.keyPackages.isNotEmpty()) {
                put(
                    KEY_PACKAGES,
                    buildJsonArray {
                        document.keyPackages.forEach {
                            add(
                                buildJsonObject {
                                    put(COORDINATOR_PUBKEY, it.coordinatorPubKey)
                                    put(REF, it.keyPackageRef)
                                    put(BUNDLE, it.bundle)
                                },
                            )
                        }
                    },
                )
            }
            document.lastResortKeyPackage?.let {
                put(
                    LAST_RESORT_KEY_PACKAGE,
                    buildJsonObject {
                        put(KEY_PACKAGE, it.keyPackage)
                        put(PRIVATE_KEY_PACKAGE, it.privateKeyPackage)
                    },
                )
            }
        }.toString()

    private fun decodeMeta(root: JsonObject): CordnMetaDocument {
        val removed =
            (root[REMOVED] as? JsonArray)
                ?.filterIsInstance<JsonObject>()
                ?.mapNotNull {
                    val gid = it.stringOrNull(GID) ?: return@mapNotNull null
                    val epoch = it.longOrNull(EPOCH) ?: return@mapNotNull null
                    CordnTombstone(gid, epoch)
                }.orEmpty()

        val keyPackage =
            (root[LAST_RESORT_KEY_PACKAGE] as? JsonObject)?.let {
                val public = it.stringOrNull(KEY_PACKAGE)
                val private = it.stringOrNull(PRIVATE_KEY_PACKAGE)
                if (public == null || private == null) {
                    // §4.2 needs both halves to be usable at join time; half an
                    // entry would resolve a Welcome and then fail to open it.
                    throw CordnDocumentException("lastResortKeyPackage requires both halves")
                }
                CordnLastResortKeyPackage(public, private)
            }

        val keyPackages =
            (root[KEY_PACKAGES] as? JsonArray)
                ?.filterIsInstance<JsonObject>()
                ?.mapNotNull {
                    val coordinator = it.stringOrNull(COORDINATOR_PUBKEY) ?: return@mapNotNull null
                    val ref = it.stringOrNull(REF) ?: return@mapNotNull null
                    val bundle = it.stringOrNull(BUNDLE) ?: return@mapNotNull null
                    CordnCarriedKeyPackage(coordinator, ref, bundle)
                }.orEmpty()

        return CordnMetaDocument(
            removed = removed,
            lastResortKeyPackage = keyPackage,
            keyPackages = keyPackages,
            issuedAt = root.longOrNull(ISSUED_AT) ?: 0L,
        )
    }

    private fun JsonObject.stringOrNull(name: String) = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.longOrNull(name: String) =
        (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.let {
            try {
                it.long
            } catch (e: NumberFormatException) {
                null
            }
        }

    private fun JsonObject.intOrNull(name: String) = longOrNull(name)?.toInt()

    private fun JsonObject.boolOrNull(name: String) = (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toBooleanStrictOrNull()
}

/** A document that is not readable. See [CordnDeviceDocument.decode]. */
class CordnDocumentException(
    message: String,
) : Exception(message)

/** One of the two §4 document types. */
sealed interface CordnDeviceDoc

/**
 * §4.1 — one group's MLS state and delivery cursor.
 *
 * [cursor] and [clientState] must be a snapshot taken at the same instant:
 * §4.1 requires that ingesting the stream up to and including [cursor] leaves
 * the writer at the epoch encoded in [clientState]. A reader trusts that,
 * because it has no way to check it.
 */
data class CordnGroupDocument(
    val gid: String,
    /** The coordinator serving [gid], so a seeded device knows where to fetch. */
    val coordinator: String,
    /** `base64(serialized MLS ClientState)`, in [clientStateFormat]. */
    val clientState: String,
    val cursor: Long,
    val issuedAt: Long = 0L,
    /**
     * §4.1's per-`gid` chain link.
     *
     * Always null on a migration snapshot: the chain exists for a live fleet
     * replaying skipped epochs (§8.5), and a handoff publishes one generation.
     */
    val prev: String? = null,
    /** See [CordnDeviceDocument.CLIENT_STATE_FORMAT]. Null when the writer said nothing. */
    val clientStateFormat: String? = CordnDeviceDocument.CLIENT_STATE_FORMAT,
    /**
     * `base64(CordnRoomStateCodec)` — the draft and the read position.
     *
     * Additive, like [clientStateFormat], and for the same reason: the spec's
     * document carries group *state*, and these are the per-device reading
     * position on top of it. A fleet syncing continuously can regard them as
     * device-local; a phone being replaced cannot, because losing them is
     * visible to the user as every conversation coming back unread with the
     * half-typed message gone.
     */
    val roomState: String? = null,
    /**
     * The conversation, as `CordnDeliveredMessageCodec` entries, oldest first.
     *
     * Additive like [roomState], and the most load-bearing of the additions. A
     * cordn message is readable exactly once — at ingest — because both of its
     * seal keys are epoch-derived and the [cursor] this document carries has
     * already advanced past everything behind it. A device seeded without this
     * cannot fetch the history back from anywhere: it would arrive holding
     * every group and no conversation, permanently.
     */
    val messages: List<String>? = null,
    /**
     * `base64(EchoStateCodec)` — pending commits and own-message cursors.
     *
     * Without it the new device re-reports its predecessor's own traffic as a
     * gap on first sync. Same additive justification as [roomState].
     */
    val echoState: String? = null,
    /** Whether this group was entered by join request rather than invitation. */
    val joinedViaRequest: Boolean = false,
    /**
     * Where the coordinator is reachable.
     *
     * §4.1's `coordinator` field is an identity, and `spec/00.md` §8.5 says a
     * coordinator has no address beyond its pubkey — so the relays its traffic
     * runs on have to come from somewhere, and for a device that has never
     * talked to it there is nowhere else. A seeded group whose coordinator has
     * no relays is a group that cannot sync.
     */
    val coordinatorRelays: List<String> = emptyList(),
) : CordnDeviceDoc {
    /** Whether this device's MLS engine can read [clientState] at all. */
    val isReadableHere: Boolean get() = clientStateFormat == CordnDeviceDocument.CLIENT_STATE_FORMAT
}

/** §4.2 — identity-level state: tombstones and the account's last-resort key package. */
data class CordnMetaDocument(
    val removed: List<CordnTombstone> = emptyList(),
    val lastResortKeyPackage: CordnLastResortKeyPackage? = null,
    /**
     * Every key package bundle this account holds, one-use ones included.
     *
     * §11.5 carries only the last-resort package, because a *fleet* keeps
     * one-use packages device-local: a sibling that cannot open one Welcome is
     * an inconvenience while the publishing device is still around. A device
     * being replaced is not still around, so a Welcome in flight against one of
     * its one-use packages would be lost outright. Additive for that reason.
     */
    val keyPackages: List<CordnCarriedKeyPackage> = emptyList(),
    val issuedAt: Long = 0L,
) : CordnDeviceDoc

/** One key package bundle travelling with a migration. `bundle` is base64. */
data class CordnCarriedKeyPackage(
    val coordinatorPubKey: String,
    val keyPackageRef: String,
    val bundle: String,
)

/** §4.2 `removed` — "stopped tracking [gid] while it was at [epoch]". */
data class CordnTombstone(
    val gid: String,
    val epoch: Long,
)

/**
 * §4.2 / §11.5 — the account's one reusable key package, both halves.
 *
 * Unlike `clientState` this **is** pinned: §4.2 says TLS is the only MLS wire
 * serialization, so both fields are base64 of the RFC 9420 TLS encoding and
 * carry across implementations.
 */
data class CordnLastResortKeyPackage(
    val keyPackage: String,
    val privateKeyPackage: String,
)
