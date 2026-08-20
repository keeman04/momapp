package com.tanu.personal.data

object ParticipantRules {
    fun normalizeWhatsapp(value: String): String {
        val trimmed = value.trim()
        val digits = trimmed.filter(Char::isDigit)
        return if (trimmed.startsWith("+") && digits.isNotBlank()) "+$digits" else digits
    }

    fun isValidWhatsapp(value: String): Boolean {
        val digits = normalizeWhatsapp(value).filter(Char::isDigit)
        return digits.length in 7..15
    }

    fun isValidEmail(value: String): Boolean {
        val email = value.trim()
        if (email.isBlank()) return true
        return email.length <= 254 &&
            email.count { it == '@' } == 1 &&
            email.substringAfter('@').contains('.') &&
            !email.startsWith('@') &&
            !email.endsWith('@')
    }

    fun canSave(name: String, whatsapp: String, email: String = ""): Boolean =
        name.trim().isNotBlank() && isValidWhatsapp(whatsapp) && isValidEmail(email)
}
