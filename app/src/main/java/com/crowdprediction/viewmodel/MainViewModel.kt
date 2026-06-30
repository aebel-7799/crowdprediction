package com.crowdprediction.viewmodel

import androidx.lifecycle.*
import com.crowdprediction.data.models.*
import com.crowdprediction.data.repository.CrowdRepository
import com.crowdprediction.data.repository.Result
import com.crowdprediction.utils.NetworkModule
import kotlinx.coroutines.*

class MainViewModel(private val repo: CrowdRepository) : ViewModel() {

    private val _dashboard = MutableLiveData<Result<DashboardSummary>>()
    val dashboard: LiveData<Result<DashboardSummary>> = _dashboard

    private val _zones = MutableLiveData<Result<List<Zone>>>()
    val zones: LiveData<Result<List<Zone>>> = _zones

    private val _alerts = MutableLiveData<Result<List<Alert>>>()
    val alerts: LiveData<Result<List<Alert>>> = _alerts

    private val _prediction = MutableLiveData<Result<PredictionResult>>()
    val prediction: LiveData<Result<PredictionResult>> = _prediction

    private val _allPredictions = MutableLiveData<Result<List<PredictionResult>>>()
    val allPredictions: LiveData<Result<List<PredictionResult>>> = _allPredictions

    private val _simulation = MutableLiveData<Result<SimulationResult>>()
    val simulation: LiveData<Result<SimulationResult>> = _simulation

    private val _updateResult = MutableLiveData<Result<Zone>?>()
    val updateResult: LiveData<Result<Zone>?> = _updateResult

    private var refreshJob: Job? = null

    init {
        loadAll()
        startAutoRefresh()
    }

    fun loadAll() { loadDashboard(); loadZones(); loadAlerts(); loadAllPredictions() }

    fun loadDashboard() = viewModelScope.launch {
        _dashboard.value = Result.Loading
        _dashboard.value = repo.getDashboard()
    }

    fun loadZones() = viewModelScope.launch {
        _zones.value = Result.Loading
        _zones.value = repo.getZones()
    }

    fun loadAlerts() = viewModelScope.launch {
        _alerts.value = Result.Loading
        _alerts.value = repo.getAlerts()
    }

    fun loadPrediction(zoneId: Int) = viewModelScope.launch {
        _prediction.value = Result.Loading
        _prediction.value = repo.getPrediction(zoneId)
    }

    fun loadAllPredictions() = viewModelScope.launch {
        _allPredictions.value = Result.Loading
        _allPredictions.value = repo.getAllPredictions()
    }

    fun resolveAlert(id: Int) = viewModelScope.launch {
        repo.resolveAlert(id); loadAlerts(); loadDashboard()
    }

    fun resolveAllAlerts() = viewModelScope.launch {
        repo.resolveAllAlerts(); loadAlerts(); loadDashboard()
    }

    fun updateZone(zoneId: Int, current: Int, capacity: Int) = viewModelScope.launch {
        val result = repo.updateZone(zoneId, current, capacity)
        _updateResult.value = result
        if (result is Result.Success) { loadZones(); loadDashboard() }
    }

    fun clearUpdateResult() { _updateResult.value = null }

    fun runSimulation(eventType: String, attendees: Int, entryRate: Int) = viewModelScope.launch {
        _simulation.value = Result.Loading
        _simulation.value = repo.runSimulation(SimulationRequest(eventType, attendees, entryRate))
    }

    private fun startAutoRefresh() {
        refreshJob = viewModelScope.launch {
            while (true) { delay(15_000); loadDashboard(); loadZones(); loadAlerts() }
        }
    }

    override fun onCleared() { super.onCleared(); refreshJob?.cancel() }

    // Factory — no Hilt needed
    class Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repo = CrowdRepository(NetworkModule.apiService)
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repo) as T
        }
    }
}
