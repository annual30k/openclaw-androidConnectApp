package com.rethinkingstudio.clawlink.ui.screens.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFileUtilsTest {
    @Test
    fun `opaque ARGB pixels do not force PNG preservation`() {
        assertFalse(ChatFileUtils.hasTransparentPixel(intArrayOf(0xFF112233.toInt(), 0xFFFFFFFF.toInt())))
    }

    @Test
    fun `actual transparent pixel preserves alpha image`() {
        assertTrue(ChatFileUtils.hasTransparentPixel(intArrayOf(0xFF112233.toInt(), 0x7FFFFFFF)))
    }
}
