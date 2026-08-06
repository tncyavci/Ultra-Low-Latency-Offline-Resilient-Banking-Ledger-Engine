package com.ledgerengine.network

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.util.AttributeKey

private val IdempotencyKeyAttribute = AttributeKey<String>("LedgerEngine.IdempotencyKey")

/**
 * Ktor client plugin that promotes a per-request idempotency key (set via
 * [HttpRequestBuilder.idempotencyKey]) into an `Idempotency-Key` header. Centralizing
 * this as a plugin — rather than setting the header ad hoc at each call site —
 * guarantees every mutating ledger request carries one, which is what lets a
 * retried request after a dropped response be safely replayed server-side
 * without double-processing.
 */
val IdempotencyKeyPlugin = createClientPlugin("IdempotencyKeyPlugin") {
    onRequest { request, _ ->
        request.attributes.getOrNull(IdempotencyKeyAttribute)?.let { key ->
            request.headers.append(HEADER_NAME, key)
        }
    }
}

fun HttpRequestBuilder.idempotencyKey(key: String) {
    attributes.put(IdempotencyKeyAttribute, key)
}

const val HEADER_NAME = "Idempotency-Key"
