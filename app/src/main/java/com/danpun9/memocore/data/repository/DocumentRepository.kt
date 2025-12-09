package com.danpun9.memocore.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.danpun9.memocore.data.Chunk
import com.danpun9.memocore.data.ChunksDB
import com.danpun9.memocore.data.Document
import com.danpun9.memocore.data.DocumentsDB
import com.danpun9.memocore.domain.SentenceEmbeddingProvider
import com.danpun9.memocore.domain.WhiteSpaceSplitter
import com.danpun9.memocore.domain.readers.Readers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import kotlin.math.min

@Single
class DocumentRepository(
    private val context: Context,
    private val contentResolver: ContentResolver,
    private val documentsDB: DocumentsDB,
    private val chunksDB: ChunksDB,
    private val sentenceEncoder: SentenceEmbeddingProvider,
) {

    suspend fun addDocumentFromUri(
        uri: Uri,
        docType: Readers.DocumentType,
        onProgress: (String) -> Unit
    ) {
        var docFileName = ""
        contentResolver.query(uri, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst()) {
                docFileName = cursor.getString(nameIndex)
            }
        }
        
        contentResolver.openInputStream(uri)?.use { inputStream ->
            addChunksFromInputStream(docFileName, docType, inputStream, onProgress)
        }
    }

    suspend fun addDocumentFromUrl(
        url: String,
        docType: Readers.DocumentType,
        onProgress: (String) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connect()
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream

                    // 1. Try to get filename from Content-Disposition
                    var fileName = ""
                    val contentDisposition = connection.getHeaderField("Content-Disposition")
                    if (contentDisposition != null) {
                        val index = contentDisposition.indexOf("filename=")
                        if (index > 0) {
                            fileName = contentDisposition.substring(index + 9).replace("\"", "").trim()
                        }
                    }

                    // 2. Fallback to URL parsing
                    if (fileName.isEmpty()) {
                        fileName = getFileNameFromURL(url)
                    }

                    // 3. Fallback to default
                    if (fileName.isEmpty()) {
                        fileName = "downloaded_doc_${System.currentTimeMillis()}"
                    }

                    // 4. Detect type if UNKNOWN
                    val finalDocType = if (docType == Readers.DocumentType.UNKNOWN) {
                        when {
                            fileName.endsWith(".pdf", ignoreCase = true) -> Readers.DocumentType.PDF
                            fileName.endsWith(".docx", ignoreCase = true) -> Readers.DocumentType.MS_DOCX
                            fileName.endsWith(".md", ignoreCase = true) -> Readers.DocumentType.MARKDOWN
                            else -> Readers.DocumentType.PDF // Default to PDF
                        }
                    } else {
                        docType
                    }

                    // Save to cache
                    val file = File(context.cacheDir, fileName)
                    file.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    
                    val fileInputStream = file.inputStream()
                    addChunksFromInputStream(fileName, finalDocType, fileInputStream, onProgress)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun addMarkdownDocument(
        title: String,
        content: String,
        onProgress: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val fileName = if (title.endsWith(".md")) title else "$title.md"
            val file = File(context.filesDir, fileName)
            file.writeText(content)
            
            val inputStream = content.byteInputStream()
            addChunksFromInputStream(fileName, Readers.DocumentType.MARKDOWN, inputStream, onProgress)
        }
    }

    suspend fun updateMarkdownDocument(
        doc: Document,
        newContent: String,
        onProgress: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            // Update file
            val file = File(context.filesDir, doc.docFileName)
            file.writeText(newContent)

            // Update Document in DB
            val updatedDoc = doc.copy(docText = newContent)
            documentsDB.updateDocument(updatedDoc)

            // Re-index chunks
            chunksDB.removeChunks(doc.docId)
            
            onProgress("Creating chunks...")
            val chunks = WhiteSpaceSplitter.createChunks(
                newContent,
                chunkSize = 1000,
                chunkOverlap = 100,
            )
            
            onProgress("Adding chunks to database...")
            val size = chunks.size
            chunks.forEachIndexed { index, s ->
                onProgress("Added ${index + 1}/$size chunk(s) to database...")
                val embedding = sentenceEncoder.encodeText(s)
                chunksDB.addChunk(
                    Chunk(
                        docId = doc.docId,
                        docFileName = doc.docFileName,
                        chunkData = s,
                        chunkEmbedding = embedding,
                    ),
                )
            }
        }
    }

    fun removeDocument(docId: Long) {
        documentsDB.removeDocument(docId)
        chunksDB.removeChunks(docId)
    }

    suspend fun readDocument(fileName: String): String? {
        return withContext(Dispatchers.IO) {
            documentsDB.getDocumentByFileName(fileName)?.docText
        }
    }

    suspend fun createMarkdownDocument(title: String, content: String): String {
        return withContext(Dispatchers.IO) {
            val fileName = if (title.endsWith(".md")) title else "$title.md"
            
            // Check if exists
            val existingDoc = documentsDB.getDocumentByFileName(fileName)
            if (existingDoc != null) {
                throw Exception("Document '$fileName' already exists.")
            }

            val file = File(context.filesDir, fileName)
            file.writeText(content)
            
            val inputStream = content.byteInputStream()
            addChunksFromInputStream(fileName, Readers.DocumentType.MARKDOWN, inputStream) { }
            "Document '$fileName' created successfully."
        }
    }

    suspend fun editMarkdownDocument(title: String, newContent: String): String {
        return withContext(Dispatchers.IO) {
            val fileName = if (title.endsWith(".md")) title else "$title.md"
            
            val doc = documentsDB.getDocumentByFileName(fileName) 
                ?: throw Exception("Document '$fileName' not found.")

            // Update file
            val file = File(context.filesDir, fileName)
            file.writeText(newContent)

            // Update Document in DB
            val updatedDoc = doc.copy(docText = newContent)
            documentsDB.updateDocument(updatedDoc)

            // Re-index chunks
            chunksDB.removeChunks(doc.docId)
            
            val chunks = WhiteSpaceSplitter.createChunks(
                newContent,
                chunkSize = 1000,
                chunkOverlap = 100,
            )
            
            chunks.forEach { s ->
                val embedding = sentenceEncoder.encodeText(s)
                chunksDB.addChunk(
                    Chunk(
                        docId = doc.docId,
                        docFileName = doc.docFileName,
                        chunkData = s,
                        chunkEmbedding = embedding,
                    ),
                )
            }
            "Document '$fileName' updated successfully."
        }
    }

    suspend fun deleteMarkdownDocument(title: String): String {
        return withContext(Dispatchers.IO) {
            val fileName = if (title.endsWith(".md")) title else "$title.md"
            val doc = documentsDB.getDocumentByFileName(fileName) 
                ?: throw Exception("Document '$fileName' not found.")

            removeDocument(doc.docId)
            
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                file.delete()
            }
            "Document '$fileName' deleted successfully."
        }
    }

    fun getAllDocuments() = documentsDB.getAllDocuments()

    private suspend fun addChunksFromInputStream(
        docFileName: String,
        docType: Readers.DocumentType,
        inputStream: InputStream,
        onProgress: (String) -> Unit
    ) {
        val text = Readers.getReaderForDocType(docType)
            .readFromInputStream(inputStream)
            ?: return

        val newDocId = documentsDB.addDocument(
            Document(
                docText = text,
                docFileName = docFileName,
                docAddedTime = System.currentTimeMillis(),
            ),
        )
        
        onProgress("Creating chunks...")
        val chunks = WhiteSpaceSplitter.createChunks(
            text,
            chunkSize = 1000,
            chunkOverlap = 100,
        )
        
        onProgress("Adding chunks to database...")
        val size = chunks.size
        chunks.forEachIndexed { index, s ->
            onProgress("Added ${index + 1}/$size chunk(s) to database...")
            val embedding = sentenceEncoder.encodeText(s)
            chunksDB.addChunk(
                Chunk(
                    docId = newDocId,
                    docFileName = docFileName,
                    chunkData = s,
                    chunkEmbedding = embedding,
                ),
            )
        }
        
        withContext(Dispatchers.IO) {
            inputStream.close()
        }
    }

    private fun getFileNameFromURL(url: String?): String {
        if (url == null) {
            return ""
        }
        try {
            val resource = URL(url)
            val host = resource.host
            if (host.isNotEmpty() && url.endsWith(host)) {
                return ""
            }
        } catch (e: MalformedURLException) {
            return ""
        }
        val startIndex = url.lastIndexOf('/') + 1
        val length = url.length
        var lastQMPos = url.lastIndexOf('?')
        if (lastQMPos == -1) {
            lastQMPos = length
        }
        var lastHashPos = url.lastIndexOf('#')
        if (lastHashPos == -1) {
            lastHashPos = length
        }
        val endIndex = min(lastQMPos.toDouble(), lastHashPos.toDouble()).toInt()
        return url.substring(startIndex, endIndex)
    }
}
