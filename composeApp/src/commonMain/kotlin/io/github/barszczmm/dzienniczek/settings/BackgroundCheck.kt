package io.github.barszczmm.dzienniczek.settings

/** What the background check looks at. Each kind gets its own notification channel. */
enum class BackgroundCheck(val label: String) {
    MESSAGES("Wiadomości"),
    GRADES("Oceny"),
    EXAMS("Sprawdziany"),
    HOMEWORK("Zadania domowe"),
    NOTES("Uwagi"),
    ANNOUNCEMENTS("Ogłoszenia"),
    ATTENDANCE("Frekwencja");

    companion object {
        /** Nothing is checked until the user ticks it. */
        val DEFAULT: Set<BackgroundCheck> = emptySet()
    }
}
