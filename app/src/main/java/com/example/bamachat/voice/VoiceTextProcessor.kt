package com.example.bamachat.voice

object VoiceTextProcessor {
    private val codeFence = Regex("```[\\s\\S]*?```")
    private val inlineCode = Regex("`([^`]+)`")
    private val markdownLink = Regex("\\[([^]]+)]\\([^)]*\\)")
    private val rawUrl = Regex("https?://\\S+")
    private val citationMarker = Regex("(?:\\[(?:\\d+|Quelle[^]]*)]|【[^】]+】)")
    private val markdownHeading = Regex("(?m)^\\s{0,3}#{1,6}\\s+")
    private val markdownBullet = Regex("(?m)^\\s*(?:[-*+] |\\d+[.)]\\s+)")
    private val markdownEmphasis = Regex("[*_~]{1,3}")
    private val jsonPunctuation = Regex("[{}\\[\\]\"]")
    private val repeatedWhitespace = Regex("\\s+")

    fun sanitize(text: String): String = text
        .replace(codeFence, " ")
        .replace(inlineCode, "$1")
        .replace(markdownLink, "$1")
        .replace(rawUrl, " ")
        .replace(citationMarker, " ")
        .replace(markdownHeading, "")
        .replace(markdownBullet, "")
        .replace(markdownEmphasis, "")
        .replace(jsonPunctuation, " ")
        .replace(repeatedWhitespace, " ")
        .trim()

    fun splitCompleteText(text: String, maxChunkChars: Int = 220): List<String> {
        val sanitized = sanitize(text)
        if (sanitized.isBlank()) return emptyList()
        if (sanitized.length <= maxChunkChars) return listOf(sanitized)

        val chunks = mutableListOf<String>()
        val sentences = sanitized.split(Regex("(?<=[.!?])\\s+"))
        val current = StringBuilder()
        for (sentence in sentences) {
            if (sentence.length > maxChunkChars) {
                flush(current, chunks)
                splitLongClause(sentence, maxChunkChars).forEach(chunks::add)
            } else if (current.isNotEmpty() && current.length + sentence.length + 1 > maxChunkChars) {
                flush(current, chunks)
                current.append(sentence)
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(sentence)
            }
        }
        flush(current, chunks)
        return chunks.filter { it.isNotBlank() }
    }

    private fun splitLongClause(text: String, maxChunkChars: Int): List<String> {
        val remaining = StringBuilder(text.trim())
        val chunks = mutableListOf<String>()
        while (remaining.length > maxChunkChars) {
            val preferredStart = (maxChunkChars * 0.55f).toInt()
            val splitAt = (maxChunkChars downTo preferredStart)
                .firstOrNull { index -> remaining[index - 1] in charArrayOf(' ', ',', ';', ':') }
                ?: maxChunkChars
            chunks += remaining.substring(0, splitAt).trim()
            remaining.delete(0, splitAt)
            while (remaining.isNotEmpty() && remaining.first().isWhitespace()) remaining.deleteCharAt(0)
        }
        if (remaining.isNotBlank()) chunks += remaining.toString().trim()
        return chunks
    }

    private fun flush(current: StringBuilder, destination: MutableList<String>) {
        if (current.isNotBlank()) destination += current.toString().trim()
        current.clear()
    }
}

class StreamingSpeechBuffer(
    private val maxChunkChars: Int = 220,
    private val minimumClauseChars: Int = 72
) {
    private var messageId: String? = null
    private var lastObservedText = ""
    private val pending = StringBuilder()
    private var insideCodeFence = false
    private var fenceRemainder = ""

    fun consume(messageId: String, fullText: String, isFinal: Boolean): List<String> {
        if (this.messageId != messageId) reset(messageId)

        val delta = when {
            fullText.startsWith(lastObservedText) -> fullText.substring(lastObservedText.length)
            lastObservedText.isBlank() -> fullText
            else -> ""
        }
        lastObservedText = fullText
        pending.append(filterCodeFences(delta))

        val chunks = mutableListOf<String>()
        while (true) {
            val boundary = findBoundary(pending, isFinal) ?: break
            val rawChunk = pending.substring(0, boundary).trim()
            pending.delete(0, boundary)
            while (pending.isNotEmpty() && pending.first().isWhitespace()) pending.deleteCharAt(0)
            VoiceTextProcessor.sanitize(rawChunk).takeIf { it.isNotBlank() }?.let(chunks::add)
        }
        return chunks
    }

    fun reset(messageId: String? = null) {
        this.messageId = messageId
        lastObservedText = ""
        pending.clear()
        insideCodeFence = false
        fenceRemainder = ""
    }

    private fun filterCodeFences(delta: String): String {
        if (delta.isEmpty()) return ""
        val source = fenceRemainder + delta
        fenceRemainder = ""
        val visible = StringBuilder()
        var offset = 0
        while (offset < source.length) {
            val fenceAt = source.indexOf("```", startIndex = offset)
            if (fenceAt >= 0) {
                if (!insideCodeFence) visible.append(source.substring(offset, fenceAt))
                insideCodeFence = !insideCodeFence
                offset = fenceAt + 3
                continue
            }
            val tail = source.substring(offset)
            val trailingBackticks = tail.takeLastWhile { it == '`' }.length.coerceAtMost(2)
            if (!insideCodeFence) {
                visible.append(tail.dropLast(trailingBackticks))
            }
            fenceRemainder = tail.takeLast(trailingBackticks)
            break
        }
        return visible.toString()
    }

    private fun findBoundary(text: StringBuilder, isFinal: Boolean): Int? {
        for (index in text.indices) {
            val character = text[index]
            val nextIsBoundary = index == text.lastIndex || text[index + 1].isWhitespace()
            if (character in charArrayOf('.', '!', '?', '\n') && nextIsBoundary) {
                return index + 1
            }
        }
        if (text.length >= maxChunkChars) {
            val preferredStart = minimumClauseChars.coerceAtMost(maxChunkChars - 1)
            return (maxChunkChars downTo preferredStart)
                .firstOrNull { index -> text[index - 1] in charArrayOf(' ', ',', ';', ':') }
                ?: maxChunkChars
        }
        return if (isFinal && text.isNotBlank()) text.length else null
    }
}
