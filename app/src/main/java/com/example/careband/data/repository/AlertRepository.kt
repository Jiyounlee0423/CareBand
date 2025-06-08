package com.example.careband.data.repository

import android.util.Log
import com.example.careband.data.model.Alert
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AlertRepository {

    private val db = FirebaseFirestore.getInstance()

    /**
     * 알림 저장 (하위 컬렉션 → 단일 컬렉션 방식)
     */
    fun saveAlert(
        userId: String,
        alert: Alert,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val timestampKey = alert.timestamp.toDate().time.toString()

        val finalAlert = Alert(
            alertId = alert.alertId,
            userId = userId,
            alertType = alert.alertType,
            isFalseAlarm = alert.isFalseAlarm,
            notifiedTo = alert.notifiedTo,
            responseReceived = alert.responseReceived,
            timestamp = alert.timestamp,
            timestampKey = timestampKey
        )


        FirebaseFirestore.getInstance().collection("alerts")
            .add(finalAlert)                // ✅ 직접 Alert 객체로 저장 (Map 아님)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e ->
                Log.e("Firestore", "알림 저장 실패: ${e.message}")
                onFailure(e.message ?: "알림 저장 실패")
            }
    }



    /**
     * 특정 사용자에 대한 모든 알림 불러오기 (하위 컬렉션 제거)
     */
    suspend fun getAlertsForUser(userId: String): List<Alert> {
        return try {
            db.collection("alerts")
                .whereEqualTo("userId", userId)
                .get()
                .await()
                .documents.mapNotNull { it.toObject(Alert::class.java) }
        } catch (e: Exception) {
            Log.e("Firestore", "알림 불러오기 실패: ${e.message}")
            emptyList()
        }
    }

    /**
     * 응답 여부 업데이트 (단일 컬렉션 기준)
     */
    fun markAlertResponded(alertId: String, onComplete: (Boolean) -> Unit) {
        db.collection("alerts")
            .whereEqualTo("alertId", alertId)
            .get()
            .addOnSuccessListener { snapshot ->
                val doc = snapshot.documents.firstOrNull()
                if (doc != null) {
                    doc.reference.update("responseReceived", true)
                        .addOnSuccessListener { onComplete(true) }
                        .addOnFailureListener {
                            Log.e("Firestore", "응답 상태 업데이트 실패: ${it.message}")
                            onComplete(false)
                        }
                } else {
                    Log.e("Firestore", "❌ 해당 alertId 문서 없음")
                    onComplete(false)
                }
            }
            .addOnFailureListener {
                Log.e("Firestore", "❌ alertId 검색 실패: ${it.message}")
                onComplete(false)
            }
    }

    /**
     * 실시간 수신 (하위 컬렉션 제거)
     */
    fun listenToAlertsForUser(
        userId: String,
        onUpdate: (List<Alert>) -> Unit
    ) {
        db.collection("alerts")
            .whereEqualTo("userId", userId)
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
