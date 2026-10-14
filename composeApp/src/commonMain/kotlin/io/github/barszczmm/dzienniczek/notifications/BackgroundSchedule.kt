package io.github.barszczmm.dzienniczek.notifications

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * When the background checks run: about once an hour (random delay, so requests don't
 * hit the servers at a regular rhythm), only during the day.
 */
object BackgroundSchedule {
    /** Random delay between checks: 50–70 minutes (≈1 hour on average). */
    const val MIN_DELAY_MINUTES = 50
    const val MAX_DELAY_MINUTES = 70

    /** Checks run from [START_HOUR]:00 to [END_HOUR]:00 local time. */
    const val START_HOUR = 6
    const val END_HOUR = 20

    /** Spread of the first morning check after [START_HOUR]:00. */
    private const val MORNING_SPREAD_MINUTES = 20

    fun isActive(now: Instant = Clock.System.now(), timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean =
        now.toLocalDateTime(timeZone).hour in START_HOUR until END_HOUR

    /** Minutes until the next check: a random delay, moved to the next morning when it would fall at night. */
    fun nextDelayMinutes(now: Instant = Clock.System.now(), timeZone: TimeZone = TimeZone.currentSystemDefault()): Long {
        var next = now + Random.nextInt(MIN_DELAY_MINUTES, MAX_DELAY_MINUTES + 1).minutes
        if (!isActive(next, timeZone)) {
            val local = next.toLocalDateTime(timeZone)
            val minuteOfDay = local.hour * 60 + local.minute
            val untilMorning = if (local.hour >= END_HOUR) {
                24 * 60 - minuteOfDay + START_HOUR * 60
            } else {
                START_HOUR * 60 - minuteOfDay
            }
            next += (untilMorning + Random.nextInt(0, MORNING_SPREAD_MINUTES + 1)).minutes
        }
        return (next - now).inWholeMinutes.coerceAtLeast(1)
    }
}
