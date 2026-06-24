package com.rethinkingstudio.clawlink.core.state.chat

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal fun isCanonicalTimelineV3(snapshot: JsonElement?): Boolean {
    val obj = snapshot as? JsonObject ?: return false
    return obj["timelineProtocolVersion"]?.jsonPrimitive?.contentOrNull == "3" ||
        obj.containsKey("snapshotRevision") ||
        obj.containsKey("rangeStartCursor") ||
        obj.containsKey("rangeEndCursor") ||
        obj.containsKey("deletedMessageIds")
}
