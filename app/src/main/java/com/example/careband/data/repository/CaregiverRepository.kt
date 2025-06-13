package com.example.careband.data.repository

import android.util.Log
import com.example.careband.data.model.Caregiver
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.tasks.await

class CaregiverRepository {

    private val db = FirebaseFirestore.getInstance()
    private val caregiversCollection = db.collection("caregivers")

    /** 보호자 정보 저장 **/
    fun saveCaregiver(
        caregiver: Caregiver,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        caregiversCollection
            .document(caregiver.id)
            .set(caregiver)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Caregiver 저장 실패: ${e.message}")
                onFailure(e.message ?: "저장 실패")
            }
    }

    /** 보호자 ID로 해당 케어 사용자 ID 가져오기 **/
    suspend fun getManagedUserIds(caregiverId: String): List<String> {
        val snapshot = Firebase.firestore.collection("users")
            .document(caregiverId)
            .get()
            .await()

        return snapshot.get("managedUserIds") as? List<String> ?: emptyList()
    }

    suspend fun getActiveUserId(caregiverId: String): String? {
        val snapshot = Firebase.firestore.collection("users")
            .document(caregiverId)
            .get()
            .await()

        return snapshot.getString("activeUserId")
    }

    /** 보호자 전체 정보 가져오기 (필요 시) **/
    suspend fun getCaregiver(caregiverId: String): Caregiver? {
        return try {
            val snapshot = caregiversCollection.document(caregiverId).get().await()
            snapshot.toObject(Caregiver::class.java)
        } catch (e: Exception) {
            Log.e("Firestore", "Caregiver 불러오기 실패: ${e.message}")
            null
        }
    }

    fun addManagedUserId(caregiverId: String, userIdToAdd: String, onComplete: (Boolean) -> Unit) {
        val docRef = FirebaseFirestore.getInstance().collection("users").document(caregiverId)

        docRef.update("managedUserIds", FieldValue.arrayUnion(userIdToAdd))
            .addOnSuccessListener {
                Log.d("Firestore", "✅ 사용자 ID 추가 성공: $userIdToAdd")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "❌ 사용자 ID 추가 실패: ${e.message}")
                onComplete(false)
            }
    }

    fun setActiveUserId(caregiverId: String, userId: String, onComplete: (Boolean) -> Unit) {
        val docRef = Firebase.firestore.collection("users").document(caregiverId)

        docRef.update("activeUserId", userId)
            .addOnSuccessListener {
                Log.d("Firestore", "✅ 연동된 사용자 ID 저장 성공: $userId")
                onComplete(true)
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "❌ 연동된 사용자 ID 저장 실패: ${e.message}")
                onComplete(false)
            }
    }

    suspend fun doesUserExist(userId: String): Boolean {
        return try {
            val snapshot = Firebase.firestore.collection("users")
                .whereEqualTo("id", userId)
                .get()
                .await()

            !snapshot.isEmpty
        } catch (e: Exception) {
            false
        }
    }

}
