package com.danpun9.memocore.domain.agent

import com.danpun9.memocore.data.ChunksDB
import com.danpun9.memocore.data.DocumentsDB
import com.danpun9.memocore.data.GeminiAPIKey
import com.danpun9.memocore.data.ModelType
import com.danpun9.memocore.data.Prompts
import com.danpun9.memocore.data.RetrievedContext
import com.danpun9.memocore.data.UserPreferences
import com.danpun9.memocore.domain.SentenceEmbeddingProvider
import com.danpun9.memocore.domain.llm.GeminiRemoteAPI
import com.danpun9.memocore.domain.llm.LLMInferenceAPI
import com.danpun9.memocore.domain.llm.LiteRTAPI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single

sealed interface AgentResponse {
    data class Streaming(val content: String) : AgentResponse
    data class Status(val message: String) : AgentResponse
    data class FinalAnswer(val answer: String, val context: List<RetrievedContext>) : AgentResponse
    data class Error(val message: String) : AgentResponse
    data class UserConfirmationRequired(val action: ToolAction) : AgentResponse
}

data class ToolAction(
    val type: ToolType,
    val title: String,
    val content: String = "",
    val originalContent: String? = null
)

enum class ToolType {
    CREATE_DOC, EDIT_DOC, DELETE_DOC, LIST_DOCS
}

