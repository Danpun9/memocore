package com.danpun9.memocore.ui.screens.docs

import AppProgressDialog
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.danpun9.memocore.data.Document
import com.danpun9.memocore.domain.readers.Readers
import com.danpun9.memocore.ui.components.AppAlertDialog
import com.danpun9.memocore.ui.components.createAlertDialog
import com.danpun9.memocore.ui.theme.DocQATheme
import dev.jeziellago.compose.markdowntext.MarkdownText

private val showDocDetailDialog = mutableStateOf(false)
private val dialogDoc = mutableStateOf<Document?>(null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocsScreen(
    uiState: DocsScreenUIState,
    onBackClick: (() -> Unit),
    onEvent: (DocsScreenUIEvent) -> Unit,
) {
    val context = LocalContext.current
    var docType by remember { mutableStateOf(Readers.DocumentType.PDF) }
    var pdfUrl by remember { mutableStateOf("") }
    var showUrlDialog by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) {
            it.data?.data?.let { uri ->
                onEvent(DocsScreenUIEvent.OnDocSelected(uri, docType))
            }
        }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Manage Documents",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Navigate Back",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            DocsExpandableFab(
                isExpanded = isFabExpanded,
                onToggle = { isFabExpanded = !isFabExpanded },
                onPdfClick = {
                    isFabExpanded = false
                    docType = Readers.DocumentType.PDF
                    launcher.launch(
                        Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/pdf" },
                    )
                },
                onDocxClick = {
                    isFabExpanded = false
                    docType = Readers.DocumentType.MS_DOCX
                    launcher.launch(
                        Intent(Intent.ACTION_GET_CONTENT).apply {
                            type =
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                        },
                    )
                },
                onUrlClick = {
                    isFabExpanded = false
                    showUrlDialog = true
                },
                onMdClick = {
                    isFabExpanded = false
                    onEvent(DocsScreenUIEvent.OnAddMarkdownClick)
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxWidth()) {
            Spacer(modifier = Modifier.height(12.dp))
            DocsList(uiState.documents, onEvent)
            
            // Handle Download State
            when (uiState.docDownloadState) {
                DocDownloadState.DOWNLOAD_NONE -> {}
                DocDownloadState.DOWNLOAD_IN_PROGRESS -> {
                    showUrlDialog = false
                }
                DocDownloadState.DOWNLOAD_SUCCESS -> {
                    Toast.makeText(context, "Document added from URL", Toast.LENGTH_SHORT).show()
                }
                DocDownloadState.DOWNLOAD_FAILURE -> {
                    Toast.makeText(context, "Failed to download", Toast.LENGTH_SHORT).show()
                }
            }

            AppProgressDialog()
            AppAlertDialog()
            DocDetailDialog(onEvent)
            
            if (uiState.showAddMarkdownDialog) {
                AddMarkdownDialog(
                    onDismiss = { onEvent(DocsScreenUIEvent.OnAddMarkdownDismiss) },
                    onConfirm = { title, content -> 
                        onEvent(DocsScreenUIEvent.OnAddMarkdownConfirm(title, content)) 
                    }
                )
            }

            // URL Dialog
            if (showUrlDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showUrlDialog = false
                        pdfUrl = ""
                    },
                    title = {
                        Column {
                            Text("Add document from URL", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "The app will determine the type of the document using the file-extension of the downloaded " +
                                    "document",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                    text = {
                        Column {
                            TextField(
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                value = pdfUrl,
                                onValueChange = { pdfUrl = it },
                                label = { Text("Enter URL") },
                            )
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (pdfUrl.isNotBlank()) {
                                onEvent(DocsScreenUIEvent.OnDocURLSubmitted(context, pdfUrl, Readers.DocumentType.UNKNOWN))
                            }
                        }) {
                            Text("Add")
                        }
                    },
                    dismissButton = {
                        Button(onClick = { showUrlDialog = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.DocsList(
    docs: List<Document>,
    onEvent: (DocsScreenUIEvent) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
        items(docs) { doc ->
            DocsListItem(
                doc,
                onRemoveDocClick = { docId -> onEvent(DocsScreenUIEvent.OnRemoveDoc(docId)) },
            )
        }
    }
}

@Composable
private fun DocsListItem(
    document: Document,
    onRemoveDocClick: ((Long) -> Unit),
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    dialogDoc.value = document
                    showDocDetailDialog.value = true
                }.background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Text(
                text = document.docFileName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text =
                    if (document.docText.length > 200) {
                        document.docText.substring(0, 200).replace("\n", "") + " ..."
                    } else {
                        document.docText.replace("\n", "")
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = DateUtils.getRelativeTimeSpanString(document.docAddedTime).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            modifier =
                Modifier.clickable {
                    createAlertDialog(
                        dialogTitle = "Remove document",
                        dialogText =
                            "Are you sure to remove this document from the database. Responses to " +
                                    "further queries will not refer content from this document.",
                        dialogPositiveButtonText = "Remove",
                        onPositiveButtonClick = { onRemoveDocClick(document.docId) },
                        dialogNegativeButtonText = "Cancel",
                        onNegativeButtonClick = {},
                    )
                },
            imageVector = Icons.Default.Clear,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            contentDescription = "Remove this document",
        )
        Spacer(modifier = Modifier.width(2.dp))
    }
}

@Composable
fun DocsExpandableFab(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onPdfClick: () -> Unit,
    onDocxClick: () -> Unit,
    onUrlClick: () -> Unit,
    onMdClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (isExpanded) {
            ExtendedFloatingActionButton(
                onClick = onPdfClick,
                containerColor = Color(0xFFF40F02),
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("PDF") }
            )
            ExtendedFloatingActionButton(
                onClick = onDocxClick,
                containerColor = Color(0xFF2B579A),
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("DOCX") }
            )
//            ExtendedFloatingActionButton(
//                onClick = onUrlClick,
//                containerColor = Color(0xFF643A71),
//                contentColor = Color.White,
//                icon = { Icon(Icons.Default.Link, contentDescription = null) },
//                text = { Text("URL") }
//            )
            ExtendedFloatingActionButton(
                onClick = onMdClick,
                containerColor = Color(0xFF333333),
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("MD") }
            )
        }

        FloatingActionButton(
            onClick = onToggle,
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = "Add Document"
            )
        }
    }
}

@Composable
private fun DocDetailDialog(onEvent: (DocsScreenUIEvent) -> Unit) {
    var isVisible by remember { showDocDetailDialog }
    val context = LocalContext.current
    val doc by remember { dialogDoc }
    var showEditDialog by remember { mutableStateOf(false) }

    if (isVisible && doc != null) {
        Dialog(onDismissRequest = { }) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(600.dp)
                        .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp))
                        .padding(16.dp),
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    // Header: Title and Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = doc?.docFileName ?: "",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isMarkdown = doc?.docFileName?.endsWith(".md") == true
                            
                            // Share Button
                            IconButton(onClick = {
                                val sendIntent: Intent =
                                    Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, doc?.docText)
                                        type = "text/plain"
                                    }
                                val shareIntent = Intent.createChooser(sendIntent, null)
                                context.startActivity(shareIntent)
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }


                            if (isMarkdown) {
                                IconButton(onClick = { showEditDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }


                            IconButton(onClick = { isVisible = false }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val isMarkdown = doc?.docFileName?.endsWith(".md") == true
                    
                    Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                         if (isMarkdown) {
                            MarkdownText(
                                markdown = doc?.docText ?: "",
                                style = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface),
                            )
                        } else {
                            Text(
                                text = doc?.docText ?: "",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
        
        if (showEditDialog) {
            EditMarkdownDialog(
                doc = doc!!,
                onDismiss = { showEditDialog = false },
                onConfirm = { newContent ->
                    onEvent(DocsScreenUIEvent.OnUpdateMarkdownDoc(doc!!, newContent))
                    showEditDialog = false
                    isVisible = false 
                }
            )
        }
    }
}

@Composable
fun AddMarkdownDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Markdown Note") },
        text = {
            Column {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content (Markdown)") },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    maxLines = 10
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (title.isNotBlank() && content.isNotBlank()) {
                    onConfirm(title, content)
                }
            }) {
                Text("Add")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditMarkdownDialog(
    doc: Document,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var content by remember { mutableStateOf(doc.docText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Markdown Note") },
        text = {
            Column {
                TextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content (Markdown)") },
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    maxLines = 15
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (content.isNotBlank()) {
                    onConfirm(content)
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
