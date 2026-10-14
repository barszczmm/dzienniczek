package io.github.barszczmm.dzienniczek.notifications

import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Spaces out the requests of one background check with a random pause of a few seconds,
 * so checking several students and kinds doesn't send a burst of requests to the journals.
 * One instance per check run; the first [pause] returns right away.
 */
class RequestPacer(
    private val minMillis: Long = 2_000,
    private val maxMillis: Long = 6_000
) {
    private var first = true

    suspend fun pause() {
        if (first) {
            first = false
            return
        }
        delay(Random.nextLong(minMillis, maxMillis + 1))
    }
}
