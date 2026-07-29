package com.rethinkingstudio.clawlink.core.state.chat

import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.security.MessageDigest
import java.util.Base64
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 单个持久会话使用的稳定、凭据安全身份。
 *
 * Relay 账号 ID 是端点与凭据的摘要，原始凭据绝不写入磁盘。
 */
@Serializable
internal data class TimelinePersistenceScope(
    val relayAccountId: String,
    val gatewayId: String,
    val sessionKey: String
) {
    fun normalized(): TimelinePersistenceScope = copy(
        relayAccountId = relayAccountId.trim(),
        gatewayId = gatewayId.trim(),
        sessionKey = normalizeSessionKey(sessionKey)
    )

    fun isValid(): Boolean {
        val normalized = normalized()
        return normalized.relayAccountId.isNotBlank() &&
            normalized.gatewayId.isNotBlank() &&
            normalized.sessionKey.isNotBlank()
    }
}

@Serializable
internal enum class TimelineOutboxKind {
    TEXT,
    VOICE
}

@Serializable
internal data class TimelineOutboxAttachment(
    val fileId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val sourceRunId: String? = null
)

@Serializable
internal data class TimelineOutboxVoice(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val contentBase64: String = "",
    val contentFileKey: String? = null,
    val message: String? = null,
    val languageHint: String? = null
)

@Serializable
internal data class TimelineOutboxEntry(
    val kind: TimelineOutboxKind,
    val clientMessageId: String,
    val idempotencyKey: String,
    val requestId: String,
    val content: String = "",
    val attachments: List<TimelineOutboxAttachment> = emptyList(),
    val voice: TimelineOutboxVoice? = null,
    val createdAtEpochMs: Long
)

@Serializable
internal data class TimelineSessionSnapshot(
    val scope: TimelinePersistenceScope,
    val confirmedMessages: List<ChatMessage>,
    val pendingMessages: List<ChatMessage>,
    val timelineState: ChatTimelineState,
    val outbox: List<TimelineOutboxEntry> = emptyList(),
    val snapshotRevision: String? = null,
    val highWatermark: Long? = null,
    val savedAtEpochMs: Long
) {
    fun restoredTimelineState(): ChatTimelineState {
        return timelineState.copy(
            messages = canonicalizeMessagesForTimelineSnapshot(confirmedMessages + pendingMessages)
        )
    }
}

internal fun timelineRelayAccountId(baseUrl: String, accessToken: String): String {
    val normalizedRelay = baseUrl.trim().trimEnd('/')
    val stableAccountId = jwtStableAccountId(accessToken)
    val accountSeed = stableAccountId?.let { "account:$it" } ?: "token:${sha256Hex(accessToken.trim())}"
    return sha256Hex(normalizedRelay + "\u0000" + accountSeed)
}

private fun jwtStableAccountId(accessToken: String): String? {
    val parts = accessToken.trim().split('.')
    if (parts.size < 2) return null
    // Relay 当前签名格式为 `<payload>.<signature>`，标准 JWT 为 `<header>.<payload>.<signature>`；
    // 两者都要解析，避免正常 token 刷新把同一用户静默切到另一个本地时间线 scope。
    val payloadCandidates = if (parts.size == 2) listOf(parts[0]) else listOf(parts[1])
    return payloadCandidates.firstNotNullOfOrNull(::stableAccountIdFromEncodedPayload)
}

