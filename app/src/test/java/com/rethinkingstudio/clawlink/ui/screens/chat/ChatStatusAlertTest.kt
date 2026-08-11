package com.rethinkingstudio.clawlink.ui.screens.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatStatusAlertTest {
    @Test
    fun noOperationMessageProducesNoDialog() {
        val alert = resolveChatStatusAlert(
            chatErrorMessage = null,
            gatewayErrorMessage = null,
            composerNotice = null
        )

        assertNull(alert.message)
        assertFalse(alert.isError)
    }

    @Test
    fun failedChatOperationRemainsAnErrorDialog() {
        val alert = resolveChatStatusAlert(
            chatErrorMessage = "发送失败",
            gatewayErrorMessage = null,
            composerNotice = "已恢复连接"
        )

        assertEquals("发送失败", alert.message)
        assertTrue(alert.isError)
    }

    @Test
    fun composerNoticeRemainsANonErrorDialog() {
        val alert = resolveChatStatusAlert(
            chatErrorMessage = null,
            gatewayErrorMessage = null,
            composerNotice = "请先选择模型"
        )

        assertEquals("请先选择模型", alert.message)
        assertFalse(alert.isError)
    }

    @Test
    fun compositionCancellationIsNeverShownAsAUserFacingError() {
        val alert = resolveChatStatusAlert(
            chatErrorMessage = null,
            gatewayErrorMessage = "The coroutine scope left the composition",
            composerNotice = null
        )

        assertNull(alert.message)
        assertFalse(alert.isError)
    }

    @Test
    fun lifecycleCancellationDoesNotHideARealComposerNotice() {
        val alert = resolveChatStatusAlert(
            chatErrorMessage = "The coroutine scope left the composition",
            gatewayErrorMessage = null,
            composerNotice = "请先选择模型"
        )

        assertEquals("请先选择模型", alert.message)
        assertFalse(alert.isError)
    }
}
