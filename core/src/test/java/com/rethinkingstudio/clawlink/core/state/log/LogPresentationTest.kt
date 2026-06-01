package com.rethinkingstudio.clawlink.core.state.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LogPresentationTest {
    @Test
    fun systemLogWithZeroFailedCountIsNotPresentedAsError() {
        val entry = parseLogLine("2026-06-02T10:00:00.000Z [sys] health check completed, failed count 0")

        assertNotEquals(LogSeverity.Error, entry.severity)
    }

    @Test
    fun actualFailedLogIsPresentedAsError() {
        val entry = parseLogLine("2026-06-02T10:00:00.000Z [sys] startup failed: missing token")

        assertEquals(LogSeverity.Error, entry.severity)
    }
}
