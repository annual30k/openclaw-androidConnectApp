package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
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
    fun stripsOpenClawMediaControlReferencesFromChatMessageText() {
        val text = """
            桌面截图已发送到你手机上了
            MEDIA:/Users/example/.openclaw/tmp/codex-shot.png
        """.trimIndent()

        assertEquals("桌面截图已发送到你手机上了", sanitizeChatMessageText(text))
    }

    @Test
    fun stripsRelayFileAttachmentReferencesFromChatMessageText() {
        val text = """
            请看看这个文件

            [file attached: /tmp/report.pdf]
        """.trimIndent()

        assertEquals("请看看这个文件", sanitizeChatMessageText(text))
    }

    @Test
    fun sanitizesHermesRuntimeContextFromTextBlocks() {
        val text = """
            昨天打完篮球小腿的前侧很痛这是怎么回事

            [Hermes runtime context]
            Current runtime: model=mimo-v2.5-pro, provider=Xiaomi MiMo.
            If the user asks which model or provider is currently being used, answer from this runtime context.

            [ClawConnect mobile bridge] You are connected to a mobile chat client through ClawConnect.
        """.trimIndent()

        val blocks = sanitizeChatContentBlocks(listOf(RelayChatContentBlock(type = "text", text = text)))

        assertEquals("昨天打完篮球小腿的前侧很痛这是怎么回事", blocks.single().text)
    }
}
