package com.rethinkingstudio.clawlink.core.state.chat

import io.ktor.client.plugins.HttpRequestTimeoutException
import java.net.SocketTimeoutException
import kotlinx.coroutines.delay

internal suspend fun <T> retryOnceOnTransientFailure(
    operationName: String,
    block: suspend () -> T
): T {
    return try {
        block()
    } catch (e: Exception) {
        if (!isTransientLoadFailure(e)) {
            throw e
        }
        logWarning("Transient timeout while loading $operationName, retrying once", e)
        delay(350)
        block()
    }
}

internal fun logWarning(message: String, throwable: Throwable? = null) {
    runCatching {
        if (throwable == null) {
            android.util.Log.w("ChatStore", message)
        } else {
            android.util.Log.w("ChatStore", message, throwable)
        }
    }
}

internal fun isTransientLoadFailure(throwable: Throwable?): Boolean {
    var current: Throwable? = throwable
    while (current != null) {
        when (current) {
            is HttpRequestTimeoutException,
            is SocketTimeoutException -> return true
        }
        val message = current.message.orEmpty()
        if (isTransientGatewayLoadFailureMessage(message)) {
            return true
        }
        current = current.cause
    }
    return false
}
