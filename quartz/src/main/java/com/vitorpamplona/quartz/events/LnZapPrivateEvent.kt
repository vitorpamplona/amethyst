package com.vitorpamplona.quartz.events

import android.util.Log
import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.Bech32
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.LnInvoiceUtil
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.utils.TimeUtils
import java.nio.charset.Charset
import java.security.SecureRandom
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Immutable
class LnZapPrivateEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: List<List<String>>,
    content: String,
    sig: HexKey
) : Event(id, pubKey, createdAt, kind, tags, content, sig) {

    companion object {
        const val kind = 9733

        fun create(
            privateKey: ByteArray,
            tags: List<List<String>> = emptyList(),
            content: String = "",
            createdAt: Long = TimeUtils.now()
        ): Event {
            val pubKey = CryptoUtils.pubkeyCreate(privateKey).toHexKey()
            val id = generateId(pubKey, createdAt, kind, tags, content)
            val sig = CryptoUtils.sign(id, privateKey).toHexKey()
            return Event(id.toHexKey(), pubKey, createdAt, kind, tags, content, sig)
        }
    }
}
