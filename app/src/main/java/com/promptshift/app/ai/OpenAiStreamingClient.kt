package com.promptshift.app.ai

import com.promptshift.app.data.SimpleJsonParser
import com.promptshift.app.domain.ConnectionTestResult
import com.promptshift.app.domain.InstructionProfile
import com.promptshift.app.domain.ProviderProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

interface AiStreamingClient {
    suspend fun streamPrompts(
        provider: ProviderProfile,
        apiKey: String,
        instruction: InstructionProfile?,
        direction: String,
        promptCount: Int,
        onPromptCompleted: (String) -> Unit,
    ): Int

    suspend fun testConnection(
        provider: ProviderProfile,
        apiKey: String,
    ): ConnectionTestResult
}

/**
 * Lightweight zero-dependency HTTP/SSE client using Android's standard HttpURLConnection.
 * Supports immediate coroutine cancellation by disconnecting the active socket on Job cancel.
 */
class HttpOpenAiStreamingClient : AiStreamingClient {

    override suspend fun streamPrompts(
        provider: ProviderProfile,
        apiKey: String,
        instruction: InstructionProfile?,
        direction: String,
        promptCount: Int,
        onPromptCompleted: (String) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val endpoint = OpenAiRequestBuilder.resolveChatCompletionsUrl(provider.baseUrl)
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            throw AiRequestException("Base URL must start with https://")
        }

        val bodyJson = OpenAiRequestBuilder.buildStreamingRequestJson(
            provider = provider,
            instruction = instruction,
            direction = direction,
            promptCount = promptCount,
        )

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = STREAM_READ_TIMEOUT_MS
            doInput = true
            doOutput = true
            useCaches = false
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream, application/json")
        }

        val cancelHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause != null) {
                try {
                    connection.disconnect()
                } catch (_: Exception) {
                }
            }
        }

        try {
            connection.outputStream.use { os ->
                os.write(bodyJson.toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                val errBody = readErrorStream(connection)
                throw AiRequestException(formatHttpError(statusCode, errBody))
            }

            val assembler = StreamingPromptAssembler(onPromptCompleted)
            val contentType = connection.contentType.orEmpty()

            if (contentType.contains("application/json", ignoreCase = true) &&
                !contentType.contains("event-stream", ignoreCase = true)
            ) {
                val fullJson = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                when (val event = StreamingPromptAssembler.parseSseLine(fullJson)) {
                    is SseEvent.ContentDelta -> assembler.feedTextDelta(event.text)
                    is SseEvent.Error -> throw AiRequestException(event.message)
                    else -> Unit
                }
                assembler.finish()
                return@withContext assembler.completedCount
            }

            BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = reader.readLine() ?: break
                    when (val event = StreamingPromptAssembler.parseSseLine(line)) {
                        is SseEvent.ContentDelta -> assembler.feedTextDelta(event.text)
                        SseEvent.Done -> break
                        is SseEvent.Error -> throw AiRequestException(event.message)
                        SseEvent.Ignore -> Unit
                    }
                }
            }

            currentCoroutineContext().ensureActive()
            assembler.finish()
            assembler.completedCount
        } finally {
            cancelHandle.dispose()
            try {
                connection.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    override suspend fun testConnection(
        provider: ProviderProfile,
        apiKey: String,
    ): ConnectionTestResult = withContext(Dispatchers.IO) {
        val endpoint = OpenAiRequestBuilder.resolveChatCompletionsUrl(provider.baseUrl)
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            return@withContext ConnectionTestResult.Failure("Base URL must start with https://")
        }

        val startNanos = System.nanoTime()
        val bodyJson = OpenAiRequestBuilder.buildConnectionTestJson(provider)
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = TEST_READ_TIMEOUT_MS
            doInput = true
            doOutput = true
            useCaches = false
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.use { os ->
                os.write(bodyJson.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            val code = connection.responseCode
            val latencyMs = ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(1L)
            if (code in 200..299) {
                ConnectionTestResult.Success(
                    latencyMs = latencyMs,
                    detail = "Connected to ${provider.modelId} (${latencyMs}ms)",
                )
            } else {
                val errBody = readErrorStream(connection)
                ConnectionTestResult.Failure(formatHttpError(code, errBody))
            }
        } catch (e: Exception) {
            ConnectionTestResult.Failure(e.message?.take(120) ?: "Connection failed")
        } finally {
            try {
                connection.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun readErrorStream(connection: HttpURLConnection): String {
        return try {
            connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val STREAM_READ_TIMEOUT_MS = 60_000
        private const val TEST_READ_TIMEOUT_MS = 20_000

        internal fun formatHttpError(statusCode: Int, errorBody: String): String {
            val parsedMessage = try {
                val root = SimpleJsonParser(errorBody).parseValue() as? Map<*, *>
                when (val err = root?.get("error")) {
                    is Map<*, *> -> err["message"] as? String
                    is String -> err
                    else -> root?.get("message") as? String
                }
            } catch (_: Exception) {
                null
            }?.trim()?.take(140)

            val prefix = when (statusCode) {
                401 -> "Invalid API key (401)"
                403 -> "Access denied (403)"
                404 -> "Endpoint or model not found (404)"
                429 -> "Rate limit / quota reached (429)"
                in 500..599 -> "Provider server error ($statusCode)"
                else -> "HTTP $statusCode"
            }

            return if (!parsedMessage.isNullOrBlank()) {
                "$prefix: $parsedMessage"
            } else {
                prefix
            }
        }
    }
}

class AiRequestException(message: String) : Exception(message)
