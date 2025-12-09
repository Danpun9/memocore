package com.danpun9.memocore.domain.readers

class Readers {
    enum class DocumentType {
        PDF,
        MS_DOCX,
        MARKDOWN,
        UNKNOWN,
    }

    companion object {
        fun getReaderForDocType(docType: DocumentType): Reader =
            when (docType) {
                DocumentType.PDF -> PDFReader()
                DocumentType.MS_DOCX -> DOCXReader()
                DocumentType.MARKDOWN -> MarkdownReader()
                DocumentType.UNKNOWN -> throw IllegalArgumentException("Unsupported document type.")
            }
    }
}
