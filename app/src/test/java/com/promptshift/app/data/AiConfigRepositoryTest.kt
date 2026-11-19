package com.promptshift.app.data

import com.promptshift.app.domain.InstructionImportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AiConfigRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class InMemoryApiKeyStore : ApiKeyStore {
        private val map = mutableMapOf<String, String>()
        override fun saveKey(profileId: String, apiKey: String) { map[profileId] = apiKey }
        override fun getKey(profileId: String): String? = map[profileId]
        override fun deleteKey(profileId: String) { map.remove(profileId) }
        override fun hasKey(profileId: String): Boolean = map.containsKey(profileId)
    }

    @Test
    fun `isolates API keys from config file and preserves key when editing provider metadata`() {
        val configFile = File(tempFolder.root, "ai_config.json")
        val keyStore = InMemoryApiKeyStore()
        val repo = DefaultAiConfigRepository(FileAiConfigPersistence(configFile), keyStore)

        val secretKey = "sk-super-secret-api-key-99999"
        val saved = repo.saveProvider(
            id = null,
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            modelId = "deepseek/deepseek-chat",
            apiKeyInput = secretKey,
        )

        assertTrue(saved.hasApiKey)
        assertEquals(secretKey, repo.getApiKey(saved.id))

        // Verify the persisted JSON config file NEVER contains the API key
        val rawConfigFile = configFile.readText(Charsets.UTF_8)
        assertFalse("Persisted config file must NEVER contain API keys", rawConfigFile.contains(secretKey))

        // Edit model ID with blank apiKeyInput -> existing encrypted key must stay intact
        val edited = repo.saveProvider(
            id = saved.id,
            name = "OpenRouter Fast",
            baseUrl = "https://openrouter.ai/api/v1",
            modelId = "openai/gpt-4o-mini",
            apiKeyInput = null,
        )
        assertTrue(edited.hasApiKey)
        assertEquals(secretKey, repo.getApiKey(saved.id))

        // Restart persistence restores providers and checks live key presence
        val restarted = DefaultAiConfigRepository(FileAiConfigPersistence(configFile), keyStore)
        assertEquals(1, restarted.configState.value.providers.size)
        assertEquals("OpenRouter Fast", restarted.configState.value.activeProvider?.name)
        assertTrue(restarted.configState.value.activeProvider?.hasApiKey == true)

        // Delete provider -> also wipes key from ApiKeyStore
        restarted.deleteProvider(saved.id)
        assertNull(keyStore.getKey(saved.id))
        assertTrue(restarted.configState.value.providers.isEmpty())
    }

    @Test
    fun `exports and imports Instruction Profiles JSON cleanly and NEVER exports API keys`() {
        val configFile = File(tempFolder.root, "ai_config_export.json")
        val keyStore = InMemoryApiKeyStore()
        val repo = DefaultAiConfigRepository(FileAiConfigPersistence(configFile), keyStore)

        val secretKey = "sk-do-not-export-me-12345"
        repo.saveProvider(null, "MyProvider", "https://api.secret-host.example/v1", "model-z", secretKey)
        repo.saveInstruction(null, "Style A", "Cinematic lighting, \"35mm\" lens\nNo text overlays.")
        repo.saveInstruction(null, "Style B", "Minimalist studio macro shots, ไทย support 🔥")

        val exportedJson = repo.exportInstructionsJson()
        assertTrue(exportedJson.contains(AiConfigCodec.EXPORT_FORMAT_TAG))
        assertTrue(exportedJson.contains("Style A"))
        assertTrue(exportedJson.contains("Style B"))

        // Strict security assertion: zero API keys or provider URLs in export JSON!
        assertFalse(exportedJson.contains(secretKey))
        assertFalse(exportedJson.contains("secret-host.example"))
        assertFalse(exportedJson.contains("MyProvider"))

        // Import into a brand-new repository
        val targetRepo = DefaultAiConfigRepository(
            FileAiConfigPersistence(File(tempFolder.root, "ai_config_target.json")),
            InMemoryApiKeyStore(),
        )
        val importResult = targetRepo.importInstructionsJson(exportedJson)
        assertEquals(InstructionImportResult.Imported(2), importResult)
        assertEquals(2, targetRepo.configState.value.instructions.size)
        assertEquals(
            "Cinematic lighting, \"35mm\" lens\nNo text overlays.",
            targetRepo.configState.value.instructions[0].body,
        )
        assertEquals(
            "Minimalist studio macro shots, ไทย support 🔥",
            targetRepo.configState.value.instructions[1].body,
        )
    }
}
