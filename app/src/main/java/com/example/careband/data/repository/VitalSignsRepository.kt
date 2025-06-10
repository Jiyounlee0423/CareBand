package com.example.careband.data.repository

import android.util.Log
import com.example.careband.data.model.VitalSignsRecord
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

class VitalSignsRepository {

    private val db = FirebaseFirestore.getInstance()
    private val types = listOf("heart_rate", "spo2", "temperature")

    suspend fun getVitalSignsInRange(
        userId: String,
        startDate: String,
        endDate: String
    ): List<VitalSignsRecord> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val from = LocalDate.parse(startDate, formatter)
        val to = LocalDate.parse(endDate, formatter)

        val results = mutableListOf<VitalSignsRecord>()

        for (date in getDateRange(from, to)) {
            val dateKey = date.toString()
            val resultMap = mutableMapOf<String, VitalSignsRecord>()

            for (type in types) {
                try {
                    val snapshot = db.collection("vital_signs")
                        .document(dateKey)
                        .collection(type)
                        .get()
                        .await()

                    for (doc in snapshot.documents) {
                        val docUserId = doc.getString("user_id")
                        val timestampObj = doc.getTimestamp("timestamp")?.toDate()
                        val value = doc.get("value") as? Number ?: continue

                        if (docUserId != userId || timestampObj == null) continue

                        val timestampStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(timestampObj)

                        val existing = resultMap[timestampStr] ?: VitalSignsRecord(
                            timestamp = timestampStr,
                            userId = userId,
                            date = dateKey
                        )

                        val updated = when (type) {
                            "heart_rate" -> existing.copy(heartRate = value.toInt())
                            "spo2" -> existing.copy(spo2 = value.toInt())
                            "temperature" -> existing.copy(bodyTemp = value.toFloat())
                            else -> existing
                        }

                        resultMap[timestampStr] = updated
                    }
                } catch (e: Exception) {
                    Log.e("VitalSignsRepo", "❌ $type Firestore fetch error: ${e.message}")
                }
            }

            results.addAll(resultMap.values)
        }

        return results.sortedBy { it.timestamp }
    }

    fun listenToVitalSigns(
        userId: String,
        date: String,
        onUpdate: (List<VitalSignsRecord>) -> Unit
    ) {
        val recordsMap = mutableMapOf<String, VitalSignsRecord>()

        types.forEach { type ->
            db.collection("vital_signs")
                .document(date)
                .collection(type)
                .whereEqualTo("user_id", userId)
                .orderBy("timestamp")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("VitalSignsRepo", "❌ $type Snapshot error: $error")
                        return@addSnapshotListener
                    }

                    val doc = snapshot?.documents?.lastOrNull() ?: return@addSnapshotListener
                    val value = doc.getDouble("value") ?: return@addSnapshotListener
                    val timestamp = doc.getTimestamp("timestamp")?.toDate() ?: Date()
                    val key = timestamp.time.toString()
                    val formattedTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(timestamp)

                    Log.d("Snapshot", "📥 $type 변경 감지됨 - value = $value")

                    val existing = recordsMap[key] ?: VitalSignsRecord(
                        timestamp = formattedTime,
                        userId = userId,
                        date = date,
                    )

                    val updated = when (type) {
                        "heart_rate" -> existing.copy(heartRate = value.toInt(), spo2 = existing.spo2, bodyTemp = existing.bodyTemp)
                        "spo2" -> existing.copy(spo2 = value.toInt(), heartRate = existing.heartRate, bodyTemp = existing.bodyTemp)
                        "temperature" -> existing.copy(bodyTemp = value.toFloat(), heartRate = existing.heartRate, spo2 = existing.spo2)
                        else -> existing
                    }

                    recordsMap[key] = updated

                    onUpdate(recordsMap.values.sortedBy { it.timestamp })
                }
        }
    }

    private fun getDateRange(from: LocalDate, to: LocalDate): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var current = from
        while (!current.isAfter(to)) {
            dates.add(current)
            current = current.plusDays(1)
        }
        return dates
    }
}
