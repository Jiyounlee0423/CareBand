package com.example.careband.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

data class Alert(
    val alertId: String = "",

    @get:PropertyName("userId") @set:PropertyName("userId")
    var userId: String = "",

    val alertType: String = "",
    val isFalseAlarm: Boolean = false,
    val notifiedTo: String = "",
    val responseReceived: Boolean = false,
    val timestamp: Timestamp = Timestamp.now(),
    val timestampKey: String = ""
)


