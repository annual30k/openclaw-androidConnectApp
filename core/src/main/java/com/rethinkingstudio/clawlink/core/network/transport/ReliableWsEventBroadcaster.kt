package com.rethinkingstudio.clawlink.core.network.transport

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * WebSocket 回调不能阻塞等待 UI 消费事件，因此每个订阅者持有独立的无损通道。
 * 这保证 ChatStore 与 GatewayStore 都能看到完整且同序的协议事件，而不是竞争消费或覆盖旧事件。
 */
internal class ReliableWsEventBroadcaster<T> {
    private val lock = Any()
    private val subscribers = LinkedHashMap<Long, Channel<T>>()
    private var nextSubscriberId = 0L

    val events: Flow<T> = flow {
        val channel = Channel<T>(Channel.UNLIMITED)
        val subscriberId = synchronized(lock) {
            nextSubscriberId += 1
            subscribers[nextSubscriberId] = channel
            nextSubscriberId
        }
        try {
            emitAll(channel)
        } finally {
            synchronized(lock) {
                subscribers.remove(subscriberId)
            }
            channel.close()
        }
    }

    fun publish(event: T) {
        val channels = synchronized(lock) { subscribers.values.toList() }
        channels.forEach { channel -> channel.trySend(event) }
    }

    fun close() {
        val channels = synchronized(lock) {
            val current = subscribers.values.toList()
            subscribers.clear()
            current
        }
        channels.forEach { channel -> channel.close() }
    }
}
