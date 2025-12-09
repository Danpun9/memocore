package com.danpun9.memocore.domain

import kotlin.math.max
import kotlin.math.min

class WhiteSpaceSplitter {
    companion object {
        fun createChunks(
            docText: String,
            chunkSize: Int,
            chunkOverlap: Int,
            separatorParagraph: String = "\n\n",
            separator: String = " ",
        ): List<String> {
            val words = docText.split(separator)
            val chunks = ArrayList<String>()
            var currentChunk = StringBuilder()

            for (word in words) {
                if (currentChunk.length + word.length + 1 > chunkSize) {
                    if (currentChunk.isNotEmpty()) {
                        chunks.add(currentChunk.toString())
                        
                        // Handle overlap
                        if (chunkOverlap > 0) {
                            val overlapStart = max(0, currentChunk.length - chunkOverlap)
                            val overlapText = currentChunk.substring(overlapStart)
                            currentChunk = StringBuilder(overlapText).append(separator).append(word)
                        } else {
                            currentChunk = StringBuilder(word)
                        }
                    } else {
                        chunks.add(word)
                        currentChunk = StringBuilder()
                    }
                } else {
                    if (currentChunk.isNotEmpty()) {
                        currentChunk.append(separator)
                    }
                    currentChunk.append(word)
                }
            }
            
            if (currentChunk.isNotEmpty()) {
                chunks.add(currentChunk.toString())
            }

            return chunks
        }
    }
}
