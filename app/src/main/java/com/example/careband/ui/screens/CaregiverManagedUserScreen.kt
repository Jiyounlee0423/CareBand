package com.example.careband.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.careband.viewmodel.CaregiverViewModel
import com.example.careband.viewmodel.CaregiverViewModelFactory

@Composable
fun CaregiverManagedUserScreen(
    caregiverId: String,
    navController: NavController
) {
    val context = LocalContext.current

    val viewModel: CaregiverViewModel = viewModel(
        factory = CaregiverViewModelFactory(caregiverId)
    )

    val managedUsers by viewModel.managedUserIds.collectAsState()
    val selectedUser by viewModel.selectedUserId.collectAsState()

    ManagedUserSelectionScreen(
        connectedUsers = managedUsers,
        selectedUserId = selectedUser,
        onSelectUser = { viewModel.selectUser(it) },
        onSearchUser = { userId ->
            viewModel.checkUserExists(userId) { exists ->
                if (exists) {
                    viewModel.addManagedUser(userIdToAdd = userId) { success ->
                        Toast.makeText(context, if (success) "추가 완료" else "추가 실패", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "존재하지 않는 사용자입니다", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onBindSelectedUser = {
            viewModel.bindSelectedUser { success ->
                Toast.makeText(context, if (success) "연동 완료" else "연동 실패", Toast.LENGTH_SHORT).show()
                if (success) navController.popBackStack()
            }
        }
    )
}
