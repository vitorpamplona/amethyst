package com.vitorpamplona.amethyst.service

import android.app.Activity
import android.content.Intent
import android.util.LruCache
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.SealedGossipEvent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object IntentUtils {
    lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
    lateinit var decryptGossipResultLauncher: ActivityResultLauncher<Intent>
    lateinit var blockListResultLauncher: ActivityResultLauncher<Intent>
    val eventCache = LruCache<String, Event>(100)

    @OptIn(DelicateCoroutinesApi::class)
    fun consume(event: Event) {
        if (LocalCache.justVerify(event)) {
            if (event is GiftWrapEvent) {
                GlobalScope.launch(Dispatchers.IO) {
                    val decryptedContent = AmberUtils.cachedDecryptedContent[event.id] ?: ""
                    if (decryptedContent.isNotBlank()) {
                        event.cachedGift(
                            NostrAccountDataSource.account.keyPair.pubKey,
                            decryptedContent
                        )?.let {
                            consume(it)
                        }
                    } else {
                        AmberUtils.decryptGossip(event)
                    }
                }
            }

            if (event is SealedGossipEvent) {
                GlobalScope.launch(Dispatchers.IO) {
                    val decryptedContent = AmberUtils.cachedDecryptedContent[event.id] ?: ""
                    if (decryptedContent.isNotBlank()) {
                        event.cachedGossip(NostrAccountDataSource.account.keyPair.pubKey, decryptedContent)?.let {
                            LocalCache.justConsume(it, null)
                        }
                    } else {
                        AmberUtils.decryptGossip(event)
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
                AmberUtils.content = event
                val id = it.data?.getStringExtra("id") ?: ""
                if (id.isNotBlank()) {
                    AmberUtils.cachedDecryptedContent[id] = event
                }
            }
            AmberUtils.isActivityRunning = false
            ServiceManager.shouldPauseService = true
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
                    AmberUtils.cachedDecryptedContent[id] = decryptedContent
                    AmberUtils.account.live.invalidateData()
                }
            }
            AmberUtils.isActivityRunning = false
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
            AmberUtils.isActivityRunning = false
            ServiceManager.shouldPauseService = true
        }
    }
}
