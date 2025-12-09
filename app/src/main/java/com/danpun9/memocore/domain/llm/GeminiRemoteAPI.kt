package com.danpun9.memocore.domain.llm

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GeminiRemoteAPI(
    private val apiKey: String,
) : LLMInferenceAPI() {
    private val generativeModel: GenerativeModel

    init {
        val configBuilder = GenerationConfig.Builder()
        configBuilder.topP = 0.4f
        configBuilder.temperature = 0.3f
        configBuilder.stopSequences = listOf("Observation:")
        generativeModel =
            GenerativeModel(
                modelName = "gemini-2.5-flash-lite",
                apiKey = apiKey,
                generationConfig = configBuilder.build(),
                systemInstruction =
                    content {
                        text(com.danpun9.memocore.data.Prompts.getSystemInstruction())
                    },
            )
    }

    override fun getResponse(prompt: String): Flow<String> {
        Log.e("APP", "Prompt given: $prompt")
        return generativeModel.generateContentStream(prompt).map { it.text ?: "" }
    }
}
