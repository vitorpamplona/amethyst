package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.ui.actions.SignerType
import com.vitorpamplona.amethyst.ui.actions.openAmber
import com.vitorpamplona.quartz.encoders.HexKey

object AmberUtils {
    var content: String = ""
    var isActivityRunning: Boolean = false

    fun decryptBookmark(encryptedContent: String, pubKey: HexKey) {
        if (content.isBlank()) {
            isActivityRunning = true
            openAmber(
                encryptedContent,
                SignerType.NIP04_DECRYPT,
                IntentUtils.decryptActivityResultLauncher,
                pubKey
            )
            while (isActivityRunning) {
                Thread.sleep(250)
            }
        }
    }

    fun encryptBookmark(decryptedContent: String, pubKey: HexKey) {
        if (content.isBlank()) {
            isActivityRunning = true
            openAmber(
                decryptedContent,
                SignerType.NIP04_ENCRYPT,
                IntentUtils.decryptActivityResultLauncher,
                pubKey
            )
            while (isActivityRunning) {
                Thread.sleep(250)
            }
        }
    }
}
