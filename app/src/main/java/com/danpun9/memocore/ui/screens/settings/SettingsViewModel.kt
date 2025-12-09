package com.danpun9.memocore.ui.screens.settings

import androidx.lifecycle.ViewModel
import com.danpun9.memocore.data.ModelType
import com.danpun9.memocore.data.ThemeMode
import com.danpun9.memocore.data.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class SettingsViewModel(
    private val userPreferences: UserPreferences
) : ViewModel() {

    private val _selectedModel = MutableStateFlow(userPreferences.getModelType())
    val selectedModel: StateFlow<ModelType> = _selectedModel.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = userPreferences.themeMode

    fun onModelSelected(modelType: ModelType) {
        userPreferences.saveModelType(modelType)
        _selectedModel.value = modelType
    }

    fun onThemeSelected(mode: ThemeMode) {
        userPreferences.saveThemeMode(mode)
    }
}
