package com.example.careband.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.careband.data.repository.CaregiverRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CaregiverViewModel(private val caregiverId: String) : ViewModel() {

    private val repository = CaregiverRepository()

    // 보호자가 관리하는 전체 사용자 ID 목록 (1:N 관계)
    private val _managedUserIds = MutableStateFlow<List<String>>(emptyList())
    val managedUserIds: StateFlow<List<String>> = _managedUserIds

    // 보호자가 현재 선택한 사용자 ID (1개)
    private val _selectedUserId = MutableStateFlow<String?>(null)
    val selectedUserId: StateFlow<String?> = _selectedUserId

    // Firebase에서 보호자가 관리하는 사용자 리스트 로드
    // ✅ 생성자에서 바로 호출하도록 변경
    init {
        viewModelScope.launch {
            val ids = repository.getManagedUserIds(caregiverId)
            _managedUserIds.value = ids
            if (ids.isNotEmpty()) {
                _selectedUserId.value = ids.first()
            }
        }
    }

    // 선택한 사용자 ID 업데이트 (ex. 라디오버튼으로 선택했을 때)
    fun selectUser(userId: String) {
        _selectedUserId.value = userId
    }

    fun loadManagedUsers() {
        viewModelScope.launch {
            val ids = repository.getManagedUserIds(caregiverId)
            _managedUserIds.value = ids
            if (ids.isNotEmpty()) _selectedUserId.value = ids[0]
        }
    }

    fun addManagedUser(userIdToAdd: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            repository.addManagedUserId(caregiverId, userIdToAdd) {
                if (it) loadManagedUsers() // 추가 성공 시 다시 로드
                onComplete(it)
            }
        }
    }

    fun checkUserExists(userId: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            val exists = repository.doesUserExist(userId)
            callback(exists)
        }
    }



}