package com.rethinkingstudio.clawlink.core.state.chat

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TimelineEventLogFixtureTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun sharedFixturesReduceToExpectedState() {
        val fixtureDir = fixtureDirectory()
        val fixtures = fixtureDir.listFiles { file -> file.extension == "json" }?.sortedBy { it.name }.orEmpty()
        assertEquals(24, fixtures.size)

        fixtures.forEach { file ->
            val fixture = json.decodeFromString(Fixture.serializer(), file.readText())
            val events = fixture.events.mapNotNull { TimelineEventLog.decodeEvent(it.toString()) }
            fixture.expectedDecodeCount?.let { expected ->
                assertEquals(file.name, expected, events.size)
                return@forEach
            }

            val state = ChatTimelineReducer.reduceAll(ChatTimelineState(), events)
            assertEquals(file.name, fixture.expectedActiveRun, state.hasActiveRun)
            assertEquals(file.name, fixture.expectedMessages.size, state.messages.size)
            fixture.expectedMessages.forEach { expected ->
                val actual = state.messages.firstOrNull { it.id == expected.id }
                assertNotNull(file.name, actual)
                assertEquals(file.name, expected.role, actual?.role?.name)
                assertEquals(file.name, expected.state, actual?.state?.name)
                assertEquals(file.name, expected.content, actual?.content)
            }
            fixture.expectedAttachments.forEach { expected ->
                val actual = state.attachmentsById[expected.id]
                assertNotNull(file.name, actual)
                assertEquals(file.name, expected.state, actual?.state)
            }
            fixture.expectedTools.forEach { expected ->
                val actual = state.toolsById[expected.id]
                assertNotNull(file.name, actual)
                assertEquals(file.name, expected.state, actual?.state)
            }
        }
    }

    private fun fixtureDirectory(): File {
        return listOf(
            File("../docs/timeline-event-log/fixtures"),
            File("docs/timeline-event-log/fixtures"),
            File("../../docs/timeline-event-log/fixtures")
        ).first { it.isDirectory }
    }

    @Serializable
    private data class Fixture(
        val events: List<JsonElement>,
        val expectedDecodeCount: Int? = null,
        val expectedMessages: List<ExpectedMessage> = emptyList(),
        val expectedAttachments: List<ExpectedState> = emptyList(),
        val expectedTools: List<ExpectedState> = emptyList(),
        val expectedActiveRun: Boolean = false
    )

    @Serializable
    private data class ExpectedMessage(
        val id: String,
        val role: String,
        val state: String,
        val content: String
    )

    @Serializable
    private data class ExpectedState(
        val id: String,
        val state: String
    )
}
