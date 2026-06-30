package com.crowdprediction.data.repository

import com.crowdprediction.data.models.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @GET("api/dashboard")
    suspend fun getDashboard(): Response<ApiResponse<DashboardSummary>>

    @GET("api/zones")
    suspend fun getZones(): Response<ApiResponse<List<Zone>>>

    @GET("api/zones/{id}")
    suspend fun getZone(@Path("id") id: Int): Response<ApiResponse<Zone>>

    @POST("api/zones/{id}")
    suspend fun updateZone(
        @Path("id") id: Int,
        @Body request: ZoneUpdateRequest
    ): Response<ApiResponse<Zone>>

    @GET("api/alerts")
    suspend fun getAlerts(): Response<ApiResponse<List<Alert>>>

    @POST("api/alerts/{id}/resolve")
    suspend fun resolveAlert(@Path("id") id: Int): Response<ApiResponse<Alert>>

    @POST("api/alerts/resolve-all")
    suspend fun resolveAllAlerts(): Response<ApiResponse<Unit>>

    @GET("api/predict/{zone_id}")
    suspend fun getPrediction(@Path("zone_id") zoneId: Int): Response<ApiResponse<PredictionResult>>

    @GET("api/predict/all")
    suspend fun getAllPredictions(): Response<ApiResponse<List<PredictionResult>>>

    @POST("api/simulate")
    suspend fun runSimulation(@Body request: SimulationRequest): Response<ApiResponse<SimulationResult>>

    @GET("api/history/{zone_id}")
    suspend fun getHistory(
        @Path("zone_id") zoneId: Int,
        @Query("minutes") minutes: Int = 60
    ): Response<ApiResponse<List<DensityPoint>>>
}
