package com.danpun9.memocore.domain.readers

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

class MarkdownReader : Reader() {
    override fun readFromInputStream(inputStream: InputStream): String? {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val stringBuilder = StringBuilder()
        var line: String?
        try {
            while (reader.readLine().also { line = it } != null) {
                stringBuilder.append(line).append("\n")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
        return stringBuilder.toString()
    }
}
