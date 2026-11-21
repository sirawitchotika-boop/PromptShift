package com.promptshift.app.data

import com.promptshift.app.domain.AiConfigState
import com.promptshift.app.domain.InstructionImportResult
import com.promptshift.app.domain.InstructionProfile
import com.promptshift.app.domain.PromptSnippet
import com.promptshift.app.domain.PromptTextComposer
import com.promptshift.app.domain.ProviderProfile
import com.promptshift.app.domain.ReasoningEffort
import com.promptshift.app.domain.SafeBackupImportResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

interface AiConfigPersistence {
    fun load(): PersistedAiConfig?
    fun save(config: PersistedAiConfig)
}

class FileAiConfigPersistence(private val targetFile: File) : AiConfigPersistence {
    override fun load(): PersistedAiConfig? {
        if (!targetFile.exists()) return null
        return try {
            AiConfigCodec.decodeConfig(targetFile.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    override fun save(config: PersistedAiConfig) {
        try {
            targetFile.parentFile?.mkdirs()
            val encoded = AiConfigCodec.encodeConfig(config)
            val tmpFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
            tmpFile.writeText(encoded, Charsets.UTF_8)
            if (!tmpFile.renameTo(targetFile)) {
                targetFile.writeText(encoded, Charsets.UTF_8)
                tmpFile.delete()
            }
        } catch (_: Exception) {
        }
    }
}

interface AiConfigRepository {
    val configState: StateFlow<AiConfigState>

    fun saveProvider(
        id: String?,
        name: String,
        baseUrl: String,
        modelId: String,
        apiKeyInput: String?,
        favoriteModels: List<String>? = null,
    ): ProviderProfile

    fun deleteProvider(id: String)
    fun selectProvider(id: String)
    fun selectProviderModel(providerId: String, modelId: String)
    fun addFavoriteModel(providerId: String, modelId: String)
    fun removeFavoriteModel(providerId: String, modelId: String)
    fun getApiKey(providerId: String): String?

    fun saveInstruction(id: String?, name: String, body: String): InstructionProfile
    fun deleteInstruction(id: String)
    fun selectInstruction(id: String?)

    fun setDefaultPromptCount(count: Int)
    fun setReasoningEffort(effort: ReasoningEffort)

    fun setActiveTriggerWord(rawWord: String?, enabled: Boolean? = null)
    fun setTriggerWordEnabled(enabled: Boolean)
    fun toggleTriggerWordChip(chip: String)
    fun addTriggerWordChip(rawWord: String): Boolean
    fun removeTriggerWordChip(chip: String)

    fun saveSnippet(
        id: String?,
        title: String,
        body: String,
        includeTriggerWord: Boolean,
    ): PromptSnippet

    fun deleteSnippet(id: String)
    fun recordRecentPrompt(prompt: String)
    fun clearRecentPrompts()

    fun recordRecentDirection(direction: String)
    fun clearRecentDirections()

    fun addTimerPreset(minutes: Int, seconds: Int): Boolean
    fun removeTimerPreset(totalSeconds: Int)
    fun setPreferredReturnImeId(imeId: String?)
    fun setTimerPillEnabled(enabled: Boolean)
    fun setTimerPillPosition(x: Int, y: Int)

    fun exportInstructionsJson(): String
    fun importInstructionsJson(json: String): InstructionImportResult

    fun exportSafeBackupJson(): String
    fun importSafeBackupJson(json: String): SafeBackupImportResult
}

class DefaultAiConfigRepository(
    private val persistence: AiConfigPersistence,
    private val apiKeyStore: ApiKeyStore,
    persistDispatcher: CoroutineDispatcher? = null,
) : AiConfigRepository {

    private val lock = Any()
    private val snapshotWriter = AsyncSnapshotWriter<PersistedAiConfig>(
        dispatcher = persistDispatcher,
        writeAction = { config -> persistence.save(config) },
    )
    private val _configState: MutableStateFlow<AiConfigState>
    override val configState: StateFlow<AiConfigState>

    init {
        val saved = persistence.load()
        val initial = if (saved != null) {
            val providers = saved.providers.map { p ->
                p.copy(hasApiKey = apiKeyStore.hasKey(p.id))
            }
            val validProviderId = saved.activeProviderId
                ?.takeIf { id -> providers.any { it.id == id } }
                ?: providers.firstOrNull()?.id
            val validInstructionId = saved.activeInstructionId
                ?.takeIf { id -> saved.instructions.any { it.id == id } }
            AiConfigState(
                providers = providers,
                activeProviderId = validProviderId,
                instructions = saved.instructions,
                activeInstructionId = validInstructionId,
                defaultPromptCount = saved.defaultPromptCount.coerceIn(1, 100),
                reasoningEffort = saved.reasoningEffort,
                triggerWords = saved.triggerWords,
                activeTriggerWord = saved.activeTriggerWord,
                triggerWordEnabled = saved.triggerWordEnabled && !saved.activeTriggerWord.isNullOrBlank(),
                snippets = saved.snippets,
                recentPrompts = saved.recentPrompts.take(MAX_RECENT_PROMPTS),
                recentDirections = saved.recentDirections.take(AiConfigState.MAX_RECENT_DIRECTIONS),
                timerPresetsSeconds = saved.timerPresetsSeconds ?: AiConfigState.DEFAULT_TIMER_PRESETS_SECONDS,
                preferredReturnImeId = saved.preferredReturnImeId,
                timerPillEnabled = saved.timerPillEnabled,
                timerPillX = saved.timerPillX,
                timerPillY = saved.timerPillY,
            )
        } else {
            AiConfigState()
        }

        _configState = MutableStateFlow(initial)
        configState = _configState.asStateFlow()
    }

    override fun saveProvider(
        id: String?,
        name: String,
        baseUrl: String,
        modelId: String,
        apiKeyInput: String?,
        favoriteModels: List<String>?,
    ): ProviderProfile {
        synchronized(lock) {
            val resolvedId = id?.takeIf { it.isNotBlank() } ?: "prov_${UUID.randomUUID().toString().take(8)}"
            if (!apiKeyInput.isNullOrBlank()) {
                apiKeyStore.saveKey(resolvedId, apiKeyInput.trim())
            }
            val current = _configState.value
            val existing = current.providers.firstOrNull { it.id == resolvedId }
            val cleanModelId = modelId.trim()
            val resolvedFavs = (favoriteModels ?: existing?.favoriteModels ?: emptyList())
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            val profile = ProviderProfile(
                id = resolvedId,
                name = name.trim(),
                baseUrl = baseUrl.trim(),
                modelId = cleanModelId,
                favoriteModels = resolvedFavs,
                hasApiKey = apiKeyStore.hasKey(resolvedId),
            )
            val updatedList = if (existing != null) {
                current.providers.map { if (it.id == resolvedId) profile else it }
            } else {
                current.providers + profile
            }
            val nextActive = current.activeProviderId ?: resolvedId
            _configState.value = current.copy(
                providers = updatedList,
                activeProviderId = nextActive,
            )
            persistLocked()
            return profile
        }
    }

    override fun deleteProvider(id: String) {
        synchronized(lock) {
            apiKeyStore.deleteKey(id)
            val current = _configState.value
            val remaining = current.providers.filterNot { it.id == id }
            val nextActive = if (current.activeProviderId == id) {
                remaining.firstOrNull()?.id
            } else {
                current.activeProviderId
            }
            _configState.value = current.copy(
                providers = remaining,
                activeProviderId = nextActive,
            )
            persistLocked()
        }
    }

    override fun selectProvider(id: String) {
        synchronized(lock) {
            val current = _configState.value
            if (current.providers.none { it.id == id }) return
            _configState.value = current.copy(activeProviderId = id)
            persistLocked()
        }
    }

    override fun selectProviderModel(providerId: String, modelId: String) {
        val cleanModel = modelId.trim()
        if (cleanModel.isEmpty()) return
        synchronized(lock) {
            val current = _configState.value
            val target = current.providers.firstOrNull { it.id == providerId } ?: return
            if (target.modelId == cleanModel) return
            val updatedFavs = (target.favoriteModels + target.modelId + cleanModel)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            val updatedProviders = current.providers.map { p ->
                if (p.id == providerId) p.copy(modelId = cleanModel, favoriteModels = updatedFavs) else p
            }
            _configState.value = current.copy(providers = updatedProviders)
            persistLocked()
        }
    }

    override fun addFavoriteModel(providerId: String, modelId: String) {
        val cleanModel = modelId.trim()
        if (cleanModel.isEmpty()) return
        synchronized(lock) {
            val current = _configState.value
            val target = current.providers.firstOrNull { it.id == providerId } ?: return
            val updatedFavs = (target.favoriteModels + cleanModel).distinct()
            if (updatedFavs == target.favoriteModels) return
            val updatedProviders = current.providers.map { p ->
                if (p.id == providerId) p.copy(favoriteModels = updatedFavs) else p
            }
            _configState.value = current.copy(providers = updatedProviders)
            persistLocked()
        }
    }

    override fun removeFavoriteModel(providerId: String, modelId: String) {
        synchronized(lock) {
            val current = _configState.value
            val target = current.providers.firstOrNull { it.id == providerId } ?: return
            val updatedFavs = target.favoriteModels.filterNot { it == modelId }
            if (updatedFavs == target.favoriteModels) return
            val updatedProviders = current.providers.map { p ->
                if (p.id == providerId) p.copy(favoriteModels = updatedFavs) else p
            }
            _configState.value = current.copy(providers = updatedProviders)
            persistLocked()
        }
    }

    override fun getApiKey(providerId: String): String? = apiKeyStore.getKey(providerId)

    override fun saveInstruction(id: String?, name: String, body: String): InstructionProfile {
        synchronized(lock) {
            val resolvedId = id?.takeIf { it.isNotBlank() } ?: "inst_${UUID.randomUUID().toString().take(8)}"
            val profile = InstructionProfile(
                id = resolvedId,
                name = name.trim(),
                body = body.trim(),
            )
            val current = _configState.value
            val updatedList = if (current.instructions.any { it.id == resolvedId }) {
                current.instructions.map { if (it.id == resolvedId) profile else it }
            } else {
                current.instructions + profile
            }
            val nextActive = if (id == null && current.activeInstructionId == null) {
                resolvedId
            } else {
                current.activeInstructionId
            }
            _configState.value = current.copy(
                instructions = updatedList,
                activeInstructionId = nextActive,
            )
            persistLocked()
            return profile
        }
    }

    override fun deleteInstruction(id: String) {
        synchronized(lock) {
            val current = _configState.value
            val remaining = current.instructions.filterNot { it.id == id }
            val nextActive = if (current.activeInstructionId == id) null else current.activeInstructionId
            _configState.value = current.copy(
                instructions = remaining,
                activeInstructionId = nextActive,
            )
            persistLocked()
        }
    }

    override fun selectInstruction(id: String?) {
        synchronized(lock) {
            val current = _configState.value
            if (id != null && current.instructions.none { it.id == id }) return
            _configState.value = current.copy(activeInstructionId = id)
            persistLocked()
        }
    }

    override fun setDefaultPromptCount(count: Int) {
        synchronized(lock) {
            val clamped = count.coerceIn(1, 100)
            if (_configState.value.defaultPromptCount == clamped) return
            _configState.value = _configState.value.copy(defaultPromptCount = clamped)
            persistLocked()
        }
    }

    override fun setReasoningEffort(effort: ReasoningEffort) {
        synchronized(lock) {
            if (_configState.value.reasoningEffort == effort) return
            _configState.value = _configState.value.copy(reasoningEffort = effort)
            persistLocked()
        }
    }

    override fun setActiveTriggerWord(rawWord: String?, enabled: Boolean?) {
        synchronized(lock) {
            val normalized = PromptTextComposer.normalizeTriggerWord(rawWord)
            val current = _configState.value
            val nextEnabled = when {
                normalized == null -> false
                enabled != null -> enabled
                else -> current.triggerWordEnabled
            }
            _configState.value = current.copy(
                activeTriggerWord = normalized,
                triggerWordEnabled = nextEnabled,
            )
            persistLocked()
        }
    }

    override fun setTriggerWordEnabled(enabled: Boolean) {
        synchronized(lock) {
            val current = _configState.value
            val effectiveEnabled = enabled && !current.activeTriggerWord.isNullOrBlank()
            if (current.triggerWordEnabled == effectiveEnabled) return
            _configState.value = current.copy(triggerWordEnabled = effectiveEnabled)
            persistLocked()
        }
    }

    override fun toggleTriggerWordChip(chip: String) {
        val normalized = PromptTextComposer.normalizeTriggerWord(chip) ?: return
        synchronized(lock) {
            val current = _configState.value
            val isSameActive = current.triggerWordEnabled && current.activeTriggerWord == normalized
            _configState.value = if (isSameActive) {
                current.copy(triggerWordEnabled = false)
            } else {
                current.copy(activeTriggerWord = normalized, triggerWordEnabled = true)
            }
            persistLocked()
        }
    }

    override fun addTriggerWordChip(rawWord: String): Boolean {
        val normalized = PromptTextComposer.normalizeTriggerWord(rawWord) ?: return false
        synchronized(lock) {
            val current = _configState.value
            val updatedChips = (current.triggerWords + normalized).distinct()
            _configState.value = current.copy(
                triggerWords = updatedChips,
                activeTriggerWord = normalized,
                triggerWordEnabled = true,
            )
            persistLocked()
            return true
        }
    }

    override fun removeTriggerWordChip(chip: String) {
        synchronized(lock) {
            val current = _configState.value
            val remaining = current.triggerWords.filterNot { it == chip }
            val clearedActive = current.activeTriggerWord == chip
            _configState.value = current.copy(
                triggerWords = remaining,
                activeTriggerWord = if (clearedActive) null else current.activeTriggerWord,
                triggerWordEnabled = if (clearedActive) false else current.triggerWordEnabled,
            )
            persistLocked()
        }
    }

    override fun saveSnippet(
        id: String?,
        title: String,
        body: String,
        includeTriggerWord: Boolean,
    ): PromptSnippet {
        synchronized(lock) {
            val resolvedId = id?.takeIf { it.isNotBlank() } ?: "snip_${UUID.randomUUID().toString().take(8)}"
            val snippet = PromptSnippet(
                id = resolvedId,
                title = title.trim(),
                body = body.trim(),
                includeTriggerWord = includeTriggerWord,
            )
            val current = _configState.value
            val updated = if (current.snippets.any { it.id == resolvedId }) {
                current.snippets.map { if (it.id == resolvedId) snippet else it }
            } else {
                current.snippets + snippet
            }
            _configState.value = current.copy(snippets = updated)
            persistLocked()
            return snippet
        }
    }

    override fun deleteSnippet(id: String) {
        synchronized(lock) {
            val current = _configState.value
            val remaining = current.snippets.filterNot { it.id == id }
            if (remaining.size == current.snippets.size) return
            _configState.value = current.copy(snippets = remaining)
            persistLocked()
        }
    }

    override fun recordRecentPrompt(prompt: String) {
        val cleaned = prompt.trim()
        if (cleaned.isEmpty()) return
        synchronized(lock) {
            val current = _configState.value
            val updated = (listOf(cleaned) + current.recentPrompts.filterNot { it == cleaned })
                .take(MAX_RECENT_PROMPTS)
            if (updated == current.recentPrompts) return
            _configState.value = current.copy(recentPrompts = updated)
            persistLocked()
        }
    }

    override fun clearRecentPrompts() {
        synchronized(lock) {
            val current = _configState.value
            if (current.recentPrompts.isEmpty()) return
            _configState.value = current.copy(recentPrompts = emptyList())
            persistLocked()
        }
    }

    override fun exportInstructionsJson(): String =
        AiConfigCodec.encodeInstructionExport(_configState.value.instructions)

    override fun importInstructionsJson(json: String): InstructionImportResult {
        val decoded = AiConfigCodec.decodeInstructionExport(json)
            ?: return InstructionImportResult.Failed("Invalid instruction profile JSON")
        if (decoded.isEmpty()) {
            return InstructionImportResult.Failed("No instruction profiles found in file")
        }

        synchronized(lock) {
            val current = _configState.value
            val merged = LinkedHashMap<String, InstructionProfile>()
            current.instructions.forEach { merged[it.id] = it }
            decoded.forEach { merged[it.id] = it }

            _configState.value = current.copy(
                instructions = merged.values.toList(),
            )
            persistLocked()
        }
        return InstructionImportResult.Imported(decoded.size)
    }

    override fun exportSafeBackupJson(): String {
        val state = _configState.value
        val favEntries = state.providers.mapNotNull { provider ->
            val models = provider.favoriteModels.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (models.isEmpty()) {
                null
            } else {
                SafeBackupFavoriteModels(
                    providerId = provider.id,
                    providerName = provider.name,
                    models = models,
                )
            }
        }
        return AiConfigCodec.encodeSafeBackup(
            SafeBackupPayload(
                instructions = state.instructions,
                snippets = state.snippets,
                triggerWords = state.triggerWords,
                favoriteModels = favEntries,
                timerPresetsSeconds = state.timerPresetsSeconds,
            ),
        )
    }

    override fun importSafeBackupJson(json: String): SafeBackupImportResult {
        val payload = AiConfigCodec.decodeSafeBackup(json)
            ?: return SafeBackupImportResult.Failed("Invalid PromptShift backup JSON")

        val hasAny = payload.instructions.isNotEmpty() ||
            payload.snippets.isNotEmpty() ||
            payload.triggerWords.isNotEmpty() ||
            payload.favoriteModels.isNotEmpty() ||
            payload.timerPresetsSeconds.isNotEmpty()
        if (!hasAny) {
            return SafeBackupImportResult.Failed("No profiles, snippets, triggers, favorite models, or timer presets found")
        }

        var mergedFavCount = 0
        synchronized(lock) {
            val current = _configState.value

            val mergedInstructions = LinkedHashMap<String, InstructionProfile>()
            current.instructions.forEach { mergedInstructions[it.id] = it }
            payload.instructions.forEach { mergedInstructions[it.id] = it }

            val mergedSnippets = LinkedHashMap<String, PromptSnippet>()
            current.snippets.forEach { mergedSnippets[it.id] = it }
            payload.snippets.forEach { mergedSnippets[it.id] = it }

            val mergedTriggers = (current.triggerWords + payload.triggerWords).distinct()
            val mergedTimerPresets = if (payload.timerPresetsSeconds.isNotEmpty()) {
                (current.timerPresetsSeconds + payload.timerPresetsSeconds).distinct().sorted()
            } else {
                current.timerPresetsSeconds
            }

            val updatedProviders = current.providers.map { provider ->
                val matched = payload.favoriteModels.filter { entry ->
                    (entry.providerId != null && entry.providerId == provider.id) ||
                        (entry.providerName.isNotEmpty() && entry.providerName.equals(provider.name, ignoreCase = true))
                }
                if (matched.isEmpty()) {
                    provider
                } else {
                    val incomingModels = matched.flatMap { it.models }
                    mergedFavCount += incomingModels.distinct().size
                    provider.copy(
                        favoriteModels = (provider.favoriteModels + incomingModels).distinct(),
                    )
                }
            }

            _configState.value = current.copy(
                providers = updatedProviders,
                instructions = mergedInstructions.values.toList(),
                snippets = mergedSnippets.values.toList(),
                triggerWords = mergedTriggers,
                timerPresetsSeconds = mergedTimerPresets,
            )
            persistLocked()
        }

        return SafeBackupImportResult.Imported(
            instructionsCount = payload.instructions.size,
            snippetsCount = payload.snippets.size,
            triggerWordsCount = payload.triggerWords.size,
            favoriteModelsCount = mergedFavCount,
            timerPresetsCount = payload.timerPresetsSeconds.size,
        )
    }

    override fun addTimerPreset(minutes: Int, seconds: Int): Boolean {
        val clampedMin = minutes.coerceIn(0, 999)
        val clampedSec = seconds.coerceIn(0, 59)
        val totalSeconds = clampedMin * 60 + clampedSec
        if (totalSeconds <= 0 || totalSeconds > AiConfigState.MAX_TIMER_PRESET_SECONDS) {
            return false
        }
        synchronized(lock) {
            val current = _configState.value
            if (current.timerPresetsSeconds.contains(totalSeconds)) {
                return false
            }
            val updated = (current.timerPresetsSeconds + totalSeconds).distinct().sorted()
            _configState.value = current.copy(timerPresetsSeconds = updated)
            persistLocked()
            return true
        }
    }

    override fun removeTimerPreset(totalSeconds: Int) {
        synchronized(lock) {
            val current = _configState.value
            val updated = current.timerPresetsSeconds.filterNot { it == totalSeconds }
            if (updated.size == current.timerPresetsSeconds.size) return
            _configState.value = current.copy(timerPresetsSeconds = updated)
            persistLocked()
        }
    }

    override fun recordRecentDirection(direction: String) {
        val cleaned = direction.trim()
        if (cleaned.isEmpty()) return
        synchronized(lock) {
            val current = _configState.value
            val updated = (listOf(cleaned) + current.recentDirections.filterNot { it == cleaned })
                .take(AiConfigState.MAX_RECENT_DIRECTIONS)
            _configState.value = current.copy(recentDirections = updated)
            persistLocked()
        }
    }

    override fun clearRecentDirections() {
        synchronized(lock) {
            val current = _configState.value
            if (current.recentDirections.isEmpty()) return
            _configState.value = current.copy(recentDirections = emptyList())
            persistLocked()
        }
    }

    override fun setPreferredReturnImeId(imeId: String?) {
        val normalized = imeId?.trim()?.takeIf { it.isNotEmpty() }
        synchronized(lock) {
            val current = _configState.value
            if (current.preferredReturnImeId == normalized) return
            _configState.value = current.copy(preferredReturnImeId = normalized)
            persistLocked()
        }
    }

    override fun setTimerPillEnabled(enabled: Boolean) {
        synchronized(lock) {
            val current = _configState.value
            if (current.timerPillEnabled == enabled) return
            _configState.value = current.copy(timerPillEnabled = enabled)
            persistLocked()
        }
    }

    override fun setTimerPillPosition(x: Int, y: Int) {
        synchronized(lock) {
            val current = _configState.value
            if (current.timerPillX == x && current.timerPillY == y) return
            _configState.value = current.copy(timerPillX = x, timerPillY = y)
            persistLocked()
        }
    }

    private fun persistLocked() {
        val state = _configState.value
        snapshotWriter.enqueue(
            PersistedAiConfig(
                providers = state.providers.map { it.copy(hasApiKey = false) },
                activeProviderId = state.activeProviderId,
                instructions = state.instructions,
                activeInstructionId = state.activeInstructionId,
                defaultPromptCount = state.defaultPromptCount,
                reasoningEffort = state.reasoningEffort,
                triggerWords = state.triggerWords,
                activeTriggerWord = state.activeTriggerWord,
                triggerWordEnabled = state.triggerWordEnabled,
                snippets = state.snippets,
                recentPrompts = state.recentPrompts,
                recentDirections = state.recentDirections,
                timerPresetsSeconds = state.timerPresetsSeconds,
                preferredReturnImeId = state.preferredReturnImeId,
                timerPillEnabled = state.timerPillEnabled,
                timerPillX = state.timerPillX,
                timerPillY = state.timerPillY,
            ),
        )
    }

    companion object {
        const val MAX_RECENT_PROMPTS = 10
    }
}
