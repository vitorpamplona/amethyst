package com.vitorpamplona.amethyst.service

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.ServiceManager
import com.vitorpamplona.amethyst.ui.MainActivity
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object IntentUtils {
    lateinit var activityResultLauncher: ActivityResultLauncher<Intent>

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
                AmberUtils.isActivityRunning = false
                return@registerForActivityResult
            }

            val event = it.data?.getStringExtra("signature") ?: ""
            val id = it.data?.getStringExtra("id") ?: ""
            if (id.isNotBlank()) {
                AmberUtils.cachedDecryptedContent[id] = event
            }
            AmberUtils.content = event
            AmberUtils.isActivityRunning = false
            ServiceManager.shouldPauseService = true
        }
    }
}
