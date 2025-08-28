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

import com.fasterxml.jackson.annotation.JsonProperty

data class VectorFile(
    val v2: V2? = V2(),
)

data class V2(
    val valid: Valid? = Valid(),
    val invalid: Invalid? = Invalid(),
)

data class Valid(
    @field:JsonProperty("get_conversation_key") val getConversationKey: ArrayList<GetConversationKey> = arrayListOf(),
    @field:JsonProperty("get_message_keys") val getMessageKeys: GetMessageKeys? = GetMessageKeys(),
    @field:JsonProperty("calc_padded_len") val calcPaddedLen: ArrayList<ArrayList<Int>> = arrayListOf(),
    @field:JsonProperty("encrypt_decrypt") val encryptDecrypt: ArrayList<EncryptDecrypt> = arrayListOf(),
    @field:JsonProperty("encrypt_decrypt_long_msg")
    val encryptDecryptLongMsg: ArrayList<EncryptDecryptLongMsg> = arrayListOf(),
)

data class Invalid(
    @field:JsonProperty("encrypt_msg_lengths") val encryptMsgLengths: ArrayList<Int> = arrayListOf(),
    @field:JsonProperty("get_conversation_key") val getConversationKey: ArrayList<GetConversationKey> = arrayListOf(),
    @field:JsonProperty("decrypt") val decrypt: ArrayList<Decrypt> = arrayListOf(),
)

data class GetConversationKey(
    val sec1: String? = null,
    val pub2: String? = null,
    val note: String? = null,
    @field:JsonProperty("conversation_key") val conversationKey: String? = null,
)

data class GetMessageKeys(
    @field:JsonProperty("conversation_key") val conversationKey: String? = null,
    val keys: ArrayList<Keys> = arrayListOf(),
)

data class Keys(
    @field:JsonProperty("nonce") val nonce: String? = null,
    @field:JsonProperty("chacha_key") val chachaKey: String? = null,
    @field:JsonProperty("chacha_nonce") val chachaNonce: String? = null,
    @field:JsonProperty("hmac_key") val hmacKey: String? = null,
)

data class EncryptDecrypt(
    val sec1: String? = null,
    val sec2: String? = null,
    @field:JsonProperty("conversation_key") val conversationKey: String? = null,
    val nonce: String? = null,
    val plaintext: String? = null,
    val payload: String? = null,
)

data class EncryptDecryptLongMsg(
    @field:JsonProperty("conversation_key") val conversationKey: String? = null,
    val nonce: String? = null,
    val pattern: String? = null,
    val repeat: Int? = null,
    @field:JsonProperty("plaintext_sha256") val plaintextSha256: String? = null,
    @field:JsonProperty("payload_sha256") val payloadSha256: String? = null,
)

data class Decrypt(
    @field:JsonProperty("conversation_key") val conversationKey: String? = null,
    val nonce: String? = null,
    val plaintext: String? = null,
    val payload: String? = null,
    val note: String? = null,
)
