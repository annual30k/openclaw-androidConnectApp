package com.rethinkingstudio.clawlink.ui.screens.chat

import com.rethinkingstudio.clawlink.core.models.chat.ComposerAttachmentDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AttachmentClientRunIdTest {
    private val first = ComposerAttachmentDraft(
        id = "draft-a",
        fileUri = "/tmp/same.png",
        fileName = "same.png",
        mimeType = "image/png",
        sizeBytes = 42,
    )
    private val second = first.copy(id = "draft-b")

    @Test
    fun `same drafts reuse the same run id for retries`() {
        assertEquals(
            stableAttachmentClientRunId("gw-a", "main", listOf(first, second)),
            stableAttachmentClientRunId("gw-a", "main", listOf(first, second)),
        )
    }

    @Test
    fun `attachment order and chat scope create a new run id`() {
        val original = stableAttachmentClientRunId("gw-a", "main", listOf(first, second))
        assertNotEquals(original, stableAttachmentClientRunId("gw-a", "main", listOf(second, first)))
        assertNotEquals(original, stableAttachmentClientRunId("gw-b", "main", listOf(first, second)))
        assertNotEquals(original, stableAttachmentClientRunId("gw-a", "other", listOf(first, second)))
    }
}
