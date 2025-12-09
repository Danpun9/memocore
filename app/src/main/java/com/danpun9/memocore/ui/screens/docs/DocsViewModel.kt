package com.danpun9.memocore.ui.screens.docs

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danpun9.memocore.data.Document
import com.danpun9.memocore.data.repository.DocumentRepository
import com.danpun9.memocore.domain.readers.Readers
import hideProgressDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel
import setProgressDialogText
import showProgressDialog

sealed interface DocsScreenUIEvent {
    data class OnDocSelected(
        val fileUri: Uri,
        val docType: Readers.DocumentType,
    ) : DocsScreenUIEvent

    data class OnDocURLSubmitted(
        val context: Context,
        val url: String,
        val docType: Readers.DocumentType,
    ) : DocsScreenUIEvent

    data class OnRemoveDoc(
        val docId: Long,
    ) : DocsScreenUIEvent

    data object OnAddMarkdownClick : DocsScreenUIEvent
    data object OnAddMarkdownDismiss : DocsScreenUIEvent
    data class OnAddMarkdownConfirm(val title: String, val content: String) : DocsScreenUIEvent
    data class OnUpdateMarkdownDoc(val doc: Document, val newContent: String) : DocsScreenUIEvent
}

enum class DocDownloadState {
    DOWNLOAD_NONE,
    DOWNLOAD_IN_PROGRESS,
    DOWNLOAD_SUCCESS,
    DOWNLOAD_FAILURE,
}

data class DocsScreenUIState(
    val documents: List<Document> = emptyList(),
    val docDownloadState: DocDownloadState = DocDownloadState.DOWNLOAD_NONE,
    val showAddMarkdownDialog: Boolean = false,
)

@KoinViewModel
class DocsViewModel(
    private val context: Context,
    private val repository: DocumentRepository,
) : ViewModel() {
    private val _docsScreenUIState = MutableStateFlow(DocsScreenUIState())
    val docsScreenUIState: StateFlow<DocsScreenUIState> = _docsScreenUIState

    init {
        viewModelScope.launch {
            repository.getAllDocuments().collect {
                _docsScreenUIState.value = _docsScreenUIState.value.copy(documents = it)
            }
        }
    }

    fun onEvent(event: DocsScreenUIEvent) {
        when (event) {
            is DocsScreenUIEvent.OnDocSelected -> {
                showProgressDialog()
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        repository.addDocumentFromUri(
                            event.fileUri,
                            event.docType
                        ) { progress ->
                            setProgressDialogText(progress)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to add document", Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        withContext(Dispatchers.Main) {
                            hideProgressDialog()
                        }
                    }
                }
            }

            is DocsScreenUIEvent.OnDocURLSubmitted -> {
                showProgressDialog()
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val success = repository.addDocumentFromUrl(
                            event.url,
                            event.docType
                        ) { progress ->
                            setProgressDialogText(progress)
                        }

                        withContext(Dispatchers.Main) {
                            if (success) {
                                _docsScreenUIState.value =
                                    _docsScreenUIState.value.copy(
                                        docDownloadState = DocDownloadState.DOWNLOAD_SUCCESS,
                                    )
                            } else {
                                _docsScreenUIState.value =
                                    _docsScreenUIState.value.copy(
                                        docDownloadState = DocDownloadState.DOWNLOAD_FAILURE,
                                    )
                            }
                            hideProgressDialog()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            _docsScreenUIState.value =
                                _docsScreenUIState.value.copy(
                                    docDownloadState = DocDownloadState.DOWNLOAD_FAILURE,
                                )
                            hideProgressDialog()
                        }
                    }
                }
            }

            is DocsScreenUIEvent.OnRemoveDoc -> {
                repository.removeDocument(event.docId)
            }

            DocsScreenUIEvent.OnAddMarkdownClick -> {
                _docsScreenUIState.value = _docsScreenUIState.value.copy(showAddMarkdownDialog = true)
            }

            DocsScreenUIEvent.OnAddMarkdownDismiss -> {
                _docsScreenUIState.value = _docsScreenUIState.value.copy(showAddMarkdownDialog = false)
            }

            is DocsScreenUIEvent.OnAddMarkdownConfirm -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        withContext(Dispatchers.Main) {
                            showProgressDialog()
                        }
                        repository.addMarkdownDocument(event.title, event.content) { progress ->
                            setProgressDialogText(progress)
                        }
                        withContext(Dispatchers.Main) {
                            _docsScreenUIState.value = _docsScreenUIState.value.copy(showAddMarkdownDialog = false)
                            hideProgressDialog()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to add Markdown document", Toast.LENGTH_SHORT).show()
                            hideProgressDialog()
                        }
                    }
                }
            }

            is DocsScreenUIEvent.OnUpdateMarkdownDoc -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        withContext(Dispatchers.Main) {
                            showProgressDialog()
                        }
                        repository.updateMarkdownDocument(event.doc, event.newContent) { progress ->
                            setProgressDialogText(progress)
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Document updated", Toast.LENGTH_SHORT).show()
                            hideProgressDialog()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Failed to update document", Toast.LENGTH_SHORT).show()
                            hideProgressDialog()
                        }
                    }
                }
            }
        }
    }
}
