package com.danpun9.memocore.ui.screens.edit_credentials

import androidx.lifecycle.ViewModel
import com.danpun9.memocore.data.GeminiAPIKey
import com.danpun9.memocore.data.HFAccessToken
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class EditCredentialsViewModel(
    private val geminiAPIKey: GeminiAPIKey,
    private val hfAccessToken: HFAccessToken,
) : ViewModel() {
    fun getGeminiAPIKey(): String? = geminiAPIKey.getAPIKey()

    fun saveGeminiAPIKey(apiKey: String) {
        geminiAPIKey.saveAPIKey(apiKey)
    }

    fun getHFAccessToken(): String? = hfAccessToken.getToken()

    fun saveHFAccessToken(accessToken: String) {
        hfAccessToken.saveToken(accessToken)
    }
}
