package com.danpun9.memocore.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.koin.core.annotation.Single

@Single
class GeminiAPIKey(
    context: Context,
) {
    private val securedSharedPrefFileName = "secret_shared_prefs"
    private val apiKeySharedPrefKey = "gemini_api_key"

    private val masterKey: MasterKey =
        MasterKey
            .Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private val sharedPreferences: SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            securedSharedPrefFileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    fun saveAPIKey(apiKey: String) {
        sharedPreferences.edit().putString(apiKeySharedPrefKey, apiKey).apply()
    }

    fun getAPIKey(): String? = sharedPreferences.getString(apiKeySharedPrefKey, null)
}
