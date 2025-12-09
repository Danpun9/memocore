package com.danpun9.memocore.ui.screens.chat

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danpun9.memocore.ui.components.AppAlertDialog
import com.danpun9.memocore.ui.components.createAlertDialog
import com.danpun9.memocore.ui.theme.DocQATheme
import com.danpun9.memocore.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.rememberLazyListState
import com.danpun9.memocore.domain.agent.ChatAgent
import com.danpun9.memocore.data.RetrievedContext
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import dev.jeziellago.compose.markdowntext.MarkdownText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    screenUiState: ChatScreenUIState,
    onScreenEvent: (ChatScreenUIEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Chat",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                actions = {
                    IconButton(onClick = {
                        onScreenEvent(ChatScreenUIEvent.OnResetChatClick)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Chat",
                        )
                    }
                    var moreOptionsVisible by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = {
                            moreOptionsVisible = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                            )
                        }
                        ChatScreenMoreOptionsPopup(
                            expanded = moreOptionsVisible,
                            onDismissRequest = { moreOptionsVisible = !moreOptionsVisible },
                            onItemClick = { onScreenEvent(it) },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
                    .fillMaxWidth(),
        ) {
            Column {
                QALayout(screenUiState)
                Spacer(modifier = Modifier.height(8.dp))
                QueryInput(onScreenEvent)
            }
        }
        
        AppAlertDialog()
        
        if (screenUiState.pendingToolAction != null) {
            val action = screenUiState.pendingToolAction
            val title = when (action.type) {
                com.danpun9.memocore.domain.agent.ToolType.CREATE_DOC -> "Create Document?"
                com.danpun9.memocore.domain.agent.ToolType.EDIT_DOC -> "Edit Document?"
                com.danpun9.memocore.domain.agent.ToolType.DELETE_DOC -> "Delete Document?"
                com.danpun9.memocore.domain.agent.ToolType.LIST_DOCS -> "List Documents?"
            }
            val text = when (action.type) {
                com.danpun9.memocore.domain.agent.ToolType.CREATE_DOC -> "Allow agent to create '${action.title}'?"
                com.danpun9.memocore.domain.agent.ToolType.EDIT_DOC -> "Allow agent to edit '${action.title}'?"
                com.danpun9.memocore.domain.agent.ToolType.DELETE_DOC -> "Allow agent to delete '${action.title}'?"
                com.danpun9.memocore.domain.agent.ToolType.LIST_DOCS -> "Allow agent to list documents?"
            }

            androidx.compose.material3.AlertDialog(
                onDismissRequest = { onScreenEvent(ChatScreenUIEvent.OnRejectToolAction) },
                title = { Text(title) },
                text = {
                    Column {
                        Text(text)
                        if (action.type == com.danpun9.memocore.domain.agent.ToolType.EDIT_DOC && action.originalContent != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Changes:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                com.danpun9.memocore.ui.components.TextDiffViewer(
                                    oldText = action.originalContent,
                                    newText = action.content
                                )
                            }
                        } else if (action.content.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Preview:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = action.content,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { onScreenEvent(ChatScreenUIEvent.OnConfirmToolAction) }
                    ) {
                        Text("Allow")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { onScreenEvent(ChatScreenUIEvent.OnRejectToolAction) }
                    ) {
                        Text("Deny")
                    }
                }
            )
        }
    }
}


@Composable
private fun ColumnScope.QALayout(screenUiState: ChatScreenUIState) {
    val context = LocalContext.current
    val scrollState = rememberLazyListState()

    LaunchedEffect(screenUiState.messages.size, screenUiState.messages.lastOrNull()?.content) {
        if (screenUiState.messages.isNotEmpty()) {
            scrollState.animateScrollToItem(screenUiState.messages.size - 1)
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .weight(1f),
    ) {
        if (screenUiState.messages.isEmpty()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    modifier = Modifier.size(75.dp),
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.LightGray,
                )
                Text(
                    text = "",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray,
                )
            }
        } else {
            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
            ) {
                items(screenUiState.messages.size) { index ->
                    val message = screenUiState.messages[index]
                    MessageItem(message)
                }
            }
        }
    }
}

@Composable
fun MessageItem(message: ChatAgent.ChatMessage) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        if (message.role == ChatAgent.ChatRole.USER) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
        } else {
            // Model Message
            Column(
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                        .padding(24.dp)
                        .fillMaxWidth(),
            ) {
                if (message.isThinking) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(modifier = Modifier.width(100.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = message.content.ifEmpty { "Thinking..." },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    MarkdownText(
                        modifier = Modifier.fillMaxWidth(),
                        markdown = message.content,
                        style =
                            TextStyle(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                            ),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        IconButton(
                            onClick = {
                                val sendIntent: Intent =
                                    Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, message.content)
                                        type = "text/plain"
                                    }
                                val shareIntent = Intent.createChooser(sendIntent, null)
                                context.startActivity(shareIntent)
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share the response",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            
            if (message.sources.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                CollapsibleSourceList(message.sources)
            }
        }
    }
}

@Composable
fun CollapsibleSourceList(sources: List<RetrievedContext>) {
    // Group sources by file name
    val groupedSources = remember(sources) { sources.groupBy { it.fileName } }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Context", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        
        groupedSources.forEach { (fileName, contexts) ->
            CollapsibleSourceItem(fileName, contexts)
        }
    }
}

@Composable
fun CollapsibleSourceItem(fileName: String, contexts: List<RetrievedContext>) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (isExpanded) androidx.compose.material.icons.Icons.Default.KeyboardArrowUp else androidx.compose.material.icons.Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        
        if (isExpanded) {
            Spacer(modifier = Modifier.height(8.dp))
            contexts.forEach { context ->
                Text(
                    text = "\"${context.context}\"",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun QueryInput(onEvent: (ChatScreenUIEvent) -> Unit) {
    var questionText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextField(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            value = questionText,
            onValueChange = { questionText = it },
            shape = RoundedCornerShape(16.dp),
            colors =
                TextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledTextColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            placeholder = { Text(text = "Ask Memocore...") },
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape),
            onClick = {
                keyboardController?.hide()

                try {
                    onEvent(
                        ChatScreenUIEvent.ResponseGeneration.Start(
                            questionText,
                            context.getString(R.string.prompt_1),
                        ),
                    )
                    questionText = "" // Clear input field
                } catch (e: Exception) {
                    createAlertDialog(
                        dialogTitle = "Error",
                        dialogText = "An error occurred while generating the response: ${e.message}",
                        dialogPositiveButtonText = "Close",
                        onPositiveButtonClick = {},
                        dialogNegativeButtonText = null,
                        onNegativeButtonClick = {},
                    )
                }
            },
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Send query",
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
@Preview
private fun ChatScreenPreview() {
    ChatScreen(
        screenUiState = ChatScreenUIState(),
        onScreenEvent = { },
    )
}
