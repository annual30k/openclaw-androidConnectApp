package com.rethinkingstudio.clawlink.core.network.transport

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class RelayChatSendPayloadTest {
    @Test
    fun buildsChatSendCommandWithImageAttachmentsForAgentAnalysis() {
        val payload = buildChatSendPayload(
            gatewayId = "gateway-1",
            sessionKey = "main",
            content = "分析一下这个图片",
            attachments = listOf(
                RelayChatSendAttachmentPayload(
                    fileId = "file-photo",
                    fileName = "photo.jpg",
                    mimeType = "image/jpeg",
                    sizeBytes = 4,
                    sha256 = "abc123",
                    sourceRunId = "client-run-1"
                )
            ),
            idempotencyKey = "client-run-1",
            requestId = "request-1"
        )

        assertEquals("cmd", payload["type"]!!.jsonPrimitive.content)
        assertEquals("request-1", payload["id"]!!.jsonPrimitive.content)
        assertEquals("gateway-1", payload["gatewayId"]!!.jsonPrimitive.content)
        assertEquals("chat.send", payload["method"]!!.jsonPrimitive.content)

        val params = payload["params"]!!.jsonObject
        assertEquals("main", params["sessionKey"]!!.jsonPrimitive.content)
        assertEquals("分析一下这个图片", params["message"]!!.jsonPrimitive.content)
        assertEquals("client-run-1", params["idempotencyKey"]!!.jsonPrimitive.content)

        val attachment = params["attachments"]!!.jsonArray.single().jsonObject
        assertEquals("file-photo", attachment["fileId"]!!.jsonPrimitive.content)
        assertEquals("photo.jpg", attachment["fileName"]!!.jsonPrimitive.content)
        assertEquals("image/jpeg", attachment["mimeType"]!!.jsonPrimitive.content)
        assertEquals("4", attachment["sizeBytes"]!!.jsonPrimitive.content)
        assertEquals("abc123", attachment["sha256"]!!.jsonPrimitive.content)
        assertEquals("client-run-1", attachment["sourceRunId"]!!.jsonPrimitive.content)
        assertEquals(null, attachment["content"])
    }

    @Test
    fun buildsSessionResetCommandWithTargetSessionKey() {
        val payload = buildSessionResetPayload(
            gatewayId = "gateway-1",
            sessionKey = "mobile-abc123",
            requestId = "request-1"
        )

        assertEquals("cmd", payload["type"]!!.jsonPrimitive.content)
        assertEquals("request-1", payload["id"]!!.jsonPrimitive.content)
        assertEquals("gateway-1", payload["gatewayId"]!!.jsonPrimitive.content)
        assertEquals("sessions.reset", payload["method"]!!.jsonPrimitive.content)

        val params = payload["params"]!!.jsonObject
        assertEquals("mobile-abc123", params["key"]!!.jsonPrimitive.content)
    }
}
