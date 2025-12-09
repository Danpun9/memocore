package com.danpun9.memocore.domain.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class LiteRTAPI : LLMInferenceAPI() {
    private lateinit var llmInference: LlmInference
    var isLoaded = false
    var loadedModelPath: String? = null

    class PartialProgressListener(
        private val onPartialResponseGenerated: (String) -> Unit,
        private val onSuccess: (String) -> Unit,
    ) : ProgressListener<String> {
        private var result = ""

        override fun run(
            partialResult: String?,
            done: Boolean,
        ) {
            if (done) {
                onSuccess(result)
                result = ""
            } else {
                result += partialResult ?: ""
                onPartialResponseGenerated(result)
            }
        }
    }

    fun load(
        context: Context,
        modelPath: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit,
    ) {
        val taskOptions =
            LlmInference.LlmInferenceOptions
                .builder()
                .setModelPath(modelPath)
                .setMaxTopK(64)
                .setMaxTokens(2048)
                .build()
        llmInference = LlmInference.createFromOptions(context, taskOptions)
        isLoaded = true
        loadedModelPath = modelPath
        onSuccess()
    }

    override fun getResponse(prompt: String): kotlinx.coroutines.flow.Flow<String> =
        kotlinx.coroutines.flow.callbackFlow {
            Log.e("APP", "Prompt given: $prompt")
                val listener =
                ProgressListener<String> { partialResult, done ->
                    if (partialResult != null) {
                        if (partialResult.contains("Observation:")) {
                            // Stop sequence detected
                            val truncatedResult = partialResult.substringBefore("Observation:")
                            trySend(truncatedResult)
                            close()
                            return@ProgressListener
                        }
                        trySend(partialResult)
                    }
                    if (done) {
                        close()
                    }
                }
            llmInference.generateResponseAsync(prompt, listener)
            awaitClose()
        }

    fun unload() {
        llmInference.close()
        isLoaded = false
        loadedModelPath = null
    }
}
