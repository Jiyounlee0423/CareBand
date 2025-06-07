package com.example.careband.data.repository

import android.util.Log
import com.example.careband.data.model.Alert
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AlertRepository {

    private val db = FirebaseFirestore.getInstance()

    /**
     * 알림 저장
     */
    fun saveAlert(
        userId: String,
        alert: Alert,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val timestampKey = alert.timestamp.toDate().time.toString()

        val finalAlert = alert.copy(
            alertId = alert.alertId,
            userId = userId,
            timestampKey = timestampKey
        )

        FirebaseFirestore.getInstance()
            .collection("alerts")
            .document(userId)
            .collection("logs")
            .document(timestampKey)  // ← 문서 ID로 사용
            .set(finalAlert)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e ->
                Log.e("Firestore", "알림 저장 실패: ${e.message}")
                onFailure(e.message ?: "알림 저장 실패")
            }
    }

    /**
     * 특정 사용자에 대한 모든 알림 불러오기
     */
    suspend fun getAlertsForUser(userId: String): List<Alert> {
        return try {
            db.collection("alerts")
                .document(userId)
                .collection("logs")
                .get()
                .await()
                .documents.mapNotNull { it.toObject(Alert::class.java) }
        } catch (e: Exception) {
            Log.e("Firestore", "알림 불러오기 실패: ${e.message}")
            emptyList()
        }
    }

    /**
     * 응답 여부 업데이트 (response_received = true)
     */
    fun markAlertResponded(userId: String, timestamp: String, onComplete: (Boolean) -> Unit) {
        db.collection("alerts")
            .document(userId)
            .collection("logs")
            .document(timestamp)
            .update("responseReceived", true)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener {
                Log.e("Firestore", "응답 상태 업데이트 실패: ${it.message}")
                onComplete(false)
            }
    }
    
    // 실시간 수신 함수 추가
    fun listenToAlertsForUser(
        userId: String,
        onUpdate: (List<Alert>) -> Unit
    ) {
        FirebaseFirestore.getInstance()
            .collection("alerts")
            .document(userId)
            .collection("logs")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("Firestore", "알림 실시간 수신 실패: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val alerts = snapshots.documents.mapNotNull { it.toObject(Alert::class.java) }
                    onUpdate(alerts)
                }
            }
    }

}
