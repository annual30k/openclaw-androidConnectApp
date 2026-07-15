package com.rethinkingstudio.clawlink.ui.screens.legal

import com.rethinkingstudio.clawlink.ui.screens.legal.models.LEGAL_DOCUMENT_VERSION
import com.rethinkingstudio.clawlink.ui.screens.legal.models.LegalDocumentType
import com.rethinkingstudio.clawlink.ui.screens.legal.models.legalDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegalDocumentTest {
    @Test
    fun legalDocumentsUseTheMiniProgramVersion() {
        LegalDocumentType.entries.forEach { type ->
            assertEquals(LEGAL_DOCUMENT_VERSION, legalDocument(type).version)
        }
    }

    @Test
    fun termsAndPrivacyContainTheExpectedSections() {
        val terms = legalDocument(LegalDocumentType.TERMS)
        val privacy = legalDocument(LegalDocumentType.PRIVACY)

        assertEquals(6, terms.sections.size)
        assertEquals(7, privacy.sections.size)
        assertTrue(terms.sections.all { it.chineseParagraphs.isNotEmpty() && it.englishParagraphs.isNotEmpty() })
        assertTrue(privacy.sections.all { it.chineseParagraphs.isNotEmpty() && it.englishParagraphs.isNotEmpty() })
    }

    @Test
    fun androidLegalCopyContainsNoMiniProgramIdentityClaims() {
        val copy = LegalDocumentType.entries.flatMap { type ->
            val document = legalDocument(type)
            buildList {
                add(document.chineseTitle)
                add(document.englishTitle)
                add(document.chineseSummary)
                add(document.englishSummary)
                document.sections.forEach { section ->
                    add(section.chineseTitle)
                    add(section.englishTitle)
                    addAll(section.chineseParagraphs)
                    addAll(section.englishParagraphs)
                }
            }
        }.joinToString("\n")

        listOf("微信", "小程序", "WeChat", "OpenID", "UnionID", "Mini Program", "AppID").forEach { forbiddenTerm ->
            assertFalse("Android legal copy must not contain $forbiddenTerm", copy.contains(forbiddenTerm, ignoreCase = true))
        }
    }
}
