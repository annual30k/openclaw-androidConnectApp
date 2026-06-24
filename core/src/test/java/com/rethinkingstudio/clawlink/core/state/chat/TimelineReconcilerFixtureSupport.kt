package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

private val timelineFixtureJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    isLenient = true
}

internal fun applyFixture(name: String): TimelineReconcileResult {
    val fixture = parseFixture(File(fixtureDir(), name))
    var messages = fixture.initialLocal
    var pending = emptyList<ChatMessage>()
    fixture.events.forEach { event ->
        val result = reconcileTimeline(messages, pending, event)
        messages = result.messages
        pending = result.pending
    }
    return TimelineReconcileResult(messages, pending)
}

internal fun fixtureFiles(): List<File> {
    return fixtureDir()
        .listFiles { file -> file.extension == "json" }
        .orEmpty()
        .sortedBy { it.name }
}

internal fun parseFixture(file: File): FixtureCase {
    val root = timelineFixtureJson.parseToJsonElement(file.readText()).jsonObject
    val sessionKey = root["events"]
        ?.jsonArray
        ?.firstOrNull()
        ?.jsonObject
        ?.get("sessionKey")
        ?.jsonPrimitive
        ?.contentOrNull
        ?: "main"
    val initialLocal = (root["initialLocal"] as? JsonArray)
        ?.map { element ->
            timelineSnapshotMessageToChatMessage(
                canonicalizedFixtureMessage(
                    timelineFixtureJson.decodeFromJsonElement<TimelineSnapshotMessage>(element),
                    sessionKey,
                    0
                ),
                sessionKey
            )
        }
        ?: emptyList()
    val events = root["events"]!!.jsonArray.map { element ->
        canonicalizedFixturePage(timelineFixtureJson.decodeFromJsonElement(element))
    }
    val expectedMessages = root["expectedMessages"]!!.jsonArray.map { it.jsonObject }
    val expectedStableKeys = expectedMessages.mapNotNull { expected ->
        expected["stableKey"]?.jsonPrimitive?.contentOrNull
    }
    val expectedPendingCount = root["expectedPending"]?.jsonArray?.size ?: 0
    return FixtureCase(
        sessionKey = sessionKey,
        initialLocal = initialLocal,
        events = events,
        expectedStableKeys = expectedStableKeys,
        expectedPendingCount = expectedPendingCount
    )
}

private fun fixtureDir(): File {
    var current: File? = File(System.getProperty("user.dir") ?: "").canonicalFile
    while (current != null) {
        val candidate = File(current, "docs/superpowers/fixtures/timeline")
        if (candidate.isDirectory) return candidate
        current = current.parentFile
    }
    error("Unable to locate docs/superpowers/fixtures/timeline from ${System.getProperty("user.dir")}")
}

private fun canonicalizedFixturePage(page: TimelineSnapshotPage): TimelineSnapshotPage {
    return page.copy(
        messages = page.messages.mapIndexed { index, message ->
            canonicalizedFixtureMessage(message, page.sessionKey, index)
        }
    )
}

private fun canonicalizedFixtureMessage(
    message: TimelineSnapshotMessage,
    sessionKey: String,
    index: Int
): TimelineSnapshotMessage {
    if (!message.timelineOrderKey.isNullOrBlank() &&
        !message.timelineIdentityKey.isNullOrBlank() &&
        !message.timelineItemKind.isNullOrBlank()
    ) {
        return message
    }

    val id = message.serverMessageId
        ?: message.messageId
        ?: message.clientMessageId
        ?: message.idempotencyKey
        ?: message.runId
        ?: "fixture-$index"
    val kind = when {
        message.content.any { it.isFileBlock || it.isVoiceMessageBlock } -> "attachment"
        message.role.equals("tool", ignoreCase = true) ||
            message.content.any { it.isToolCallBlock || it.isToolResultBlock } -> "tool"
        else -> "message:${message.role.ifBlank { "assistant" }}"
    }
    return message.copy(
        timelineOrderKey = message.timelineOrderKey ?: "%04d".format(index + 1),
        timelineIdentityKey = message.timelineIdentityKey ?: "$sessionKey:message:$id",
        timelineItemKind = message.timelineItemKind ?: kind
    )
}

internal data class FixtureCase(
    val sessionKey: String,
    val initialLocal: List<ChatMessage>,
    val events: List<TimelineSnapshotPage>,
    val expectedStableKeys: List<String>,
    val expectedPendingCount: Int
)
