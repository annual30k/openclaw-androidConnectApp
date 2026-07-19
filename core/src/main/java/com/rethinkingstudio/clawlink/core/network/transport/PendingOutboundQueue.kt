package com.rethinkingstudio.clawlink.core.network.transport

internal data class PendingOutboundMessage(
    val text: String,
    val requestId: String?
)

internal enum class PendingOutboundOfferResult {
    accepted,
    rejectedFull
}

/** 有界队列只拒绝新请求并显式回报，绝不能为腾位置静默删除已经接受的请求。 */
internal class PendingOutboundQueue(private val capacity: Int) {
    private val messages = ArrayDeque<PendingOutboundMessage>()

    init {
        require(capacity > 0)
    }

    @Synchronized
    fun offer(message: PendingOutboundMessage): PendingOutboundOfferResult {
        if (messages.size >= capacity) return PendingOutboundOfferResult.rejectedFull
        messages.addLast(message)
        return PendingOutboundOfferResult.accepted
    }

    /** 只有 WebSocket 明确接受入队后才移除；false 会保留当前消息及其后的顺序。 */
    @Synchronized
    fun flush(send: (PendingOutboundMessage) -> Boolean): Boolean {
        while (messages.isNotEmpty()) {
            val next = messages.first()
            if (!send(next)) return false
            messages.removeFirst()
        }
        return true
    }

    @Synchronized
    fun clear() {
        messages.clear()
    }

    @Synchronized
    fun size(): Int = messages.size

    @Synchronized
    fun snapshot(): List<PendingOutboundMessage> = messages.toList()

    @Synchronized
    fun drain(): List<PendingOutboundMessage> {
        val pending = messages.toList()
        messages.clear()
        return pending
    }
}
