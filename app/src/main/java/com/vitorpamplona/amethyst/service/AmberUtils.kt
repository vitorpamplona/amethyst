package com.vitorpamplona.amethyst.service

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.ui.actions.SignerType
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.EventInterface

object AmberUtils {
    var content: String = ""
    var isActivityRunning: Boolean = false
    val cachedDecryptedContent = mutableMapOf<HexKey, String>()

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
        }
        intent.putExtra("type", signerType)
        intent.putExtra("pubKey", pubKey)
        intent.putExtra("id", id)
        intent.`package` = "com.greenart7c3.nostrsigner.debug"
        intentResult.launch(intent)
    }

    fun openAmber(event: EventInterface) {
        checkNotInMainThread()
        ServiceManager.shouldPauseService = false
        content = ""
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

    fun loginWithAmber() {
        checkNotInMainThread()
        content = ""
        isActivityRunning = true
        openAmber(
            "",
            SignerType.GET_PUBLIC_KEY,
            IntentUtils.activityResultLauncher,
            "",
            ""
        )
        while (isActivityRunning) {
            // do nothing
        }
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

    fun encrypt(decryptedContent: String, pubKey: HexKey, signerType: SignerType = SignerType.NIP04_ENCRYPT) {
        if (content.isBlank()) {
            isActivityRunning = true
            openAmber(
                decryptedContent,
                signerType,
                IntentUtils.activityResultLauncher,
                pubKey,
                ""
            )
            while (isActivityRunning) {
                // do nothing
            }
        }
    }
}
