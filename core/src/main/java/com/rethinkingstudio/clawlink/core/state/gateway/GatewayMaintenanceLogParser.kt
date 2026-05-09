package com.rethinkingstudio.clawlink.core.state.gateway

import com.rethinkingstudio.clawlink.core.models.MaintenanceLogEntry
import com.rethinkingstudio.clawlink.core.state.LocalizedText.choose
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun createLogEntry(payload: JsonObject): MaintenanceLogEntry {
        val text = payload["text"]?.jsonPrimitive?.content
            ?: payload["delta"]?.jsonPrimitive?.content
            ?: payload["errorMessage"]?.jsonPrimitive?.content
            ?: payload["error_message"]?.jsonPrimitive?.content
            ?: payload["data"]?.jsonObject?.get("text")?.jsonPrimitive?.content
            ?: payload["data"]?.jsonObject?.get("delta")?.jsonPrimitive?.content
            ?: payload["data"]?.jsonObject?.get("error")?.jsonPrimitive?.content
            ?: payload["data"]?.jsonObject?.get("result")?.jsonPrimitive?.content
            ?: ""
        val stream = payload["stream"]?.jsonPrimitive?.content ?: "stdout"
        return MaintenanceLogEntry(
            timestamp = System.currentTimeMillis(),
            stream = stream,
            text = text
        )
    }
