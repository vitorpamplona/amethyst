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
package com.vitorpamplona.quartz.mls.messages

import com.vitorpamplona.quartz.mls.codec.TlsReader
import com.vitorpamplona.quartz.mls.codec.TlsWriter

/**
 * Persisting a [KeyPackageBundle] — the published KeyPackage plus the three
 * private keys only its owner holds.
 *
 * A published KeyPackage is useless to its publisher without these: the Welcome
 * that admits them is encrypted to the init key, and the leaf they land on is
 * signed with the signature key. Lose the bundle and the invitation cannot be
 * opened; the group has to re-add them from a fresh one.
 *
 * **Whatever stores the output must encrypt it at rest.** This is key material,
 * and the format makes no attempt to protect it — that is the storage layer's
 * job, and saying so here is the only warning it gets.
 *
 * Binding-agnostic on purpose. Marmot serialises bundles inside its own
 * rotation snapshot, which also carries rotation state it alone has; cordn has
 * no rotation state and no KeyPackage event kind at all (`spec/00.md` §4.2), so
 * it needs the bundle by itself. Neither binding is the right home for a codec
 * over a plain RFC 9420 structure, so it lives here with the structure.
 */
object KeyPackageBundleCodec {
    /**
     * Bumped only for a breaking layout change.
     *
     * [decode] refuses anything it does not know rather than guessing, and
     * refusing is the right failure: a misread bundle yields key material that
     * is silently wrong, which surfaces much later as a Welcome that will not
     * open.
     */
    const val VERSION = 1

    fun encode(bundle: KeyPackageBundle): ByteArray {
        val writer = TlsWriter()
        writer.putUint16(VERSION)
        writer.putOpaque4(bundle.keyPackage.toTlsBytes())
        writer.putOpaque2(bundle.initPrivateKey)
        writer.putOpaque2(bundle.encryptionPrivateKey)
        writer.putOpaque2(bundle.signaturePrivateKey)
        return writer.toByteArray()
    }

    fun decode(bytes: ByteArray): KeyPackageBundle {
        val reader = TlsReader(bytes)
        val version = reader.readUint16()
        require(version == VERSION) { "unknown KeyPackageBundle layout version $version" }
        return KeyPackageBundle(
            keyPackage = MlsKeyPackage.decodeTls(TlsReader(reader.readOpaque4())),
            initPrivateKey = reader.readOpaque2(),
            encryptionPrivateKey = reader.readOpaque2(),
            signaturePrivateKey = reader.readOpaque2(),
        )
    }
}
