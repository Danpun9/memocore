package com.danpun9.memocore.ui.screens.chat

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danpun9.memocore.data.RetrievedContext
import com.danpun9.memocore.data.UserPreferences
import com.danpun9.memocore.domain.agent.AgentResponse
import com.danpun9.memocore.domain.agent.ChatAgent
import com.danpun9.memocore.ui.components.createAlertDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

sealed interface ChatScreenUIEvent {
    data object OnEditCredentialsClick : ChatScreenUIEvent

    data object OnOpenDocsClick : ChatScreenUIEvent

    data object OnLocalModelsClick : ChatScreenUIEvent

    data object OnSettingsClick : ChatScreenUIEvent

    data object OnResetChatClick : ChatScreenUIEvent

    sealed class ResponseGeneration {
        data class Start(
            val query: String,
            val prompt: String,
        ) : ChatScreenUIEvent

        data class StopWithSuccess(
            val response: String,
            val retrievedContextList: List<RetrievedContext>,
        ) : ChatScreenUIEvent

        data class StopWithError(
            val errorMessage: String,
        ) : ChatScreenUIEvent
    }

    data object OnConfirmToolAction : ChatScreenUIEvent
    data object OnRejectToolAction : ChatScreenUIEvent
}

sealed interface ChatNavEvent {
    data object None : ChatNavEvent

    data object ToEditAPIKeyScreen : ChatNavEvent

    data object ToDocsScreen : ChatNavEvent

    data object ToLocalModelsScreen : ChatNavEvent

    data object ToSettingsScreen : ChatNavEvent
}

data class ChatScreenUIState(
    val messages: List<ChatAgent.ChatMessage> = emptyList(),
    val isGeneratingResponse: Boolean = false,
    val pendingToolAction: com.danpun9.memocore.domain.agent.ToolAction? = null,
)

