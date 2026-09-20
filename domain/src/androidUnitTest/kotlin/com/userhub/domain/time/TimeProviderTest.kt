package com.userhub.domain.time

import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class TimeProviderTest {

    private lateinit var originalZone: TimeZone

    @BeforeTest
    fun setUp() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London")) // BST = UTC+1 in summer
    }

    @AfterTest
    fun tearDown() {
        TimeZone.setDefault(originalZone)
    }

    @Test
    fun `nowEpochMillis matches true wall-clock epoch millis regardless of local timezone offset`() {
        val before = System.currentTimeMillis()
        val now = TimeProvider.nowEpochMillis()
        val after = System.currentTimeMillis()

        assertTrue(now in before..after, "expected $now to be within [$before, $after]")
    }
}
