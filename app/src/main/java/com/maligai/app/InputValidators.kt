package com.maligai.app

/** Filters and validates customer name / phone for kadan entry. */
object InputValidators {

    private val nameAllowed = Regex("""[^\p{L}\p{M}\s.\-]""")

    fun filterCustomerName(input: String): String =
        input.replace(nameAllowed, "").take(60)

    fun filterPhone(input: String): String =
        input.filter { it.isDigit() }.take(10)

    fun isValidCustomerName(name: String): Boolean {
        val t = name.trim()
        return t.length in 2..60
    }

    fun isValidPhone(phone: String): Boolean {
        val t = phone.trim()
        return t.isEmpty() || t.length == 10
    }

    fun customerNameError(name: String): String? =
        if (name.isBlank()) "Name required"
        else if (!isValidCustomerName(name)) "Name: 2–60 letters"
        else null

    fun phoneError(phone: String): String? =
        if (!isValidPhone(phone)) "Phone: 10 digits or leave empty"
        else null
}
