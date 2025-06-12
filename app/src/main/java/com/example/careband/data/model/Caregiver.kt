package com.example.careband.data.model

data class Caregiver(
    val id: String = "",  // 보호자 UID
    val name: String = "",
    //val caredUserId: String = ""  // 케어할 사용자 UID (입력받은 값)
    val managedUserIds: List<String> = emptyList()  // ✅ 여러 사용자 UID를 저장하는 리스트로 변경
)
