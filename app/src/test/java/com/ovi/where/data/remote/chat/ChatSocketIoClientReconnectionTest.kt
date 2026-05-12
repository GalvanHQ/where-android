package com.ovi.where.data.remote.chat

import com.ovi.where.data.remote.chat.ChatSocketIoClient.Companion.INITIAL_BACKOFF_MS
import com.ovi.where.data.remote.chat.ChatSocketIoClient.Companion.MAX_BACKOFF_MS
import com.ovi.where.data.remote.chat.ChatSocketIoClient.Companion.MAX_RECONNECT_ATTEMPTS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the exponential backoff reconnection logic in ChatSocketIoClient.
 * Validates Requirements 13.1, 13.2, 13.3.
 */
class ChatSocketIoClientReconnectionTest {

    private val client = ChatSocketIoClient()

    @Test
    fun `calculateBackoffDelay returns 1s for first attempt`() {
        val delay = client.calculateBackoffDelay(0)
        assertEquals(1000L, delay)
    }

    @Test
    fun `calculateBackoffDelay returns 2s for second attempt`() {
        val delay = client.calculateBackoffDelay(1)
        assertEquals(2000L, delay)
    }

    @Test
    fun `calculateBackoffDelay returns 4s for third attempt`() {
        val delay = client.calculateBackoffDelay(2)
        assertEquals(4000L, delay)
    }

    @Test
    fun `calculateBackoffDelay returns 8s for fourth attempt`() {
        val delay = client.calculateBackoffDelay(3)
        assertEquals(8000L, delay)
    }

    @Test
    fun `calculateBackoffDelay returns 16s for fifth attempt`() {
        val delay = client.calculateBackoffDelay(4)
        assertEquals(16000L, delay)
    }

    @Test
    fun `calculateBackoffDelay is capped at 30s for sixth attempt and beyond`() {
        // 2^5 * 1000 = 32000, capped at 30000
        val delay5 = client.calculateBackoffDelay(5)
        assertEquals(MAX_BACKOFF_MS, delay5)

        val delay6 = client.calculateBackoffDelay(6)
        assertEquals(MAX_BACKOFF_MS, delay6)

        val delay9 = client.calculateBackoffDelay(9)
        assertEquals(MAX_BACKOFF_MS, delay9)
    }

    @Test
    fun `calculateBackoffDelay follows exponential pattern before cap`() {
        for (attempt in 0 until 5) {
            val expected = INITIAL_BACKOFF_MS * (1L shl attempt)
            val actual = client.calculateBackoffDelay(attempt)
            assertEquals("Attempt $attempt should have delay $expected", expected, actual)
        }
    }

    @Test
    fun `calculateBackoffDelay never exceeds MAX_BACKOFF_MS`() {
        for (attempt in 0 until 20) {
            val delay = client.calculateBackoffDelay(attempt)
            assertTrue(
                "Delay for attempt $attempt ($delay) should not exceed $MAX_BACKOFF_MS",
                delay <= MAX_BACKOFF_MS
            )
        }
    }

    @Test
    fun `MAX_RECONNECT_ATTEMPTS is 10`() {
        assertEquals(10, MAX_RECONNECT_ATTEMPTS)
    }

    @Test
    fun `INITIAL_BACKOFF_MS is 1000`() {
        assertEquals(1000L, INITIAL_BACKOFF_MS)
    }

    @Test
    fun `MAX_BACKOFF_MS is 30000`() {
        assertEquals(30_000L, MAX_BACKOFF_MS)
    }

    @Test
    fun `initial connectionState is DISCONNECTED`() {
        assertEquals(ChatSocketIoClient.ConnectionState.DISCONNECTED, client.connectionState.value)
    }
}
