package com.promptshift.app.data

import com.promptshift.app.domain.InstructionProfile
import com.promptshift.app.domain.ProviderProfile

data class PersistedAiConfig(
    val providers: List<ProviderProfile> = emptyList(),
    val activeProviderId: String? = null,
    val instructions: List<InstructionProfile> = emptyList(),
    val activeInstructionId: String? = null,
    val defaultPromptCount: Int = 20,
)

/**
 * Pure-Kotlin JSON codec for AI Provider metadata, Instruction Profiles,
 * and Instruction-only Export/Import JSON. API keys are NEVER encoded here.
 */
object AiConfigCodec {

    const val EXPORT_FORMAT_TAG = "promptshift-instructions-v1"

    fun encodeConfig(config: PersistedAiConfig): String {
        val sb = StringBuilder(512)
        sb.append('{')
        sb.append("\"defaultPromptCount\":").append(config.defaultPromptCount).append(',')
        sb.append("\"activeProviderId\":")
        appendNullableString(sb, config.activeProviderId)
        sb.append(",\"activeInstructionId\":")
        appendNullableString(sb, config.activeInstructionId)
        sb.append(",\"providers\":[")
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
            sb.append('}')
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
        sb.append("]}")
        return sb.toString()
    }

    fun decodeConfig(json: String): PersistedAiConfig? {
        if (json.isBlank()) return null
        return try {
            val root = SimpleJsonParser(json).parseValue() as? Map<*, *> ?: return null
            val defaultCount = (root["defaultPromptCount"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 20
            val activeProviderId = root["activeProviderId"] as? String
            val activeInstructionId = root["activeInstructionId"] as? String

            val providers = (root["providers"] as? List<*>)?.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val id = (map["id"] as? String)?.trim().orEmpty()
                val name = (map["name"] as? String)?.trim().orEmpty()
                val baseUrl = (map["baseUrl"] as? String)?.trim().orEmpty()
                val modelId = (map["modelId"] as? String)?.trim().orEmpty()
                if (id.isEmpty() || name.isEmpty() || baseUrl.isEmpty() || modelId.isEmpty()) {
                    null
                } else {
                    ProviderProfile(id = id, name = name, baseUrl = baseUrl, modelId = modelId)
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

            PersistedAiConfig(
                providers = providers,
                activeProviderId = activeProviderId,
                instructions = instructions,
                activeInstructionId = activeInstructionId,
                defaultPromptCount = defaultCount,
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Exports ONLY Instruction Profiles (zero provider URLs or API keys).
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

            rawList.mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                val name = (map["name"] as? String)?.trim().orEmpty()
                val body = (map["body"] as? String).orEmpty()
                if (name.isEmpty()) return@mapNotNull null
                val id = (map["id"] as? String)?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: "inst_${name.lowercase().replace(Regex("[^a-z0-9]+"), "_")}_${body.hashCode().toUInt()}"
                InstructionProfile(id = id, name = name, body = body)
            }
        } catch (_: Exception) {
            null
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
