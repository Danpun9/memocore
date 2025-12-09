package com.danpun9.memocore.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single

enum class ModelType {
    GEMINI,
    LOCAL
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Single
class UserPreferences(
    context: Context,
) {
    private val sharedPrefFileName = "user_prefs"
    private val modelTypeKey = "model_type"
    private val themeModeKey = "theme_mode"

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(sharedPrefFileName, Context.MODE_PRIVATE)


    fun saveModelType(modelType: ModelType) {
        sharedPreferences.edit().putString(modelTypeKey, modelType.name).apply()
    }

    fun getModelType(): ModelType {
        val type = sharedPreferences.getString(modelTypeKey, ModelType.GEMINI.name)
        return try {
            ModelType.valueOf(type ?: ModelType.GEMINI.name)
        } catch (e: IllegalArgumentException) {
            ModelType.GEMINI
        }
    }

    private val _themeMode = MutableStateFlow(getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun saveThemeMode(themeMode: ThemeMode) {
        sharedPreferences.edit().putString(themeModeKey, themeMode.name).apply()
        _themeMode.value = themeMode
    }

    fun getThemeMode(): ThemeMode {
        val mode = sharedPreferences.getString(themeModeKey, ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(mode ?: ThemeMode.SYSTEM.name)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    fun saveLocalModelPath(path: String) {
        sharedPreferences.edit().putString("local_model_path", path).apply()
    }

    fun getLocalModelPath(): String? {
        return sharedPreferences.getString("local_model_path", null)
    }
}

