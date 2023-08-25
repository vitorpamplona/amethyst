package com.vitorpamplona.amethyst.service

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher

object IntentUtils {
    lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
    lateinit var authActivityResultLauncher: ActivityResultLauncher<Intent>
    lateinit var decryptActivityResultLauncher: ActivityResultLauncher<Intent>
}
