package com.rethinkingstudio.clawlink.ui.screens.chat

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ChatTimestampFormatterTest {
    private lateinit var previousLocale: Locale

    @Before
    fun setUp() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
    }

    @After
    fun tearDown() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun formatsRecentIsoTimestampLikeIos() {
        val now = Instant.parse("2026-05-09T04:56:00Z")

        assertEquals("刚刚", formatChatTimestamp("2026-05-09T04:55:30Z", now))
        assertEquals("2分钟前", formatChatTimestamp("2026-05-09T04:54:00Z", now))
    }

    @Test
    fun formatsOlderSameDayTimestampAsHourMinute() {
        val zoneId = ZoneId.systemDefault()
        val now = ZonedDateTime.of(2026, 5, 9, 12, 56, 0, 0, zoneId)
        val messageTime = now.minusHours(2).minusMinutes(5)

        assertEquals(
            messageTime.format(DateTimeFormatter.ofPattern("HH:mm")),
            formatChatTimestamp(messageTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), now.toInstant())
        )
    }

    @Test
    fun formatsSameYearAndCrossYearTimestampLikeIos() {
        val zoneId = ZoneId.systemDefault()
        val now = ZonedDateTime.of(2026, 5, 9, 12, 56, 0, 0, zoneId)

        assertEquals("5月8日 12:56", formatChatTimestamp("2026-05-08T12:56:00", now.toInstant()))
        assertEquals("2025年12月31日 23:59", formatChatTimestamp("2025-12-31T23:59:00", now.toInstant()))
    }
}
