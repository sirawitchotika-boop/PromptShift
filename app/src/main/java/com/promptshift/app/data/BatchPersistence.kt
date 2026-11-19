package com.promptshift.app.data

import java.io.File

interface BatchPersistence {
    fun load(): PersistedBatchSnapshot?
    fun save(snapshot: PersistedBatchSnapshot)
}

/**
 * Atomic file-backed persistence designed for large 40k–500k+ char prompt batches
 * without bloating SharedPreferences XML memory.
 */
class FileBatchPersistence(private val targetFile: File) : BatchPersistence {
    override fun load(): PersistedBatchSnapshot? {
        if (!targetFile.exists()) return null
        return try {
            PromptJsonCodec.decode(targetFile.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    override fun save(snapshot: PersistedBatchSnapshot) {
        try {
            targetFile.parentFile?.mkdirs()
            val encoded = PromptJsonCodec.encode(snapshot)
            val tmpFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            tmpFile.writeText(encoded, Charsets.UTF_8)
            if (!tmpFile.renameTo(targetFile)) {
                targetFile.writeText(encoded, Charsets.UTF_8)
                tmpFile.delete()
            }
        } catch (_: Exception) {
            // Best-effort persistence; never crash active overlay on storage I/O error.
        }
    }
}
