package com.vitorpamplona.amethyst.service

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.LruCache
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.actions.SignerType
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.EventInterface
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object AmberUtils {
    val content = LruCache<String, String>(10)
    var isActivityRunning: Boolean = false
    val cachedDecryptedContent = mutableMapOf<HexKey, String>()
    lateinit var account: Account
    lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
    lateinit var blockListResultLauncher: ActivityResultLauncher<Intent>
    lateinit var signEventResultLauncher: ActivityResultLauncher<Intent>

    @OptIn(DelicateCoroutinesApi::class)
    fun start(activity: MainActivity) {
        signEventResultLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (it.resultCode != Activity.RESULT_OK) {
                GlobalScope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        Amethyst.instance,
                        "Sign request rejected",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                val json = it.data?.getStringExtra("event") ?: ""
                GlobalScope.launch(Dispatchers.IO) {
                    val signedEvent = Event.fromJson(json)
                    if (signedEvent.hasValidSignature()) {
                        Client.send(signedEvent)
                        LocalCache.verifyAndConsume(signedEvent, null)
                    }
                }
            }
            isActivityRunning = false
            ServiceManager.shouldPauseService = true
        }

        activityResultLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (it.resultCode != Activity.RESULT_OK) {
                GlobalScope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        Amethyst.instance,
                        "Sign request rejected",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                val event = it.data?.getStringExtra("signature") ?: ""
                val id = it.data?.getStringExtra("id") ?: ""
                if (id.isNotBlank()) {
                    content.put(id, event)
                    cachedDecryptedContent[id] = event
                }
            }
            isActivityRunning = false
            ServiceManager.shouldPauseService = true
            GlobalScope.launch(Dispatchers.IO) {
                isActivityRunning = false
                ServiceManager.shouldPauseService = true
            }
        }

        blockListResultLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (it.resultCode != Activity.RESULT_OK) {
                GlobalScope.launch(Dispatchers.Main) {
                    Toast.makeText(
                        Amethyst.instance,
                        "Sign request rejected",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                val decryptedContent = it.data?.getStringExtra("signature") ?: ""
                val id = it.data?.getStringExtra("id") ?: ""
                if (id.isNotBlank()) {
                    cachedDecryptedContent[id] = decryptedContent
                    account.live.invalidateData()
                }
            }
            isActivityRunning = false
            ServiceManager.shouldPauseService = true
            GlobalScope.launch(Dispatchers.IO) {
                isActivityRunning = false
                ServiceManager.shouldPauseService = true
            }
        }
    }

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

        val result = getDataFromResolver(SignerType.SIGN_EVENT, arrayOf(event.toJson(), event.pubKey()))
        if (result !== null) {
            content.put(event.id(), result)
            return
        }

        ServiceManager.shouldPauseService = false
        isActivityRunning = true
        openAmber(
            event.toJson(),
            SignerType.SIGN_EVENT,
            activityResultLauncher,
            "",
            event.id()
        )
        while (isActivityRunning) {
            Thread.sleep(100)
        }
    }

    fun signEvent(event: EventInterface) {
        checkNotInMainThread()
        ServiceManager.shouldPauseService = false
        isActivityRunning = true
        openAmber(
            event.toJson(),
            SignerType.SIGN_EVENT,
            signEventResultLauncher,
            account.keyPair.pubKey.toHexKey(),
            event.id()
        )
    }

    fun decryptBlockList(encryptedContent: String, pubKey: HexKey, id: String, signerType: SignerType = SignerType.NIP04_DECRYPT) {
        val result = getDataFromResolver(signerType, arrayOf(encryptedContent, pubKey))
        if (result !== null) {
            content.put(id, result)
            cachedDecryptedContent[id] = result
            return
        }
        isActivityRunning = true
        openAmber(
            encryptedContent,
            signerType,
            blockListResultLauncher,
            pubKey,
            id
        )
    }

    fun getDataFromResolver(signerType: SignerType, data: Array<out String>, columnName: String = "signature"): String? {
        Amethyst.instance.contentResolver.query(
            Uri.parse("content://com.greenart7c3.nostrsigner.$signerType"),
            data,
            null,
            null,
            null
        ).use {
            if (it !== null) {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(columnName)
                    return it.getString(index)
                }
            }
        }
        return null
    }

    fun decrypt(encryptedContent: String, pubKey: HexKey, id: String, signerType: SignerType = SignerType.NIP04_DECRYPT) {
        val result = getDataFromResolver(signerType, arrayOf(encryptedContent, pubKey))
        if (result !== null) {
            content.put(id, result)
            cachedDecryptedContent[id] = result
            return
        }

        isActivityRunning = true
        openAmber(
            encryptedContent,
            signerType,
            activityResultLauncher,
            pubKey,
            id
        )
        while (isActivityRunning) {
            // do nothing
        }
    }

    fun decryptDM(encryptedContent: String, pubKey: HexKey, id: String, signerType: SignerType = SignerType.NIP04_DECRYPT) {
        val result = getDataFromResolver(signerType, arrayOf(encryptedContent, pubKey))
        if (result !== null) {
            content.put(id, result)
            cachedDecryptedContent[id] = result
            return
        }
        openAmber(
            encryptedContent,
            signerType,
            activityResultLauncher,
            pubKey,
            id
        )
    }

    fun decryptBookmark(encryptedContent: String, pubKey: HexKey, id: String, signerType: SignerType = SignerType.NIP04_DECRYPT) {
        val result = getDataFromResolver(signerType, arrayOf(encryptedContent, pubKey))
        if (result !== null) {
            content.put(id, result)
            cachedDecryptedContent[id] = result
            return
        }
        openAmber(
            encryptedContent,
            signerType,
            activityResultLauncher,
            pubKey,
            id
        )
    }

    fun encrypt(decryptedContent: String, pubKey: HexKey, id: String, signerType: SignerType = SignerType.NIP04_ENCRYPT) {
        val result = getDataFromResolver(signerType, arrayOf(decryptedContent, pubKey))
        if (result !== null) {
            content.put(id, result)
            return
        }

        isActivityRunning = true
        openAmber(
            decryptedContent,
            signerType,
            activityResultLauncher,
            pubKey,
            "encrypt"
        )
        while (isActivityRunning) {
            Thread.sleep(100)
        }
    }

    fun decryptZapEvent(event: LnZapRequestEvent) {
        val result = getDataFromResolver(SignerType.DECRYPT_ZAP_EVENT, arrayOf(event.toJson()))
        if (result !== null) {
            content.put(event.id, result)
            cachedDecryptedContent[event.id] = result
            return
        }
        openAmber(
            event.toJson(),
            SignerType.DECRYPT_ZAP_EVENT,
            activityResultLauncher,
            event.pubKey,
            event.id
        )
    }
}
