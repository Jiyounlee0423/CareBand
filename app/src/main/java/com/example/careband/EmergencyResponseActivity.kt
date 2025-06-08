package com.example.careband

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.Timestamp
import com.example.careband.data.model.Alert

class EmergencyResponseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val alertType = intent.getStringExtra("alert_type") ?: "unknown"
        val alertMessage = intent.getStringExtra("alert_message") ?: "긴급 알림 수신"
        val userId = intent.getStringExtra("user_id") ?: "unknown"

        // ✅ 진입 로그 + 토스트
        Log.d("EmergencyActivity", "🔥 진입 확인됨")
        Toast.makeText(this, "팝업 액티비티 진입됨", Toast.LENGTH_SHORT).show()

        setContent {
            MaterialTheme {
                EmergencyAlertDialog(
                    alertType = alertType,
                    alertMessage = alertMessage,
                    onResponse = {
                        updateOrSaveAlert(userId, alertType)

                        if (!isTaskRoot) {
                            // ✅ 앱이 이미 실행 중이면 기존 스택으로 복귀
                            finish()
                        } else {
                            // ✅ 앱이 꺼진 상태에서 알림으로 진입한 경우
                            val intent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            finish()
                        }
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun updateOrSaveAlert(userId: String, alertType: String) {
        val db = Firebase.firestore

        db.collection("alerts")
            .whereEqualTo("alertType", alertType)
            .whereEqualTo("notifiedTo", "사용자")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val existing = snapshot.documents.firstOrNull()
                if (existing != null) {
                    db.collection("alerts").document(existing.id)
                        .update("responseReceived", true)
                        .addOnSuccessListener {
                            Log.d("Firestore", "✅ 기존 알림 응답 처리 완료")
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firestore", "❌ 알림 업데이트 실패: \${e.message}")
                        }
                } else {
                    val newAlert = Alert(
                        alertType = alertType,
                        notifiedTo = "사용자",
                        responseReceived = true,
                        timestamp = Timestamp.now()
                    )
                    db.collection("alerts")
                        .add(newAlert)
                        .addOnSuccessListener {
                            Log.d("Firestore", "✅ 새 응답 알림 저장 완료")
                        }
                        .addOnFailureListener { e ->
                            Log.e("Firestore", "❌ 새 알림 저장 실패: \${e.message}")
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "❌ 알림 조회 실패: \${e.message}")
            }
    }
}

@Composable
fun EmergencyAlertDialog(
    alertType: String,
    alertMessage: String,
    onResponse: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("긴급 알림: \$alertType") },
        text = { Text(alertMessage) },
        confirmButton = {
            TextButton(onClick = { onResponse() }) {
                Text("응답")
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("닫기")
            }
        }
    )
}
