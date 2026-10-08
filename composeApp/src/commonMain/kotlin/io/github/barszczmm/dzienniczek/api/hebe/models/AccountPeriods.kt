package io.github.barszczmm.dzienniczek.api.hebe.models

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/** School term (1 or 2) of [date]: from the account periods, or Sep–Jan = 1, Feb–Aug = 2. */
fun Account.semesterOf(date: LocalDate): Int {
    periods?.firstOrNull { date >= it.start && date <= it.end }?.number
        ?.takeIf { it == 1 || it == 2 }
        ?.let { return it }
    return if (date.month.number >= 9 || date.month.number == 1) 1 else 2
}

/** First day of the school year that contains [today] (1 September). */
fun schoolYearStart(today: LocalDate): LocalDate =
    LocalDate(if (today.month.number >= 9) today.year else today.year - 1, 9, 1)
