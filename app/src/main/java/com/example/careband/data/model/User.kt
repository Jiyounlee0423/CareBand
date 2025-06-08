package com.example.careband.data.model

data class User(
    val uid: String = "",      // Firebase UID
    val id: String = "",                      // 사용자가 입력한 ID
    val name: String = "",
    val type: UserType = UserType.USER,
    val birth: String = "",
    val gender: String = "",
    val contactInfo: String = "",
    val protectedUserId: String? = null       // 보호자 계정일 경우 연결된 사용자 UID
)
