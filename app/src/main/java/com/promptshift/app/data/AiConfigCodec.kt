package com.promptshift.app.data

import com.promptshift.app.domain.InstructionProfile
import com.promptshift.app.domain.PromptSnippet
import com.promptshift.app.domain.PromptTextComposer
import com.promptshift.app.domain.ProviderProfile
import com.promptshift.app.domain.ReasoningEffort

data class PersistedAiConfig(
    val providers: List<ProviderProfile> = emptyList(),
    val activeProviderId: String? = null,
    val instructions: List<InstructionProfile> = emptyList(),
    val activeInstructionId: String? = null,
    val defaultPromptCount: Int = 20,
    val reasoningEffort: ReasoningEffort = ReasoningEffort.DEFAULT,
    val triggerWords: List<String> = emptyList(),
    val activeTriggerWord: String? = null,
    val triggerWordEnabled: Boolean = false,
    val snippets: List<PromptSnippet> = emptyList(),
    val recentPrompts: List<String> = emptyList(),
    val recentDirections: List<String> = emptyList(),
    val timerPresetsSeconds: List<Int>? = null,
    val preferredReturnImeId: String? = null,
    val timerPillEnabled: Boolean = true,
    val timerPillX: Int? = null,
    val timerPillY: Int? = null,
)

data class SafeBackupFavoriteModels(
    val providerId: String? = null,
    val providerName: String,
    val models: List<String>,
)

data class SafeBackupPayload(
    val instructions: List<InstructionProfile> = emptyList(),
    val snippets: List<PromptSnippet> = emptyList(),
    val triggerWords: List<String> = emptyList(),
    val favoriteModels: List<SafeBackupFavoriteModels> = emptyList(),
)

/**
 * Pure-Kotlin JSON codec for AI Provider metadata, Instruction Profiles,
 * Snippets, Trigger Words, and Safe Export/Import JSON. API keys are NEVER encoded here.
 */
object AiConfigCodec {

    const val EXPORT_FORMAT_TAG = "promptshift-instructions-v1"
    const val EXPORT_FORMAT_TAG_V2 = "promptshift-backup-v2"

    fun encodeConfig(config: PersistedAiConfig): String {
        val sb = StringBuilder(1024)
        sb.append('{')
        sb.append("\"defaultPromptCount\":").append(config.defaultPromptCount).append(',')
        sb.append("\"reasoningEffort\":")
        appendJsonString(sb, config.reasoningEffort.name)
        sb.append(",\"activeProviderId\":")
        appendNullableString(sb, config.activeProviderId)
        sb.append(",\"activeInstructionId\":")
        appendNullableString(sb, config.activeInstructionId)
        sb.append(",\"activeTriggerWord\":")
        appendNullableString(sb, config.activeTriggerWord)
        sb.append(",\"triggerWordEnabled\":").append(config.triggerWordEnabled)
        sb.append(",\"triggerWords\":[")
        config.triggerWords.forEachIndexed { idx, tw ->
            if (idx > 0) sb.append(',')
            appendJsonString(sb, tw)
        }
        sb.append("],\"providers\":[")
        config.providers.forEachIndexed { idx, p ->
            if (idx > 0) sb.append(',')
            sb.append("{\"id\":")
            appendJsonString(sb, p.id)
            sb.append(",\"name\":")
            appendJsonString(sb, p.name)
            sb.append(",\"baseUrl\":")
            appendJsonString(sb, p.baseUrl)
            sb.append(",\"modelId\":")
            appendJsonString(sb, p.modelId)
            sb.append(",\"favoriteModels\":[")
            p.favoriteModels.forEachIndexed { mIdx, model ->
                if (mIdx > 0) sb.append(',')
                appendJsonString(sb, model)
            }
            sb.append("]}")
        }
        sb.append("],\"instructions\":[")
        config.instructions.forEachIndexed { idx, inst ->
            if (idx > 0) sb.append(',')
            sb.append("{\"id\":")
            appendJsonString(sb, inst.id)
            sb.append(",\"name\":")
            appendJsonString(sb, inst.name)
            sb.append(",\"body\":")
            appendJsonString(sb, inst.body)
            sb.append('}')
        }
        sb.append("],\"snippets\":[")
        config.snippets.forEachIndexed { idx, snip ->
            if (idx > 0) sb.append(',')
            sb.append("{\"id\":")
            appendJsonString(sb, snip.id)
            sb.append(",\"title\":")
            appendJsonString(sb, snip.title)
            sb.append(",\"body\":")
            appendJsonString(sb, snip.body)
            sb.append(",\"includeTriggerWord\":").append(snip.includeTriggerWord)
            sb.append('}')
        }
        sb.append("],\"recentPrompts\":[")
        config.recentPrompts.forEachIndexed { idx, recent ->
            if (idx > 0) sb.append(',')
            appendJsonString(sb, recent)
        }
        sb.append("],\"recentDirections\":[")
        config.recentDirections.forEachIndexed { idx, dir ->
            if (idx > 0) sb.append(',')
            appendJsonString(sb, dir)
        }
        sb.append("]")
        if (config.timerPresetsSeconds != null) {
            sb.append(",\"timerPresetsSeconds\":[")
            config.timerPresetsSeconds.forEachIndexed { idx, sec ->
                if (idx > 0) sb.append(',')
                sb.append(sec)
            }
            sb.append(']')
        }
        sb.append(",\"preferredReturnImeId\":")
        appendNullableString(sb, config.preferredReturnImeId)
        sb.append(",\"timerPillEnabled\":").append(config.timerPillEnabled)
        if (config.timerPillX != null) {
            sb.append(",\"timerPillX\":").append(config.timerPillX)
        }
        if (config.timerPillY != null) {
            sb.append(",\"timerPillY\":").append(config.timerPillY)
        }
        sb.append('}')
        return sb.toString()
    }

