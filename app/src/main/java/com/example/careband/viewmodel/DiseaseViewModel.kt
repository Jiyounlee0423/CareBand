package com.example.careband.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.careband.data.model.DiseaseRecord
import com.example.careband.data.repository.DiseaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DiseaseViewModel(private val userId: String) : ViewModel() {

    private val repository = DiseaseRepository()

    private val _diseaseRecords = MutableStateFlow<List<DiseaseRecord>>(emptyList())
    val diseaseRecords: StateFlow<List<DiseaseRecord>> = _diseaseRecords

    init {
        println("🟢 DiseaseViewModel 초기화됨 - userId: $userId")
        loadDiseaseRecords()
    }

    fun loadDiseaseRecords() {
        viewModelScope.launch {
            _diseaseRecords.value = repository.getDiseaseRecords(userId)
        }
    }

    fun addDiseaseRecord(record: DiseaseRecord) {
        viewModelScope.launch {
            repository.addDiseaseRecord(userId, record)
            loadDiseaseRecords()
        }
    }

    fun updateDiseaseRecord(record: DiseaseRecord) {
        viewModelScope.launch {
            repository.updateDiseaseRecord(userId, record)
            loadDiseaseRecords()
        }
    }
}
