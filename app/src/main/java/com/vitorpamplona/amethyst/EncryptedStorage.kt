package com.vitorpamplona.amethyst

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class EncryptedStorage {

    fun preferences(context: Context): EncryptedSharedPreferences {
        val secretKey: String = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val preferencesName = "secret_keeper"

        return EncryptedSharedPreferences.create(
            preferencesName,
            secretKey,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences
    }
}
