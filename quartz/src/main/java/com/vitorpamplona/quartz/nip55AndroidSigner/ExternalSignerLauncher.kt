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
package com.vitorpamplona.quartz.nip55AndroidSigner

import android.content.ContentResolver
import android.content.Intent
import android.util.Log
import android.util.LruCache
import androidx.core.net.toUri
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent

enum class SignerType {
    SIGN_EVENT,
    NIP04_ENCRYPT,
    NIP04_DECRYPT,
    NIP44_ENCRYPT,
    NIP44_DECRYPT,
    GET_PUBLIC_KEY,
    DECRYPT_ZAP_EVENT,
    DERIVE_KEY,
}

class Permission(
    val type: String,
    val kind: Int? = null,
) {
    fun toJson(): String = "{\"type\":\"${type}\",\"kind\":$kind}"
}

class Result(
    @JsonProperty("package") val `package`: String?,
    @JsonProperty("signature") val signature: String?,
    @JsonProperty("result") val result: String?,
    @JsonProperty("id") val id: String?,
) {
    companion object {
        val mapper: ObjectMapper =
            jacksonObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .registerModule(
                    SimpleModule().addDeserializer(Result::class.java, ResultDeserializer()),
                )

        private class ResultDeserializer : StdDeserializer<Result>(Result::class.java) {
            override fun deserialize(
                jp: JsonParser,
                ctxt: DeserializationContext,
            ): Result {
                val jsonObject: JsonNode = jp.codec.readTree(jp)
                return Result(
                    jsonObject.get("package").asText().intern(),
                    jsonObject.get("signature")?.asText()?.intern(),
                    jsonObject.get("result")?.asText()?.intern(),
                    jsonObject.get("id").asText().intern(),
                )
            }
        }

        fun fromJson(json: String): Result = mapper.readValue(json, Result::class.java)

        /**
         * Parses the json with a string of events to an Array of Event objects.
         */
        fun fromJsonArray(json: String): Array<Result> = mapper.readValue(json)
    }
}

