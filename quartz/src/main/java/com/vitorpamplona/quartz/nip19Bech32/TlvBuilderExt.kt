/**
 * Copyright (c) 2024 Vitor Pamplona
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
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip19Bech32.TlvTypes
import com.vitorpamplona.quartz.nip19Bech32.tlv.TlvBuilder

fun TlvBuilder.addString(
    type: TlvTypes,
    string: String,
) = addString(type.id, string)

fun TlvBuilder.addHex(
    type: TlvTypes,
    key: HexKey,
) = addHex(type.id, key)

fun TlvBuilder.addInt(
    type: TlvTypes,
    data: Int,
) = addInt(type.id, data)

fun TlvBuilder.addStringIfNotNull(
    type: TlvTypes,
    data: String?,
) = addStringIfNotNull(type.id, data)

fun TlvBuilder.addHexIfNotNull(
    type: TlvTypes,
    data: HexKey?,
) = addHexIfNotNull(type.id, data)

fun TlvBuilder.addIntIfNotNull(
    type: TlvTypes,
    data: Int?,
) = addIntIfNotNull(type.id, data)
