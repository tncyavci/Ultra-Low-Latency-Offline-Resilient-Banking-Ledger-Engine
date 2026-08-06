package com.ledgerengine.network

import com.ledgerengine.network.LedgerApiClient.Companion.installLedgerPlugins
import com.ledgerengine.network.dto.TransactionSyncRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class LedgerApiClientTest {

    private fun requestDto(idempotencyKey: String = "idem-1") = TransactionSyncRequestDto(
        id = "tx-1",
        idempotencyKey = idempotencyKey,
        accountId = "acct-1",
        counterpartyAccountId = "acct-2",
        direction = "DEBIT",
        amountMinorUnits = 2599,
        currencyCode = "EUR",
        memo = "coffee",
        hlcTimestamp = "0000000000001700000000.0000000000.device-a",
        createdAtEpochMillis = 1_700_000_000_000,
        signature = "c2ln",
        signingKeyId = "device-key-1",
    )

    @Test
    fun `successful submit returns parsed response and injects idempotency header`() = runTest {
        var capturedIdempotencyHeader: String? = null

        val mockEngine = MockEngine { request ->
            capturedIdempotencyHeader = request.headers[HEADER_NAME]
            respond(
                content = """{"id":"tx-1","status":"CONFIRMED","server_sequence":42}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = LedgerApiClient(
            config = NetworkConfig("https://api.test", "wss://api.test/ws", "device-a"),
            engine = HttpClient(mockEngine) { installLedgerPlugins() },
        )

        val result = client.submitTransaction(requestDto(idempotencyKey = "idem-42"))

        check(result is NetworkResult.Success)
        assertEquals(42L, result.value.serverSequence)
        assertEquals("CONFIRMED", result.value.status)
        assertEquals("idem-42", capturedIdempotencyHeader)
    }

    @Test
    fun `persistent server errors are retried then surfaced as ApiError`() = runTest {
        val attempts = AtomicInteger(0)
        val mockEngine = MockEngine {
            attempts.incrementAndGet()
            respond(content = "server exploded", status = HttpStatusCode.InternalServerError)
        }
        val client = LedgerApiClient(
            config = NetworkConfig("https://api.test", "wss://api.test/ws", "device-a"),
            engine = HttpClient(mockEngine) { installLedgerPlugins() },
        )

        val result = client.submitTransaction(requestDto())

        check(result is NetworkResult.ApiError)
        assertEquals(500, result.httpStatus)
        assertTrue("expected multiple retry attempts, got ${attempts.get()}", attempts.get() > 1)
    }

    @Test
    fun `connectivity failures are surfaced as ConnectivityError, not thrown`() = runTest {
        val mockEngine = MockEngine { throw IOException("no network") }
        val client = LedgerApiClient(
            config = NetworkConfig("https://api.test", "wss://api.test/ws", "device-a"),
            engine = HttpClient(mockEngine) { installLedgerPlugins() },
        )

        val result = client.submitTransaction(requestDto())

        assertTrue(result is NetworkResult.ConnectivityError)
    }
}
