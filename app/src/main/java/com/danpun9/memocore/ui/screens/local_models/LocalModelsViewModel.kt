package com.danpun9.memocore.ui.screens.local_models

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ketch.Ketch
import com.ketch.Status
import com.danpun9.memocore.data.HFAccessToken
import com.danpun9.memocore.data.LocalModel
import com.danpun9.memocore.domain.llm.LiteRTAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koin.android.annotation.KoinViewModel
import java.util.concurrent.TimeUnit

sealed class LocalModelsUIEvent {
    data class OnModelDownloadClick(
        val model: LocalModel,
    ) : LocalModelsUIEvent()

    data class OnUseModelClick(
        val model: LocalModel,
    ) : LocalModelsUIEvent()

    data object RefreshModelsList : LocalModelsUIEvent()
}

data class LocalModelsUIState(
    val models: List<LocalModel> = emptyList(),
    val downloadModelDialogState: DownloadModelDialogUIState = DownloadModelDialogUIState(),
)

data class DownloadModelDialogUIState(
    val isDialogVisible: Boolean = false,
    val showProgress: Boolean = false,
    val progress: Int = 0,
)

@KoinViewModel
class LocalModelsViewModel(
    private val context: Context,
    private val liteRTAPI: LiteRTAPI,
    private val hfAccessToken: HFAccessToken,
    private val userPreferences: com.danpun9.memocore.data.UserPreferences,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            LocalModelsUIState(
                models =
                    listOf(
                        LocalModel(
                            name = "Qwen2.5 1.5B Instruct Q8",
                            description = "",
                            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_seq128_q8_ekv4096.task",
                        ),
                        LocalModel(
                            name = "Phi 4 Mini Instruct Q8",
                            description = "",
                            downloadUrl = "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task",
                        ),
                        LocalModel(
                            name = "DeepSeek R1 Distill Qwen 1.5B Q8",
                            description = "",
                            downloadUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.task",
                        ),
                        LocalModel(
                            name = "Gemma3 1B IT (Recommended)",
                            description = "",
                            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q8_ekv4096.task",
                        ),
                        LocalModel(
                            name = "Gemma3 4B IT",
                            description = "",
                            downloadUrl = "https://huggingface.co/litert-community/Gemma3-4B-IT/resolve/main/Gemma3-4b-it-int4-web.task",
                        ),
                        LocalModel(
                            name = "Gemma 3n E2B IT (Recommended)",
                            description = "",
                            downloadUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/resolve/main/gemma-3n-E2B-it-int4.task",
                        ),
                        LocalModel(
                            name = "Gemma 3n E4B IT",
                            description = "",
                            downloadUrl = "https://huggingface.co/google/gemma-3n-E4B-it-litert-preview/resolve/main/gemma-3n-E4B-it-int4.task",
                        ),
                        LocalModel(
                            name = "Llama 3.2 1B Q8",
                            description = "",
                            downloadUrl = "https://huggingface.co/litert-community/Llama-3.2-1B-Instruct/resolve/main/Llama-3.2-1B-Instruct_multi-prefill-seq_q8_ekv1280.task",
                        ),
                        LocalModel(
                            name = "Llama 3.2 3B Q8",
                            description = "",
                            downloadUrl = "https://huggingface.co/litert-community/Llama-3.2-3B-Instruct/resolve/main/Llama-3.2-3B-Instruct_multi-prefill-seq_q8_ekv1280.task",
                        ),
                    ),
            ),
        )
    val uiState: StateFlow<LocalModelsUIState> = _uiState.asStateFlow()

    private var ketch: Ketch =
        Ketch
            .builder()
            .setOkHttpClient(
                OkHttpClient
                    .Builder()
                    .connectTimeout(60L, TimeUnit.SECONDS)
                    .readTimeout(60L, TimeUnit.SECONDS)
                    .build(),
            ).build(context)

    fun onEvent(event: LocalModelsUIEvent) {
        when (event) {
            is LocalModelsUIEvent.OnModelDownloadClick -> {
                viewModelScope.launch(Dispatchers.IO) {
                    downloadModel(event.model)
                }
            }
            is LocalModelsUIEvent.OnUseModelClick -> {
                if (liteRTAPI.isLoaded) {
                    liteRTAPI.unload()
                }
                viewModelScope.launch(Dispatchers.IO) {
                    val modelPath = event.model.getLocalModelPath(context.filesDir.absolutePath)
                    userPreferences.saveLocalModelPath(modelPath)
                    loadModel(event.model)
                    onEvent(LocalModelsUIEvent.RefreshModelsList)
                }
            }
            is LocalModelsUIEvent.RefreshModelsList -> {
                _uiState.update {
                    it.copy(
                        models =
                            it.models.map { model ->
                                model.copy(
                                    isLoaded =
                                        liteRTAPI.loadedModelPath == model.getLocalModelPath(context.filesDir.absolutePath),
                                )
                            },
                    )
                }
            }
        }
    }

    private suspend fun loadModel(model: LocalModel) =
        withContext(Dispatchers.IO) {
            val modelPath = model.getLocalModelPath(context.filesDir.absolutePath)
            val file = java.io.File(modelPath)

            Log.d("APP", "Checking for model file at: $modelPath")
            Log.d("APP", "Files in directory ${context.filesDir.absolutePath}:")
            context.filesDir.listFiles()?.forEach { 
                Log.d("APP", " - ${it.name} (Size: ${it.length()})") 
            }

            if (!file.exists()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Model file not found at $modelPath", Toast.LENGTH_LONG).show()
                }
                return@withContext
            }

            // Check if file is too small (likely an error page or corrupted download)
            // < 1MB is definitely not a valid model
            if (file.length() < 1024 * 1024) {
                withContext(Dispatchers.Main) {
                    Toast
                        .makeText(
                            context,
                            "Invalid model file (Size: ${file.length()} bytes). Please delete and re-download.",
                            Toast.LENGTH_LONG,
                        ).show()
                }
                return@withContext
            }

            try {
                liteRTAPI.load(
                    context,
                    modelPath,
                    onSuccess = {},
                    onError = { exception ->
                        Log.e("APP", "Failed to load LiteRT model: ${exception.message}")
                        // We need to show toast on Main thread
                        // Since onError might be called from background, we can't easily switch context here
                        // relying on the log for now, or we could pass a callback that handles UI
                    },
                )
            } catch (e: Exception) {
                Log.e("APP", "Crash avoided during model load: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast
                        .makeText(
                            context,
                            "Failed to load model: ${e.message}",
                            Toast.LENGTH_LONG,
                        ).show()
                }
            }
        }

    private suspend fun downloadModel(model: LocalModel) {
        val headers =
            if (hfAccessToken.getToken() != null) {
                HashMap(
                    mapOf("Authorization" to "Bearer ${hfAccessToken.getToken()}"),
                )
            } else {
                HashMap()
            }
        val downloadId =
            ketch.download(
                model.downloadUrl,
                context.filesDir.absolutePath,
                model.getFileName(),
                headers = headers,
            )
        ketch
            .observeDownloadById(downloadId)
            .flowOn(Dispatchers.IO)
            .collect { downloadModel ->
                downloadModel?.let { ketchDownload ->
                    when (ketchDownload.status) {
                        Status.QUEUED -> {
                            _uiState.update {
                                it.copy(
                                    downloadModelDialogState = it.downloadModelDialogState.copy(isDialogVisible = true),
                                )
                            }
                        }

                        Status.PROGRESS -> {
                            _uiState.update {
                                it.copy(
                                    downloadModelDialogState = it.downloadModelDialogState.copy(progress = ketchDownload.progress),
                                )
                            }
                        }

                        Status.SUCCESS -> {
                            _uiState.update {
                                it.copy(
                                    downloadModelDialogState = it.downloadModelDialogState.copy(isDialogVisible = false),
                                )
                            }
                            onEvent(LocalModelsUIEvent.OnUseModelClick(model))
                            withContext(Dispatchers.Main) {
                                Toast
                                    .makeText(
                                        context,
                                        "Model downloaded successfully",
                                        Toast.LENGTH_LONG,
                                    ).show()
                            }
                        }

                        Status.FAILED -> {
                            _uiState.update {
                                it.copy(
                                    downloadModelDialogState = it.downloadModelDialogState.copy(isDialogVisible = false),
                                )
                            }
                            withContext(Dispatchers.Main) {
                                Log.e("APP", "Failure reason ${ketchDownload.failureReason}")
                                Toast
                                    .makeText(
                                        context,
                                        "Model downloaded failed ${ketchDownload.failureReason}",
                                        Toast.LENGTH_LONG,
                                    ).show()
                            }
                        }

                        Status.STARTED -> {
                            _uiState.update {
                                it.copy(
                                    downloadModelDialogState =
                                        it.downloadModelDialogState.copy(
                                            isDialogVisible = true,
                                            showProgress = true,
                                        ),
                                )
                            }
                        }

                        else -> {}
                    }
                }
            }
    }
}
