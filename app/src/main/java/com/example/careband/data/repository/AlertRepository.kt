package com.example.careband.data.repository

import android.util.Log
import com.example.careband.data.model.Alert
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.careband.MainActivity
import com.example.careband.R

class AlertRepository {

    private val db = FirebaseFirestore.getInstance()

    /**
     * 알림 저장 (하위 컬렉션 → 단일 컬렉션 방식)
     */
    fun saveAlert(
        context: Context,
        userId: String,
        alert: Alert,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
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
            .addOnSuccessListener {
                showAlertNotification(context, alert.alertId) // ✅ 알림 띄우기
                onSuccess() }
            .addOnFailureListener { e ->
                Log.e("Firestore", "알림 저장 실패: ${e.message}")
                onFailure(e)
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

    private fun showAlertNotification(context: Context, alertId: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("navigateToAlertScreen", true)
            putExtra("alertId", alertId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "careband_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_email) // 종 모양 기본 내장 아이콘
            .setContentTitle("⚠️ 이상 상태 감지")
            .setContentText("새로운 이상 징후가 감지되었습니다.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(android.graphics.Color.RED) // 강조색 빨강 (상단바 강조용)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1001, notification)
    }
}
