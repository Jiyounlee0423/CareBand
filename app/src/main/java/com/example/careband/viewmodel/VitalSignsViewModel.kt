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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.asStateFlow


class VitalSignsViewModel(private val userId: String) : ViewModel() {

    private val repository = VitalSignsRepository()
    private val healthRepository = HealthRepository()

    private val _records = MutableStateFlow<List<VitalSignsRecord>>(emptyList())
    val records: StateFlow<List<VitalSignsRecord>> = _records

    private val _healthRecords = MutableStateFlow<List<HealthRecord>>(emptyList())
    val healthRecords: StateFlow<List<HealthRecord>> = _healthRecords

    private var isListening = false

    private val _latestRecord = MutableStateFlow<VitalSignsRecord?>(null)
    val latestRecord: StateFlow<VitalSignsRecord?> = _latestRecord.asStateFlow()





    fun loadVitalSignsInRange(startDate: String, endDate: String) {
        viewModelScope.launch {
            val data = repository.getVitalSignsInRange(userId, startDate, endDate)
            _records.value = data
        }
    }

    fun updateLatestVitalSigns(bpm: Float?, spo2: Float?, temp: Float?) {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
        val record = VitalSignsRecord(
            userId = userId,
            timestamp = now,
            date = now.substring(0, 10),
            heartRate = bpm?.toInt() ?: 0,
            spo2 = spo2?.toInt() ?: 0,
            bodyTemp = temp ?: 0f
        )
        _latestRecord.value = record
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
        if (isListening) return // 중복 방지
        isListening = true

        val dateStr = date.toString()
        repository.listenToVitalSigns(userId, dateStr) { latestList ->
            _records.value = latestList
            Log.d("ViewModel", "🟡 Snapshot으로 수신된 최신 데이터 수: ${latestList.size}")
        }
    }

    fun updateLiveVitalSign(type: String, value: Float) {
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
        val today = now.substring(0, 10)

        val current = _latestRecord.value ?: VitalSignsRecord(
            timestamp = now,
            userId = userId,
            date = today,
            heartRate = 0,
            spo2 = 0,
            bodyTemp = 0f
        )
        Log.d("ViewModel", "✅ USERID in updateLiveVitalSign -> $userId")

        val updated = when (type) {
            "BPM" -> current.copy(heartRate = value.toInt(), timestamp = now)
            "SpO2" -> current.copy(spo2 = value.toInt(), timestamp = now)
            "TEMP" -> current.copy(bodyTemp = value, timestamp = now)
            else -> current
        }

        Log.d("ViewModel", "✅ $type 수신됨 -> 최신 상태: $updated")
        Log.d("ViewModel", "✅ updateLiveVitalSign 호출됨 -> $updated")
        Log.d("ViewModel", "✅ USERID -> $userId")

        _latestRecord.value = VitalSignsRecord(
            date = updated.date,
            id = updated.id,
            userId = updated.userId,
            heartRate = updated.heartRate,
            spo2 = updated.spo2,
            bodyTemp = updated.bodyTemp,
            fallDetected = updated.fallDetected,
            timestamp = updated.timestamp
        )
    }

}
