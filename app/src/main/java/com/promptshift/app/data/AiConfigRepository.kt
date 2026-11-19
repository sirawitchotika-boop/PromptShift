package com.promptshift.app.data

import com.promptshift.app.domain.AiConfigState
import com.promptshift.app.domain.InstructionImportResult
import com.promptshift.app.domain.InstructionProfile
import com.promptshift.app.domain.ProviderProfile
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
    ): ProviderProfile

    fun deleteProvider(id: String)
    fun selectProvider(id: String)
    fun getApiKey(providerId: String): String?

    fun saveInstruction(id: String?, name: String, body: String): InstructionProfile
    fun deleteInstruction(id: String)
    fun selectInstruction(id: String?)

    fun setDefaultPromptCount(count: Int)

    fun exportInstructionsJson(): String
    fun importInstructionsJson(json: String): InstructionImportResult
}

class DefaultAiConfigRepository(
    private val persistence: AiConfigPersistence,
    private val apiKeyStore: ApiKeyStore,
) : AiConfigRepository {

    private val lock = Any()
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
    ): ProviderProfile {
        synchronized(lock) {
            val resolvedId = id?.takeIf { it.isNotBlank() } ?: "prov_${UUID.randomUUID().toString().take(8)}"
            if (!apiKeyInput.isNullOrBlank()) {
                apiKeyStore.saveKey(resolvedId, apiKeyInput.trim())
            }
            val profile = ProviderProfile(
                id = resolvedId,
                name = name.trim(),
                baseUrl = baseUrl.trim(),
                modelId = modelId.trim(),
                hasApiKey = apiKeyStore.hasKey(resolvedId),
            )
            val current = _configState.value
            val updatedList = if (current.providers.any { it.id == resolvedId }) {
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

    private fun persistLocked() {
        val state = _configState.value
        persistence.save(
            PersistedAiConfig(
                providers = state.providers.map { it.copy(hasApiKey = false) },
                activeProviderId = state.activeProviderId,
                instructions = state.instructions,
                activeInstructionId = state.activeInstructionId,
                defaultPromptCount = state.defaultPromptCount,
            ),
        )
    }
}
