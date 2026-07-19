package com.rethinkingstudio.clawlink.core.network.transport

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayWebSocketReliabilityTest {
    @Test
    fun boundedOutboundQueueRejectsNewMessageWithoutEvictingAcceptedMessages() {
        val queue = PendingOutboundQueue(capacity = 2)
        val first = PendingOutboundMessage("first", "run-1")
        val second = PendingOutboundMessage("second", "run-2")

        assertEquals(PendingOutboundOfferResult.accepted, queue.offer(first))
        assertEquals(PendingOutboundOfferResult.accepted, queue.offer(second))
        assertEquals(
            PendingOutboundOfferResult.rejectedFull,
            queue.offer(PendingOutboundMessage("third", "run-3"))
        )
        assertEquals(listOf(first, second), queue.snapshot())
        assertEquals(listOf(first, second), queue.drain())
        assertEquals(0, queue.size())
    }

    @Test
    fun outboundQueueRetainsRejectedSocketMessageAndPreservesOrderForReconnect() {
        val queue = PendingOutboundQueue(capacity = 3)
        queue.offer(PendingOutboundMessage("first", "run-1"))
        queue.offer(PendingOutboundMessage("second", "run-2"))

        assertFalse(queue.flush { message -> message.text != "first" })
        assertEquals(listOf("first", "second"), queue.snapshot().map(PendingOutboundMessage::text))

        val delivered = mutableListOf<String>()
        assertTrue(queue.flush { message -> delivered.add(message.text) })
        assertEquals(listOf("first", "second"), delivered)
        assertEquals(0, queue.size())
    }

    @Test
    fun everySubscriberReceivesBurstEventsEvenWhenOneConsumerIsSlow() = runBlocking {
        val broadcaster = ReliableWsEventBroadcaster<Int>()
        val fast = async(start = CoroutineStart.UNDISPATCHED) {
            broadcaster.events.take(200).toList()
        }
        val slow = async(start = CoroutineStart.UNDISPATCHED) {
            broadcaster.events.onEach { delay(1) }.take(200).toList()
        }

        repeat(200, broadcaster::publish)

        assertEquals((0 until 200).toList(), fast.await())
        assertEquals((0 until 200).toList(), slow.await())
        broadcaster.close()
    }
}
