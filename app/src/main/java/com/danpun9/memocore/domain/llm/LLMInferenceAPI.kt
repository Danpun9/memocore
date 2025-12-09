package com.danpun9.memocore.domain.llm

import kotlinx.coroutines.flow.Flow

abstract class LLMInferenceAPI {
    abstract fun getResponse(prompt: String): Flow<String>
}
