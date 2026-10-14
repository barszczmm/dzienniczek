package io.github.barszczmm.dzienniczek.notifications

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * When the background checks run: about once an hour (random delay, so requests don't
 * hit the servers at a regular rhythm), only between the hours set by the user.
 */
object BackgroundSchedule {
    /** Random delay between checks: 50–70 minutes (≈1 hour on average). */
    const val MIN_DELAY_MINUTES = 50
    const val MAX_DELAY_MINUTES = 70

    /** Default hours: checks run from [DEFAULT_START_HOUR]:00 to [DEFAULT_END_HOUR]:00 local time. */
    const val DEFAULT_START_HOUR = 6
    const val DEFAULT_END_HOUR = 20

    /** Spread of the first check after the start hour. */
    private const val MORNING_SPREAD_MINUTES = 20

    /** Whether [now] falls between [startHour]:00 and [endHour]:00 (endHour 24 = midnight). */
    fun isActive(
        startHour: Int,
        endHour: Int,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Boolean = now.toLocalDateTime(timeZone).hour in startHour until endHour

    /** Minutes until the next check: a random delay, moved to the next start hour when it would fall outside the hours. */
    fun nextDelayMinutes(
        startHour: Int,
        endHour: Int,
        now: Instant = Clock.System.now(),
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Long {
        var next = now + Random.nextInt(MIN_DELAY_MINUTES, MAX_DELAY_MINUTES + 1).minutes
        if (!isActive(startHour, endHour, next, timeZone)) {
            val local = next.toLocalDateTime(timeZone)
            val minuteOfDay = local.hour * 60 + local.minute
            val untilMorning = if (local.hour >= endHour) {
                24 * 60 - minuteOfDay + startHour * 60
            } else {
                startHour * 60 - minuteOfDay
            }
            next += (untilMorning + Random.nextInt(0, MORNING_SPREAD_MINUTES + 1)).minutes
        }
        return (next - now).inWholeMinutes.coerceAtLeast(1)
    }
}
