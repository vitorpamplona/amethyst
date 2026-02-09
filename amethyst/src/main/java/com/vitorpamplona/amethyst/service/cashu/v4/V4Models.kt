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
package com.vitorpamplona.amethyst.service.cashu.v4

import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString

@Serializable
class V4Token(
    // mint
    val m: String,
    // unit
    val u: String,
    // memo
    val d: String? = null,
    val t: Array<V4T>?,
)

@Serializable
class V4T(
    // identifier
    @ByteString
    val i: ByteArray,
    val p: Array<V4Proof>,
)

@Serializable
class V4Proof(
    // amount
    val a: Int,
    // secret
    val s: String,
    // signature
    @ByteString
    val c: ByteArray,
    // no idea what this is
    val d: V4DleqProof? = null,
    // witness
    val w: String? = null,
)

@Serializable
class V4DleqProof(
    @ByteString
    val e: ByteArray,
    @ByteString
    val s: ByteArray,
    @ByteString
    val r: ByteArray,
)
