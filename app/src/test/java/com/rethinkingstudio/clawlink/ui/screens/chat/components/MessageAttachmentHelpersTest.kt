package com.rethinkingstudio.clawlink.ui.screens.chat.components

import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageAttachmentHelpersTest {
    @Test
    fun rewritesLoopbackRelayFileUrlsToConfiguredRelayBaseUrl() {
        val url = resolveFileUrl(
            raw = "http://127.0.0.1:8080/api/mobile/files/file_1",
            relayBaseUrl = "http://10.0.2.2:8080"
        )

        assertEquals("http://10.0.2.2:8080/api/mobile/files/file_1", url)
    }

    @Test
    fun keepsExternalHttpUrlsUnchanged() {
        val url = resolveFileUrl(
            raw = "https://example.com/api/mobile/files/file_1",
            relayBaseUrl = "http://10.0.2.2:8080"
        )

        assertEquals("https://example.com/api/mobile/files/file_1", url)
    }

    @Test
    fun prefersCurrentRelayFileEndpointOverStaleAbsoluteUrlWhenFileIdExists() {
        val block = RelayChatContentBlock(
            type = "image",
            fileId = "file_image_history",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            downloadUrl = "https://old-device.example/api/mobile/files/file_image_history"
        )

        val url = resolveFileDownloadUrl(block, relayBaseUrl = "https://relay.example.com")

        assertEquals("https://relay.example.com/api/mobile/files/file_image_history", url)
    }

    @Test
    fun prefersRelayFileEndpointWhenBlockHasFileIdAndLocalHostPath() {
        val block = RelayChatContentBlock(
            type = "image",
            fileId = "file_image_1",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            downloadUrl = "/Users/example/Desktop/photo.jpg"
        )

        val url = resolveFileDownloadUrl(block, relayBaseUrl = "http://10.0.2.2:8080")

        assertEquals("http://10.0.2.2:8080/api/mobile/files/file_image_1", url)
    }

    @Test
    fun usesRelayFileEndpointWhenOnlyFileIdIsPresent() {
        val block = RelayChatContentBlock(
            type = "image",
            fileId = "file_image_2",
            fileName = "photo.jpg",
            mimeType = "image/jpeg"
        )

        val url = resolveFileDownloadUrl(block, relayBaseUrl = "http://10.0.2.2:8080")

        assertEquals("http://10.0.2.2:8080/api/mobile/files/file_image_2", url)
    }

    @Test
    fun resolvesMediaUriWithEmbeddedFileIdToRelayFileEndpoint() {
        val block = RelayChatContentBlock(
            type = "image",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            downloadUrl = "media://inbound/file_image_3"
        )

        val url = resolveFileDownloadUrl(block, relayBaseUrl = "http://10.0.2.2:8080")

        assertEquals("http://10.0.2.2:8080/api/mobile/files/file_image_3", url)
    }

    @Test
    fun resolvesApiPathWithoutFileIdAgainstRelayBaseUrl() {
        val block = RelayChatContentBlock(
            type = "image",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            downloadUrl = "/api/mobile/files/file_image_4"
        )

        val url = resolveFileDownloadUrl(block, relayBaseUrl = "http://10.0.2.2:8080")

        assertEquals("http://10.0.2.2:8080/api/mobile/files/file_image_4", url)
    }

    @Test
    fun resolvesExplicitPreviewFallbackAgainstRelayBaseUrl() {
        val block = RelayChatContentBlock(
            type = "image",
            fileName = "photo.jpg",
            mimeType = "image/jpeg",
            downloadUrl = "/tmp/missing-photo.jpg",
            downloadPath = "/api/mobile/files/file_image_5"
        )

        val url = resolveFileDownloadUrl(
            block,
            relayBaseUrl = "http://10.0.2.2:8080",
            rawOverride = block.preferredImagePreviewURLString
        )

        assertEquals("http://10.0.2.2:8080/api/mobile/files/file_image_5", url)
    }
}
