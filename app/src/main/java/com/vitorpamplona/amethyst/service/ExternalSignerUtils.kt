package com.vitorpamplona.amethyst.service

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.LruCache
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.EventInterface
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.Timer
import kotlin.concurrent.schedule

enum class SignerType {
    SIGN_EVENT,
    NIP04_ENCRYPT,
    NIP04_DECRYPT,
    NIP44_ENCRYPT,
    NIP44_DECRYPT,
    GET_PUBLIC_KEY,
    DECRYPT_ZAP_EVENT
}

object ExternalSignerUtils {
    val content = LruCache<String, String?>(10)
    var isActivityRunning: Boolean = false
    val cachedDecryptedContent = mutableMapOf<HexKey, String>()
    lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
    lateinit var decryptResultLauncher: ActivityResultLauncher<Intent>
    lateinit var blockListResultLauncher: ActivityResultLauncher<Intent>

    @OptIn(DelicateCoroutinesApi::class)
    fun requestRejectedToast() {
        GlobalScope.launch(Dispatchers.Main) {
            Toast.makeText(
                Amethyst.instance.applicationContext,
                Amethyst.instance.applicationContext.getString(R.string.sign_request_rejected),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun default() {
        Timer().schedule(200) {
            isActivityRunning = false
            ServiceManager.shouldPauseService = true
            GlobalScope.launch(Dispatchers.IO) {
                isActivityRunning = false
                ServiceManager.shouldPauseService = true
            }
        }
    }

    fun start(activity: MainActivity) {
        activityResultLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (it.resultCode != Activity.RESULT_OK) {
                requestRejectedToast()
            } else {
                val event = it.data?.getStringExtra("signature") ?: ""
                val id = it.data?.getStringExtra("id") ?: ""
                if (id.isNotBlank()) {
                    content.put(id, event)
                }
            }
            default()
        }

        decryptResultLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (it.resultCode != Activity.RESULT_OK) {
                requestRejectedToast()
            } else {
                val event = it.data?.getStringExtra("signature") ?: ""
                val id = it.data?.getStringExtra("id") ?: ""
                if (id.isNotBlank()) {
                    content.put(id, event)
                    cachedDecryptedContent[id] = event
                }
            }
            default()
        }

        blockListResultLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (it.resultCode != Activity.RESULT_OK) {
                requestRejectedToast()
            } else {
                val decryptedContent = it.data?.getStringExtra("signature") ?: ""
                val id = it.data?.getStringExtra("id") ?: ""
                if (id.isNotBlank()) {
                    cachedDecryptedContent[id] = decryptedContent
                }
            }
            default()
        }
    }
}

