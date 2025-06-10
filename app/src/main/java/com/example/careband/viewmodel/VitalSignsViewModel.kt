package com.example.careband.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.careband.data.model.HealthRecord
import com.example.careband.data.model.VitalSignsRecord
import com.example.careband.data.repository.HealthRepository
import com.example.careband.data.repository.VitalSignsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import kotlinx.coroutines.launch

class VitalSignsViewModel(private val userId: String) : ViewModel() {

    private val repository = VitalSignsRepository()
    private val healthRepository = HealthRepository()

    private val _records = MutableStateFlow<List<VitalSignsRecord>>(emptyList())
    val records: StateFlow<List<VitalSignsRecord>> = _records

    private val _healthRecords = MutableStateFlow<List<HealthRecord>>(emptyList())
    val healthRecords: StateFlow<List<HealthRecord>> = _healthRecords


    fun loadVitalSignsInRange(startDate: String, endDate: String) {
        viewModelScope.launch {
            val data = repository.getVitalSignsInRange(userId, startDate, endDate)
            _records.value = data
        }
    }

    fun loadVitalRecords(fromDate: LocalDate) {
        val startDate = fromDate.toString()
        val endDate = LocalDate.now().toString()
        viewModelScope.launch {
            println("📅 기간: $startDate ~ $endDate")
            val data = repository.getVitalSignsInRange(userId, startDate, endDate)
            println("✅ 수신된 레코드 수: ${data.size}")
            Log.d("BLE",userId)
            _records.value = data
        }

    }
//    fun loadVitalRecords(fromDate: LocalDate = LocalDate.of(2025, 6, 9)) {
//        val startDate = fromDate.toString()
//        val endDate = fromDate.toString() // 하루만 조회
//        viewModelScope.launch {
//            println("📅 강제 요청: $startDate ~ $endDate")
//            val data = repository.getVitalSignsInRange(userId, startDate, endDate)
//            println("✅ 수신된 레코드 수: ${data.size}")
//            _records.value = data
//        }
//    }

    fun loadHealthRecords(fromDate: LocalDate) {
        val startDate = fromDate.toString()
        val endDate = LocalDate.now().toString()
        viewModelScope.launch {
            val data = healthRepository.getHealthRecordsInRange(userId, startDate, endDate)
            _healthRecords.value = data
        }
    }
    fun startListeningToVitalSigns(date: String) {
        repository.listenToVitalSigns(userId, date) { data ->
            _records.value = data
        }
    }
    fun observeVitalSignsSnapshot(date: LocalDate) {
        val dateStr = date.toString()
        repository.listenToVitalSigns(userId, dateStr) { latestList ->
            // 누적 추가 방식으로 변경 (또는 timestamp 기준 중복 제거)
            Log.d("ViewModel", "🟡 Snapshot으로 수신된 최신 데이터 수: ${latestList.size}")
            _records.value = latestList.sortedBy { it.timestamp }
        }
    }


}
