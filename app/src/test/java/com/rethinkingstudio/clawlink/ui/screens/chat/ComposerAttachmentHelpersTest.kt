package com.rethinkingstudio.clawlink.ui.screens.chat

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerAttachmentHelpersTest {
    @Test
    fun filePickerUsesAllMimeTypes() {
        assertArrayEquals(arrayOf("*/*"), attachmentPickerMimeTypes(ComposerAttachmentPickTarget.FILES))
    }

    @Test
    fun imagePickerRestrictsToImages() {
        assertArrayEquals(arrayOf("image/*"), attachmentPickerMimeTypes(ComposerAttachmentPickTarget.IMAGES))
    }

    @Test
    fun albumAndCameraUseImageMimeTypes() {
        assertArrayEquals(arrayOf("image/*"), attachmentPickerMimeTypes(ComposerAttachmentPickTarget.ALBUM))
        assertArrayEquals(arrayOf("image/*"), attachmentPickerMimeTypes(ComposerAttachmentPickTarget.CAMERA))
    }

    @Test
    fun menuTargetsMatchIosOrder() {
        assertEquals(
            listOf(
                ComposerAttachmentPickTarget.ALBUM,
                ComposerAttachmentPickTarget.CAMERA,
                ComposerAttachmentPickTarget.FILES
            ),
            composerAttachmentMenuTargets()
        )
    }

    @Test
    fun detectsImageMimeTypes() {
        assertTrue(isImageMimeType(" image/png "))
        assertFalse(isImageMimeType("application/pdf"))
    }

    @Test
    fun formatsAttachmentSizeLikeComposer() {
        assertEquals("512 B", formatAttachmentSize(512))
        assertEquals("1.5 KB", formatAttachmentSize(1536))
        assertEquals("2.0 MB", formatAttachmentSize(2L * 1024 * 1024))
    }

    @Test
    fun choosesAttachmentSymbolsByMimeType() {
        assertEquals("photo", attachmentSymbolName("image/jpeg"))
        assertEquals("doc.richtext", attachmentSymbolName("application/pdf"))
        assertEquals("archivebox", attachmentSymbolName("application/zip"))
        assertEquals("doc", attachmentSymbolName("application/octet-stream"))
    }

    @Test
    fun composerDraftDerivesImageMetadataAndDisplayText() {
        val draft = ComposerAttachmentDraft(
            fileUri = "/tmp/photo.png",
            fileName = "photo.png",
            mimeType = "image/png",
            sizeBytes = 2048
        )

        assertTrue(draft.isImage)
        assertEquals("2.0 KB", draft.displaySize)
        assertEquals("image/png · 2.0 KB", draft.displaySubtitle)
        assertEquals("photo", draft.symbolName)
    }
}
