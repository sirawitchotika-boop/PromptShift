package com.promptshift.app.data

data class PersistedBatchSnapshot(
    val prompts: List<String> = emptyList(),
    val currentIndex: Int = 0,
    val copiedIndices: Set<Int> = emptySet(),
    val overlayX: Int = 0,
    val overlayY: Int = 180,
    val hasSavedPosition: Boolean = false,
    val isCollapsed: Boolean = false,
)

/**
 * Pure-Kotlin JSON codec for persisting large prompt batches (40k–500k+ chars)
 * with zero external JSON library dependencies and full JVM testability.
 */
object PromptJsonCodec {

    fun encode(snapshot: PersistedBatchSnapshot): String {
        val estimatedSize = snapshot.prompts.sumOf { it.length + 8 } + 192
        val sb = StringBuilder(estimatedSize)
        sb.append('{')
        sb.append("\"currentIndex\":").append(snapshot.currentIndex).append(',')
        sb.append("\"overlayX\":").append(snapshot.overlayX).append(',')
        sb.append("\"overlayY\":").append(snapshot.overlayY).append(',')
        sb.append("\"hasSavedPosition\":").append(snapshot.hasSavedPosition).append(',')
        sb.append("\"isCollapsed\":").append(snapshot.isCollapsed).append(',')
        sb.append("\"copiedIndices\":[")
        snapshot.copiedIndices.sorted().forEachIndexed { idx, value ->
            if (idx > 0) sb.append(',')
            sb.append(value)
        }
        sb.append("],\"prompts\":[")
        snapshot.prompts.forEachIndexed { idx, prompt ->
            if (idx > 0) sb.append(',')
            appendJsonString(sb, prompt)
        }
        sb.append("]}")
        return sb.toString()
    }

    fun decode(json: String): PersistedBatchSnapshot? {
        if (json.isBlank()) return null
        return try {
            val reader = JsonReader(json)
            reader.skipWhitespace()
            if (!reader.consumeChar('{')) return null

            var prompts: List<String> = emptyList()
            var currentIndex = 0
            var copiedIndices: Set<Int> = emptySet()
            var overlayX = 0
            var overlayY = 180
            var hasSavedPosition = false
            var isCollapsed = false

            reader.skipWhitespace()
            if (!reader.peekChar('}')) {
                while (true) {
                    reader.skipWhitespace()
                    val key = reader.readString() ?: return null
                    reader.skipWhitespace()
                    if (!reader.consumeChar(':')) return null
                    reader.skipWhitespace()

                    when (key) {
                        "currentIndex" -> currentIndex = reader.readInt() ?: return null
                        "overlayX" -> overlayX = reader.readInt() ?: return null
                        "overlayY" -> overlayY = reader.readInt() ?: return null
                        "hasSavedPosition" -> hasSavedPosition = reader.readBoolean() ?: return null
                        "isCollapsed" -> isCollapsed = reader.readBoolean() ?: return null
                        "copiedIndices" -> copiedIndices = reader.readIntList()?.toSet() ?: return null
                        "prompts" -> prompts = reader.readStringList() ?: return null
                        else -> if (!reader.skipValue()) return null
                    }

                    reader.skipWhitespace()
                    if (reader.consumeChar(',')) {
                        continue
                    } else if (reader.peekChar('}')) {
                        break
                    } else {
                        return null
                    }
                }
            }
            if (!reader.consumeChar('}')) return null

            PersistedBatchSnapshot(
                prompts = prompts,
                currentIndex = currentIndex,
                copiedIndices = copiedIndices,
                overlayX = overlayX,
                overlayY = overlayY,
                hasSavedPosition = hasSavedPosition,
                isCollapsed = isCollapsed,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun appendJsonString(sb: StringBuilder, value: String) {
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

    private class JsonReader(private val input: String) {
        private var pos = 0

        fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) {
                pos++
            }
        }

        fun peekChar(expected: Char): Boolean = pos < input.length && input[pos] == expected

        fun consumeChar(expected: Char): Boolean {
            if (pos < input.length && input[pos] == expected) {
                pos++
                return true
            }
            return false
        }

        fun readString(): String? {
            if (!consumeChar('"')) return null
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
                                val hex = input.substring(pos, pos + 4)
                                val code = hex.toIntOrNull(16) ?: return null
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

        fun readInt(): Int? {
            val start = pos
            if (pos < input.length && input[pos] == '-') pos++
            val digitsStart = pos
            while (pos < input.length && input[pos].isDigit()) pos++
            if (pos == digitsStart) return null
            return input.substring(start, pos).toIntOrNull()
        }

        fun readBoolean(): Boolean? {
            if (input.startsWith("true", pos)) {
                pos += 4
                return true
            }
            if (input.startsWith("false", pos)) {
                pos += 5
                return false
            }
            return null
        }

        fun readIntList(): List<Int>? {
            if (!consumeChar('[')) return null
            val list = mutableListOf<Int>()
            skipWhitespace()
            if (consumeChar(']')) return list
            while (true) {
                skipWhitespace()
                val value = readInt() ?: return null
                list.add(value)
                skipWhitespace()
                if (consumeChar(',')) continue
                if (consumeChar(']')) return list
                return null
            }
        }

        fun readStringList(): List<String>? {
            if (!consumeChar('[')) return null
            val list = mutableListOf<String>()
            skipWhitespace()
            if (consumeChar(']')) return list
            while (true) {
                skipWhitespace()
                val value = readString() ?: return null
                list.add(value)
                skipWhitespace()
                if (consumeChar(',')) continue
                if (consumeChar(']')) return list
                return null
            }
        }

        fun skipValue(): Boolean {
            skipWhitespace()
            if (pos >= input.length) return false
            when (input[pos]) {
                '"' -> return readString() != null
                't', 'f' -> return readBoolean() != null
                'n' -> {
                    return if (input.startsWith("null", pos)) {
                        pos += 4
                        true
                    } else {
                        false
                    }
                }
                '[' -> {
                    pos++
                    skipWhitespace()
                    if (consumeChar(']')) return true
                    while (true) {
                        if (!skipValue()) return false
                        skipWhitespace()
                        if (consumeChar(',')) continue
                        return consumeChar(']')
                    }
                }
                '{' -> {
                    pos++
                    skipWhitespace()
                    if (consumeChar('}')) return true
                    while (true) {
                        skipWhitespace()
                        if (readString() == null) return false
                        skipWhitespace()
                        if (!consumeChar(':')) return false
                        if (!skipValue()) return false
                        skipWhitespace()
                        if (consumeChar(',')) continue
                        return consumeChar('}')
                    }
                }
                else -> {
                    val start = pos
                    if (input[pos] == '-') pos++
                    while (pos < input.length && (input[pos].isDigit() || input[pos] in ".eE+-")) {
                        pos++
                    }
                    return pos > start
                }
            }
        }
    }
}
