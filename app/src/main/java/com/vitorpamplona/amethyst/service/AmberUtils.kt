package com.vitorpamplona.amethyst.service

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.ui.actions.SignerType
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventInterface
import com.vitorpamplona.quartz.events.LnZapRequestEvent

object AmberUtils {
    var content: String = ""
    var isActivityRunning: Boolean = false
    val cachedDecryptedContent = mutableMapOf<HexKey, String>()
    lateinit var account: Account

    fun openAmber(
        data: String,
        type: SignerType,
        intentResult: ActivityResultLauncher<Intent>,
        pubKey: HexKey,
        id: String
    ) {
        ServiceManager.shouldPauseService = false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$data"))
        val signerType = when (type) {
            SignerType.SIGN_EVENT -> "sign_event"
            SignerType.NIP04_ENCRYPT -> "nip04_encrypt"
            SignerType.NIP04_DECRYPT -> "nip04_decrypt"
            SignerType.NIP44_ENCRYPT -> "nip44_encrypt"
            SignerType.NIP44_DECRYPT -> "nip44_decrypt"
            SignerType.GET_PUBLIC_KEY -> "get_public_key"
            SignerType.DECRYPT_ZAP_EVENT -> "decrypt_zap_event"
        }
        intent.putExtra("type", signerType)
        intent.putExtra("pubKey", pubKey)
        intent.putExtra("id", id)
        intent.`package` = "com.greenart7c3.nostrsigner"
        intentResult.launch(intent)
    }

    fun openAmber(event: EventInterface) {
        checkNotInMainThread()
        ServiceManager.shouldPauseService = false
        isActivityRunning = true
        openAmber(
            event.toJson(),
            SignerType.SIGN_EVENT,
            IntentUtils.activityResultLauncher,
            "",
            event.id()
        )
        while (isActivityRunning) {
            // do nothing
        }
    }

    fun signEvent(event: EventInterface) {
        checkNotInMainThread()
        ServiceManager.shouldPauseService = false
        isActivityRunning = true
        openAmber(
            event.toJson(),
            SignerType.SIGN_EVENT,
            IntentUtils.signEventResultLauncher,
            account.keyPair.pubKey.toHexKey(),
            event.id()
        )
    }

    fun decryptBlockList(encryptedContent: String, pubKey: HexKey, id: String, signerType: SignerType = SignerType.NIP04_DECRYPT) {
        isActivityRunning = true
        openAmber(
            encryptedContent,
            signerType,
            IntentUtils.blockListResultLauncher,
            pubKey,
            id
        )
    }

    fun decrypt(encryptedContent: String, pubKey: HexKey, id: String, signerType: SignerType = SignerType.NIP04_DECRYPT) {
        if (content.isBlank()) {
            isActivityRunning = true
            openAmber(
                encryptedContent,
                signerType,
                IntentUtils.activityResultLauncher,
                pubKey,
                id
            )
            while (isActivityRunning) {
                // do nothing
            }
        }
    }

    fun decryptDM(encryptedContent: String, pubKey: HexKey, id: String, signerType: SignerType = SignerType.NIP04_DECRYPT) {
        openAmber(
            encryptedContent,
            signerType,
            IntentUtils.activityResultLauncher,
            pubKey,
            id
        )
    }

    fun decryptBookmark(encryptedContent: String, pubKey: HexKey, id: String, signerType: SignerType = SignerType.NIP04_DECRYPT) {
        openAmber(
            encryptedContent,
            signerType,
            IntentUtils.activityResultLauncher,
            pubKey,
            id
        )
    }

    fun decryptGossip(event: Event) {
        if (IntentUtils.eventCache.get(event.id) == null) {
            IntentUtils.eventCache.put(event.id, event)
        }
        isActivityRunning = true
        openAmber(
            event.content,
            SignerType.NIP44_DECRYPT,
            IntentUtils.decryptGossipResultLauncher,
            event.pubKey,
            event.id
        )
    }

    fun encrypt(decryptedContent: String, pubKey: HexKey, signerType: SignerType = SignerType.NIP04_ENCRYPT) {
        isActivityRunning = true
        openAmber(
            decryptedContent,
            signerType,
            IntentUtils.activityResultLauncher,
            pubKey,
            "encrypt"
        )
        while (isActivityRunning) {
            Thread.sleep(100)
        }
    }

    fun decryptZapEvent(event: LnZapRequestEvent) {
        isActivityRunning = true
        openAmber(
            event.toJson(),
            SignerType.DECRYPT_ZAP_EVENT,
            IntentUtils.activityResultLauncher,
            event.pubKey,
            event.id
        )
        while (isActivityRunning) {
            Thread.sleep(100)
        }
    }
}
