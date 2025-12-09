package com.danpun9.memocore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danpun9.memocore.data.ThemeMode
import com.danpun9.memocore.data.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class MainViewModel(
    userPreferences: UserPreferences
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = userPreferences.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = userPreferences.getThemeMode()
        )
}
