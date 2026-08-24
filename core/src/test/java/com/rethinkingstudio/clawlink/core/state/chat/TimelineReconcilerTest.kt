package com.rethinkingstudio.clawlink.core.state.chat

import com.rethinkingstudio.clawlink.core.models.chat.ChatMessage
import com.rethinkingstudio.clawlink.core.models.chat.MessageRole
import com.rethinkingstudio.clawlink.core.models.chat.MessageState
import com.rethinkingstudio.clawlink.core.models.chat.RelayChatContentBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineReconcilerTest {
    @Test
    fun hermesRehydrationUsesRelayOrderForConfirmedRows() {
        fun canonicalMessage(
            id: String,
            role: MessageRole,
            runId: String,
            sequence: Long,
            localTurnOrder: Long? = null
        ) = ChatMessage(
            id = id,
            role = role,
            state = MessageState.completed,
            content = id,
            runId = runId,
            turnId = runId,
            clientMessageId = runId.takeIf { role == MessageRole.user }.orEmpty(),
            timelineOrderKey = "v5|0|${sequence.toString().padStart(20, '0')}|00000000000000000000|${if (role == MessageRole.user) "10" else "30"}|$id",
            timelineIdentityKey = "v1|mobile-hermes|${role.name}|$id",
            timelineItemKind = if (role == MessageRole.tool) "tool" else "message:${role.name}",
            source = "history",
            localTurnOrder = localTurnOrder
        )

        val messages = buildList {
            add(canonicalMessage("epoch-user", MessageRole.user, "epoch-run", 17_864_413_825_600L, 0))
            add(canonicalMessage("epoch-answer", MessageRole.assistant, "epoch-run", 17_864_413_852_070L))
            add(canonicalMessage("hermes-user", MessageRole.user, "hermes-run", 4_379, 10))
            // Hermes live tool rows may use a run-local sequence before state.db assigns
            // the later conversation sequence to their matching persisted user row.
            // The live tool can share the overall conversation but is not the
            // confirmed user's canonical turn identity; do not infer a pair
            // from arrival order or a provider run label.
            add(canonicalMessage("hermes-live-tool", MessageRole.tool, "hermes-live-tool-run", 4))
            repeat(11) { turnIndex ->
                val runId = "history-run-$turnIndex"
                val userSequence = 4_357L + turnIndex * 2
                add(canonicalMessage("history-user-$turnIndex", MessageRole.user, runId, userSequence, turnIndex + 1L))
                add(canonicalMessage("history-answer-$turnIndex", MessageRole.assistant, runId, userSequence + 1))
            }
            add(canonicalMessage("tool-run-user", MessageRole.user, "tool-run", 4_381, 11))
            add(canonicalMessage("tool-run-first", MessageRole.tool, "tool-run", 4_385))
            add(canonicalMessage("tool-run-second", MessageRole.tool, "tool-run", 4_392))
            add(canonicalMessage("tool-run-answer", MessageRole.assistant, "tool-run", 4_388))
            add(canonicalMessage("tail-user-a", MessageRole.user, "tail-run-a", 4_394, 12))
            add(canonicalMessage("tail-user-b", MessageRole.user, "tail-run-b", 4_395, 13))
            add(canonicalMessage("tail-answer-b", MessageRole.assistant, "tail-run-b", 4_396))
        }

        val ordered = sortTimelineMessagesV3(messages, "mobile-hermes")

        assertEquals(messages.size, ordered.size)
        assertEquals(messages.map { it.id }.toSet(), ordered.map { it.id }.toSet())
        // Both rows are confirmed Relay rows. The live tool's canonical sequence is
        // earlier, so no same-turn identity heuristic may lift the user above it.
        assertTrue(ordered.indexOfFirst { it.id == "hermes-live-tool" } < ordered.indexOfFirst { it.id == "hermes-user" })
    }

    @Test
    fun confirmedImageUserFollowsRelaySlotWhenStaleToolArrivesBeforeIt() {
        fun order(namespace: Int, sequence: Long, slot: Int, id: String): String =
            "v5|$namespace|${sequence.toString().padStart(20, '0')}|00000000000000000000|${slot.toString().padStart(2, '0')}|$id"

        fun confirmed(
            id: String,
            role: MessageRole,
            sequence: Long,
            slot: Int,
            runId: String,
            turnId: String,
            content: String = id,
            blocks: List<RelayChatContentBlock> = listOf(RelayChatContentBlock(type = "text", text = content)),
            namespace: Int = 0
        ) = ChatMessage(
            id = id,
            role = role,
            state = MessageState.completed,
            content = content,
            contentBlocks = blocks,
            runId = runId,
            turnId = turnId,
            conversationSeq = sequence,
            seq = sequence,
            conversationSeqState = "committed",
            timelineOrderKey = order(namespace, sequence, slot, id),
            timelineIdentityKey = "v1|main|${role.name}|$id",
            timelineItemKind = if (role == MessageRole.tool) "tool" else "message:${role.name}",
            source = "history"
        )

        val attachmentTurn = "attachment-f0f2b02ff0c8b3ebd24dd05328d1908450b3e9d5b6867ed7ad81ec6fb796fcec"
        val confirmedImageUser = confirmed(
            id = "image-user-4905",
            role = MessageRole.user,
            sequence = 4905,
            slot = 10,
            runId = "file-file_a60b9b239c8d459b94a5c914f3ed356b",
            turnId = attachmentTurn,
            content = "分析一下这张图",
            blocks = listOf(
                RelayChatContentBlock(type = "text", text = "分析一下这张图"),
                RelayChatContentBlock(
                    type = "image",
                    fileId = "file-a60b9b239c8d459b94a5c914f3ed356b",
                    fileName = "photo.jpg",
                    mimeType = "image/jpeg",
                    downloadUrl = "/api/mobile/files/file-a60b9b239c8d459b94a5c914f3ed356b",
                    sourceRunId = "file-file_a60b9b239c8d459b94a5c914f3ed356b"
                )
            )
        )
        val messages = listOf(
            confirmed(
                id = "stale-tool-ns0-seq10",
                role = MessageRole.tool,
                sequence = 10,
                slot = 30,
                runId = attachmentTurn,
                turnId = attachmentTurn,
                content = "stale tool"
            ).copy(contentBlocks = listOf(RelayChatContentBlock(type = "tool_result", text = "stale tool"))),
            confirmed("old-user-4895", MessageRole.user, 4895, 10, "old-turn", "old-turn", "你好"),
            confirmed("old-answer-4896", MessageRole.assistant, 4896, 50, "old-turn", "old-turn", "你好！"),
            confirmedImageUser,
            confirmed(
                id = "live-tool-ns1-seq5",
                role = MessageRole.tool,
                sequence = 5,
                slot = 30,
                runId = attachmentTurn,
                turnId = attachmentTurn,
                content = "live tool",
                namespace = 1
            ).copy(contentBlocks = listOf(RelayChatContentBlock(type = "tool_result", text = "live tool"))),
            confirmed(
                id = "live-answer-ns1-seq5",
                role = MessageRole.assistant,
                sequence = 5,
                slot = 50,
                runId = attachmentTurn,
                turnId = attachmentTurn,
                content = "图片分析结果",
                namespace = 1
            )
        )

        val ordered = sortTimelineMessagesV3(messages, "main")

        // The hidden stale tool may occupy the first canonical slot, but it
        // must not move the confirmed image user to the list head.
        assertEquals(
            listOf(
                "stale-tool-ns0-seq10",
                "old-user-4895",
                "old-answer-4896",
                "image-user-4905",
                "live-tool-ns1-seq5",
                "live-answer-ns1-seq5"
            ),
            ordered.map(ChatMessage::id)
        )
    }

    @Test
    fun acknowledgedImageUserAnchorsLocalWaitingThatArrivedAtListHead() {
        val turnId = "attachment-acknowledged-image"
        val older = canonicalTimelineMessage(
            id = "older-user",
            role = MessageRole.user,
            turnId = "older-turn",
            sequence = 4_786,
            slot = 10,
            content = "旧消息"
        )
        val acknowledgedImageUser = canonicalTimelineMessage(
            id = "acknowledged-image-user",
            role = MessageRole.user,
            turnId = turnId,
            sequence = 4_787,
            slot = 10,
            content = "帮我分析一下这张图",
            blocks = listOf(
                RelayChatContentBlock(type = "text", text = "帮我分析一下这张图"),
                RelayChatContentBlock(
                    type = "image",
                    attachmentId = turnId,
                    fileId = "file-image",
                    fileName = "waterfall.jpeg",
                    mimeType = "image/jpeg",
                    sourceRunId = turnId
                )
            )
        )
        val waiting = localOutputOverlay(
            id = "waiting-for-image-answer",
            turnId = turnId,
            kind = "waiting",
            content = "[[clawlink:typing]]"
        )

        val ordered = sortTimelineMessagesV3(
            listOf(waiting, acknowledgedImageUser, older),
            "main"
        )

        assertEquals(
            listOf("older-user", "acknowledged-image-user", "waiting-for-image-answer"),
            ordered.map(ChatMessage::id)
        )
    }

    @Test
    fun acknowledgedImageUserAnchorsLocalStreamingAfterCanonicalToolOutput() {
        val turnId = "attachment-streaming-image"
        val older = canonicalTimelineMessage(
            id = "older-answer",
            role = MessageRole.assistant,
            turnId = "older-turn",
            sequence = 4_786,
            slot = 50,
            content = "旧回答"
        )
        val acknowledgedImageUser = canonicalTimelineMessage(
            id = "streaming-image-user",
            role = MessageRole.user,
            turnId = turnId,
            sequence = 4_787,
            slot = 10,
            content = "看看图片"
        )
        val canonicalTool = canonicalTimelineMessage(
            id = "image-tool",
            role = MessageRole.tool,
            turnId = turnId,
            sequence = 4_788,
            slot = 30,
            content = "图片读取完成",
            blocks = listOf(RelayChatContentBlock(type = "tool_result", text = "图片读取完成"))
        )
        val streaming = localOutputOverlay(
            id = "local-streaming-image-answer",
            turnId = turnId,
            kind = "message:assistant",
            content = "这张图片里……"
        )

        val ordered = sortTimelineMessagesV3(
            listOf(streaming, acknowledgedImageUser, older, canonicalTool),
            "main"
        )

        assertEquals(
            listOf("older-answer", "streaming-image-user", "image-tool", "local-streaming-image-answer"),
            ordered.map(ChatMessage::id)
        )
    }

    @Test
    fun ambiguousLocalOutputIdentityKeepsItsPhysicalPosition() {
        val sharedTurnId = "shared-turn"
        val waiting = localOutputOverlay(
            id = "ambiguous-waiting",
            turnId = sharedTurnId,
            kind = "waiting",
            content = "[[clawlink:typing]]"
        )
        val first = canonicalTimelineMessage(
            id = "first-shared-user",
            role = MessageRole.user,
            turnId = sharedTurnId,
            sequence = 1,
            slot = 10,
            content = "第一条"
        )
        val second = canonicalTimelineMessage(
            id = "second-shared-user",
            role = MessageRole.user,
            turnId = sharedTurnId,
            sequence = 2,
            slot = 10,
            content = "第二条"
        )

        val ordered = sortTimelineMessagesV3(listOf(waiting, second, first), "main")

        assertEquals(
            listOf("ambiguous-waiting", "first-shared-user", "second-shared-user"),
            ordered.map(ChatMessage::id)
        )
    }

    @Test
    fun mediaTurnKeepsLocalDisplayIdsAcrossStreamingAndFinalCanonicalSnapshots() {
        listOf(
            "image" to "image/jpeg",
            "voice" to "audio/mp4"
        ).forEach { (mediaType, mimeType) ->
            val turnId = "attachment-$mediaType-stable-turn"
            val localUserId = "local-$mediaType-user"
            val localAssistantId = "local-$mediaType-assistant"
            val mediaBlock = RelayChatContentBlock(
                type = mediaType,
                attachmentId = "attachment-$mediaType",
                fileId = "file-$mediaType",
                fileName = if (mediaType == "image") "photo.jpg" else "recording.m4a",
                mimeType = mimeType,
                sourceRunId = turnId
            )
            val localUser = ChatMessage(
                id = localUserId,
                role = MessageRole.user,
                state = MessageState.completed,
                content = "分析这个${if (mediaType == "image") "图片" else "语音"}",
                contentBlocks = listOf(mediaBlock),
                runId = "local-user-$turnId",
                turnId = turnId,
                clientMessageId = turnId,
                idempotencyKey = turnId,
                conversationSeqState = "provisional",
                timelineOrderKey = "local:$turnId|10|$localUserId",
                timelineIdentityKey = "local:$turnId:message:user",
                timelineItemKind = "message:user",
                source = "local",
                localTurnOrder = 1
            )
            val localWaiting = ChatMessage(
                id = localAssistantId,
                role = MessageRole.assistant,
                state = MessageState.streaming,
                content = "[[clawlink:typing]]",
                contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "[[clawlink:typing]]")),
                runId = turnId,
                turnId = turnId,
                clientMessageId = turnId,
                idempotencyKey = turnId,
                timelineOrderKey = "local:$turnId|20|$localAssistantId",
                timelineIdentityKey = "local:$turnId:waiting",
                timelineItemKind = "waiting",
                source = "local",
                localTurnOrder = 1
            )

            fun snapshot(
                userMessageId: String,
                assistantMessageId: String,
                assistantText: String,
                assistantState: String,
                revision: String
            ) = TimelineSnapshotPage(
                sessionKey = "main",
                snapshotRevision = revision,
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = userMessageId,
                        conversationSeq = 100,
                        seq = 100,
                        turnId = turnId,
                        runId = turnId,
                        clientMessageId = turnId,
                        idempotencyKey = turnId,
                        role = "user",
                        messageState = "completed",
                        content = listOf(mediaBlock),
                        source = "history",
                        timelineOrderKey = "v5|0|00000000000000000100|00000000000000000000|10|$userMessageId",
                        timelineIdentityKey = "v1|main|message|user|$userMessageId",
                        timelineItemKind = "message:user"
                    ),
                    TimelineSnapshotMessage(
                        messageId = assistantMessageId,
                        conversationSeq = if (assistantState == "completed") 102 else 101,
                        seq = if (assistantState == "completed") 102 else 101,
                        turnId = turnId,
                        runId = turnId,
                        clientMessageId = turnId,
                        role = "assistant",
                        messageState = assistantState,
                        content = listOf(RelayChatContentBlock(type = "text", text = assistantText)),
                        source = if (assistantState == "completed") "history" else "live",
                        timelineOrderKey = if (assistantState == "completed") {
                            "v5|0|00000000000000000102|00000000000000000000|50|$assistantMessageId"
                        } else {
                            "v5|0|00000000000000000101|00000000000000000000|50|$assistantMessageId"
                        },
                        timelineIdentityKey = "v1|main|message|assistant|$assistantMessageId",
                        timelineItemKind = "message:assistant",
                        timelineResolvesWaiting = true
                    )
                )
            )

            val firstStreaming = reconcileTimeline(
                existing = listOf(localUser, localWaiting),
                snapshot = snapshot(
                    userMessageId = "server-$mediaType-user-live",
                    assistantMessageId = "server-$mediaType-assistant-live",
                    assistantText = "回复显示到一半",
                    assistantState = "streaming",
                    revision = "rev-$mediaType-stream-1"
                )
            )
            assertEquals(emptyList<ChatMessage>(), firstStreaming.pending)
            assertEquals(
                listOf(localUserId, localAssistantId),
                sortTimelineMessagesV3(firstStreaming.messages, "main").map(ChatMessage::id)
            )

            val continuedStreaming = reconcileTimeline(
                existing = firstStreaming.messages,
                snapshot = snapshot(
                    userMessageId = "server-$mediaType-user-live",
                    assistantMessageId = "server-$mediaType-assistant-live",
                    assistantText = "回复显示到一半，继续输出",
                    assistantState = "streaming",
                    revision = "rev-$mediaType-stream-2"
                )
            )
            assertEquals(
                listOf(localUserId, localAssistantId),
                sortTimelineMessagesV3(continuedStreaming.messages, "main").map(ChatMessage::id)
            )

            val completed = reconcileTimeline(
                existing = continuedStreaming.messages,
                snapshot = snapshot(
                    userMessageId = "server-$mediaType-user-history",
                    assistantMessageId = "server-$mediaType-assistant-history",
                    assistantText = "回复完成",
                    assistantState = "completed",
                    revision = "rev-$mediaType-completed"
                )
            )
            assertEquals(
                listOf(localUserId, localAssistantId),
                sortTimelineMessagesV3(completed.messages, "main").map(ChatMessage::id)
            )
            assertEquals(
                "server-$mediaType-user-history",
                completed.messages.first { it.role == MessageRole.user }.timelineMessageId
            )
            assertEquals(
                "server-$mediaType-assistant-history",
                completed.messages.first { it.role == MessageRole.assistant }.timelineMessageId
            )
        }
    }

    @Test
    fun unconfirmedLocalUserOverlayStaysAtTailBeforeItsMatchingOutput() {
        fun canonical(id: String, role: MessageRole, sequence: Long, slot: Int, content: String) = ChatMessage(
            id = id,
            role = role,
            state = MessageState.completed,
            content = content,
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = content)),
            runId = "old-turn",
            turnId = "old-turn",
            conversationSeq = sequence,
            seq = sequence,
            conversationSeqState = "committed",
            timelineOrderKey = "v5|0|${sequence.toString().padStart(20, '0')}|00000000000000000000|${slot.toString().padStart(2, '0')}|$id",
            timelineIdentityKey = "v1|main|message|${role.name}|$id",
            timelineItemKind = "message:${role.name}",
            source = "history"
        )

        val clientRunId = "client-run-unconfirmed-image"
        val localUser = ChatMessage(
            id = "local-user-unconfirmed-image",
            role = MessageRole.user,
            state = MessageState.pending,
            content = "分析这张图",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "分析这张图")),
            runId = "local-user-$clientRunId",
            turnId = clientRunId,
            clientMessageId = clientRunId,
            idempotencyKey = clientRunId,
            conversationSeqState = "provisional",
            timelineOrderKey = "local:$clientRunId|10|local-user-unconfirmed-image",
            timelineIdentityKey = "local:message:user:$clientRunId",
            timelineItemKind = "message:user",
            source = "local",
            localTurnOrder = 0
        )
        val matchingOutput = ChatMessage(
            id = "live-answer-unconfirmed-image",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在分析图片",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "正在分析图片")),
            runId = "provider-run-unconfirmed-image",
            turnId = clientRunId,
            clientMessageId = clientRunId,
            conversationSeq = 5,
            seq = 5,
            timelineOrderKey = "v5|1|00000000000000000005|00000000000000000000|50|live-answer-unconfirmed-image",
            timelineIdentityKey = "v1|main|message:assistant|live-answer-unconfirmed-image",
            timelineItemKind = "message:assistant",
            source = "live"
        )

        val ordered = sortTimelineMessagesV3(
            listOf(matchingOutput, canonical("old-user", MessageRole.user, 1, 10, "旧问题"), canonical("old-answer", MessageRole.assistant, 2, 50, "旧回答"), localUser),
            "main"
        )

        assertEquals(
            listOf("old-user", "old-answer", "local-user-unconfirmed-image", "live-answer-unconfirmed-image"),
            ordered.map(ChatMessage::id)
        )
    }

    private fun canonicalTimelineMessage(
        id: String,
        role: MessageRole,
        turnId: String,
        sequence: Long,
        slot: Int,
        content: String,
        blocks: List<RelayChatContentBlock> = listOf(RelayChatContentBlock(type = "text", text = content))
    ) = ChatMessage(
        id = id,
        role = role,
        state = MessageState.completed,
        content = content,
        contentBlocks = blocks,
        runId = turnId,
        turnId = turnId,
        clientMessageId = turnId.takeIf { role == MessageRole.user }.orEmpty(),
        idempotencyKey = turnId.takeIf { role == MessageRole.user }.orEmpty(),
        conversationSeq = sequence,
        seq = sequence,
        conversationSeqState = "committed",
        timelineOrderKey = "v5|0|${sequence.toString().padStart(20, '0')}|00000000000000000000|${slot.toString().padStart(2, '0')}|$id",
        timelineIdentityKey = "v1|main|message|${role.name}|$id",
        timelineItemKind = if (role == MessageRole.tool) "tool" else "message:${role.name}",
        source = "history"
    )

    private fun localOutputOverlay(
        id: String,
        turnId: String,
        kind: String,
        content: String
    ) = ChatMessage(
        id = id,
        role = MessageRole.assistant,
        state = MessageState.streaming,
        content = content,
        contentBlocks = listOf(RelayChatContentBlock(type = "text", text = content)),
        runId = turnId,
        turnId = turnId,
        clientMessageId = turnId,
        timelineOrderKey = "local:$turnId|20|$id",
        timelineIdentityKey = "local:waiting:$turnId",
        timelineItemKind = kind,
        source = "local"
    )

    @Test
    fun mixedV4V5OrderKeysFormATransitiveTotalOrderWithoutChangingVersionSemantics() {
        val olderV5 = "v5|0|00000000000000000010|00000000000000000001|10|0000000000000001:00000000000000000001:user-old|aaaa"
        val newerV4 = "v4|0|00000000000000000020|10|0000000000000002:00000000000000000002:user-new|bbbb"
        assertTrue(compareCanonicalTimelineOrderKeys(olderV5, newerV4) < 0)
        assertTrue(compareCanonicalTimelineOrderKeys(newerV4, olderV5) > 0)

        val userV4 = "v4|0|00000000000000000030|10|0000000000000003:00000000000000000003:user|cccc"
        val assistantV5 = "v5|0|00000000000000000030|00000000000000000004|50|0000000000000004:00000000000000000004:assistant|dddd"
        assertTrue(compareCanonicalTimelineOrderKeys(userV4, assistantV5) < 0)

        val userV5 = "v5|0|00000000000000000040|00000000000000000005|10|0000000000000005:00000000000000000005:user|eeee"
        val assistantV4 = "v4|0|00000000000000000040|50|0000000000000006:00000000000000000006:assistant|ffff"
        assertTrue(compareCanonicalTimelineOrderKeys(assistantV4, userV5) < 0)

        val confirmedV5 = "v5|0|00000000000000000999|00000000000000000001|50|0000000000000001:00000000000000000001:confirmed|1111"
        val pendingV4 = "v4|1|00000000000000000001|10|0000000000000001:00000000000000000001:pending|2222"
        assertTrue(compareCanonicalTimelineOrderKeys(confirmedV5, pendingV4) < 0)
        assertTrue(compareCanonicalTimelineOrderKeys(pendingV4, confirmedV5) > 0)

        val highSuborderV4 = "v4|0|00000000000000000050|10|0000000000000030:00000000000000000030:a|aaaa"
        val lowSuborderV4 = "v4|0|00000000000000000050|50|0000000000000010:00000000000000000010:b|bbbb"
        val middleSuborderV5 = "v5|0|00000000000000000050|0000000000000020|30|0000000000000020:00000000000000000020:c|cccc"
        val expected = listOf(highSuborderV4, lowSuborderV4, middleSuborderV5)
        listOf(
            listOf(highSuborderV4, lowSuborderV4, middleSuborderV5),
            listOf(highSuborderV4, middleSuborderV5, lowSuborderV4),
            listOf(lowSuborderV4, highSuborderV4, middleSuborderV5),
            listOf(lowSuborderV4, middleSuborderV5, highSuborderV4),
            listOf(middleSuborderV5, highSuborderV4, lowSuborderV4),
            listOf(middleSuborderV5, lowSuborderV4, highSuborderV4)
        ).forEach { permutation ->
            assertEquals(
                expected,
                permutation.sortedWith { left, right -> compareCanonicalTimelineOrderKeys(left, right) }
            )
        }
    }

    @Test
    fun mixedV4V5TimelineRepairsOnlyOutputThatPrecedesItsUniqueUser() {
        val question = ChatMessage(
            id = "user-mixed-version",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "帮我分析一下这个图片",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "帮我分析一下这个图片")),
            runId = "mixed-version-run:user",
            timelineOrderKey = "v5|0|00000000000000000040|00000000000000000005|10|0000000000000005:00000000000000000005:user|eeee",
            timelineIdentityKey = "message:user:mixed-version",
            timelineItemKind = "message:user"
        )
        val answer = ChatMessage(
            id = "assistant-mixed-version",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "这是一张自然风景照。",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "这是一张自然风景照。")),
            runId = "mixed-version-run:assistant",
            timelineOrderKey = "v4|0|00000000000000000040|50|0000000000000006:00000000000000000006:assistant|ffff",
            timelineIdentityKey = "message:assistant:mixed-version",
            timelineItemKind = "message:assistant"
        )

        val ordered = sortTimelineMessagesV3(listOf(answer, question))

        assertEquals(listOf(question.id, answer.id), ordered.map { it.id })
    }

    @Test
    fun localPendingTurnsKeepEachUserBeforeItsOwnAssistantPlaceholder() {
        val firstUser = ChatMessage(
            id = "user-first-run",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "1111",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "1111")),
            runId = "local-user-first-run",
            sortTimestamp = 100.0,
            timelineOrderKey = "local:first-run|10|user-first-run",
            timelineIdentityKey = "local:message:user:first-run",
            timelineItemKind = "message:user"
        )
        val firstWaiting = ChatMessage(
            id = "assistant-first-run",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = protocolTypingMarkerText)),
            runId = "first-run",
            sortTimestamp = 100.001,
            timelineOrderKey = "local:first-run|20|assistant-first-run",
            timelineIdentityKey = "local:waiting:first-run",
            timelineItemKind = "waiting"
        )
        val secondUser = ChatMessage(
            id = "user-second-run",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "2222",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "2222")),
            runId = "local-user-second-run",
            sortTimestamp = 101.0,
            timelineOrderKey = "local:second-run|10|user-second-run",
            timelineIdentityKey = "local:message:user:second-run",
            timelineItemKind = "message:user"
        )
        val secondWaiting = ChatMessage(
            id = "assistant-second-run",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = protocolTypingMarkerText)),
            runId = "second-run",
            sortTimestamp = 101.001,
            timelineOrderKey = "local:second-run|20|assistant-second-run",
            timelineIdentityKey = "local:waiting:second-run",
            timelineItemKind = "waiting"
        )

        val ordered = sortTimelineMessagesV3(
            listOf(firstUser, firstWaiting, secondUser, secondWaiting)
        )

        assertEquals(
            listOf("user-first-run", "assistant-first-run", "user-second-run", "assistant-second-run"),
            ordered.map { it.id }
        )
    }

    @Test
    fun hermesWaitingStaysWithItsToolOutputWhenOldQueuedPromptArrivesBetweenThem() {
        val activeRunId = "attachment-active-run"
        val activeUser = ChatMessage(
            id = "active-image-user",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "分析这张图片",
            runId = activeRunId,
            turnId = activeRunId,
            timelineOrderKey = "v5|0|00000000000000004404|00000000000000000000|10|active-image-user",
            timelineIdentityKey = "message:user:active-image-user",
            timelineItemKind = "message:user",
            source = "history"
        )
        val activeTool = ChatMessage(
            id = "active-tool-result",
            role = MessageRole.tool,
            state = MessageState.completed,
            content = "tool result",
            runId = activeRunId,
            turnId = activeRunId,
            timelineOrderKey = "v5|0|00000000000000004408|00000000000000000000|30|active-tool-result",
            timelineIdentityKey = "tool:active-tool-result",
            timelineItemKind = "tool",
            source = "live"
        )
        val oldQueuedUser = ChatMessage(
            id = "old-queued-user",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "Run the shell command: sleep 30.",
            runId = "local-user-old-queued-run",
            turnId = "old-queued-run",
            clientMessageId = "old-queued-run",
            idempotencyKey = "old-queued-run",
            timelineOrderKey = "local:old-queued-run|10|old-queued-user",
            timelineIdentityKey = "local:message:user:old-queued-run",
            timelineItemKind = "message:user",
            source = "local",
            deliveryState = "queued",
            localTurnOrder = 1
        )
        val activeWaiting = ChatMessage(
            id = "active-waiting",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = protocolTypingMarkerText)),
            runId = activeRunId,
            turnId = activeRunId,
            timelineOrderKey = "local:$activeRunId|20|active-waiting",
            timelineIdentityKey = "local:waiting:$activeRunId",
            timelineItemKind = "waiting",
            source = "local"
        )

        val ordered = sortTimelineMessagesV3(
            listOf(activeUser, activeTool, oldQueuedUser, activeWaiting),
            "mobile-hermes"
        )

        assertEquals(
            listOf("active-image-user", "active-tool-result", "active-waiting", "old-queued-user"),
            ordered.map { it.id }
        )
    }

    @Test
    fun repeatedSameTextFixtureKeepsBothTurns() {
        val result = applyFixture("repeated_same_text_two_real_turns.json")
        assertEquals(listOf("user-repeat-1", "user-repeat-2"), result.messages.map { it.id })
    }

    @Test
    fun canonicalToolAssistantFixtureObeysRelayOrderKey() {
        val result = applyFixture("canonical_tool_assistant_authority.json")

        assertEquals(listOf("assistant-authority", "tool-authority"), result.messages.map { it.id })
    }

    @Test
    fun iosMixedMediaProjectionFixtureKeepsOnlyStableTextAndImageBlocks() {
        val message = applyFixture("ios_mixed_media_projection_replay.json").messages.single()

        assertEquals("分析一下这张图", message.content)
        assertEquals(listOf("blk-ios-image-prompt"), message.contentBlocks.filter { it.isTextBlock }.map { it.contentBlockId })
        assertEquals(listOf("blk-ios-image-file"), message.fileContentBlocks.map { it.contentBlockId })
    }

    @Test
    fun canonicalTimelineUpsertsOnlyByIdentityAndSortsOnlyByOrderKey() {
        val existing = listOf(
            ChatMessage(
                id = "old-message-id",
                role = MessageRole.assistant,
                state = MessageState.completed,
                content = "old",
                contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "old")),
                createdAt = "2026-06-09T08:00:00.000Z",
                seq = 99,
                timelineOrderKey = "0002",
                timelineIdentityKey = "identity-assistant",
                timelineItemKind = "message"
            )
        )

        val result = reconcileTimeline(
            existing = existing,
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "later-created-first",
                        seq = 2,
                        role = "assistant",
                        messageState = "completed",
                        createdAt = "2026-06-09T08:00:10.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "first by order")),
                        timelineOrderKey = "0001",
                        timelineIdentityKey = "identity-first",
                        timelineItemKind = "message"
                    ),
                    TimelineSnapshotMessage(
                        messageId = "new-message-id",
                        seq = 1,
                        role = "assistant",
                        messageState = "completed",
                        createdAt = "2026-06-09T08:00:00.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "updated")),
                        timelineOrderKey = "0002",
                        timelineIdentityKey = "identity-assistant",
                        timelineItemKind = "message"
                    )
                )
            )
        )

        assertEquals(listOf("later-created-first", "new-message-id"), result.messages.map { it.id })
        assertEquals(listOf("first by order", "updated"), result.messages.map { it.content })
        assertTrue(result.pending.isEmpty())
    }

    @Test
    fun transcriptResetUsesRelayGlobalOrderKeysAcrossOldAndNewSessions() {
        fun message(
            id: String,
            role: String,
            content: RelayChatContentBlock,
            orderKey: String,
            itemKind: String
        ) = TimelineSnapshotMessage(
            messageId = id,
            role = role,
            messageState = "completed",
            createdAt = "2026-07-26T20:07:31.000Z",
            content = listOf(content),
            timelineOrderKey = orderKey,
            timelineIdentityKey = "v1|main|$itemKind|$role|$id",
            timelineItemKind = itemKind
        )

        val result = reconcileTimeline(
            existing = emptyList(),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    message(
                        "new-command",
                        "user",
                        RelayChatContentBlock(type = "text", text = "/new"),
                        "v4|0|00004259300355960803|10|0000000000000001|new-command",
                        "message:user"
                    ),
                    message(
                        "old-water-assistant",
                        "assistant",
                        RelayChatContentBlock(type = "text", text = "water reading"),
                        "v4|0|00000000000000000321|50|0000000000000008|old-water-assistant",
                        "message:assistant"
                    ),
                    message(
                        "old-hello",
                        "user",
                        RelayChatContentBlock(type = "text", text = "你好"),
                        "v4|0|00000000000000000320|10|0000000000000001|old-hello",
                        "message:user"
                    ),
                    message(
                        "new-session",
                        "assistant",
                        RelayChatContentBlock(type = "text", text = "New session started."),
                        "v4|0|00004259300355960804|50|0000000000000002|new-session",
                        "message:assistant"
                    ),
                    message(
                        "old-water-image",
                        "user",
                        RelayChatContentBlock(
                            type = "image",
                            attachmentId = "att-water",
                            fileId = "water",
                            downloadUrl = "/api/mobile/files/water"
                        ),
                        "v4|0|00000000000000000321|10|0000000000000009|old-water-image",
                        "attachment"
                    ),
                    message(
                        "old-reply",
                        "assistant",
                        RelayChatContentBlock(type = "text", text = "你好 Alex"),
                        "v4|0|00000000000000000320|50|0000000000000004|old-reply",
                        "message:assistant"
                    )
                )
            )
        )

        assertEquals(
            listOf(
                "old-hello",
                "old-reply",
                "old-water-image",
                "old-water-assistant",
                "new-command",
                "new-session"
            ),
            result.messages.map { it.id }
        )
    }

    @Test
    fun fullCanonicalSnapshotDoesNotRetainExistingConfirmedMessagesMissingFromRelay() {
        val stale = ChatMessage(
            id = "stale-history",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "stale",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "stale")),
            createdAt = "2026-06-09T08:00:00.000Z",
            seq = 1,
            timelineOrderKey = "0001",
            timelineIdentityKey = "identity-stale",
            timelineItemKind = "message:assistant"
        )
        val pending = ChatMessage(
            id = "assistant-waiting",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "[[clawlink:typing]]",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "[[clawlink:typing]]")),
            runId = "run-new",
            sortTimestamp = 100.001,
            timelineOrderKey = "local:run-new:20:assistant-waiting",
            timelineIdentityKey = "local:waiting:run-new",
            timelineItemKind = "waiting"
        )

        val result = reconcileTimeline(
            existing = listOf(stale, pending),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "server-user",
                        role = "user",
                        messageState = "completed",
                        runId = "run-new",
                        createdAt = "2026-06-09T08:00:10.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "fresh")),
                        timelineOrderKey = "0002",
                        timelineIdentityKey = "identity-server-user",
                        timelineItemKind = "message:user"
                    )
                )
            )
        )

        assertEquals(listOf("server-user"), result.messages.map { it.id })
        assertEquals(listOf("assistant-waiting"), result.pending.map { it.id })
    }

    @Test
    fun canonicalSnapshotResolvesLocalUserWhenHostAddsRoleSuffixToRunIdentity() {
        val clientRunId = "66794ce4-6664-4581-88bd-ae57d27f5782"
        val localUser = ChatMessage(
            id = "user-$clientRunId",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "Send desktop screenshot to my phone test 1053",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "Send desktop screenshot to my phone test 1053")),
            createdAt = "",
            runId = "local-user-$clientRunId",
            sortTimestamp = 1_782_185_590.0,
            timelineOrderKey = "local:$clientRunId:010-user",
            timelineIdentityKey = "local:$clientRunId:message:user:010-user",
            timelineItemKind = "message:user"
        )
        val localAssistant = ChatMessage(
            id = "assistant-$clientRunId",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = protocolTypingMarkerText)),
            createdAt = "",
            runId = clientRunId,
            sortTimestamp = 1_782_185_590.001,
            timelineOrderKey = "local:$clientRunId:020-waiting",
            timelineIdentityKey = "local:$clientRunId:message:waiting:020-waiting",
            timelineItemKind = "message:assistant"
        )

        val result = reconcileTimeline(
            existing = listOf(localUser, localAssistant),
            snapshot = TimelineSnapshotPage(
                sessionKey = "ios-session",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "987948be",
                        seq = 1009,
                        turnSeq = 1,
                        turnId = "$clientRunId:user",
                        runId = "$clientRunId:user",
                        idempotencyKey = "$clientRunId:user",
                        role = "user",
                        messageState = "completed",
                        createdAt = "2026-06-23T02:53:13.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "Send desktop screenshot to my phone test 1053")),
                        timelineOrderKey = "v1|00000001782185593000|10|0000000000001009:part-text-1:987948be|user",
                        timelineIdentityKey = "ios-session:$clientRunId:user:message:user:987948be",
                        timelineItemKind = "message:user"
                    )
                )
            )
        )

        val visible = sortTimelineMessagesV3(result.messages + result.pending, "ios-session")
        assertEquals(listOf(localUser.id, "assistant-$clientRunId"), visible.map { it.id })
        assertEquals(1, visible.count { it.role == MessageRole.user })
        assertEquals("Send desktop screenshot to my phone test 1053", visible.first().content)
        assertEquals("987948be", visible.first().timelineMessageId)
        assertEquals(listOf("assistant-$clientRunId"), result.pending.map { it.id })
    }

    @Test
    fun canonicalSnapshotReplacesLocalWaitingWhenStreamingAssistantRunHasRoleSuffix() {
        val clientRunId = "client-run-stream-suffix"
        val localUser = ChatMessage(
            id = "local-user-stream-suffix",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "帮我分析一下这张图",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "帮我分析一下这张图")),
            createdAt = "2026-06-29T09:07:30.000Z",
            runId = "local-user-$clientRunId",
            sortTimestamp = 1_782_704_850.0,
            timelineOrderKey = "local:$clientRunId:010-user",
            timelineIdentityKey = "local:$clientRunId:message:user:010-user",
            timelineItemKind = "message:user"
        )
        val waiting = ChatMessage(
            id = "assistant-local-waiting",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = protocolTypingMarkerText,
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = protocolTypingMarkerText)),
            createdAt = "2026-06-29T09:07:30.001Z",
            runId = clientRunId,
            sortTimestamp = 1_782_704_850.001,
            timelineOrderKey = "local:$clientRunId:020-waiting",
            timelineIdentityKey = "local:$clientRunId:waiting:020-waiting",
            timelineItemKind = "waiting"
        )

        val result = reconcileTimeline(
            existing = listOf(localUser, waiting),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "assistant-stream-suffix",
                        seq = 42,
                        turnSeq = 42,
                        turnId = "$clientRunId:assistant",
                        runId = "$clientRunId:assistant",
                        role = "assistant",
                        messageState = "streaming",
                        createdAt = "2026-06-29T09:07:30.050Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = protocolTypingMarkerText)),
                        timelineOrderKey = "v1|00000000000000000042|20|0000000000000042:part-text-1:assistant-stream-suffix",
                        timelineIdentityKey = "v1|main|message|assistant|assistant-stream-suffix",
                        timelineItemKind = "message:assistant"
                    )
                )
            )
        )

        val visible = sortTimelineMessagesV3(result.messages + result.pending, "main")
        assertEquals(listOf("local-user-stream-suffix", waiting.id), visible.map { it.id })
        assertEquals("assistant-stream-suffix", visible.last().timelineMessageId)
        assertEquals(1, visible.count { it.role == MessageRole.assistant && it.state == MessageState.streaming })
    }

    @Test
    fun fullCanonicalSnapshotKeepsLocalUserForCurrentPendingTurnOnly() {
        val staleLocalUser = ChatMessage(
            id = "local-stale",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "stale",
            runId = "local-user-stale-run"
        )
        val currentLocalUser = ChatMessage(
            id = "local-current",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "current",
            runId = "local-user-current-run"
        )
        val waiting = ChatMessage(
            id = "assistant-waiting",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "[[clawlink:typing]]",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "[[clawlink:typing]]")),
            runId = "current-run"
        )

        val result = reconcileTimeline(
            existing = listOf(staleLocalUser, currentLocalUser, waiting),
            snapshot = TimelineSnapshotPage(sessionKey = "main", messages = emptyList())
        )

        assertTrue(result.messages.isEmpty())
        assertEquals(listOf("local-current", "assistant-waiting"), result.pending.map { it.id })
    }

    @Test
    fun boundedCanonicalSnapshotRetainsExistingMessagesOutsideFetchedRange() {
        val older = ChatMessage(
            id = "older",
            role = MessageRole.user,
            state = MessageState.completed,
            content = "older",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "older")),
            createdAt = "2026-06-09T08:00:00.000Z",
            seq = 1,
            timelineOrderKey = "0001",
            timelineIdentityKey = "identity-older",
            timelineItemKind = "message:user"
        )
        val inRangeStale = ChatMessage(
            id = "in-range-stale",
            role = MessageRole.assistant,
            state = MessageState.completed,
            content = "stale",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "stale")),
            createdAt = "2026-06-09T08:00:01.000Z",
            seq = 2,
            timelineOrderKey = "0002",
            timelineIdentityKey = "identity-stale",
            timelineItemKind = "message:assistant"
        )

        val result = reconcileTimeline(
            existing = listOf(older, inRangeStale),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                rangeStartCursor = "seq:2",
                rangeEndCursor = "seq:4",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "fresh",
                        seq = 2,
                        role = "assistant",
                        messageState = "completed",
                        createdAt = "2026-06-09T08:00:02.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "fresh")),
                        timelineOrderKey = "0002",
                        timelineIdentityKey = "identity-fresh",
                        timelineItemKind = "message:assistant"
                    )
                )
            )
        )

        assertEquals(listOf("older", "fresh"), result.messages.map { it.id })
    }

    @Test
    fun canonicalToolAndNonResolvingAttachmentDoNotClearWaitingButAssistantTextDoes() {
        val waitingAssistant = ChatMessage(
            id = "assistant-waiting",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "正在同步回复...",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "正在同步回复...")),
            runId = "run-tool",
            sortTimestamp = 100.001
        )

        val toolOnly = reconcileTimeline(
            existing = listOf(waitingAssistant),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "tool-1",
                        role = "tool",
                        messageState = "completed",
                        runId = "run-tool",
                        createdAt = "2026-06-09T08:00:00.000Z",
                        content = listOf(RelayChatContentBlock(type = "tool_result", text = "tool output", toolCallId = "call-1")),
                        timelineOrderKey = "0001",
                        timelineIdentityKey = "identity-tool-1",
                        timelineItemKind = "tool"
                    )
                )
            )
        )

        assertEquals(listOf("tool-1"), toolOnly.messages.map { it.id })
        assertEquals(listOf("assistant-waiting"), toolOnly.pending.map { it.id })

        val attachmentResult = reconcileTimeline(
            existing = listOf(waitingAssistant),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "image-1",
                        role = "assistant",
                        messageState = "completed",
                        runId = "run-tool",
                        createdAt = "2026-06-09T08:00:00.000Z",
                        content = listOf(
                            RelayChatContentBlock(
                                type = "image",
                                attachmentId = "att-image-1",
                                fileName = "result.png",
                                mimeType = "image/png",
                                downloadUrl = "https://relay.example/files/att-image-1"
                            )
                        ),
                        timelineOrderKey = "0001",
                        timelineIdentityKey = "identity-image-1",
                        timelineItemKind = "attachment",
                        timelineResolvesWaiting = false
                    )
                )
            )
        )

        assertEquals(listOf("image-1"), attachmentResult.messages.map { it.id })
        assertEquals(listOf("assistant-waiting"), attachmentResult.pending.map { it.id })

        val resolvingAttachmentResult = reconcileTimeline(
            existing = listOf(waitingAssistant),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "image-2",
                        role = "assistant",
                        messageState = "completed",
                        runId = "run-tool",
                        createdAt = "2026-06-09T08:00:00.000Z",
                        content = listOf(
                            RelayChatContentBlock(
                                type = "image",
                                attachmentId = "att-image-2",
                                fileName = "result.png",
                                mimeType = "image/png",
                                downloadUrl = "https://relay.example/files/att-image-2"
                            )
                        ),
                        timelineOrderKey = "0001",
                        timelineIdentityKey = "identity-image-2",
                        timelineItemKind = "attachment",
                        timelineResolvesWaiting = true
                    )
                )
            )
        )

        assertEquals(listOf("image-2"), resolvingAttachmentResult.messages.map { it.id })
        assertTrue(resolvingAttachmentResult.pending.isEmpty())

        val assistantResult = reconcileTimeline(
            existing = listOf(waitingAssistant),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "assistant-final",
                        role = "assistant",
                        messageState = "completed",
                        runId = "run-tool",
                        createdAt = "2026-06-09T08:00:01.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "final answer")),
                        timelineOrderKey = "0002",
                        timelineIdentityKey = "identity-assistant-final",
                        timelineItemKind = "message"
                    )
                )
            )
        )

        assertEquals(listOf("assistant-final"), assistantResult.messages.map { it.id })
        assertTrue(assistantResult.pending.isEmpty())
    }

    @Test
    fun localEchoFixtureReplacesPendingWithServerAck() {
        val result = applyFixture("local_echo_then_history_snapshot.json")
        assertEquals(listOf("local-user-client-run-1"), result.messages.map { it.id })
        assertEquals(listOf("user-client-run-1"), result.messages.map { it.timelineMessageId })
        assertTrue(result.pending.isEmpty())
    }

    @Test
    fun userEchoDoesNotRemoveSameRunWaitingAssistantPlaceholder() {
        val waitingAssistant = ChatMessage(
            id = "assistant-waiting",
            role = MessageRole.assistant,
            state = MessageState.streaming,
            content = "[[clawlink:typing]]",
            contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "[[clawlink:typing]]")),
            runId = "run-hi",
            sortTimestamp = 100.001
        )

        val result = reconcileTimeline(
            existing = listOf(waitingAssistant),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "server-user-hi",
                        role = "user",
                        messageState = "completed",
                        runId = "run-hi",
                        turnId = "run-hi",
                        createdAt = "2026-06-09T17:00:00.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "Hi")),
                        timelineOrderKey = "0001",
                        timelineIdentityKey = "main:message:server-user-hi",
                        timelineItemKind = "message:user"
                    )
                )
            )
        )

        assertEquals(listOf("server-user-hi"), result.messages.map { it.id })
        assertEquals(listOf("assistant-waiting"), result.pending.map { it.id })
    }

    @Test
    fun fileAttachmentFixtureKeepsImageMetadata() {
        val result = applyFixture("image_user_then_file_result.json")
        val assistant = result.messages.single { it.id == "assistant-screenshot-result-1" }
        val image = assistant.contentBlocks.single { it.isFileBlock }
        assertEquals("att-screenshot-1", image.attachmentId)
        assertEquals(1440, image.imageWidth)
        assertEquals(900, image.imageHeight)
        assertEquals("https://relay.example/thumb/att-screenshot-1", image.thumbnailUrl)
    }

    @Test
    fun attachmentRefreshFixtureUpdatesOneBubbleByAttachmentId() {
        val result = applyFixture("attachment_url_refresh.json")
        assertEquals(1, result.messages.size)
        assertEquals("https://relay.example/fresh", result.messages.single().contentBlocks.single().downloadUrl)
    }

    @Test
    fun serverMessageIdAndConversationSeqAreAuthoritative() {
        val result = reconcileTimeline(
            existing = emptyList(),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        serverMessageId = "srv_user_1",
                        conversationSeq = 1,
                        messageId = "history-user-1",
                        seq = 1_781_000_000_000_000,
                        turnSeq = 1,
                        role = "user",
                        messageState = "completed",
                        runId = "client-run-1",
                        turnId = "client-run-1",
                        partId = "part-text-1",
                        clientMessageId = "client-run-1",
                        idempotencyKey = "client-run-1",
                        createdAt = "2026-06-09T08:00:10.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "把桌面图片发过来")),
                        timelineOrderKey = "0001",
                        timelineIdentityKey = "main:message:srv_user_1",
                        timelineItemKind = "message:user"
                    ),
                    TimelineSnapshotMessage(
                        serverMessageId = "srv_assistant_1",
                        conversationSeq = 2,
                        messageId = "history-assistant-1",
                        seq = 10,
                        turnSeq = 2,
                        role = "assistant",
                        messageState = "completed",
                        runId = "client-run-1",
                        turnId = "client-run-1",
                        partId = "part-text-1",
                        createdAt = "2026-06-09T08:00:00.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "收到")),
                        timelineOrderKey = "0002",
                        timelineIdentityKey = "main:message:srv_assistant_1",
                        timelineItemKind = "message:assistant"
                    )
                )
            )
        )

        assertEquals(listOf("srv_user_1", "srv_assistant_1"), result.messages.map { it.id })
        assertEquals(listOf(1L, 2L), result.messages.map { it.seq })
    }

    @Test
    fun canonicalTimelineKeepsLocalUserBeforeMatchingPendingAssistantWhenUserTimestampMovesLater() {
        val messages = sortTimelineMessagesV3(
            listOf(
                ChatMessage(
                    id = "assistant-pending",
                    role = MessageRole.assistant,
                    state = MessageState.streaming,
                    content = "正在连接 Relay...",
                    contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "正在连接 Relay...")),
                    createdAt = "2026-06-07T12:00:00.000Z",
                    runId = "client-run-1",
                    sortTimestamp = 100.0
                ),
                ChatMessage(
                    id = "local-user",
                    role = MessageRole.user,
                    state = MessageState.completed,
                    content = "你好啊",
                    contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "你好啊")),
                    createdAt = "2026-06-07T12:00:00.100Z",
                    runId = "local-user-client-run-1",
                    sortTimestamp = 110.0
                )
            )
        )

        assertEquals(listOf("local-user", "assistant-pending"), messages.map { it.id })
    }

    @Test
    fun canonicalTimelineKeepsLocalUserBeforeMatchingStreamingAssistantTextWhenUserTimestampMovesLater() {
        val messages = sortTimelineMessagesV3(
            listOf(
                ChatMessage(
                    id = "assistant-streaming-text",
                    role = MessageRole.assistant,
                    state = MessageState.streaming,
                    content = "好的，有需要随时找我",
                    contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "好的，有需要随时找我")),
                    createdAt = "2026-06-07T12:00:00.000Z",
                    runId = "client-run-streaming-text",
                    sortTimestamp = 100.0
                ),
                ChatMessage(
                    id = "local-user-streaming-text",
                    role = MessageRole.user,
                    state = MessageState.completed,
                    content = "No",
                    contentBlocks = listOf(RelayChatContentBlock(type = "text", text = "No")),
                    createdAt = "2026-06-07T12:00:00.100Z",
                    runId = "local-user-client-run-streaming-text",
                    sortTimestamp = 110.0
                )
            )
        )

        assertEquals(listOf("local-user-streaming-text", "assistant-streaming-text"), messages.map { it.id })
    }

    @Test
    fun canonicalTimelineAnchorsProviderRunAnswerByClientAliasBeforeHistoryCatchesUp() {
        val localUser = ChatMessage(
            id = "local-user-alias",
            role = MessageRole.user,
            content = "question",
            runId = "local-user-client-run-alias",
            turnId = "client-run-alias",
            clientMessageId = "client-run-alias",
            idempotencyKey = "client-run-alias",
            timelineOrderKey = "local:client-run-alias|10|local-user-alias",
            timelineIdentityKey = "local:message:user:client-run-alias",
            timelineItemKind = "message:user",
            source = "local",
            localTurnOrder = 0
        )
        val providerAnswer = ChatMessage(
            id = "provider-answer",
            role = MessageRole.assistant,
            content = "answer",
            runId = "provider-run-alias",
            turnId = "server-turn-alias",
            clientMessageId = "client-run-alias",
            idempotencyKey = "client-run-alias",
            timelineOrderKey = "v1|00000000000000000001|50|000000|provider-answer",
            timelineIdentityKey = "message:assistant:provider-answer",
            timelineItemKind = "message:assistant"
        )

        val ordered = sortTimelineMessagesV3(listOf(providerAnswer, localUser))

        assertEquals(listOf("local-user-alias", "provider-answer"), ordered.map { it.id })
    }

    @Test
    fun canonicalTimelineKeepsMultipleLocalPromptsInOrderWhenSecondAnswerArrivesFirst() {
        fun localUser(id: String, runId: String, order: Long) = ChatMessage(
            id = id,
            role = MessageRole.user,
            content = id,
            runId = "local-user-$runId",
            turnId = runId,
            clientMessageId = runId,
            idempotencyKey = runId,
            timelineOrderKey = "local:$runId|10|$id",
            timelineIdentityKey = "local:message:user:$runId",
            timelineItemKind = "message:user",
            source = "local",
            localTurnOrder = order
        )
        val firstUser = localUser("question-a", "client-run-a", 10)
        val secondUser = localUser("question-b", "client-run-b", 11)
        val secondAnswer = ChatMessage(
            id = "answer-b",
            role = MessageRole.assistant,
            content = "answer-b",
            runId = "provider-run-b",
            turnId = "server-turn-b",
            clientMessageId = "client-run-b",
            idempotencyKey = "client-run-b",
            timelineOrderKey = "v1|00000000000000000002|50|000000|answer-b",
            timelineIdentityKey = "message:assistant:answer-b",
            timelineItemKind = "message:assistant"
        )

        val ordered = sortTimelineMessagesV3(listOf(secondAnswer, firstUser, secondUser))

        assertEquals(listOf("question-a", "question-b", "answer-b"), ordered.map { it.id })
    }

    @Test
    fun canonicalSnapshotUsesRelayOrderAfterLocalTurnsAreConfirmedAcrossMixedHermesDomains() {
        fun localUser(id: String, runId: String, content: String, order: Long) = ChatMessage(
            id = id,
            role = MessageRole.user,
            content = content,
            runId = "local-user-$runId",
            turnId = runId,
            clientMessageId = runId,
            idempotencyKey = runId,
            timelineOrderKey = "local:$runId|10|$id",
            timelineIdentityKey = "local:message:user:$runId",
            timelineItemKind = "message:user",
            source = "local",
            localTurnOrder = order
        )
        fun snapshotMessage(
            id: String,
            role: String,
            runId: String,
            content: String,
            orderKey: String,
            kind: String
        ) = TimelineSnapshotMessage(
            serverMessageId = id,
            messageId = id,
            role = role,
            messageState = "completed",
            runId = runId,
            turnId = runId,
            clientMessageId = runId.takeIf { role == "user" },
            content = listOf(RelayChatContentBlock(type = "text", text = content)),
            timelineOrderKey = orderKey,
            timelineIdentityKey = "v1|mobile-hermes|message|$role|$id",
            timelineItemKind = kind,
            source = "history"
        )

        val newRun = "new-run"
        val helloRun = "hello-run"
        val pingRun = "ping-run"
        val existing = listOf(
            localUser("local-new", newRun, "/new", 0),
            localUser("local-hello", helloRun, "hello", 1),
            localUser("local-ping", pingRun, "ping", 2)
        )
        val snapshot = TimelineSnapshotPage(
            sessionKey = "mobile-hermes",
            messages = listOf(
                snapshotMessage("hello-user", "user", helloRun, "hello", "v5|0|00000000000000004349|00000000000000000000|10|hello-user", "message:user"),
                snapshotMessage("hello-answer", "assistant", helloRun, "hello-answer", "v5|0|00000000000000004350|00000000000000000000|50|hello-answer", "message:assistant"),
                snapshotMessage("ping-user", "user", pingRun, "ping", "v5|0|00000000000000004351|00000000000000000000|10|ping-user", "message:user"),
                snapshotMessage("new-user", "user", newRun, "/new", "v5|0|00000001786421720969|00000000000000000000|10|new-user", "message:user"),
                snapshotMessage("new-answer", "assistant", newRun, "New session started!", "v5|0|00001786421722525000|00000000000000000000|50|new-answer", "message:assistant"),
                snapshotMessage("ping-answer", "assistant", pingRun, "pong", "v5|1|00000000000000000003|00000000000000000000|50|ping-answer", "message:assistant")
            )
        )

        val reconciled = reconcileTimeline(existing = existing, snapshot = snapshot)
        val ordered = sortTimelineMessagesV3(reconciled.messages + reconciled.pending, snapshot.sessionKey)

        assertEquals(emptyList<ChatMessage>(), reconciled.pending)
        // Once the snapshot confirms all three users, they are canonical rows.
        // Their previous localTurnOrder is retained as metadata only; it must
        // not override the Relay's mixed namespace/sequence order.
        assertEquals(listOf(1L, 2L, 0L), ordered.filter { it.role == MessageRole.user }.mapNotNull { it.localTurnOrder })
        assertEquals(
            listOf("hello", "hello-answer", "ping", "/new", "New session started!", "pong"),
            ordered.map { it.content }
        )
    }

    @Test
    fun failedLocalPromptDoesNotJoinLaterAnswerOverlay() {
        val failedUser = ChatMessage(
            id = "failed-question",
            role = MessageRole.user,
            content = "failed",
            runId = "local-user-failed-run",
            clientMessageId = "failed-run",
            idempotencyKey = "failed-run",
            timelineOrderKey = "local:failed-run|10|failed-question",
            timelineIdentityKey = "local:message:user:failed-run",
            timelineItemKind = "message:user",
            source = "local",
            deliveryState = "failed",
            localTurnOrder = 20
        )
        val laterUser = ChatMessage(
            id = "later-question",
            role = MessageRole.user,
            content = "later",
            runId = "local-user-later-run",
            clientMessageId = "later-run",
            idempotencyKey = "later-run",
            timelineOrderKey = "local:later-run|10|later-question",
            timelineIdentityKey = "local:message:user:later-run",
            timelineItemKind = "message:user",
            source = "local",
            localTurnOrder = 21
        )
        val laterAnswer = ChatMessage(
            id = "later-answer",
            role = MessageRole.assistant,
            content = "later-answer",
            runId = "provider-later-run",
            clientMessageId = "later-run",
            timelineOrderKey = "v1|00000000000000000003|50|000000|later-answer",
            timelineIdentityKey = "message:assistant:later-answer",
            timelineItemKind = "message:assistant"
        )

        val ordered = sortTimelineMessagesV3(listOf(failedUser, laterAnswer, laterUser))

        assertEquals(listOf("failed-question", "later-question", "later-answer"), ordered.map { it.id })
    }

    @Test
    fun sameRunAndPartWithDifferentRolesRemainSeparateMessages() {
        val result = reconcileTimeline(
            existing = emptyList(),
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "user-1",
                        seq = 1,
                        runId = "run-1",
                        partId = "part-text-1",
                        role = "user",
                        messageState = "completed",
                        createdAt = "2026-06-07T12:00:00.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "look")),
                        timelineOrderKey = "0001",
                        timelineIdentityKey = "main:message:user-1",
                        timelineItemKind = "message:user"
                    ),
                    TimelineSnapshotMessage(
                        messageId = "assistant-1",
                        seq = 2,
                        runId = "run-1",
                        partId = "part-text-1",
                        role = "assistant",
                        messageState = "completed",
                        createdAt = "2026-06-07T12:00:01.000Z",
                        content = listOf(RelayChatContentBlock(type = "text", text = "ok")),
                        timelineOrderKey = "0002",
                        timelineIdentityKey = "main:message:assistant-1",
                        timelineItemKind = "message:assistant"
                    )
                )
            )
        )

        assertEquals(listOf("user-1", "assistant-1"), result.messages.map { it.id })
    }

    @Test
    fun historyAttachmentReplacesLocalAttachmentWithSameAttachmentId() {
        val existing = listOf(
            ChatMessage(
                id = "local-file",
                role = MessageRole.user,
                content = "photo.jpg",
                contentBlocks = listOf(
                    RelayChatContentBlock(
                        type = "image",
                        attachmentId = "att-local",
                        fileName = "photo.jpg",
                        downloadUrl = "file:///tmp/photo.jpg"
                    )
                ),
                createdAt = "2026-06-07T12:00:00.000Z",
                runId = "file-att-local",
                sortTimestamp = 100.0
            )
        )
        val result = reconcileTimeline(
            existing = existing,
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                rangeStartCursor = "seq:1",
                rangeEndCursor = "seq:4",
                messages = listOf(
                    TimelineSnapshotMessage(
                        messageId = "history-file",
                        seq = 3,
                        role = "user",
                        messageState = "completed",
                        createdAt = "2026-06-07T12:00:03.000Z",
                        content = listOf(
                            RelayChatContentBlock(
                                type = "image",
                                attachmentId = "att-local",
                                fileName = "photo.jpg",
                                downloadUrl = "/api/mobile/files/att-local"
                            )
                        ),
                        attachmentIds = listOf("att-local"),
                        timelineOrderKey = "0003",
                        timelineIdentityKey = "main:attachment:history-file",
                        timelineItemKind = "attachment"
                    )
                )
            )
        )

        assertEquals(listOf("history-file"), result.messages.map { it.id })
        assertEquals("/api/mobile/files/att-local", result.messages.single().contentBlocks.single().downloadUrl)
    }

    @Test
    fun hermesAcceptedInputSurvivesStaleSnapshotUntilExplicitCommit() {
        val clientRunId = "client-run-accepted-gap"
        val localUser = ChatMessage(
            id = "local-user-accepted-gap",
            role = MessageRole.user,
            content = "never disappear",
            runId = "local-user-$clientRunId",
            turnId = clientRunId,
            clientMessageId = clientRunId,
            idempotencyKey = clientRunId,
            timelineOrderKey = "local:$clientRunId|10|user",
            timelineIdentityKey = "local:message:user:$clientRunId",
            timelineItemKind = "message:user",
            source = "local"
        )
        fun relayRow(state: String, source: String) = TimelineSnapshotMessage(
            serverMessageId = "srv-user-accepted-gap",
            conversationSeq = 101,
            conversationSeqState = state,
            messageId = "user-$clientRunId",
            role = "user",
            messageState = "completed",
            runId = clientRunId,
            turnId = clientRunId,
            clientMessageId = clientRunId,
            idempotencyKey = clientRunId,
            createdAt = "2026-08-18T09:30:00.000Z",
            content = listOf(RelayChatContentBlock(type = "text", text = "never disappear")),
            source = source,
            timelineOrderKey = "v5|1|00000000000000000101|00000000000000000001|10|accepted-gap",
            timelineIdentityKey = "v1|main|message|user|accepted-gap",
            timelineItemKind = "message:user"
        )

        val accepted = reconcileTimeline(
            existing = listOf(localUser),
            snapshot = TimelineSnapshotPage(sessionKey = "main", messages = listOf(relayRow("provisional", "local")))
        )
        assertEquals(listOf(localUser.id), accepted.messages.map { it.id })
        assertEquals(listOf("provisional"), accepted.messages.map { it.conversationSeqState })

        val stale = reconcileTimeline(
            existing = accepted.messages,
            snapshot = TimelineSnapshotPage(
                sessionKey = "main",
                rangeStartCursor = "seq:100",
                rangeEndCursor = "seq:102",
                messages = emptyList()
            )
        )
        assertEquals(listOf(localUser.id), stale.pending.map { it.id })

        val committed = reconcileTimeline(
            existing = stale.pending,
            snapshot = TimelineSnapshotPage(sessionKey = "main", messages = listOf(relayRow("committed", "history")))
        )
        assertTrue(committed.pending.isEmpty())
        assertEquals(listOf(localUser.id), committed.messages.map { it.id })
        assertEquals(listOf("committed"), committed.messages.map { it.conversationSeqState })
    }

}

private fun debugTimelineDump(messages: List<ChatMessage>, sessionKey: String): List<Map<String, String>> {
    return messages.map { message ->
        mapOf(
            "stableKey" to message.timelineStableKey
        )
    }
}
