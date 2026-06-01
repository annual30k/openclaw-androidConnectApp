package com.rethinkingstudio.clawlink.core.network.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryItemSerializerTest {
    @Test
    fun decodesContentArrayAsContentBlocksWhenBlocksContainExtraFields() {
        val response = Json.decodeFromString<ChatHistoryResponse>(
            """
            {
              "items": [
                {
                  "id": "msg-1",
                  "role": "assistant",
                  "content": [
                    {
                      "type": "output_text",
                      "text": "done",
                      "annotations": []
                    }
                  ],
                  "created_at": "2026-05-08T14:20:15Z"
                }
              ]
            }
            """.trimIndent()
        )

        val item = response.items.single()
        assertEquals("assistant", item.role)
        assertEquals("2026-05-08T14:20:15Z", item.createdAt)
        assertEquals("output_text", item.contentBlocks?.single()?.type)
        assertEquals("done", item.contentBlocks?.single()?.text)
    }

    @Test
    fun decodesSnakeCaseContentBlocks() {
        val response = Json.decodeFromString<ChatHistoryResponse>(
            """
            {
              "items": [
                {
                  "id": "msg-2",
                  "role": "tool",
                  "content_blocks": [
                    {
                      "type": "tool_result",
                      "tool_call_id": "call-1",
                      "text": "ok",
                      "preview": "ok",
                      "has_full_detail": true,
                      "detail_truncated": false
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val block = response.items.single().contentBlocks?.single()
        assertTrue(block?.isToolResultBlock == true)
        assertEquals("call-1", block?.toolCallId)
        assertEquals("ok", block?.preview)
        assertEquals(true, block?.hasFullDetail)
        assertEquals(false, block?.detailTruncated)
    }

    @Test
    fun decodesSnakeCaseHistoryPageMetadata() {
        val response = Json.decodeFromString<ChatHistoryResponse>(
            """
            {
              "items": [],
              "has_more": true,
              "next_cursor": "seq:101",
              "newest_cursor": "seq:200"
            }
            """.trimIndent()
        )

        assertTrue(response.hasMore)
        assertEquals("seq:101", response.nextCursor)
        assertEquals("seq:200", response.newestCursor)
    }
}
