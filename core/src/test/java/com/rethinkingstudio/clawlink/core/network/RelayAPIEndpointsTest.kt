package com.rethinkingstudio.clawlink.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class RelayAPIEndpointsTest {
    @Test
    fun backupPathIdsAreEncodedAsSinglePathSegment() {
        val backupId = "/Users/qiuqiquan/hermes-backup-20260521.zip"

        assertEquals(
            "/api/mobile/gateways/gw_1/backups/%2FUsers%2Fqiuqiquan%2Fhermes-backup-20260521.zip/restore",
            APIEndpoints.Mobile.Backup.restore("gw_1", backupId).path
        )
        assertEquals(
            "/api/mobile/gateways/gw_1/backups/%2FUsers%2Fqiuqiquan%2Fhermes-backup-20260521.zip",
            APIEndpoints.Mobile.Backup.delete("gw_1", backupId).path
        )
        assertEquals(
            "/api/mobile/gateways/gw_1/backups/%2FUsers%2Fqiuqiquan%2Fhermes-backup-20260521.zip",
            APIEndpoints.Mobile.Backup.update("gw_1", backupId).path
        )
    }

    @Test
    fun gatewayIdsAndSessionKeysAreEncoded() {
        val gatewayId = "gw/one two"

        assertEquals(
            "/api/mobile/gateways/gw%2Fone%20two/models",
            APIEndpoints.Mobile.Gateway.models(gatewayId).path
        )
        assertEquals(
            "/api/mobile/gateways/gw%2Fone%20two/tasks/task%2Fmain%201",
            APIEndpoints.Mobile.Task.update(gatewayId, "task/main 1").path
        )
        assertEquals(
            listOf("sessionKey" to "agent%2Fmain%201", "deleteTranscript" to "true"),
            APIEndpoints.Mobile.Chat.deleteSession(gatewayId, "agent/main 1", deleteTranscript = true).queryItems
        )
    }

    @Test
    fun slashCommandQueryIsEncoded() {
        val endpoint = APIEndpoints.Mobile.Gateway.slashCommands("gw_1", "/cha list", limit = 16, offset = 32)

        assertEquals("/api/mobile/gateways/gw_1/slash-commands", endpoint.path)
        assertEquals(
            listOf("query" to "%2Fcha%20list", "limit" to "16", "offset" to "32"),
            endpoint.queryItems
        )
    }

    @Test
    fun chatHistoryIncludesPaginationQueryItemsInOrder() {
        val endpoint = APIEndpoints.Mobile.Chat.history(
            gatewayID = "gw_1",
            sessionKey = "main",
            limit = 100,
            cursor = "seq:151",
            direction = "older"
        )

        assertEquals("/api/mobile/gateways/gw_1/chat/history", endpoint.path)
        assertEquals(
            listOf(
                "sessionKey" to "main",
                "limit" to "100",
                "cursor" to "seq%3A151",
                "direction" to "older"
            ),
            endpoint.queryItems
        )
    }

    @Test
    fun chatHistoryOmitsBlankCursorAndKeepsDefaultDirection() {
        val endpoint = APIEndpoints.Mobile.Chat.history(
            gatewayID = "gw_1",
            sessionKey = "main",
            limit = 100,
            cursor = "   "
        )

        assertEquals(
            listOf(
                "sessionKey" to "main",
                "limit" to "100",
                "direction" to "older"
            ),
            endpoint.queryItems
        )
    }
}
