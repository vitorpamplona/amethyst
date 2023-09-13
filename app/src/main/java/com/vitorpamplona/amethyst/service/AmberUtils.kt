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
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import com.vitorpamplona.quartz.events.SealedGossipEvent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object AmberUtils {
    var content: String = ""
    var isActivityRunning: Boolean = false
    val cachedDecryptedContent = mutableMapOf<HexKey, String>()
    lateinit var account: Account
    lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
    lateinit var decryptGossipResultLauncher: ActivityResultLauncher<Intent>
    lateinit var blockListResultLauncher: ActivityResultLauncher<Intent>
    lateinit var signEventResultLauncher: ActivityResultLauncher<Intent>
    val eventCache = LruCache<String, Event>(100)

    @OptIn(DelicateCoroutinesApi::class)
    fun consume(event: Event) {
        if (LocalCache.justVerify(event)) {
            if (event is GiftWrapEvent) {
                GlobalScope.launch(Dispatchers.IO) {
                    val decryptedContent = cachedDecryptedContent[event.id] ?: ""
                    if (decryptedContent.isNotBlank()) {
                        event.cachedGift(
                            NostrAccountDataSource.account.keyPair.pubKey,
                            decryptedContent
                        )?.let {
                            consume(it)
                        }
                    } else {
                        decryptGossip(event)
                    }
                }
            }

            if (event is SealedGossipEvent) {
                GlobalScope.launch(Dispatchers.IO) {
                    val decryptedContent = cachedDecryptedContent[event.id] ?: ""
                    if (decryptedContent.isNotBlank()) {
                        event.cachedGossip(NostrAccountDataSource.account.keyPair.pubKey, decryptedContent)?.let {
                            LocalCache.justConsume(it, null)
                        }
                    } else {
                        decryptGossip(event)
                    }
                }
                // Don't store sealed gossips to avoid rebroadcasting by mistake.
            } else {
                LocalCache.justConsume(event, null)
            }
        }
    }

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
            isActivityRunning = false
            ServiceManager.shouldPauseService = true
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
                content = event
                val id = it.data?.getStringExtra("id") ?: ""
                if (id.isNotBlank()) {
                    cachedDecryptedContent[id] = event
                }
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
        }

        decryptGossipResultLauncher = activity.registerForActivityResult(
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
                    val event = eventCache.get(id)
                    if (event != null) {
                        GlobalScope.launch(Dispatchers.IO) {
                            AmberUtils.cachedDecryptedContent[event.id] = decryptedContent
                            consume(event)
                        }
                    }
                }
            }
            isActivityRunning = false
            ServiceManager.shouldPauseService = true
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
        isActivityRunning = true
        openAmber(
            encryptedContent,
            signerType,
            blockListResultLauncher,
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
                activityResultLauncher,
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
            activityResultLauncher,
            pubKey,
            id
        )
    }

    fun decryptBookmark(encryptedContent: String, pubKey: HexKey, id: String, signerType: SignerType = SignerType.NIP04_DECRYPT) {
        openAmber(
            encryptedContent,
            signerType,
            activityResultLauncher,
            pubKey,
            id
        )
    }

    fun decryptGossip(event: Event) {
        if (eventCache.get(event.id) == null) {
            eventCache.put(event.id, event)
        }
        isActivityRunning = true
        openAmber(
            event.content,
            SignerType.NIP44_DECRYPT,
            decryptGossipResultLauncher,
            event.pubKey,
            event.id
        )
    }

    fun encrypt(decryptedContent: String, pubKey: HexKey, signerType: SignerType = SignerType.NIP04_ENCRYPT) {
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
        isActivityRunning = true
        openAmber(
            event.toJson(),
            SignerType.DECRYPT_ZAP_EVENT,
            activityResultLauncher,
            event.pubKey,
            event.id
        )
        while (isActivityRunning) {
            Thread.sleep(100)
        }
    }
}
