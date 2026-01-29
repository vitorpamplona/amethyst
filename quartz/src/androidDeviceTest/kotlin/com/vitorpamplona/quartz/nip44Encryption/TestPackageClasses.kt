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
package com.vitorpamplona.quartz.nip44Encryption

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VectorFile(
    val v2: V2? = V2(),
)

@Serializable
data class V2(
    val valid: Valid? = Valid(),
    val invalid: Invalid? = Invalid(),
)

@Serializable
data class Valid(
    @SerialName("get_conversation_key") val getConversationKey: ArrayList<GetConversationKey> = arrayListOf(),
    @SerialName("get_message_keys") val getMessageKeys: GetMessageKeys? = GetMessageKeys(),
    @SerialName("calc_padded_len") val calcPaddedLen: ArrayList<ArrayList<Int>> = arrayListOf(),
    @SerialName("encrypt_decrypt") val encryptDecrypt: ArrayList<EncryptDecrypt> = arrayListOf(),
    @SerialName("encrypt_decrypt_long_msg")
    val encryptDecryptLongMsg: ArrayList<EncryptDecryptLongMsg> = arrayListOf(),
)

@Serializable
data class Invalid(
    @SerialName("encrypt_msg_lengths") val encryptMsgLengths: ArrayList<Int> = arrayListOf(),
    @SerialName("get_conversation_key") val getConversationKey: ArrayList<GetConversationKey> = arrayListOf(),
    @SerialName("decrypt") val decrypt: ArrayList<Decrypt> = arrayListOf(),
)

@Serializable
data class GetConversationKey(
    val sec1: String? = null,
    val pub2: String? = null,
    val note: String? = null,
    @SerialName("conversation_key") val conversationKey: String? = null,
)

@Serializable
data class GetMessageKeys(
    @SerialName("conversation_key") val conversationKey: String? = null,
    val keys: ArrayList<Keys> = arrayListOf(),
)

@Serializable
data class Keys(
    @SerialName("nonce") val nonce: String? = null,
    @SerialName("chacha_key") val chachaKey: String? = null,
    @SerialName("chacha_nonce") val chachaNonce: String? = null,
    @SerialName("hmac_key") val hmacKey: String? = null,
)

@Serializable
data class EncryptDecrypt(
    val sec1: String? = null,
    val sec2: String? = null,
    @SerialName("conversation_key") val conversationKey: String? = null,
    val nonce: String? = null,
    val plaintext: String? = null,
    val payload: String? = null,
)

@Serializable
data class EncryptDecryptLongMsg(
    @SerialName("conversation_key") val conversationKey: String? = null,
    val nonce: String? = null,
    val pattern: String? = null,
    val repeat: Int? = null,
    @SerialName("plaintext_sha256") val plaintextSha256: String? = null,
    @SerialName("payload_sha256") val payloadSha256: String? = null,
)

@Serializable
data class Decrypt(
    @SerialName("conversation_key") val conversationKey: String? = null,
    val nonce: String? = null,
    val plaintext: String? = null,
    val payload: String? = null,
    val note: String? = null,
)
