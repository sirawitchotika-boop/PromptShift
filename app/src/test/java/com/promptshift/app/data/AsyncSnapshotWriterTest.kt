package com.promptshift.app.data

import com.promptshift.app.ai.HttpOpenAiStreamingClient
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class AsyncSnapshotWriterTest {

    @Test
    fun `writer offloads disk writes off calling thread and persists latest coalesced snapshot`() {
        val callerThreadId = Thread.currentThread().id
        val writerThreadId = AtomicLong(-1L)
        val lastWritten = AtomicReference<Int?>(null)
        val gate = CountDownLatch(1)
        val finalWritten = CountDownLatch(1)

        val writer = AsyncSnapshotWriter<Int>(Dispatchers.IO) { value ->
            writerThreadId.set(Thread.currentThread().id)
            if (value == 1) {
                gate.await(2, TimeUnit.SECONDS)
            }
            lastWritten.set(value)
            if (value == 50) {
                finalWritten.countDown()
            }
        }

        for (i in 1..50) {
            writer.enqueue(i)
        }
        gate.countDown()

        assertTrue("Expected final snapshot to be written asynchronously", finalWritten.await(5, TimeUnit.SECONDS))
        assertNotEquals(callerThreadId, writerThreadId.get())
        assertEquals(50, lastWritten.get())
    }

    @Test
    fun `repositories update StateFlow immediately while persisting JSON off caller thread`() {
        val tempDir = Files.createTempDirectory("promptshift_async_repo").toFile()
        try {
            val batchFile = File(tempDir, "promptshift_state.json")
            val batchSaved = CountDownLatch(1)
            val batchWriteThread = AtomicLong(-1L)
            val fileBatchPersistence = FileBatchPersistence(batchFile)
            val batchRepo = DefaultPromptBatchRepository(
                persistence = object : BatchPersistence {
                    override fun load() = fileBatchPersistence.load()
                    override fun save(snapshot: PersistedBatchSnapshot) {
                        batchWriteThread.set(Thread.currentThread().id)
                        fileBatchPersistence.save(snapshot)
                        if (snapshot.prompts.size == 2) {
                            batchSaved.countDown()
                        }
                    }
                },
                persistDispatcher = Dispatchers.IO,
            )

            val callerThreadId = Thread.currentThread().id
            batchRepo.loadParsedPrompts(listOf("prompt one", "prompt two"))
            // StateFlow is updated synchronously on the caller thread
            assertEquals(2, batchRepo.batchState.value.totalCount)
            assertTrue(batchSaved.await(5, TimeUnit.SECONDS))
            assertNotEquals(callerThreadId, batchWriteThread.get())
            assertEquals(listOf("prompt one", "prompt two"), fileBatchPersistence.load()?.prompts)

            val configFile = File(tempDir, "promptshift_ai_config.json")
            val configSaved = CountDownLatch(1)
            val configWriteThread = AtomicLong(-1L)
            val fileConfigPersistence = FileAiConfigPersistence(configFile)
            val configRepo = DefaultAiConfigRepository(
                persistence = object : AiConfigPersistence {
                    override fun load() = fileConfigPersistence.load()
                    override fun save(config: PersistedAiConfig) {
                        configWriteThread.set(Thread.currentThread().id)
                        fileConfigPersistence.save(config)
                        if (config.timerPresetsSeconds?.contains(435) == true) {
                            configSaved.countDown()
                        }
                    }
                },
                apiKeyStore = object : ApiKeyStore {
                    override fun saveKey(providerId: String, apiKey: String) = Unit
                    override fun getKey(providerId: String): String? = null
                    override fun deleteKey(providerId: String) = Unit
                    override fun hasKey(providerId: String): Boolean = false
                },
                persistDispatcher = Dispatchers.IO,
            )

            assertTrue(configRepo.addTimerPreset(7, 15))
            assertTrue(configRepo.configState.value.timerPresetsSeconds.contains(435))
            assertTrue(configSaved.await(5, TimeUnit.SECONDS))
            assertNotEquals(callerThreadId, configWriteThread.get())
            assertTrue(fileConfigPersistence.load()?.timerPresetsSeconds?.contains(435) == true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `stream read timeout allows slow thinking models without cutting off at 60s`() {
        assertTrue(
            "Expected STREAM_READ_TIMEOUT_MS in 180_000..300_000",
            HttpOpenAiStreamingClient.STREAM_READ_TIMEOUT_MS in 180_000..300_000,
        )
    }
}
