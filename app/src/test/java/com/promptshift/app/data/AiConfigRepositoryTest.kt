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

    @Test
    fun `persists favorite models, reasoning effort, trigger words, snippets, and deduplicated Recent 10`() {
        val configFile = File(tempFolder.root, "phase4_config.json")
        val keyStore = InMemoryApiKeyStore()
        val repo = DefaultAiConfigRepository(FileAiConfigPersistence(configFile), keyStore)

        val provider = repo.saveProvider(
            id = null,
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            modelId = "deepseek/deepseek-chat",
            apiKeyInput = "sk-key-1",
            favoriteModels = listOf("openai/gpt-4o-mini", "anthropic/claude-3.5-sonnet"),
        )
        repo.selectProviderModel(provider.id, "openai/gpt-4o-mini")
        assertEquals("openai/gpt-4o-mini", repo.configState.value.activeProvider?.modelId)
        assertTrue("deepseek/deepseek-chat" in repo.configState.value.activeProvider!!.favoriteModels)

        repo.setReasoningEffort(com.promptshift.app.domain.ReasoningEffort.HIGH)
        assertTrue(repo.addTriggerWordChip("ohwx woman,"))
        assertEquals("ohwx woman", repo.configState.value.effectiveTriggerWord)
        repo.toggleTriggerWordChip("ohwx woman")
        assertNull(repo.configState.value.effectiveTriggerWord)
        repo.toggleTriggerWordChip("ohwx woman")
        assertEquals("ohwx woman", repo.configState.value.effectiveTriggerWord)

        val snip = repo.saveSnippet(null, "35mm Film", "35mm Kodak Portra, soft grain", includeTriggerWord = false)
        assertFalse(snip.includeTriggerWord)

        // Record 12 recent prompts with a duplicate -> capped at 10, newest first
        for (i in 1..12) {
            repo.recordRecentPrompt("Recent prompt #$i")
        }
        repo.recordRecentPrompt("Recent prompt #5")
        assertEquals(10, repo.configState.value.recentPrompts.size)
        assertEquals("Recent prompt #5", repo.configState.value.recentPrompts.first())

        // Cold restart restores everything cleanly
        val restarted = DefaultAiConfigRepository(FileAiConfigPersistence(configFile), keyStore)
        assertEquals("openai/gpt-4o-mini", restarted.configState.value.activeProvider?.modelId)
        assertEquals(com.promptshift.app.domain.ReasoningEffort.HIGH, restarted.configState.value.reasoningEffort)
        assertEquals("ohwx woman", restarted.configState.value.effectiveTriggerWord)
        assertEquals(1, restarted.configState.value.snippets.size)
        assertEquals(10, restarted.configState.value.recentPrompts.size)
        assertEquals("Recent prompt #5", restarted.configState.value.recentPrompts.first())
    }

    @Test
    fun `safe backup v2 exports and restores profiles, snippets, triggers, and favorite models with ZERO API keys`() {
        val sourceRepo = DefaultAiConfigRepository(
            FileAiConfigPersistence(File(tempFolder.root, "backup_src.json")),
            InMemoryApiKeyStore(),
        )
        val secretKey = "sk-ultra-secret-never-export-777"
        sourceRepo.saveProvider(
            id = null,
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            modelId = "deepseek/deepseek-chat",
            apiKeyInput = secretKey,
            favoriteModels = listOf("openai/gpt-4o-mini", "google/gemini-2.5-pro"),
        )
        sourceRepo.saveInstruction(null, "Cinematic", "Moody lighting")
        sourceRepo.saveSnippet(null, "Quality", "8k ultra detailed", includeTriggerWord = false)
        sourceRepo.addTriggerWordChip("ohwx woman")

        val backupJson = sourceRepo.exportSafeBackupJson()
        assertTrue(backupJson.contains(AiConfigCodec.EXPORT_FORMAT_TAG_V2))
        assertFalse("Backup MUST NEVER contain API keys", backupJson.contains(secretKey))
        assertFalse("Backup MUST NEVER contain provider baseUrl", backupJson.contains("openrouter.ai/api/v1"))

        val targetRepo = DefaultAiConfigRepository(
            FileAiConfigPersistence(File(tempFolder.root, "backup_dst.json")),
            InMemoryApiKeyStore(),
        )
        // Target already has a matching provider by name (with its own local API key)
        targetRepo.saveProvider(null, "OpenRouter", "https://openrouter.ai/api/v1", "deepseek/deepseek-chat", "sk-local")

        val result = targetRepo.importSafeBackupJson(backupJson)
        assertEquals(
            com.promptshift.app.domain.SafeBackupImportResult.Imported(
                instructionsCount = 1,
                snippetsCount = 1,
                triggerWordsCount = 1,
                favoriteModelsCount = 2,
            ),
            result,
        )
        assertEquals(1, targetRepo.configState.value.instructions.size)
        assertEquals(1, targetRepo.configState.value.snippets.size)
        assertEquals(listOf("ohwx woman"), targetRepo.configState.value.triggerWords)
        assertEquals(
            listOf("openai/gpt-4o-mini", "google/gemini-2.5-pro"),
            targetRepo.configState.value.activeProvider?.favoriteModels,
        )

        // Backward compatibility: v1 instructions-only JSON still imports via importSafeBackupJson
        val v1Json = sourceRepo.exportInstructionsJson()
        val v1Target = DefaultAiConfigRepository(
            FileAiConfigPersistence(File(tempFolder.root, "backup_v1_dst.json")),
            InMemoryApiKeyStore(),
        )
        val v1Res = v1Target.importSafeBackupJson(v1Json)
        assertEquals(
            com.promptshift.app.domain.SafeBackupImportResult.Imported(1, 0, 0, 0),
            v1Res,
        )
    }
}