@KoinViewModel
class ChatViewModel(
    private val context: Context,
    private val chatAgent: ChatAgent,
    private val userPreferences: UserPreferences,
) : ViewModel() {
    init {
        chatAgent.loadLocalModel()
    }
    private val _chatScreenUIState = MutableStateFlow(ChatScreenUIState())
    val chatScreenUIState: StateFlow<ChatScreenUIState> = _chatScreenUIState

    private val _navEventChannel = Channel<ChatNavEvent>()
    val navEventChannel = _navEventChannel.receiveAsFlow()

    fun onChatScreenEvent(event: ChatScreenUIEvent) {
        when (event) {
            is ChatScreenUIEvent.ResponseGeneration.Start -> {
                if (!chatAgent.checkNumDocuments()) {
                    Toast
                        .makeText(
                            context,
                            "Add documents to execute queries",
                            Toast.LENGTH_LONG,
                        ).show()
                    return
                }

                val modelType = userPreferences.getModelType()

                if (modelType == com.danpun9.memocore.data.ModelType.GEMINI) {
                    if (!chatAgent.checkValidAPIKey()) {
                        createAlertDialog(
                            dialogTitle = "Invalid API Key",
                            dialogText = "Please enter a Gemini API key to use a LLM for generating responses.",
                            dialogPositiveButtonText = "Add API key",
                            onPositiveButtonClick = {
                                onChatScreenEvent(ChatScreenUIEvent.OnEditCredentialsClick)
                            },
                            dialogNegativeButtonText = "Open Gemini Console",
                            onNegativeButtonClick = {
                                Intent(Intent.ACTION_VIEW).apply {
                                    data = "https://aistudio.google.com/apikey".toUri()
                                    context.startActivity(this)
                                }
                            },
                        )
                        return
                    }
                } else {
                    // Local Model
                    if (!chatAgent.isLocalModelLoaded()) {
                        Toast.makeText(context, "Local model not loaded. Please download it in Settings.", Toast.LENGTH_LONG).show()
                        // Optionally navigate to local models screen
                        onChatScreenEvent(ChatScreenUIEvent.OnLocalModelsClick)
                        return
                    }
                }

                if (event.query.trim().isEmpty()) {
                    Toast
                        .makeText(context, "Enter a query to execute", Toast.LENGTH_LONG)
                        .show()
                    return
                }

                // Add User Message
                val userMessage = ChatAgent.ChatMessage(ChatAgent.ChatRole.USER, event.query)
                val currentMessages = _chatScreenUIState.value.messages.toMutableList()
                currentMessages.add(userMessage)
                
                // Add Placeholder Model Message
                val placeholderModelMessage = ChatAgent.ChatMessage(ChatAgent.ChatRole.MODEL, "", isThinking = true)
                currentMessages.add(placeholderModelMessage)

                _chatScreenUIState.value = _chatScreenUIState.value.copy(
                    messages = currentMessages,
                    isGeneratingResponse = true
                )

                viewModelScope.launch(Dispatchers.IO) {
                    // Pass history excluding the last two messages (User query + Placeholder)
                    val history = currentMessages.dropLast(2)
                    
                    chatAgent.generateResponse(history, event.query).collect { response ->
                        when (response) {
                            is AgentResponse.Streaming -> {
                                updateLastMessageContent(response.content, isThinking = false)
                            }
                            is AgentResponse.Status -> {
                                updateLastMessageContent(response.message, isThinking = true)
                            }
                            is AgentResponse.FinalAnswer -> {
                                onChatScreenEvent(
                                    ChatScreenUIEvent.ResponseGeneration.StopWithSuccess(
                                        response.answer,
                                        response.context
                                    )
                                )
                            }
                            is AgentResponse.Error -> {
                                onChatScreenEvent(ChatScreenUIEvent.ResponseGeneration.StopWithError(response.message))
                            }
                            is AgentResponse.UserConfirmationRequired -> {
                                _chatScreenUIState.value = _chatScreenUIState.value.copy(
                                    isGeneratingResponse = false,
                                    pendingToolAction = response.action
                                )
                            }
                        }
                    }
                }
            }

            is ChatScreenUIEvent.ResponseGeneration.StopWithSuccess -> {
                updateLastMessageContent(event.response, isThinking = false, sources = event.retrievedContextList)
                _chatScreenUIState.value = _chatScreenUIState.value.copy(isGeneratingResponse = false)
            }

            is ChatScreenUIEvent.ResponseGeneration.StopWithError -> {
                _chatScreenUIState.value = _chatScreenUIState.value.copy(isGeneratingResponse = false)
                // Remove the placeholder message or show error?
                // For now, let's just show the error toast and maybe update the message to show error
                 updateLastMessageContent("Error: ${event.errorMessage}", isThinking = false)
                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(context, event.errorMessage, Toast.LENGTH_LONG).show()
                }
            }

            is ChatScreenUIEvent.OnOpenDocsClick -> {
                viewModelScope.launch {
                    _navEventChannel.send(ChatNavEvent.ToDocsScreen)
                }
            }

            is ChatScreenUIEvent.OnEditCredentialsClick -> {
                viewModelScope.launch {
                    _navEventChannel.send(ChatNavEvent.ToEditAPIKeyScreen)
                }
            }

            is ChatScreenUIEvent.OnLocalModelsClick -> {
                viewModelScope.launch {
                    _navEventChannel.send(ChatNavEvent.ToLocalModelsScreen)
                }
            }

            is ChatScreenUIEvent.OnSettingsClick -> {
                viewModelScope.launch {
                    _navEventChannel.send(ChatNavEvent.ToSettingsScreen)
                }
            }

            is ChatScreenUIEvent.OnResetChatClick -> {
                _chatScreenUIState.value = ChatScreenUIState()
            }

            is ChatScreenUIEvent.OnConfirmToolAction -> {
                val action = _chatScreenUIState.value.pendingToolAction ?: return
                _chatScreenUIState.value = _chatScreenUIState.value.copy(pendingToolAction = null, isGeneratingResponse = true)
                
                viewModelScope.launch(Dispatchers.IO) {
                    val observation = chatAgent.performToolAction(action)

                    val currentMessages = _chatScreenUIState.value.messages.toMutableList()
                    currentMessages.add(ChatAgent.ChatMessage(ChatAgent.ChatRole.SYSTEM, observation))

                    currentMessages.add(ChatAgent.ChatMessage(ChatAgent.ChatRole.MODEL, "", isThinking = true))
                    
                    _chatScreenUIState.value = _chatScreenUIState.value.copy(messages = currentMessages)

                    val history = currentMessages.dropLast(2)
                    val continueQuery = "Action executed successfully. The file has been updated. Do not verify. Do not use tools. Provide the Final Answer now."
                     
                     chatAgent.generateResponse(currentMessages.dropLast(1), continueQuery, isSystemQuery = true).collect { response ->
                        // Handle response same as above
                         when (response) {
                            is AgentResponse.Streaming -> {
                                updateLastMessageContent(response.content, isThinking = false)
                            }
                            is AgentResponse.Status -> {
                                updateLastMessageContent(response.message, isThinking = true)
                            }
                            is AgentResponse.FinalAnswer -> {
                                onChatScreenEvent(
                                    ChatScreenUIEvent.ResponseGeneration.StopWithSuccess(
                                        response.answer,
                                        response.context
                                    )
                                )
                            }
                            is AgentResponse.Error -> {
                                onChatScreenEvent(ChatScreenUIEvent.ResponseGeneration.StopWithError(response.message))
                            }
                            is AgentResponse.UserConfirmationRequired -> {
                                _chatScreenUIState.value = _chatScreenUIState.value.copy(
                                    isGeneratingResponse = false,
                                    pendingToolAction = response.action
                                )
                            }
                        }
                    }
                }
            }

            is ChatScreenUIEvent.OnRejectToolAction -> {
                 _chatScreenUIState.value = _chatScreenUIState.value.copy(pendingToolAction = null, isGeneratingResponse = true)
                 
                 viewModelScope.launch(Dispatchers.IO) {
                    val observation = "\nObservation: User rejected the action."

                    val currentMessages = _chatScreenUIState.value.messages.toMutableList()
                    currentMessages.add(ChatAgent.ChatMessage(ChatAgent.ChatRole.SYSTEM, observation))

                    currentMessages.add(ChatAgent.ChatMessage(ChatAgent.ChatRole.MODEL, "", isThinking = true))
                    
                    _chatScreenUIState.value = _chatScreenUIState.value.copy(messages = currentMessages)

                    val continueQuery = "User rejected the action. Do not attempt to edit again. Do not use tools. Ask for new instructions."
                    
                    chatAgent.generateResponse(currentMessages.dropLast(1), continueQuery, isSystemQuery = true).collect { response ->
                         when (response) {
                            is AgentResponse.Streaming -> {
                                updateLastMessageContent(response.content, isThinking = false)
                            }
                            is AgentResponse.Status -> {
                                updateLastMessageContent(response.message, isThinking = true)
                            }
                            is AgentResponse.FinalAnswer -> {
                                onChatScreenEvent(
                                    ChatScreenUIEvent.ResponseGeneration.StopWithSuccess(
                                        response.answer,
                                        response.context
                                    )
                                )
                            }
                            is AgentResponse.Error -> {
                                onChatScreenEvent(ChatScreenUIEvent.ResponseGeneration.StopWithError(response.message))
                            }
                            is AgentResponse.UserConfirmationRequired -> {
                                _chatScreenUIState.value = _chatScreenUIState.value.copy(
                                    isGeneratingResponse = false,
                                    pendingToolAction = response.action
                                )
                            }
                        }
                    }
                 }
            }
        }
    }

    private fun updateLastMessageContent(
        newContent: String, 
        isThinking: Boolean, 
        sources: List<RetrievedContext> = emptyList()
    ) {
        val currentMessages = _chatScreenUIState.value.messages.toMutableList()
        if (currentMessages.isNotEmpty()) {
            val lastIndex = currentMessages.lastIndex
            val lastMessage = currentMessages[lastIndex]
            if (lastMessage.role == ChatAgent.ChatRole.MODEL) {
                currentMessages[lastIndex] = lastMessage.copy(
                    content = newContent, 
                    isThinking = isThinking,
                    sources = sources
                )
                _chatScreenUIState.value = _chatScreenUIState.value.copy(messages = currentMessages)
            }
        }
    }
}