    fun decodeConfig(json: String): PersistedAiConfig? {
        if (json.isBlank()) return null
        return try {
            val root = SimpleJsonParser(json).parseValue() as? Map<*, *> ?: return null
            val defaultCount = (root["defaultPromptCount"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 20
            val reasoningEffort = ReasoningEffort.fromKey(root["reasoningEffort"] as? String)
            val activeProviderId = root["activeProviderId"] as? String
            val activeInstructionId = root["activeInstructionId"] as? String
            val activeTriggerWord = PromptTextComposer.normalizeTriggerWord(root["activeTriggerWord"] as? String)
            val triggerWordEnabled = (root["triggerWordEnabled"] as? Boolean) ?: false

            val triggerWords = (root["triggerWords"] as? List<*>)
                ?.mapNotNull { PromptTextComposer.normalizeTriggerWord(it as? String) }
                ?.distinct()
                ?: emptyList()

            val providers = (root["providers"] as? List<*>)?.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val id = (map["id"] as? String)?.trim().orEmpty()
                val name = (map["name"] as? String)?.trim().orEmpty()
                val baseUrl = (map["baseUrl"] as? String)?.trim().orEmpty()
                val modelId = (map["modelId"] as? String)?.trim().orEmpty()
                val favs = (map["favoriteModels"] as? List<*>)
                    ?.mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }
                    ?.distinct()
                    ?: emptyList()
                if (id.isEmpty() || name.isEmpty() || baseUrl.isEmpty() || modelId.isEmpty()) {
                    null
                } else {
                    ProviderProfile(
                        id = id,
                        name = name,
                        baseUrl = baseUrl,
                        modelId = modelId,
                        favoriteModels = favs,
                    )
                }
            } ?: emptyList()

            val instructions = (root["instructions"] as? List<*>)?.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val id = (map["id"] as? String)?.trim().orEmpty()
                val name = (map["name"] as? String)?.trim().orEmpty()
                val body = (map["body"] as? String).orEmpty()
                if (id.isEmpty() || name.isEmpty()) {
                    null
                } else {
                    InstructionProfile(id = id, name = name, body = body)
                }
            } ?: emptyList()

            val snippets = (root["snippets"] as? List<*>)?.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val id = (map["id"] as? String)?.trim().orEmpty()
                val title = (map["title"] as? String)?.trim().orEmpty()
                val body = (map["body"] as? String)?.trim().orEmpty()
                val includeTrigger = (map["includeTriggerWord"] as? Boolean) ?: false
                if (id.isEmpty() || title.isEmpty() || body.isEmpty()) {
                    null
                } else {
                    PromptSnippet(
                        id = id,
                        title = title,
                        body = body,
                        includeTriggerWord = includeTrigger,
                    )
                }
            } ?: emptyList()

