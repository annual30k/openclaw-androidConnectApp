package com.rethinkingstudio.clawlink.core.network.transport

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RelayVoiceSendPayloadTest {
    @Test
    fun buildsChatVoiceSendCommandWithAudioAndContext() {
        val payload = buildChatVoiceSendPayload(
            gatewayId = "gateway-1",
            sessionKey = "session-main",
            requestId = "request-1",
            idempotencyKey = "voice-idem-1",
            audio = VoiceSendAudioPayload(
                fileName = "voice-input.m4a",
                mimeType = "audio/mp4",
                sizeBytes = 12,
                contentBase64 = "YWJjZA=="
            ),
            message = "use this context",
            languageHint = "zh-CN"
        )

        assertEquals("cmd", payload.string("type"))
        assertEquals("request-1", payload.string("id"))
        assertEquals("gateway-1", payload.string("gatewayId"))
        assertEquals("chat.voice.send", payload.string("method"))

        val params = payload["params"]!!.jsonObject
        assertEquals("session-main", params.string("sessionKey"))
        assertEquals("voice-idem-1", params.string("idempotencyKey"))
        assertEquals("use this context", params.string("message"))
        assertEquals("zh-CN", params.string("languageHint"))

        val audio = params["audio"]!!.jsonObject
        assertEquals("voice-input.m4a", audio.string("fileName"))
        assertEquals("audio/mp4", audio.string("mimeType"))
        assertEquals("12", audio["sizeBytes"]!!.jsonPrimitive.content)
        assertEquals("YWJjZA==", audio.string("content"))
    }

    @Test
    fun omitsBlankOptionalVoiceContext() {
        val payload = buildChatVoiceSendPayload(
            gatewayId = "gateway-1",
            sessionKey = "session-main",
            requestId = "request-1",
            idempotencyKey = "voice-idem-1",
            audio = VoiceSendAudioPayload(
                fileName = "voice-input.m4a",
                mimeType = "audio/mp4",
                sizeBytes = 12,
                contentBase64 = "YWJjZA=="
            ),
            message = "  ",
            languageHint = ""
        )

        val params = payload["params"]!!.jsonObject
        assertFalse(params.containsKey("message"))
        assertFalse(params.containsKey("languageHint"))
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content
}
