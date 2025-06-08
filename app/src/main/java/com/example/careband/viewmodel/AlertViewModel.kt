package com.example.careband.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.careband.data.model.Alert
import com.example.careband.data.repository.AlertRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*

class AlertViewModel(
    private val userId: String,
    private val repository: AlertRepository = AlertRepository()
) : ViewModel() {

    // 초기화 블록: 뷰모델 생성 시 자동으로 리스너 시작
    init {
        startAlertListener()
    }

    private val _alertList = MutableStateFlow<List<Alert>>(emptyList())
    val alertList: StateFlow<List<Alert>> = _alertList

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /**
     * 실시간 알림 수신 리스너 연결
     */
    fun startAlertListener() {
        repository.listenToAlertsForUser(userId) { alerts ->
            _alertList.value = alerts.sortedByDescending { it.timestamp }
            Log.d("AlertViewModel", "⚠️ alerts 수신됨: ${alerts.size}개")
        }
    }

    /**
     * 수동 갱신용 - 비실시간 조회
     */
    fun loadUserAlerts() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val alerts = repository.getAlertsForUser(userId)
                _alertList.value = alerts.sortedByDescending { it.timestamp }
                _error.value = null
            } catch (e: Exception) {
                _error.value = "알림을 불러오지 못했습니다: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * 낙상 알림 전송
     */
    fun submitFallAlert() {
        val alert = Alert(
            alertId = UUID.randomUUID().toString(),
            userId = userId,
            alertType = "fall",
            isFalseAlarm = false,
            notifiedTo = "보호자",
            responseReceived = false,
            timestamp = Timestamp.now()
        )

        repository.saveAlert(
            userId = userId,
            alert = alert,
            onSuccess = {
                Log.d("AlertViewModel", "낙상 알림 저장 성공")
            },
            onFailure = { error ->
                Log.e("AlertViewModel", "낙상 알림 저장 실패: $error")
            }
        )
    }

    /**
     * 응답 여부 처리
     */
    fun markAlertAsResponded(alertId: String) {
        repository.markAlertResponded(alertId) { success ->
            if (success) {
                loadUserAlerts() // or 생략 가능
            }
        }
    }
}