            val recentPrompts = (root["recentPrompts"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }
                ?.distinct()
                ?.take(10)
                ?: emptyList()

            val recentDirections = (root["recentDirections"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }
                ?.distinct()
                ?.take(8)
                ?: emptyList()

            val timerPresetsSeconds = if (root.containsKey("timerPresetsSeconds")) {
                (root["timerPresetsSeconds"] as? List<*>)
                    ?.mapNotNull { (it as? Number)?.toInt()?.takeIf { sec -> sec in 1..59_999 } }
                    ?.distinct()
                    ?.sorted()
                    ?: emptyList()
            } else {
                null
            }

            val preferredReturnImeId = (root["preferredReturnImeId"] as? String)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

            val timerPillEnabled = (root["timerPillEnabled"] as? Boolean) ?: true
            val timerPillX = (root["timerPillX"] as? Number)?.toInt()
            val timerPillY = (root["timerPillY"] as? Number)?.toInt()

            PersistedAiConfig(
                providers = providers,
                activeProviderId = activeProviderId,
                instructions = instructions,
                activeInstructionId = activeInstructionId,
                defaultPromptCount = defaultCount,
                reasoningEffort = reasoningEffort,
                triggerWords = triggerWords,
                activeTriggerWord = activeTriggerWord,
                triggerWordEnabled = triggerWordEnabled,
                snippets = snippets,
                recentPrompts = recentPrompts,
                recentDirections = recentDirections,
                timerPresetsSeconds = timerPresetsSeconds,
                preferredReturnImeId = preferredReturnImeId,
                timerPillEnabled = timerPillEnabled,
                timerPillX = timerPillX,
                timerPillY = timerPillY,
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Exports ONLY Instruction Profiles (v1 format, zero provider URLs or API keys).
     */
    fun encodeInstructionExport(instructions: List<InstructionProfile>): String {
        val sb = StringBuilder(256)
        sb.append("{\n  \"format\": ")
        appendJsonString(sb, EXPORT_FORMAT_TAG)
        sb.append(",\n  \"profiles\": [")
        instructions.forEachIndexed { idx, inst ->
            if (idx > 0) sb.append(',')
            sb.append("\n    {\n      \"id\": ")
            appendJsonString(sb, inst.id)
            sb.append(",\n      \"name\": ")
            appendJsonString(sb, inst.name)
            sb.append(",\n      \"body\": ")
            appendJsonString(sb, inst.body)
            sb.append("\n    }")
        }
        if (instructions.isNotEmpty()) sb.append("\n  ")
        sb.append("]\n}\n")
        return sb.toString()
    }

    fun decodeInstructionExport(json: String): List<InstructionProfile>? {
        if (json.isBlank()) return null
        return try {
            val parsed = SimpleJsonParser(json).parseValue() ?: return null
            val rawList = when (parsed) {
                is Map<*, *> -> (parsed["profiles"] as? List<*>) ?: (parsed["instructions"] as? List<*>)
                is List<*> -> parsed
                else -> null
            } ?: return null

            parseInstructionList(rawList)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Expanded Safe Backup JSON (v2): Instruction Profiles + Saved Snippets +
     * Trigger Word Chips + Favorite Model IDs. Strictly ZERO API keys or base URLs.
     */
    fun encodeSafeBackup(payload: SafeBackupPayload): String {
        val sb = StringBuilder(512)
        sb.append("{\n  \"format\": ")
        appendJsonString(sb, EXPORT_FORMAT_TAG_V2)

        sb.append(",\n  \"profiles\": [")
        payload.instructions.forEachIndexed { idx, inst ->
            if (idx > 0) sb.append(',')
            sb.append("\n    {\n      \"id\": ")
            appendJsonString(sb, inst.id)
            sb.append(",\n      \"name\": ")
            appendJsonString(sb, inst.name)
            sb.append(",\n      \"body\": ")
            appendJsonString(sb, inst.body)
            sb.append("\n    }")
        }
        if (payload.instructions.isNotEmpty()) sb.append("\n  ")

        sb.append("],\n  \"snippets\": [")
        payload.snippets.forEachIndexed { idx, snip ->
            if (idx > 0) sb.append(',')
            sb.append("\n    {\n      \"id\": ")
            appendJsonString(sb, snip.id)
            sb.append(",\n      \"title\": ")
            appendJsonString(sb, snip.title)
            sb.append(",\n      \"body\": ")
            appendJsonString(sb, snip.body)
            sb.append(",\n      \"includeTriggerWord\": ").append(snip.includeTriggerWord)
            sb.append("\n    }")
        }
        if (payload.snippets.isNotEmpty()) sb.append("\n  ")

        sb.append("],\n  \"triggerWords\": [")
        payload.triggerWords.forEachIndexed { idx, tw ->
            if (idx > 0) sb.append(", ")
            appendJsonString(sb, tw)
        }

        sb.append("],\n  \"favoriteModels\": [")
        payload.favoriteModels.forEachIndexed { idx, entry ->
            if (idx > 0) sb.append(',')
            sb.append("\n    {\n      \"providerId\": ")
            appendNullableString(sb, entry.providerId)
            sb.append(",\n      \"providerName\": ")
            appendJsonString(sb, entry.providerName)
            sb.append(",\n      \"models\": [")
            entry.models.forEachIndexed { mIdx, model ->
                if (mIdx > 0) sb.append(", ")
                appendJsonString(sb, model)
            }
            sb.append("]\n    }")
        }
        if (payload.favoriteModels.isNotEmpty()) sb.append("\n  ")
        sb.append("]\n}\n")
        return sb.toString()
    }

    fun decodeSafeBackup(json: String): SafeBackupPayload? {
        if (json.isBlank()) return null
        return try {
            val parsed = SimpleJsonParser(json).parseValue() ?: return null
            if (parsed is List<*>) {
                return SafeBackupPayload(instructions = parseInstructionList(parsed))
            }
            val map = parsed as? Map<*, *> ?: return null
            val rawProfiles = (map["profiles"] as? List<*>) ?: (map["instructions"] as? List<*>)
            val rawSnippets = map["snippets"] as? List<*>
            val rawTriggers = map["triggerWords"] as? List<*>
            val rawFavModels = map["favoriteModels"] as? List<*>

            if (rawProfiles == null && rawSnippets == null && rawTriggers == null && rawFavModels == null) {
                return null
            }

            val instructions = rawProfiles?.let { parseInstructionList(it) } ?: emptyList()
            val snippets = rawSnippets?.mapNotNull { item ->
                val sMap = item as? Map<*, *> ?: return@mapNotNull null
                val title = (sMap["title"] as? String)?.trim().orEmpty()
                val body = (sMap["body"] as? String)?.trim().orEmpty()
                if (title.isEmpty() || body.isEmpty()) return@mapNotNull null
                val includeTrigger = (sMap["includeTriggerWord"] as? Boolean) ?: false
                val id = (sMap["id"] as? String)?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: "snip_${title.lowercase().replace(Regex("[^a-z0-9]+"), "_")}_${body.hashCode().toUInt()}"
                PromptSnippet(id = id, title = title, body = body, includeTriggerWord = includeTrigger)
            } ?: emptyList()

            val triggerWords = rawTriggers
                ?.mapNotNull { PromptTextComposer.normalizeTriggerWord(it as? String) }
                ?.distinct()
                ?: emptyList()

            val favoriteModels = rawFavModels?.mapNotNull { item ->
                val fMap = item as? Map<*, *> ?: return@mapNotNull null
                val providerId = (fMap["providerId"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                val providerName = (fMap["providerName"] as? String)?.trim().orEmpty()
                val models = (fMap["models"] as? List<*>)
                    ?.mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }
                    ?.distinct()
                    ?: emptyList()
                if (models.isEmpty() || (providerId == null && providerName.isEmpty())) {
                    null
                } else {
                    SafeBackupFavoriteModels(
                        providerId = providerId,
                        providerName = providerName,
                        models = models,
                    )
                }
            } ?: emptyList()

            SafeBackupPayload(
                instructions = instructions,
                snippets = snippets,
                triggerWords = triggerWords,
                favoriteModels = favoriteModels,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseInstructionList(rawList: List<*>): List<InstructionProfile> {
        return rawList.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val name = (map["name"] as? String)?.trim().orEmpty()
            val body = (map["body"] as? String).orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val id = (map["id"] as? String)?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "inst_${name.lowercase().replace(Regex("[^a-z0-9]+"), "_")}_${body.hashCode().toUInt()}"
            InstructionProfile(id = id, name = name, body = body)
        }
    }

    fun appendNullableString(sb: StringBuilder, value: String?) {
        if (value == null) sb.append("null") else appendJsonString(sb, value)
    }

    fun appendJsonString(sb: StringBuilder, value: String) {
        sb.append('"')
        for (i in value.indices) {
            when (val ch = value[i]) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append("\\u")
                        sb.append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
    }
}

/**
 * Compact, pure-Kotlin RFC-8259 JSON parser shared by AI config persistence and OpenAI SSE streaming.
 */
internal class SimpleJsonParser(private val input: String) {
    private var pos = 0

    fun parseValue(): Any? {
        skipWhitespace()
        if (pos >= input.length) return null
        return when (input[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't' -> if (input.startsWith("true", pos)) { pos += 4; true } else null
            'f' -> if (input.startsWith("false", pos)) { pos += 5; false } else null
            'n' -> if (input.startsWith("null", pos)) { pos += 4; null } else null
            '-', in '0'..'9' -> parseNumber()
            else -> null
        }
    }

    private fun parseObject(): Map<String, Any?>? {
        if (!consume('{')) return null
        val map = LinkedHashMap<String, Any?>()
        skipWhitespace()
        if (consume('}')) return map
        while (true) {
            skipWhitespace()
            val key = parseString() ?: return null
            skipWhitespace()
            if (!consume(':')) return null
            val value = parseValue()
            map[key] = value
            skipWhitespace()
            if (consume(',')) continue
            if (consume('}')) return map
            return null
        }
    }

    private fun parseArray(): List<Any?>? {
        if (!consume('[')) return null
        val list = ArrayList<Any?>()
        skipWhitespace()
        if (consume(']')) return list
        while (true) {
            val value = parseValue()
            list.add(value)
            skipWhitespace()
            if (consume(',')) continue
            if (consume(']')) return list
            return null
        }
    }

    private fun parseString(): String? {
        if (!consume('"')) return null
        val sb = StringBuilder()
        while (pos < input.length) {
            val ch = input[pos++]
            when (ch) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (pos >= input.length) return null
                    when (val esc = input[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > input.length) return null
                            val code = input.substring(pos, pos + 4).toIntOrNull(16) ?: return null
                            sb.append(code.toChar())
                            pos += 4
                        }
                        else -> return null
                    }
                }
                else -> sb.append(ch)
            }
        }
        return null
    }

    private fun parseNumber(): Number? {
        val start = pos
        if (pos < input.length && input[pos] == '-') pos++
        while (pos < input.length && input[pos].isDigit()) pos++
        var isFloat = false
        if (pos < input.length && input[pos] == '.') {
            isFloat = true
            pos++
            while (pos < input.length && input[pos].isDigit()) pos++
        }
        if (pos < input.length && (input[pos] == 'e' || input[pos] == 'E')) {
            isFloat = true
            pos++
            if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) pos++
            while (pos < input.length && input[pos].isDigit()) pos++
        }
        val raw = input.substring(start, pos)
        return if (isFloat) raw.toDoubleOrNull() else raw.toLongOrNull() ?: raw.toDoubleOrNull()
    }

    private fun skipWhitespace() {
        while (pos < input.length && input[pos].isWhitespace()) pos++
    }

    private fun consume(expected: Char): Boolean {
        if (pos < input.length && input[pos] == expected) {
            pos++
            return true
        }
        return false
    }
}
