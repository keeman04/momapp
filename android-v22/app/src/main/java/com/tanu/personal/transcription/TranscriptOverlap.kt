package com.tanu.personal.transcription

object TranscriptOverlap {
    fun remove(previous: String, current: String): String {
        if (previous.isBlank() || current.isBlank()) return current.trim()
        val a = previous.trim().split(Regex("\\s+")).takeLast(14)
        val b = current.trim().split(Regex("\\s+"))
        val max = minOf(a.size, b.size, 12)
        for (n in max downTo 2) {
            if (a.takeLast(n).map(::normalize) == b.take(n).map(::normalize)) {
                return b.drop(n).joinToString(" ").trim()
            }
        }
        return current.trim()
    }

    private fun normalize(value: String): String =
        value.lowercase().trim(',', '.', '!', '?', ':', ';', '"', '\'', '(', ')', '[', ']')
}
