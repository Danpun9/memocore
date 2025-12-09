package com.danpun9.memocore.domain.readers

import java.io.InputStream

abstract class Reader {
    abstract fun readFromInputStream(inputStream: InputStream): String?
}