class ExternalSigner(val npub: String) {
    @OptIn(DelicateCoroutinesApi::class)
    fun openSigner(
        data: String,
        type: SignerType,
        intentResult: ActivityResultLauncher<Intent>,
        pubKey: HexKey,
        id: String
    ) {
        try {
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
            if (type !== SignerType.GET_PUBLIC_KEY) {
                intent.putExtra("current_user", npub)
            }
            intent.`package` = "com.greenart7c3.nostrsigner"
            intentResult.launch(intent)
        } catch (e: Exception) {
            Log.e("Signer", "Error opening Signer app", e)
            GlobalScope.launch(Dispatchers.Main) {
                Toast.makeText(
                    Amethyst.instance.applicationContext,
                    Amethyst.instance.applicationContext.getString(R.string.error_opening_external_signer),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun openSigner(event: EventInterface, columnName: String = "signature") {
        checkNotInMainThread()

        val result = getDataFromResolver(SignerType.SIGN_EVENT, arrayOf(event.toJson(), event.pubKey()), columnName)
        if (result == null) {
            ServiceManager.shouldPauseService = false
            ExternalSignerUtils.isActivityRunning = true
            openSigner(
                event.toJson(),
                SignerType.SIGN_EVENT,
                ExternalSignerUtils.activityResultLauncher,
                "",
                event.id()
            )
            while (ExternalSignerUtils.isActivityRunning) {
                Thread.sleep(100)
            }
        } else {
            ExternalSignerUtils.content.put(event.id(), result)
        }
    }

    fun decryptBlockList(encryptedContent: String, pubKey: HexKey, id: String, signerType: SignerType = SignerType.NIP04_DECRYPT) {
        val result = getDataFromResolver(signerType, arrayOf(encryptedContent, pubKey))
        if (result == null) {
            ExternalSignerUtils.isActivityRunning = true
            openSigner(
                encryptedContent,
                signerType,
                ExternalSignerUtils.blockListResultLauncher,
                pubKey,
                id
            )
        } else {
            ExternalSignerUtils.content.put(id, result)
            ExternalSignerUtils.cachedDecryptedContent[id] = result
        }
    }

    fun getDataFromResolver(signerType: SignerType, data: Array<out String>, columnName: String = "signature"): String? {
        val localData = if (signerType !== SignerType.GET_PUBLIC_KEY) {
            data.toList().plus(npub).toTypedArray()
        } else {
            data
        }

        Amethyst.instance.applicationContext.contentResolver.query(
            Uri.parse("content://com.greenart7c3.nostrsigner.$signerType"),
            localData,
            null,
            null,
            null
        ).use {
            if (it == null) {
                return null
            }
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(columnName)
                if (index < 0) {
                    Log.d("getDataFromResolver", "column '$columnName' not found")
                    return null
                }
                return it.getString(index)
            }
        }
        return null
    }

    fun decrypt(encryptedContent: String, pubKey: HexKey, id: String, signerType: SignerType = SignerType.NIP04_DECRYPT) {
        val result = getDataFromResolver(signerType, arrayOf(encryptedContent, pubKey))
        if (result == null) {
            ExternalSignerUtils.isActivityRunning = true
            openSigner(
                encryptedContent,
                signerType,
                ExternalSignerUtils.decryptResultLauncher,
                pubKey,
                id
            )
            while (ExternalSignerUtils.isActivityRunning) {
                // do nothing
            }
        } else {
            ExternalSignerUtils.content.put(id, result)
            ExternalSignerUtils.cachedDecryptedContent[id] = result
        }
    }

    fun decryptDM(encryptedContent: String, pubKey: HexKey, id: String, signerType: SignerType = SignerType.NIP04_DECRYPT) {
        val result = getDataFromResolver(signerType, arrayOf(encryptedContent, pubKey))
        if (result == null) {
            openSigner(
                encryptedContent,
                signerType,
                ExternalSignerUtils.decryptResultLauncher,
                pubKey,
                id
            )
        } else {
            ExternalSignerUtils.content.put(id, result)
            ExternalSignerUtils.cachedDecryptedContent[id] = result
        }
    }

    fun decryptBookmark(encryptedContent: String, pubKey: HexKey, id: String, signerType: SignerType = SignerType.NIP04_DECRYPT) {
        val result = getDataFromResolver(signerType, arrayOf(encryptedContent, pubKey))
        if (result == null) {
            openSigner(
                encryptedContent,
                signerType,
                ExternalSignerUtils.decryptResultLauncher,
                pubKey,
                id
            )
        } else {
            ExternalSignerUtils.content.put(id, result)
            ExternalSignerUtils.cachedDecryptedContent[id] = result
        }
    }

    fun encrypt(decryptedContent: String, pubKey: HexKey, id: String, signerType: SignerType = SignerType.NIP04_ENCRYPT) {
        ExternalSignerUtils.content.remove(id)
        ExternalSignerUtils.cachedDecryptedContent.remove(id)
        val result = getDataFromResolver(signerType, arrayOf(decryptedContent, pubKey))
        if (result == null) {
            ExternalSignerUtils.isActivityRunning = true
            openSigner(
                decryptedContent,
                signerType,
                ExternalSignerUtils.activityResultLauncher,
                pubKey,
                id
            )
            while (ExternalSignerUtils.isActivityRunning) {
                Thread.sleep(100)
            }
        } else {
            ExternalSignerUtils.content.put(id, result)
        }
    }

    fun decryptZapEvent(event: LnZapRequestEvent) {
        val result = getDataFromResolver(SignerType.DECRYPT_ZAP_EVENT, arrayOf(event.toJson(), event.pubKey))
        if (result == null) {
            openSigner(
                event.toJson(),
                SignerType.DECRYPT_ZAP_EVENT,
                ExternalSignerUtils.decryptResultLauncher,
                event.pubKey,
                event.id
            )
        } else {
            ExternalSignerUtils.content.put(event.id, result)
            ExternalSignerUtils.cachedDecryptedContent[event.id] = result
        }
    }
}
