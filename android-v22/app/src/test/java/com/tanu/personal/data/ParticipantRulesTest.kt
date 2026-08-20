package com.tanu.personal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParticipantRulesTest {
    @Test
    fun requiresNameAndWhatsappNumber() {
        assertFalse(ParticipantRules.canSave("", "+919876543210"))
        assertFalse(ParticipantRules.canSave("Ravi", ""))
        assertFalse(ParticipantRules.canSave("Ravi", "123"))
        assertTrue(ParticipantRules.canSave("Ravi", "+919876543210"))
    }

    @Test
    fun emailIsOptionalButValidatedWhenPresent() {
        assertTrue(ParticipantRules.canSave("Ravi", "+919876543210", ""))
        assertTrue(ParticipantRules.canSave("Ravi", "+919876543210", "ravi@example.com"))
        assertFalse(ParticipantRules.canSave("Ravi", "+919876543210", "not-an-email"))
    }

    @Test
    fun normalizesCommonPhoneFormatting() {
        assertEquals("+919876543210", ParticipantRules.normalizeWhatsapp("+91 98765-43210"))
        assertEquals("9876543210", ParticipantRules.normalizeWhatsapp("(987) 654-3210"))
    }
}
