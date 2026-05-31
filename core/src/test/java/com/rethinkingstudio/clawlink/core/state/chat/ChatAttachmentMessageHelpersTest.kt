package com.rethinkingstudio.clawlink.core.state.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatAttachmentMessageHelpersTest {
    @Test
    fun stripsRelayMediaAttachmentReferencesFromChatMessageText() {
        val text = """
            分析一下这张照片

            [media attached: /Users/example/.openclaw/media/outbound/run/photo.jpg (image/jpeg) | /Users/example/.openclaw/media/outbound/run/photo.jpg]
        """.trimIndent()

        assertEquals("分析一下这张照片", sanitizeChatMessageText(text))
    }

    @Test
    fun extractsRelayMediaAttachmentReferenceFileNames() {
        val text = """
            分析一下这张照片

            [media attached: /Users/example/.openclaw/media/outbound/run/photo.jpg (image/jpeg) | /Users/example/.openclaw/media/outbound/run/photo.jpg]
        """.trimIndent()

        assertEquals(listOf("photo.jpg"), chatMediaAttachmentReferenceFileNames(text))
    }

    @Test
    fun stripsCompactMediaUriAttachmentReferencesFromChatMessageText() {
        val text = """
            分析一下这张图片

            [media attached: media://inbound/album-8E28059F-104B-43E1-8059-2E97E07F0E1B---d786f4a0-bb83-4853-97ae-cb7a604326e0.heic]
        """.trimIndent()

        assertEquals("分析一下这张图片", sanitizeChatMessageText(text))
        assertEquals(
            listOf("album-8E28059F-104B-43E1-8059-2E97E07F0E1B---d786f4a0-bb83-4853-97ae-cb7a604326e0.heic"),
            chatMediaAttachmentReferenceFileNames(text)
        )
    }

    @Test
    fun stripsRelayFileAttachmentReferencesFromChatMessageText() {
        val text = """
            请看看这个文件

            [file attached: /tmp/report.pdf]
        """.trimIndent()

        assertEquals("请看看这个文件", sanitizeChatMessageText(text))
    }
}
