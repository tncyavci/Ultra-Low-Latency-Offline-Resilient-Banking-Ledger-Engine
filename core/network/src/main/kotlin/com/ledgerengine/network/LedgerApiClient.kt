package com.ledgerengine.network

import com.ledgerengine.network.dto.TransactionEventDto
import com.ledgerengine.network.dto.TransactionSyncRequestDto
import com.ledgerengine.network.dto.TransactionSyncResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.UUID

data class NetworkConfig(
    val baseUrl: String,
    val webSocketUrl: String,
    val deviceId: String,
)

/**
 * Thin, typed wrapper over the ledger backend's REST + WebSocket surface.
 * Every mutating call is idempotency-keyed and every request round-trips
 * through [HttpRequestRetry]'s exponential backoff, so a flaky connection
 * degrades to "slow" rather than "silently drops a transaction".
 */
class LedgerApiClient(
    private val config: NetworkConfig,
    engine: HttpClient = defaultClient(),
) {
    private val client = engine

    suspend fun submitTransaction(request: TransactionSyncRequestDto): NetworkResult<TransactionSyncResponseDto> =
        try {
            val response = client.post("${config.baseUrl}/v1/transactions") {
                contentType(ContentType.Application.Json)
                header("X-Device-Id", config.deviceId)
                idempotencyKey(request.idempotencyKey)
                setBody(request)
            }
            NetworkResult.Success(response.body())
        } catch (e: ResponseException) {
            NetworkResult.ApiError(e.response.status.value, e.message)
        } catch (e: IOException) {
            NetworkResult.ConnectivityError(e)
        }

    /** Live stream of confirmed transactions affecting this device's accounts, replayed from [sinceServerSequence]. */
    fun observeTransactionEvents(sinceServerSequence: Long?): Flow<TransactionEventDto> = flow {
        val session = client.webSocketSession(config.webSocketUrl) {
            header("X-Device-Id", config.deviceId)
            sinceServerSequence?.let { header("X-Since-Sequence", it.toString()) }
        }
        try {
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    emit(Json.decodeFromString<TransactionEventDto>(frame.readText()))
                }
            }
        } finally {
            session.close()
        }
    }

    companion object {
        fun defaultClient(): HttpClient = HttpClient(CIO) { installLedgerPlugins() }

        /**
         * Shared plugin pipeline, factored out so tests can attach it to a
         * [io.ktor.client.engine.mock.MockEngine] instead of the real CIO engine
         * while exercising the exact same retry/serialization/idempotency behavior.
         */
        internal fun HttpClientConfig<*>.installLedgerPlugins() {
            expectSuccess = true

            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                    },
                )
            }

            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 5)
                retryOnExceptionIf(maxRetries = 5) { _, cause -> cause is IOException }
                exponentialDelay(base = 2.0, maxDelayMs = 30_000)
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 5_000
            }

            install(WebSockets)
            install(IdempotencyKeyPlugin)

            install(Logging) {
                level = LogLevel.INFO
            }
        }

        fun newIdempotencyKey(): String = UUID.randomUUID().toString()
    }
}
