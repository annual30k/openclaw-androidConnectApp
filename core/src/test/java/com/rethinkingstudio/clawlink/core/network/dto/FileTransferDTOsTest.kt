package com.rethinkingstudio.clawlink.core.network.dto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class FileTransferDTOsTest {
    @Test
    fun fileUploadInitRequestSerializesSourceRunIdAndIdempotencyKey() {
        val encoded = Json.encodeToString(
            FileUploadInitRequest(
                sessionKey = "main",
                fileName = "report.pdf",
                mimeType = "application/pdf",
                sizeBytes = 123,
                sha256 = "abc",
                sourceRunId = "client-run-file-1",
                idempotencyKey = "attachment-draft-1"
            )
        )

        val obj = Json.parseToJsonElement(encoded).jsonObject
        assertEquals("client-run-file-1", obj["sourceRunId"]?.jsonPrimitive?.content)
        assertEquals("attachment-draft-1", obj["idempotencyKey"]?.jsonPrimitive?.content)
    }
}
