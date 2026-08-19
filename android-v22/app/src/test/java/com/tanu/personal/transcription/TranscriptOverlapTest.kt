package com.tanu.personal.transcription

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptOverlapTest {
    @Test
    fun removesRepeatedBoundaryWordsIgnoringCaseAndPunctuation() {
        val previous = "Please send the revised quotation by Friday."
        val current = "By friday, and confirm it with the vendor."
        assertEquals("and confirm it with the vendor.", TranscriptOverlap.remove(previous, current))
    }

    @Test
    fun keepsCurrentTextWhenNoReliableOverlapExists() {
        val previous = "We approved the design."
        val current = "The vendor will send samples tomorrow."
        assertEquals(current, TranscriptOverlap.remove(previous, current))
    }

    @Test
    fun removesLongerRepeatedPhrase() {
        val previous = "The team agreed to close the vendor quotation by tomorrow EOD"
        val current = "vendor quotation by tomorrow EOD and Suresh will send the mail"
        assertEquals("and Suresh will send the mail", TranscriptOverlap.remove(previous, current))
    }
}