@Single
class ChatAgent(
    private val context: android.content.Context,
    private val documentsDB: DocumentsDB,
    private val chunksDB: ChunksDB,
    private val documentRepository: com.danpun9.memocore.data.repository.DocumentRepository,
    private val geminiAPIKey: GeminiAPIKey,
    private val sentenceEncoder: SentenceEmbeddingProvider,
    private val liteRTAPI: LiteRTAPI,
    private val userPreferences: UserPreferences,
) {

    fun loadLocalModel() {
        if (userPreferences.getModelType() == ModelType.LOCAL) {
            val modelPath = userPreferences.getLocalModelPath()
            if (modelPath != null) {
                val file = java.io.File(modelPath)
                if (file.exists()) {
                    liteRTAPI.load(
                        context,
                        modelPath,
                        onSuccess = {
                            android.util.Log.d("ChatAgent", "Auto-loaded local model: $modelPath")
                        },
                        onError = { e ->
                            android.util.Log.e("ChatAgent", "Failed to auto-load local model: ${e.message}")
                        }
                    )
                }
            }
        }
    }

    data class ChatMessage(
        val role: ChatRole,
        val content: String,
        val isThinking: Boolean = false,
        val sources: List<RetrievedContext> = emptyList()
    )

    enum class ChatRole { USER, MODEL, SYSTEM }

    fun generateResponse(history: List<ChatMessage>, currentQuery: String, isSystemQuery: Boolean = false): Flow<AgentResponse> = flow {
        try {
            val modelType = userPreferences.getModelType()
            val llm: LLMInferenceAPI = if (modelType == ModelType.LOCAL) {
                emit(AgentResponse.Status("Using local model..."))
                liteRTAPI
            } else {
                val apiKey = geminiAPIKey.getAPIKey() ?: throw Exception("Gemini API key is null")
                emit(AgentResponse.Status("Using Gemini cloud model..."))
                GeminiRemoteAPI(apiKey)
            }

            // Construct prompt from history
            val historyPrompt = StringBuilder()
            if (modelType == ModelType.LOCAL) {
                historyPrompt.append(Prompts.getSystemInstruction())
                historyPrompt.append("\n\n")
            }

            history.forEach { message ->
                if (!message.isThinking) { // Filter out thinking steps
                    when (message.role) {
                        ChatRole.USER -> historyPrompt.append("User Query: ${message.content}\n")
                        ChatRole.MODEL -> {
                            historyPrompt.append("Model Answer: ${message.content}\n")
                            if (message.sources.isNotEmpty()) {
                                historyPrompt.append("Context from previous turn:\n")
                                message.sources.forEach { source ->
                                    historyPrompt.append("<file>${source.fileName}</file>\n")
                                    historyPrompt.append("<content>\n${source.context}\n</content>\n")
                                }
                            }
                        }
                        ChatRole.SYSTEM -> {
                            historyPrompt.append("[System Notification]: ${message.content}\n")
                        }
                    }
                }
            }
            if (isSystemQuery) {
                historyPrompt.append("[System Notification]: $currentQuery")
            } else {
                historyPrompt.append("User Query: $currentQuery")
            }

            var currentPrompt = historyPrompt.toString()

            var displayedResponse = ""
            val retrievedContextList = ArrayList<RetrievedContext>()
            var turn = 0
            val maxTurns = 5
            val previousSearchQueries = HashSet<String>()

            while (turn < maxTurns) {
                var currentTurnResponse = ""
                android.util.Log.d("ChatAgent", "Turn: $turn")
                android.util.Log.d("ChatAgent", "Prompt being sent:\n$currentPrompt")

                llm.getResponse(currentPrompt).collect { chunk ->
                    android.util.Log.d("ChatAgent", "Received chunk: $chunk")
                    currentTurnResponse += chunk
                    
                    val finalAnswerTag = "Final Answer:"
                    val searchTag = "<search>"
                    
                    if (currentTurnResponse.contains(finalAnswerTag)) {
                        val finalAnswer = currentTurnResponse.substringAfter(finalAnswerTag).trimStart()
                        emit(AgentResponse.Streaming(finalAnswer))
                    } else if (currentTurnResponse.contains(searchTag)) {
                        val searchQuery = currentTurnResponse.substringAfter(searchTag).substringBefore("</search>", "").trim()
                        if (searchQuery.isNotEmpty()) {
                            emit(AgentResponse.Status("Searching for '$searchQuery'..."))
                        } else {
                            emit(AgentResponse.Status("Thinking..."))
                        }
                    } else if (currentTurnResponse.contains("<read_doc>")) {
                         emit(AgentResponse.Status("Reading document..."))
                    } else if (currentTurnResponse.contains("<create_doc>")) {
                         emit(AgentResponse.Status("Creating document..."))
                    } else if (currentTurnResponse.contains("<edit_doc>")) {
                         emit(AgentResponse.Status("Editing document..."))
                    } else if (currentTurnResponse.contains("<delete_doc>")) {
                         emit(AgentResponse.Status("Deleting document..."))
                    } else {
                         emit(AgentResponse.Streaming(currentTurnResponse))
                    }
                }

                displayedResponse += currentTurnResponse

                val searchTag = "<search>"
                val searchEndTag = "</search>"
                val startIndex = currentTurnResponse.indexOf(searchTag)
                val endIndex = currentTurnResponse.indexOf(searchEndTag)
                
                 if (currentTurnResponse.contains("<create_doc>")) {
                    val start = currentTurnResponse.indexOf("<create_doc>")
                    val end = currentTurnResponse.indexOf("</create_doc>")
                    if (start != -1 && end != -1) {
                        val toolContent = currentTurnResponse.substring(start + "<create_doc>".length, end)
                        val title = toolContent.substringAfter("<title>").substringBefore("</title>").trim()
                        val content = toolContent.substringAfter("<content>").substringBefore("</content>").trim()
                        
                        emit(AgentResponse.UserConfirmationRequired(ToolAction(ToolType.CREATE_DOC, title, content)))
                        return@flow
                    } else {
                         turn++ 
                    }
                } else if (currentTurnResponse.contains("<edit_doc>")) {
                    val start = currentTurnResponse.indexOf("<edit_doc>")
                    val end = currentTurnResponse.indexOf("</edit_doc>")
                    if (start != -1 && end != -1) {
                        val toolContent = currentTurnResponse.substring(start + "<edit_doc>".length, end)
                        val title = toolContent.substringAfter("<title>").substringBefore("</title>").trim()
                        val content = toolContent.substringAfter("<content>").substringBefore("</content>").trim()
                        
                        // Fetch original content
                        val fullDocFileName = if (title.endsWith(".md")) title else "$title.md"
                        val originalContent = documentRepository.readDocument(fullDocFileName)

                        emit(AgentResponse.UserConfirmationRequired(ToolAction(ToolType.EDIT_DOC, title, content, originalContent)))
                        return@flow
                    } else {
                        turn++
                    }
                } else if (currentTurnResponse.contains("<delete_doc>")) {
                    val start = currentTurnResponse.indexOf("<delete_doc>")
                    val end = currentTurnResponse.indexOf("</delete_doc>")
                    if (start != -1 && end != -1) {
                        val toolContent = currentTurnResponse.substring(start + "<delete_doc>".length, end)
                        val title = toolContent.substringAfter("<title>").substringBefore("</title>").trim()
                        
                        emit(AgentResponse.UserConfirmationRequired(ToolAction(ToolType.DELETE_DOC, title)))
                        return@flow
                    } else {
                        turn++
                    }
                } else if (startIndex != -1 && endIndex != -1) {
                    val searchQuery = currentTurnResponse.substring(startIndex + searchTag.length, endIndex).trim()

                    if (previousSearchQueries.contains(searchQuery)) {
                        val systemMessage = "\nSystem: You have already searched for \"$searchQuery\". Please do not search for the same thing again. Extract the answer from the previous Observation or state that you cannot find it."
                        currentPrompt += "\n$currentTurnResponse$systemMessage"
                        displayedResponse += systemMessage
                        turn++
                        continue
                    }
                    previousSearchQueries.add(searchQuery)

                    var observation = ""
                    
                    // Smart Search: If query is a markdown file, try to read full content
                    if (searchQuery.endsWith(".md", ignoreCase = true)) {
                        val fullContent = documentRepository.readDocument(searchQuery)
                        if (fullContent != null) {
                            val truncatedContent = if (fullContent.length > 20000) {
                                fullContent.substring(0, 20000) + "\n...(truncated)"
                            } else {
                                fullContent
                            }
                            observation = "\nObservation:\n<search_results>\n"
                            observation += "<result>\n"
                            observation += "<file>$searchQuery</file>\n"
                            observation += "<content>\n$truncatedContent\n</content>\n"
                            observation += "</result>\n"
                            observation += "</search_results>"
                        }
                    }

                    // Fallback to Vector Search if not found or not a file
                    if (observation.isEmpty()) {
                        val queryEmbedding = sentenceEncoder.encodeText(searchQuery)
                        val results = chunksDB.getSimilarChunks(queryEmbedding, n = 10) // Increased to 10

                        observation = "\nObservation:\n<search_results>\n"
                        results.forEachIndexed { index, result ->
                            observation += "<result>\n"
                            observation += "<file>${result.second.docFileName}</file>\n"
                            observation += "<content>\n${result.second.chunkData}\n</content>\n"
                            observation += "</result>\n"
                            retrievedContextList.add(
                                RetrievedContext(
                                    result.second.docFileName,
                                    result.second.chunkData,
                                )
                            )
                        }
                        observation += "</search_results>"
                    }

                    currentPrompt += "\n$currentTurnResponse\n$observation\nSystem: Search results provided above. Do NOT search again. Answer the user query using the information above."
                    displayedResponse += observation
                    turn++
                } else if (currentTurnResponse.contains("<read_doc>")) {
                    val start = currentTurnResponse.indexOf("<read_doc>")
                    val end = currentTurnResponse.indexOf("</read_doc>")
                    if (start != -1 && end != -1) {
                        val toolContent = currentTurnResponse.substring(start + "<read_doc>".length, end)
                        val title = toolContent.substringAfter("<title>").substringBefore("</title>").trim()
                        
                        val fullDocFileName = if (title.endsWith(".md")) title else "$title.md"
                        val content = documentRepository.readDocument(fullDocFileName)

                        val observation = if (content != null) {
                             val truncatedContent = if (content.length > 20000) {
                                 content.substring(0, 20000) + "\n...(truncated)"
                             } else {
                                 content
                             }
                              "\nObservation: Content of '$fullDocFileName':\n$truncatedContent"
                         } else {
                              "\nObservation: File '$fullDocFileName' not found."
                         }

                        currentPrompt += "\n$currentTurnResponse\n$observation"
                        displayedResponse += observation
                        turn++
                    } else {
                        turn++
                    }
                } else if (currentTurnResponse.contains("<list_docs/>")) {
                    emit(AgentResponse.Status("Listing documents..."))
                    val documents = documentRepository.getAllDocuments().first()
                    val docListString = if (documents.isEmpty()) {
                        "No documents found."
                    } else {
                        documents.joinToString("\n") { "- ${it.docFileName}" }
                    }
                    val observation = "\nObservation:\n<file_list>\n$docListString\n</file_list>"
                    currentPrompt += "\n$currentTurnResponse\n$observation\nSystem: File list provided above."
                    displayedResponse += observation
                    turn++
                } else {
                    // Final answer found or no search tag
                    val finalAnswerTag = "Final Answer:"
                    val finalAnswer = if (currentTurnResponse.contains(finalAnswerTag)) {
                        currentTurnResponse.substringAfter(finalAnswerTag).trimStart()
                    } else {
                        currentTurnResponse // Fallback
                    }
                    
                    emit(AgentResponse.FinalAnswer(finalAnswer, retrievedContextList))
                    return@flow
                }
            }
            
            // Max turns reached
             val finalAnswerTag = "Final Answer:"
             val finalAnswer = if (displayedResponse.contains(finalAnswerTag)) {
                 displayedResponse.substringAfterLast(finalAnswerTag).trimStart()
             } else {
                 displayedResponse // Or some error message?
             }
            emit(AgentResponse.FinalAnswer(finalAnswer, retrievedContextList))

        } catch (e: Exception) {
            e.printStackTrace()
            emit(AgentResponse.Error(e.message ?: "Unknown error"))
        }
    }

    suspend fun performToolAction(action: ToolAction): String {
        return try {
            val result = when (action.type) {
                ToolType.CREATE_DOC -> documentRepository.createMarkdownDocument(action.title, action.content)
                ToolType.EDIT_DOC -> documentRepository.editMarkdownDocument(action.title, action.content)
                ToolType.DELETE_DOC -> documentRepository.deleteMarkdownDocument(action.title)
                ToolType.LIST_DOCS -> "Observation: Listed documents."
            }
            "Observation: $result"
        } catch (e: Exception) {
            "Observation: Error executing action: ${e.message}"
        }
    }

    fun checkNumDocuments(): Boolean = documentsDB.getDocsCount() > 0
    fun checkValidAPIKey(): Boolean = geminiAPIKey.getAPIKey() != null
    fun isLocalModelLoaded(): Boolean = liteRTAPI.isLoaded
}
