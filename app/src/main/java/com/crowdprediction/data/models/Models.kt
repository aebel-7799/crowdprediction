package com.crowdprediction.data.models

import com.google.gson.annotations.SerializedName

// ── Zone ────────────────────────────────────────────────────────────────────
data class Zone(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("current") val current: Int,
    @SerializedName("capacity") val capacity: Int,
    @SerializedName("status") val status: String,   // "ok" | "warning" | "critical"
    @SerializedName("lat") val lat: Double = 0.0,
    @SerializedName("lng") val lng: Double = 0.0
) {
    val densityPercent: Int get() = ((current.toDouble() / capacity) * 100).toInt()
}

// ── Alert ────────────────────────────────────────────────────────────────────
data class Alert(
    @SerializedName("id") val id: Int,
    @SerializedName("zone") val zone: String,
    @SerializedName("level") val level: String,     // "critical" | "warning" | "info"
    @SerializedName("message") val message: String,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("resolved") var resolved: Boolean = false
)

// ── Zone update request ──────────────────────────────────────────────────────
data class ZoneUpdateRequest(
    @SerializedName("current") val current: Int,
    @SerializedName("capacity") val capacity: Int
)

// ── Density data point ───────────────────────────────────────────────────────
data class DensityPoint(
    @SerializedName("time") val time: String,
    @SerializedName("value") val value: Float
)

// ── Prediction result ────────────────────────────────────────────────────────
data class PredictionResult(
    @SerializedName("zone_id") val zoneId: Int,
    @SerializedName("zone_name") val zoneName: String,
    @SerializedName("predicted_density") val predictedDensity: List<Float>,
    @SerializedName("time_labels") val timeLabels: List<String>,
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("confidence") val confidence: Float,
    @SerializedName("peak_time") val peakTime: String,
    @SerializedName("peak_value") val peakValue: Float
)

// ── Dashboard summary ────────────────────────────────────────────────────────
data class DashboardSummary(
    @SerializedName("total_crowd") val totalCrowd: Int,
    @SerializedName("avg_density") val avgDensity: Int,
    @SerializedName("active_alerts") val activeAlerts: Int,
    @SerializedName("peak_in_minutes") val peakInMinutes: Int,
    @SerializedName("zones") val zones: List<Zone>,
    @SerializedName("recent_history") val recentHistory: List<DensityPoint>
)

// ── Simulation request ───────────────────────────────────────────────────────
data class SimulationRequest(
    @SerializedName("event_type") val eventType: String,
    @SerializedName("attendees") val attendees: Int,
    @SerializedName("entry_rate") val entryRate: Int
)

// ── Simulation result ─────────────────────────────────────────────────────────
data class SimulationResult(
    @SerializedName("time_to_full") val timeToFull: Int,
    @SerializedName("peak_crowd") val peakCrowd: Int,
    @SerializedName("risk_level") val riskLevel: String,
    @SerializedName("recommendations") val recommendations: List<String>
)

// ── API wrapper ───────────────────────────────────────────────────────────────
data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: T?,
    @SerializedName("message") val message: String = ""
)