private fun stableAccountIdFromEncodedPayload(encodedPayload: String): String? {
    val payload = encodedPayload.trim().takeIf { it.isNotEmpty() } ?: return null
    val paddedPayload = payload + "=".repeat((4 - payload.length % 4) % 4)
    val payloadJson = runCatching {
        String(Base64.getUrlDecoder().decode(paddedPayload), Charsets.UTF_8)
    }.getOrNull() ?: return null
    val claims = runCatching { Json.parseToJsonElement(payloadJson) as? JsonObject }.getOrNull() ?: return null
    return listOf("userId", "user_id", "sub")
        .firstNotNullOfOrNull { claimName ->
            (claims[claimName] as? JsonPrimitive)
                ?.contentOrNull
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
}

internal fun timelinePersistenceScope(
    baseUrl: String,
    accessToken: String,
    gatewayId: String,
    sessionKey: String
): TimelinePersistenceScope = TimelinePersistenceScope(
    relayAccountId = timelineRelayAccountId(baseUrl, accessToken),
    gatewayId = gatewayId,
    sessionKey = sessionKey
).normalized()

object TimelinePersistenceMiddleware {
    private const val LEGACY_V7_PREFS_NAME = "clawlink_chat_timeline_sessions"
    private const val OUTBOX_PREFS_NAME = "clawlink_chat_timeline_outbox_v1"
    private const val SNAPSHOT_DIR_NAME = "clawlink_chat_timeline_v9"
    private const val LEGACY_V8_SNAPSHOT_DIR_NAME = "clawlink_chat_timeline_v8"
    private const val OUTBOX_BLOB_DIR_NAME = "clawlink_chat_outbox_blobs_v1"
    private const val LEGACY_PREFS_NAME = "clawlink_chat_timeline_pending"
    private const val LEGACY_KEY_SNAPSHOT = "snapshot"
    private const val KEY_PREFIX = "session:"
    private const val SNAPSHOT_SCHEMA_VERSION = 9
    private const val LEGACY_V8_SCHEMA_VERSION = 8
    private const val LEGACY_V7_SCHEMA_VERSION = 7
    private const val OUTBOX_SCHEMA_VERSION = 1
    private const val SNAPSHOT_MAX_CONFIRMED_MESSAGES = 500
    @Volatile private var legacyV7Prefs: SharedPreferences? = null
    @Volatile private var outboxPrefs: SharedPreferences? = null
    @Volatile private var snapshotDirectory: File? = null
    @Volatile private var legacyV8SnapshotDirectory: File? = null
    @Volatile private var outboxBlobDirectory: File? = null
    private val pendingSnapshotWrites = ConcurrentHashMap<String, TimelineSessionSnapshot>()
    private val snapshotWriterScheduled = AtomicBoolean(false)
    private val snapshotWriter = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ClawLinkTimelineWriter").apply { isDaemon = true }
    }
    private val lastOutboxJournalByKey = ConcurrentHashMap<String, String>()
    private val lastPendingOverlayByKey = ConcurrentHashMap<String, List<ChatMessage>>()

    @Serializable
    private data class TimelineSnapshotEnvelope(
        val schemaVersion: Int,
        val snapshot: TimelineSessionSnapshot
    )

    @Serializable
    private data class TimelineOutboxEnvelope(
        val schemaVersion: Int,
        val scope: TimelinePersistenceScope,
        val entries: List<TimelineOutboxEntry>,
        val pendingMessages: List<ChatMessage> = emptyList()
    )

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun init(context: Context) {
        val appContext = context.applicationContext
        legacyV7Prefs = appContext.getSharedPreferences(LEGACY_V7_PREFS_NAME, Context.MODE_PRIVATE)
        outboxPrefs = appContext.getSharedPreferences(OUTBOX_PREFS_NAME, Context.MODE_PRIVATE)
        snapshotDirectory = File(appContext.filesDir, SNAPSHOT_DIR_NAME).apply { mkdirs() }
        legacyV8SnapshotDirectory = File(appContext.filesDir, LEGACY_V8_SNAPSHOT_DIR_NAME)
        outboxBlobDirectory = File(appContext.filesDir, OUTBOX_BLOB_DIR_NAME).apply { mkdirs() }
        // v6 使用无 scope 的全局值，可能属于其他 Relay、网关或会话，不能安全展示。
        appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(LEGACY_KEY_SNAPSHOT)
            .apply()
    }

    internal fun buildSnapshot(
        scope: TimelinePersistenceScope,
        state: ChatTimelineState,
        outbox: List<TimelineOutboxEntry> = emptyList(),
        snapshotRevision: String? = null,
        highWatermark: Long? = null,
        savedAtEpochMs: Long = System.currentTimeMillis()
    ): TimelineSessionSnapshot {
        val canonicalState = state.canonicalSnapshotState()
        val partition = canonicalState.messages.partition { it.isPendingTimelineOverlay() }
        return TimelineSessionSnapshot(
            scope = scope.normalized(),
            // 冷启动只保存当前有界 canonical 窗口；更旧消息由向上翻页从服务端按需获取，
            // 避免单个会话无限增长并让后台全量编码长期占用内存和磁盘。
            confirmedMessages = partition.second.takeLast(SNAPSHOT_MAX_CONFIRMED_MESSAGES),
            pendingMessages = partition.first,
            timelineState = canonicalState.copy(messages = emptyList()),
            outbox = outbox.distinctBy { it.idempotencyKey },
            snapshotRevision = snapshotRevision?.trim()?.takeIf { it.isNotEmpty() },
            // ChatMessage.seq 还承载旧协议/分页局部序号；watermark 只能来自显式 conversationSeq
            // 快照契约，否则重启后可能把有效快照误判为陈旧。
            highWatermark = highWatermark,
            savedAtEpochMs = savedAtEpochMs
        )
    }

    internal fun encodeSnapshot(snapshot: TimelineSessionSnapshot): String {
        require(snapshot.scope.isValid()) { "Timeline snapshot scope is incomplete" }
        return json.encodeToString(
            TimelineSnapshotEnvelope(
                schemaVersion = SNAPSHOT_SCHEMA_VERSION,
                snapshot = snapshot.copy(scope = snapshot.scope.normalized())
            )
        )
    }

    internal fun encodeDiskSnapshot(snapshot: TimelineSessionSnapshot): String {
        return encodeSnapshot(snapshot.copy(outbox = emptyList()))
    }

    internal fun decodeSnapshot(
        raw: String,
        expectedScope: TimelinePersistenceScope
    ): TimelineSessionSnapshot? {
        if (!expectedScope.isValid()) return null
        return try {
            val envelope = json.decodeFromString(TimelineSnapshotEnvelope.serializer(), raw)
            envelope.snapshot
                .takeIf {
                    envelope.schemaVersion == SNAPSHOT_SCHEMA_VERSION ||
                        envelope.schemaVersion == LEGACY_V8_SCHEMA_VERSION ||
                        envelope.schemaVersion == LEGACY_V7_SCHEMA_VERSION
                }
                ?.takeIf { it.scope.normalized() == expectedScope.normalized() }
                ?.copy(
                    scope = expectedScope.normalized(),
                    // v7/v8 在缺少显式 conversation watermark 时曾从普通 seq 推导；
                    // schema v9 不继承这种无法证明的顺序声明。
                    highWatermark = if (envelope.schemaVersion == SNAPSHOT_SCHEMA_VERSION) {
                        envelope.snapshot.highWatermark
                    } else {
                        null
                    }
                )
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    internal fun persistSnapshot(
        snapshot: TimelineSessionSnapshot,
        durablePendingOverlay: Boolean = false
    ): Boolean {
        if (!snapshot.scope.isValid()) return false
        if (snapshotDirectory == null) return false
        // 小 journal 是发送事务边界；同步写成功前，调用方不得触碰 WebSocket 传输。
        if (!persistOutbox(
                scope = snapshot.scope,
                entries = snapshot.outbox,
                pendingMessages = snapshot.pendingMessages.takeIf { durablePendingOverlay }
            )
        ) return false
        val key = storageKey(snapshot.scope)
        // 契约：调用方只发布每个 scope 的最新快照；单线程 writer 立即 drain，
        // 同 scope 的并发更新 latest-wins。这里不使用固定 debounce，因此 final
        // 到达后不会人为增加未落盘窗口，同时也不在 UI 线程 commit 500 条 JSON。
        pendingSnapshotWrites[key] = snapshot.copy(outbox = emptyList())
        scheduleSnapshotWriterDrain()
        return true
    }

    internal fun persistSnapshot(
        scope: TimelinePersistenceScope,
        state: ChatTimelineState,
        outbox: List<TimelineOutboxEntry> = emptyList(),
        snapshotRevision: String? = null,
        highWatermark: Long? = null,
        durablePendingOverlay: Boolean = false
    ): Boolean = persistSnapshot(
        buildSnapshot(
            scope = scope,
            state = state,
            outbox = outbox,
            snapshotRevision = snapshotRevision,
            highWatermark = highWatermark
        ),
        durablePendingOverlay = durablePendingOverlay
    )

    internal fun restoreSnapshot(scope: TimelinePersistenceScope): TimelineSessionSnapshot? {
        if (!scope.isValid()) return null
        val key = storageKey(scope)
        val current = readSnapshotFile(scope)
        val migrated = current
            ?: restoreAndMigrateV8Snapshot(scope)
            ?: restoreAndMigrateV7Snapshot(scope, key)
        val journal = restoreOutbox(scope)
        val outbox = if (journal.entries.isNotEmpty()) journal.entries else migrated?.outbox.orEmpty()
        val pendingOverlayByKey = linkedMapOf<String, ChatMessage>()
        (journal.pendingMessages + migrated?.pendingMessages.orEmpty()).forEach { message ->
            val identity = message.timelineIdentityKey.trim().ifBlank { message.id }
            pendingOverlayByKey[identity] = message
        }
        val pendingOverlay = pendingOverlayByKey.values.toList()
        return when {
            migrated != null -> migrated.copy(pendingMessages = pendingOverlay, outbox = outbox)
            outbox.isNotEmpty() || pendingOverlay.isNotEmpty() -> TimelineSessionSnapshot(
                scope = scope.normalized(),
                confirmedMessages = emptyList(),
                pendingMessages = pendingOverlay,
                timelineState = ChatTimelineState(),
                outbox = outbox,
                savedAtEpochMs = System.currentTimeMillis()
            )
            else -> null
        }
    }

    internal fun clearSnapshot(scope: TimelinePersistenceScope): Boolean {
        if (!scope.isValid()) return false
        val key = storageKey(scope)
        pendingSnapshotWrites.remove(key)
        snapshotWriter.execute {
            snapshotFile(scope)?.delete()
            legacyV8SnapshotFile(scope)?.delete()
            legacyV7Prefs?.edit()?.remove(key)?.apply()
        }
        return persistOutbox(scope, emptyList())
    }

    internal fun persistOutbox(
        scope: TimelinePersistenceScope,
        entries: List<TimelineOutboxEntry>,
        pendingMessages: List<ChatMessage>? = null
    ): Boolean {
        if (!scope.isValid()) return false
        val prefs = outboxPrefs ?: return false
        val distinctEntries = entries.distinctBy { it.idempotencyKey }
        val journalEntries = distinctEntries.mapNotNull { externalizeVoicePayload(scope, it) }
        if (journalEntries.size != distinctEntries.size) return false
        val key = storageKey(scope)
        val durableOverlay = pendingMessages
            ?.filter { it.isPendingTimelineOverlay() }
            ?: lastPendingOverlayByKey[key].orEmpty()
        val envelope = TimelineOutboxEnvelope(
            schemaVersion = OUTBOX_SCHEMA_VERSION,
            scope = scope.normalized(),
            entries = journalEntries,
            pendingMessages = if (journalEntries.isEmpty()) emptyList() else durableOverlay
        )
        val raw = json.encodeToString(TimelineOutboxEnvelope.serializer(), envelope)
        if (lastOutboxJournalByKey[key] == raw) return true
        val committed = if (journalEntries.isEmpty()) {
            prefs.edit().remove(key).commit()
        } else {
            prefs.edit().putString(key, raw).commit()
        }
        if (committed) {
            lastOutboxJournalByKey[key] = raw
            if (journalEntries.isEmpty()) {
                lastPendingOverlayByKey.remove(key)
            } else {
                lastPendingOverlayByKey[key] = durableOverlay
            }
            pruneOutboxBlobs(scope, journalEntries.mapNotNull { it.voice?.contentFileKey }.toSet())
        }
        return committed
    }

    internal fun resolveVoiceContentBase64(
        scope: TimelinePersistenceScope,
        voice: TimelineOutboxVoice
    ): String? {
        voice.contentBase64.trim().takeIf { it.isNotEmpty() }?.let { return it }
        val fileKey = voice.contentFileKey?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val file = outboxBlobFile(scope, fileKey) ?: return null
        if (!file.exists()) return null
        return runCatching { Base64.getEncoder().encodeToString(file.readBytes()) }.getOrNull()
    }

    private fun scheduleSnapshotWriterDrain() {
        if (!snapshotWriterScheduled.compareAndSet(false, true)) return
        snapshotWriter.execute {
            try {
                while (true) {
                    val batch = pendingSnapshotWrites.entries.toList()
                    if (batch.isEmpty()) break
                    batch.forEach { (key, snapshot) ->
                        if (pendingSnapshotWrites.remove(key, snapshot)) {
                            writeSnapshotFile(snapshot)
                        }
                    }
                }
            } finally {
                snapshotWriterScheduled.set(false)
                if (pendingSnapshotWrites.isNotEmpty()) scheduleSnapshotWriterDrain()
            }
        }
    }

    private fun writeSnapshotFile(snapshot: TimelineSessionSnapshot): Boolean {
        val target = snapshotFile(snapshot.scope) ?: return false
        target.parentFile?.mkdirs()
        val atomicFile = AtomicFile(target)
        var output: java.io.FileOutputStream? = null
        return try {
            output = atomicFile.startWrite()
            output.write(encodeDiskSnapshot(snapshot).toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
            true
        } catch (_: Exception) {
            output?.let(atomicFile::failWrite)
            false
        }
    }

    private fun readSnapshotFile(scope: TimelinePersistenceScope): TimelineSessionSnapshot? {
        val target = snapshotFile(scope) ?: return null
        if (!target.exists()) return null
        val raw = runCatching { AtomicFile(target).readFully().toString(Charsets.UTF_8) }.getOrNull()
            ?: return null
        return decodeSnapshot(raw, scope).also { decoded ->
            if (decoded == null) target.delete()
        }
    }

    private fun restoreAndMigrateV7Snapshot(
        scope: TimelinePersistenceScope,
        key: String
    ): TimelineSessionSnapshot? {
        val raw = legacyV7Prefs?.getString(key, null) ?: return null
        val decoded = decodeSnapshot(raw, scope)
        if (decoded == null) {
            // 损坏或跨 scope 的 v7 数据绝不渲染。
            legacyV7Prefs?.edit()?.remove(key)?.apply()
            return null
        }
        if (writeSnapshotFile(decoded.copy(outbox = emptyList())) &&
            persistOutbox(scope, decoded.outbox, decoded.pendingMessages)
        ) {
            legacyV7Prefs?.edit()?.remove(key)?.apply()
        }
        return decoded
    }

    private fun restoreAndMigrateV8Snapshot(scope: TimelinePersistenceScope): TimelineSessionSnapshot? {
        val legacyFile = legacyV8SnapshotFile(scope) ?: return null
        if (!legacyFile.exists()) return null
        val raw = runCatching {
            AtomicFile(legacyFile).readFully().toString(Charsets.UTF_8)
        }.getOrNull()
        val decoded = raw?.let { decodeSnapshot(it, scope) }
        if (decoded == null) {
            legacyFile.delete()
            return null
        }
        // decodeSnapshot 已清除 v8 中由普通 seq 猜出的 watermark；先以 v9
        // 原子落盘成功，再删除旧文件，迁移过程可安全重入。
        if (writeSnapshotFile(decoded.copy(outbox = emptyList()))) {
            legacyFile.delete()
        }
        return decoded
    }

    private fun restoreOutbox(scope: TimelinePersistenceScope): TimelineOutboxEnvelope {
        val key = storageKey(scope)
        val empty = TimelineOutboxEnvelope(
            schemaVersion = OUTBOX_SCHEMA_VERSION,
            scope = scope.normalized(),
            entries = emptyList()
        )
        val raw = outboxPrefs?.getString(key, null) ?: return empty
        val envelope = runCatching {
            json.decodeFromString(TimelineOutboxEnvelope.serializer(), raw)
        }.getOrNull()
        if (envelope == null ||
            envelope.schemaVersion != OUTBOX_SCHEMA_VERSION ||
            envelope.scope.normalized() != scope.normalized()
        ) {
            outboxPrefs?.edit()?.remove(key)?.apply()
            return empty
        }
        lastOutboxJournalByKey[key] = raw
        val pendingMessages = envelope.pendingMessages.filter { it.isPendingTimelineOverlay() }
        lastPendingOverlayByKey[key] = pendingMessages
        val validEntries = envelope.entries.filter { entry ->
            entry.kind != TimelineOutboxKind.VOICE ||
                entry.voice?.let { voice ->
                    voice.contentBase64.isNotBlank() ||
                        voice.contentFileKey?.let { outboxBlobFile(scope, it)?.exists() } == true
                } == true
        }
        return envelope.copy(entries = validEntries, pendingMessages = pendingMessages)
    }

    private fun externalizeVoicePayload(
        scope: TimelinePersistenceScope,
        entry: TimelineOutboxEntry
    ): TimelineOutboxEntry? {
        if (entry.kind != TimelineOutboxKind.VOICE) return entry
        val voice = entry.voice ?: return null
        val existingKey = voice.contentFileKey?.trim()?.takeIf { it.isNotEmpty() }
        val fileKey = existingKey ?: sha256Hex(
            entry.idempotencyKey + "\u0000" + voice.fileName + "\u0000" + voice.sizeBytes
        )
        if (outboxBlobFile(scope, fileKey)?.let { file ->
                file.exists() && file.length() == voice.sizeBytes
            } == true
        ) {
            return entry.copy(voice = voice.copy(contentBase64 = "", contentFileKey = fileKey))
        }
        val content = voice.contentBase64.trim().takeIf { it.isNotEmpty() } ?: return null
        val bytes = runCatching { Base64.getMimeDecoder().decode(content) }.getOrNull() ?: return null
        val file = outboxBlobFile(scope, fileKey) ?: return null
        file.parentFile?.mkdirs()
        val atomicFile = AtomicFile(file)
        var output: java.io.FileOutputStream? = null
        val written = try {
            output = atomicFile.startWrite()
            output.write(bytes)
            atomicFile.finishWrite(output)
            true
        } catch (_: Exception) {
            output?.let(atomicFile::failWrite)
            false
        }
        if (!written) return null
        return entry.copy(voice = voice.copy(contentBase64 = "", contentFileKey = fileKey))
    }

    private fun pruneOutboxBlobs(scope: TimelinePersistenceScope, retainedKeys: Set<String>) {
        outboxBlobScopeDirectory(scope)?.listFiles()?.forEach { file ->
            if (file.name !in retainedKeys) file.delete()
        }
    }

    private fun snapshotFile(scope: TimelinePersistenceScope): File? {
        return snapshotDirectory?.let { directory -> File(directory, storageKey(scope) + ".json") }
    }

    private fun legacyV8SnapshotFile(scope: TimelinePersistenceScope): File? {
        return legacyV8SnapshotDirectory?.let { directory -> File(directory, storageKey(scope) + ".json") }
    }

    private fun outboxBlobScopeDirectory(scope: TimelinePersistenceScope): File? {
        return outboxBlobDirectory?.let { directory -> File(directory, storageKey(scope)) }
    }

    private fun outboxBlobFile(scope: TimelinePersistenceScope, fileKey: String): File? {
        return outboxBlobScopeDirectory(scope)?.let { directory -> File(directory, fileKey) }
    }

    private fun storageKey(scope: TimelinePersistenceScope): String {
        val normalized = scope.normalized()
        return KEY_PREFIX + sha256Hex(
            listOf(normalized.relayAccountId, normalized.gatewayId, normalized.sessionKey)
                .joinToString("\u0000")
        )
    }
}

private fun ChatTimelineState.canonicalSnapshotState(): ChatTimelineState {
    val snapshotMessages = canonicalizeMessagesForTimelineSnapshot(messages)
    val canonicalMessages = snapshotMessages.filter { it.hasCanonicalTimelineKeysForSnapshot() }
    if (canonicalMessages == messages) return this
    if (canonicalMessages.size == messages.size) return copy(messages = canonicalMessages)
    return copy(
        messages = canonicalMessages,
        activeRunId = null,
        activeRunsByTurnId = emptyMap(),
        activeTurnByRunId = emptyMap(),
        seenPartSeqKeys = emptySet(),
        messagePartSeqByKey = emptyMap(),
        messagePartsById = emptyMap(),
        attachmentsById = emptyMap(),
        toolsById = emptyMap()
    )
}

private fun ChatMessage.hasCanonicalTimelineKeysForSnapshot(): Boolean {
    return timelineOrderKey.trim().isNotEmpty() &&
        timelineIdentityKey.trim().isNotEmpty() &&
        timelineItemKind.trim().isNotEmpty()
}

private fun ChatMessage.isPendingTimelineOverlay(): Boolean {
    return timelineOrderKey.startsWith("local:") ||
        timelineIdentityKey.startsWith("local:") ||
        source.equals("local", ignoreCase = true)
}

private fun sha256Hex(value: String): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
