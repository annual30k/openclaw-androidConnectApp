package com.rethinkingstudio.clawlink.core.state.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatAttachmentUploaderTest {
    @Test
    fun `zero byte attachment still uploads one empty chunk`() {
        assertEquals(listOf(0 to 0), mobileAttachmentChunkRanges(0, 1024))
    }

    @Test
    fun `non empty attachment creates contiguous chunks`() {
        assertEquals(listOf(0 to 2, 2 to 4, 4 to 5), mobileAttachmentChunkRanges(5, 2))
    }
}
