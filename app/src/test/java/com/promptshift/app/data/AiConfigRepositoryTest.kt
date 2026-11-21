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
        sourceRepo.addTimerPreset(7, 30)

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
                timerPresetsCount = 4,
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
        assertEquals(listOf(60, 180, 300, 450), targetRepo.configState.value.timerPresetsSeconds)

        // Backward compatibility: v1 instructions-only JSON still imports via importSafeBackupJson
        val v1Json = sourceRepo.exportInstructionsJson()
        val v1Target = DefaultAiConfigRepository(
            FileAiConfigPersistence(File(tempFolder.root, "backup_v1_dst.json")),
            InMemoryApiKeyStore(),
        )
        val v1Res = v1Target.importSafeBackupJson(v1Json)
        assertEquals(
            com.promptshift.app.domain.SafeBackupImportResult.Imported(1, 0, 0, 0, 0),
            v1Res,
        )
    }

    @Test
    fun `timer presets start with editable defaults and persist additions, deletions, and empty state`() {
        val file = File(tempFolder.root, "timer_presets.json")
        val repo = DefaultAiConfigRepository(FileAiConfigPersistence(file), InMemoryApiKeyStore())

        // Starts with editable starter presets (1m, 3m, 5m)
        assertEquals(listOf(60, 180, 300), repo.configState.value.timerPresetsSeconds)

        // Rejects zero duration and duplicates
        assertFalse(repo.addTimerPreset(0, 0))
        assertFalse(repo.addTimerPreset(1, 0))

        // Adds new presets sorted ascending
        assertTrue(repo.addTimerPreset(0, 30))
        assertTrue(repo.addTimerPreset(10, 0))
        assertEquals(listOf(30, 60, 180, 300, 600), repo.configState.value.timerPresetsSeconds)

        // Persists across reload
        val reloaded = DefaultAiConfigRepository(FileAiConfigPersistence(file), InMemoryApiKeyStore())
        assertEquals(listOf(30, 60, 180, 300, 600), reloaded.configState.value.timerPresetsSeconds)

        // Removing all presets persists an intentionally empty list (does not snap back to defaults)
        listOf(30, 60, 180, 300, 600).forEach { reloaded.removeTimerPreset(it) }
        assertEquals(emptyList<Int>(), reloaded.configState.value.timerPresetsSeconds)

        val reloadedEmpty = DefaultAiConfigRepository(FileAiConfigPersistence(file), InMemoryApiKeyStore())
        assertEquals(emptyList<Int>(), reloadedEmpty.configState.value.timerPresetsSeconds)
    }

    @Test
    fun `preferredReturnImeId persists and clears cleanly across reload`() {
        val file = File(tempFolder.root, "return_ime.json")
        val repo = DefaultAiConfigRepository(FileAiConfigPersistence(file), InMemoryApiKeyStore())

        assertEquals(null, repo.configState.value.preferredReturnImeId)

        val gboardId = "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"
        repo.setPreferredReturnImeId(gboardId)
        assertEquals(gboardId, repo.configState.value.preferredReturnImeId)

        val reloaded = DefaultAiConfigRepository(FileAiConfigPersistence(file), InMemoryApiKeyStore())
        assertEquals(gboardId, reloaded.configState.value.preferredReturnImeId)

        reloaded.setPreferredReturnImeId(null)
        val reloadedCleared = DefaultAiConfigRepository(FileAiConfigPersistence(file), InMemoryApiKeyStore())
        assertEquals(null, reloadedCleared.configState.value.preferredReturnImeId)
    }

    @Test
    fun `recentDirections deduplicates to top, caps at 8, clears, and persists`() {
        val file = File(tempFolder.root, "recent_directions.json")
        val repo = DefaultAiConfigRepository(FileAiConfigPersistence(file), InMemoryApiKeyStore())

        assertEquals(emptyList<String>(), repo.configState.value.recentDirections)
        repo.recordRecentDirection("   ")
        assertEquals(emptyList<String>(), repo.configState.value.recentDirections)

        for (i in 1..10) {
            repo.recordRecentDirection("Direction #$i")
        }
        // Capped at 8, most recent first
        assertEquals(8, repo.configState.value.recentDirections.size)
        assertEquals("Direction #10", repo.configState.value.recentDirections.first())
        assertEquals("Direction #3", repo.configState.value.recentDirections.last())

        // Re-recording an older item moves it back to top without duplicating
        repo.recordRecentDirection("Direction #5")
        assertEquals(8, repo.configState.value.recentDirections.size)
        assertEquals("Direction #5", repo.configState.value.recentDirections.first())

        val reloaded = DefaultAiConfigRepository(FileAiConfigPersistence(file), InMemoryApiKeyStore())
        assertEquals(repo.configState.value.recentDirections, reloaded.configState.value.recentDirections)

        reloaded.clearRecentDirections()
        assertEquals(emptyList<String>(), reloaded.configState.value.recentDirections)
    }

    @Test
    fun `timerPillEnabled and saved pill position persist cleanly across reload`() {
        val file = File(tempFolder.root, "timer_pill_prefs.json")
        val repo = DefaultAiConfigRepository(FileAiConfigPersistence(file), InMemoryApiKeyStore())

        assertTrue(repo.configState.value.timerPillEnabled)
        assertEquals(null, repo.configState.value.timerPillX)
        assertEquals(null, repo.configState.value.timerPillY)

        repo.setTimerPillPosition(240, 96)
        repo.setTimerPillEnabled(false)

        val reloaded = DefaultAiConfigRepository(FileAiConfigPersistence(file), InMemoryApiKeyStore())
        assertFalse(reloaded.configState.value.timerPillEnabled)
        assertEquals(240, reloaded.configState.value.timerPillX)
        assertEquals(96, reloaded.configState.value.timerPillY)
    }
}
