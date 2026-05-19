package com.thai2chinese.util

import kotlinx.coroutines.delay

suspend fun <T> retry(times: Int, block: suspend () -> T): T? {
    repeat(times) {
        try { return block() } catch (_: Exception) { delay(1000L * (it + 1)) }
    }
    return try { block() } catch (_: Exception) { null }
}