class ExternalSignerLauncher(
    private val currentUserPubKeyHex: String,
    val signerPackageName: String,
) {
    private val contentCache = LruCache<String, (String) -> Unit>(50)

    private var signerAppLauncher: ((Intent) -> Unit)? = null
    private var contentResolver: (() -> ContentResolver)? = null

    /** Call this function when the launcher becomes available on activity, fragment or compose */
    fun registerLauncher(
        launcher: ((Intent) -> Unit),
        contentResolver: (() -> ContentResolver),
    ) {
        this.signerAppLauncher = launcher
        this.contentResolver = contentResolver
    }

    /** Call this function when the activity is destroyed or is about to be replaced. */
    fun clearLauncher() {
        this.signerAppLauncher = null
        this.contentResolver = null
    }

    fun newResult(data: Intent) {
        val results = data.getStringExtra("results")
        if (results != null) {
            val localResults: Array<Result> = Result.fromJsonArray(results)
            localResults.forEach {
                val signature = it.result ?: it.signature ?: ""
                val packageName = it.`package`?.let { "-$it" } ?: ""
                val id = it.id ?: ""
                if (id.isNotBlank()) {
                    val result = if (packageName.isNotBlank()) "$signature$packageName" else signature
                    val contentCache = contentCache.get(id)
                    contentCache?.invoke(result)
                }
            }
        } else {
            val signature = data.getStringExtra("result") ?: data.getStringExtra("signature") ?: ""
            val packageName = data.getStringExtra("package")?.let { "-$it" } ?: ""
            val id = data.getStringExtra("id") ?: ""
            if (id.isNotBlank()) {
                val result = if (packageName.isNotBlank()) "$signature$packageName" else signature
                val contentCache = contentCache.get(id)
                contentCache?.invoke(result)
            }
        }
    }

    fun openSignerApp(
        data: String,
        type: SignerType,
        pubKey: HexKey,
        id: String,
        onReady: (String) -> Unit,
    ) {
        signerAppLauncher?.let {
            openSignerApp(
                data,
                type,
                it,
                pubKey,
                id,
                onReady,
            )
        }
    }

    private fun defaultPermissions(): String {
        val permissions =
            listOf(
                Permission(
                    "sign_event",
                    22242,
                ),
                Permission(
                    "sign_event",
                    31234,
                ),
                Permission(
                    "nip04_encrypt",
                ),
                Permission(
                    "nip04_decrypt",
                ),
                Permission(
                    "nip44_encrypt",
                ),
                Permission(
                    "nip44_decrypt",
                ),
                Permission(
                    "decrypt_zap_event",
                ),
            )
        val jsonArray = StringBuilder("[")
        permissions.forEachIndexed { index, permission ->
            jsonArray.append(permission.toJson())
            if (index < permissions.size - 1) {
                jsonArray.append(",")
            }
        }
        jsonArray.append("]")

        return jsonArray.toString()
    }

    private fun openSignerApp(
        data: String,
        type: SignerType,
        intentLauncher: (Intent) -> Unit,
        pubKey: HexKey,
        id: String,
        onReady: (String) -> Unit,
    ) {
        val intent = Intent(Intent.ACTION_VIEW, "nostrsigner:$data".toUri())
        val signerType =
            when (type) {
                SignerType.SIGN_EVENT -> "sign_event"
                SignerType.NIP04_ENCRYPT -> "nip04_encrypt"
                SignerType.NIP04_DECRYPT -> "nip04_decrypt"
                SignerType.NIP44_ENCRYPT -> "nip44_encrypt"
                SignerType.NIP44_DECRYPT -> "nip44_decrypt"
                SignerType.GET_PUBLIC_KEY -> "get_public_key"
                SignerType.DECRYPT_ZAP_EVENT -> "decrypt_zap_event"
                SignerType.DERIVE_KEY -> "derive_key"
            }
        intent.putExtra("type", signerType)
        intent.putExtra("pubKey", pubKey)
        intent.putExtra("id", id)
        if (type !== SignerType.GET_PUBLIC_KEY) {
            intent.putExtra("current_user", currentUserPubKeyHex)
        } else {
            intent.putExtra("permissions", defaultPermissions())
        }
        if (signerPackageName.isNotBlank()) {
            intent.`package` = signerPackageName
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        contentCache.put(id, onReady)

        intentLauncher(intent)
    }

    fun openSigner(
        event: Event,
        onReady: (String) -> Unit,
    ) {
        getDataFromResolver(
            SignerType.SIGN_EVENT,
            arrayOf(event.toJson(), event.pubKey),
        ).fold(
            onFailure = { },
            onSuccess = {
                if (it == null) {
                    openSignerApp(
                        event.toJson(),
                        SignerType.SIGN_EVENT,
                        "",
                        event.id,
                        onReady,
                    )
                } else {
                    onReady(it)
                }
            },
        )
    }

    private fun getDataFromResolver(
        signerType: SignerType,
        data: Array<out String>,
    ): kotlin.Result<String?> = getDataFromResolver(signerType, data, contentResolver)

    private fun getDataFromResolver(
        signerType: SignerType,
        data: Array<out String>,
        contentResolver: (() -> ContentResolver)? = null,
    ): kotlin.Result<String?> {
        val localData =
            if (signerType !== SignerType.GET_PUBLIC_KEY) {
                arrayOf(*data, currentUserPubKeyHex)
            } else {
                data
            }

        try {
            contentResolver
                ?.let { it() }
                ?.query(
                    "content://$signerPackageName.$signerType".toUri(),
                    localData,
                    "1",
                    null,
                    null,
                ).use {
                    if (it == null) {
                        return kotlin.Result.success(null)
                    }
                    if (it.moveToFirst()) {
                        if (it.getColumnIndex("rejected") > -1) {
                            Log.d("getDataFromResolver", "Permission denied")
                            return kotlin.Result.failure(Exception("Permission denied"))
                        }
                        var index = it.getColumnIndex("result")
                        if (index < 0) {
                            index = it.getColumnIndex("signature")
                        }
                        if (index < 0) {
                            Log.d("getDataFromResolver", "column 'signature' not found")
                            return kotlin.Result.success(null)
                        }
                        return kotlin.Result.success(it.getString(index))
                    }
                }
        } catch (e: Exception) {
            Log.e("ExternalSignerLauncher", "Failed to query the Signer app in the background", e)
            return kotlin.Result.success(null)
        }

        return kotlin.Result.success(null)
    }

    fun hashCodeFields(
        str1: String,
        onReady: (String) -> Unit,
    ): Int {
        var result = str1.hashCode()
        result = 31 * result + onReady.hashCode()
        return result
    }

    fun hashCodeFields(
        str1: String,
        str2: String,
        onReady: (String) -> Unit,
    ): Int {
        var result = str1.hashCode()
        result = 31 * result + str2.hashCode()
        result = 31 * result + onReady.hashCode()
        return result
    }

    fun decrypt(
        encryptedContent: String,
        pubKey: HexKey,
        signerType: SignerType = SignerType.NIP04_DECRYPT,
        onReady: (String) -> Unit,
    ) {
        getDataFromResolver(signerType, arrayOf(encryptedContent, pubKey)).fold(
            onFailure = { },
            onSuccess = {
                if (it == null) {
                    openSignerApp(
                        encryptedContent,
                        signerType,
                        pubKey,
                        hashCodeFields(encryptedContent, pubKey, onReady).toString(),
                        onReady,
                    )
                } else {
                    onReady(it)
                }
            },
        )
    }

    fun encrypt(
        decryptedContent: String,
        pubKey: HexKey,
        signerType: SignerType = SignerType.NIP04_ENCRYPT,
        onReady: (String) -> Unit,
    ) {
        getDataFromResolver(signerType, arrayOf(decryptedContent, pubKey)).fold(
            onFailure = { },
            onSuccess = {
                if (it == null) {
                    openSignerApp(
                        decryptedContent,
                        signerType,
                        pubKey,
                        hashCodeFields(decryptedContent, pubKey, onReady).toString(),
                        onReady,
                    )
                } else {
                    onReady(it)
                }
            },
        )
    }

    fun decryptZapEvent(
        event: LnZapRequestEvent,
        onReady: (String) -> Unit,
    ) {
        getDataFromResolver(SignerType.DECRYPT_ZAP_EVENT, arrayOf(event.toJson(), event.pubKey)).fold(
            onFailure = { },
            onSuccess = {
                if (it == null) {
                    openSignerApp(
                        event.toJson(),
                        SignerType.DECRYPT_ZAP_EVENT,
                        event.pubKey,
                        event.id,
                        onReady,
                    )
                } else {
                    onReady(it)
                }
            },
        )
    }

    fun deriveKey(
        nonce: HexKey,
        signerType: SignerType = SignerType.DERIVE_KEY,
        onReady: (String) -> Unit,
    ) {
        getDataFromResolver(signerType, arrayOf(nonce)).fold(
            onFailure = { },
            onSuccess = {
                if (it == null) {
                    openSignerApp(
                        nonce,
                        signerType,
                        "",
                        hashCodeFields(nonce, onReady).toString(),
                        onReady,
                    )
                } else {
                    onReady(it)
                }
            },
        )
    }
}
