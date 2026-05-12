package com.solar.dashka.data.api.dto

import kotlinx.serialization.Serializable

/**
 * Wire response from GET /api/health.
 *
 * The backend returns `{status: "ok", version, timestamp}` always — there is
 * no deep health check yet. Useful for connectivity verification on app start.
 */
@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val timestamp: String,
)
