package com.rethinkingstudio.clawlink.core.utils

import android.os.Build
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class PairingQRCode(
    val server: String,
    val gatewayId: String,
    val accessCode: String
)

data class ResolvedPairingInput(
    val serverURL: String,
    val gatewayID: String?,
    val accessCode: String
)

object PairingInputResolver {
    private val json = Json { ignoreUnknownKeys = true }

    fun resolvePairingInput(
        qrPayload: String,
        currentServerURL: String,
        manualGatewayID: String,
        manualAccessCode: String
    ): ResolvedPairingInput {
        val trimmedPayload = qrPayload.trim()
        if (trimmedPayload.isNotEmpty()) {
            return try {
                val decoded = json.decodeFromString<PairingQRCode>(trimmedPayload)
                ResolvedPairingInput(
                    serverURL = normalizeServerURL(decoded.server, currentServerURL),
                    gatewayID = if (decoded.gatewayId.isBlank()) null else decoded.gatewayId,
                    accessCode = decoded.accessCode
                )
            } catch (e: Exception) {
                // If it's not a JSON QR, maybe it's just the access code?
                // For now, let's assume it must be JSON as per iOS.
                throw Exception("Invalid QR code payload")
            }
        }

        if (manualAccessCode.isBlank()) {
            throw Exception("Missing access code")
        }

        return ResolvedPairingInput(
            serverURL = normalizeServerURL(currentServerURL, ""),
            gatewayID = if (manualGatewayID.isBlank()) null else manualGatewayID,
            accessCode = manualAccessCode.trim()
        )
    }

    fun normalizeServerURL(raw: String, fallback: String): String {
        var candidate = raw.trim().ifBlank { fallback.trim() }
        if (candidate.isEmpty()) return ""
        
        if (!candidate.contains("://")) {
            val scheme = preferredScheme(candidate)
            candidate = "$scheme://$candidate"
        }
        return rewriteEmulatorLoopback(candidate)
    }

    private fun preferredScheme(host: String): String {
        val cleanHost = host.lowercase()
        return if (isLocalOrPrivate(cleanHost)) "http" else "https"
    }

    private fun isLocalOrPrivate(host: String): Boolean {
        if (host == "localhost" || host == "127.0.0.1" || host == "0.0.0.0") return true
        if (host.endsWith(".local")) return true
        
        // Very basic IP check for private ranges
        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
             // More precise check for 172.16-31 could be added
             return true
        }
        return false
    }

    private fun rewriteEmulatorLoopback(candidate: String): String {
        if (!isAndroidEmulator()) return candidate

        return candidate
            .replace("://127.0.0.1", "://10.0.2.2")
            .replace("://localhost", "://10.0.2.2")
            .replace("://0.0.0.0", "://10.0.2.2")
    }

    private fun isAndroidEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("sdk_gphone", ignoreCase = true) ||
            Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
            Build.PRODUCT.contains("sdk", ignoreCase = true)
    }
}
