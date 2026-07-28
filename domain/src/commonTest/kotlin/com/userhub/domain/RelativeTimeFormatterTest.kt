package com.userhub.domain

import com.userhub.domain.time.formatRelativeTime
import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeFormatterTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `returns just now for timestamps under a minute`() {
        assertEquals("Just now", formatRelativeTime(now - 30_000, now))
    }

    @Test
    fun `returns singular minute for exactly one minute`() {
        assertEquals("1 minute ago", formatRelativeTime(now - 60_000, now))
    }

    @Test
    fun `returns plural minutes for five minutes`() {
        assertEquals("5 minutes ago", formatRelativeTime(now - 5 * 60_000, now))
    }

    @Test
    fun `returns plural minutes close to the hour boundary`() {
        assertEquals("59 minutes ago", formatRelativeTime(now - 59 * 60_000, now))
    }
}
