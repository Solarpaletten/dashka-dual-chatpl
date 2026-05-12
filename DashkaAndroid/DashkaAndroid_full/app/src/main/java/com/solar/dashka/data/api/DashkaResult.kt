package com.solar.dashka.data.api

/**
 * Typed result wrapper used at the repository boundary. Converts Retrofit's
 * exception-based error model into a domain-friendly sealed class.
 *
 * The presentation layer pattern-matches on this type and renders a localized
 * message — it never sees raw HttpException.
 */
sealed class DashkaResult<out T> {

    data class Success<T>(val data: T) : DashkaResult<T>()

    sealed class Error : DashkaResult<Nothing>() {
        /** 401 — invalid or missing X-Dashka-Token. */
        data object Unauthorized : Error()

        /** Network unreachable (no connectivity, DNS failure, etc.). */
        data object NetworkError : Error()

        /** Request did not finish in time (OkHttp timeout). */
        data object Timeout : Error()

        /** Non-2xx response. `code` is the HTTP status, `message` is the parsed envelope message. */
        data class Server(val code: Int, val message: String) : Error()

        /** Anything else — wraps the raw throwable. */
        data class Unknown(val throwable: Throwable) : Error()
    }
}
