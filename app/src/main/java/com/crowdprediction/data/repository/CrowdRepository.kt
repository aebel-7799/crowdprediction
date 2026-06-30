package com.crowdprediction.data.repository

import com.crowdprediction.data.models.*
import kotlinx.coroutines.delay
import kotlin.math.*

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

class CrowdRepository(private val apiService: ApiService) {

    private val mockZones = listOf(
        Zone(1, "Gate A",      980,  1200, "warning"),
        Zone(2, "Main Stage", 2840,  3000, "critical"),
        Zone(3, "Food Court",  540,   800, "ok"),
        Zone(4, "VIP Zone",    180,   400, "ok"),
        Zone(5, "Parking",    1560,  2000, "warning"),
        Zone(6, "Exit A",      220,   600, "ok")
    )

    private val mockAlerts = mutableListOf(
        Alert(1, "Main Stage", "critical", "Density exceeded 94%. Immediate action required.", "2 min ago"),
        Alert(2, "Gate A",     "warning",  "Entry rate spiking. Expected overflow in 12 min.",  "5 min ago"),
        Alert(3, "Parking",    "warning",  "Occupancy at 78%. Monitor entry points.",            "9 min ago")
    )

    private fun makeDensityHistory(): List<DensityPoint> =
        (0..11).map { i ->
            DensityPoint("${-(60 - i * 5)}m", (65 + 20 * sin(i * 0.5)).toFloat().coerceIn(10f, 100f))
        }

    private fun makeForecast(zoneId: Int): PredictionResult {
        val zone = mockZones.firstOrNull { it.id == zoneId } ?: mockZones[0]
        val base = zone.densityPercent.toFloat()
        val labels = listOf("Now") + (1..12).map { "+${it * 10}m" }
        val values = labels.mapIndexed { i, _ -> (base + 15 * sin(i * 0.4)).toFloat().coerceIn(0f, 100f) }
        val peak = values.max()
        val peakIdx = values.indexOfFirst { it == peak }
        return PredictionResult(
            zoneId, zone.name, values, labels,
            if (peak > 85) "critical" else if (peak > 70) "warning" else "ok",
            0.89f, labels[peakIdx], peak
        )
    }

    suspend fun getDashboard(): Result<DashboardSummary> = try {
        val resp = apiService.getDashboard()
        if (resp.isSuccessful && resp.body()?.success == true)
            Result.Success(resp.body()!!.data!!)
        else mockDashboard()
    } catch (e: Exception) { mockDashboard() }

    private fun mockDashboard() = Result.Success(
        DashboardSummary(
            mockZones.sumOf { it.current },
            mockZones.map { it.densityPercent }.average().toInt(),
            mockAlerts.count { !it.resolved },
            12, mockZones, makeDensityHistory()
        )
    )

    suspend fun getZones(): Result<List<Zone>> = try {
        val resp = apiService.getZones()
        if (resp.isSuccessful && resp.body()?.success == true)
            Result.Success(resp.body()!!.data!!)
        else Result.Success(mockZones)
    } catch (e: Exception) { Result.Success(mockZones) }

    suspend fun updateZone(zoneId: Int, current: Int, capacity: Int): Result<Zone> = try {
        val resp = apiService.updateZone(zoneId, ZoneUpdateRequest(current, capacity))
        if (resp.isSuccessful && resp.body()?.success == true)
            Result.Success(resp.body()!!.data!!)
        else Result.Error("Server rejected the update")
    } catch (e: Exception) { Result.Error(e.message ?: "Could not reach server") }

    suspend fun getAlerts(): Result<List<Alert>> = try {
        val resp = apiService.getAlerts()
        if (resp.isSuccessful && resp.body()?.success == true)
            Result.Success(resp.body()!!.data!!)
        else Result.Success(mockAlerts.toList())
    } catch (e: Exception) { Result.Success(mockAlerts.toList()) }

    suspend fun resolveAlert(id: Int): Result<Unit> {
        mockAlerts.find { it.id == id }?.resolved = true
        return try { apiService.resolveAlert(id); Result.Success(Unit) }
        catch (e: Exception) { Result.Success(Unit) }
    }

    suspend fun resolveAllAlerts(): Result<Unit> {
        mockAlerts.forEach { it.resolved = true }
        return try { apiService.resolveAllAlerts(); Result.Success(Unit) }
        catch (e: Exception) { Result.Success(Unit) }
    }

    suspend fun getPrediction(zoneId: Int): Result<PredictionResult> = try {
        val resp = apiService.getPrediction(zoneId)
        if (resp.isSuccessful && resp.body()?.success == true)
            Result.Success(resp.body()!!.data!!)
        else Result.Success(makeForecast(zoneId))
    } catch (e: Exception) { Result.Success(makeForecast(zoneId)) }

    suspend fun getAllPredictions(): Result<List<PredictionResult>> = try {
        val resp = apiService.getAllPredictions()
        if (resp.isSuccessful && resp.body()?.success == true)
            Result.Success(resp.body()!!.data!!)
        else Result.Success(mockZones.map { makeForecast(it.id) })
    } catch (e: Exception) { Result.Success(mockZones.map { makeForecast(it.id) }) }

    suspend fun runSimulation(request: SimulationRequest): Result<SimulationResult> = try {
        val resp = apiService.runSimulation(request)
        if (resp.isSuccessful && resp.body()?.success == true)
            Result.Success(resp.body()!!.data!!)
        else mockSimulation(request)
    } catch (e: Exception) { mockSimulation(request) }

    private fun mockSimulation(req: SimulationRequest): Result<SimulationResult> {
        val timeToFull = req.attendees / req.entryRate.coerceAtLeast(1)
        val risk = if (timeToFull < 30) "Critical" else if (timeToFull < 60) "Warning" else "Normal"
        return Result.Success(SimulationResult(timeToFull, req.attendees, risk,
            listOf("Open secondary entry gates.", "Deploy additional personnel.",
                   "Activate crowd redirection signage.", "Broadcast PA announcement.")))
    }
}
