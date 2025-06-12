package com.example.careband.ui.screens

import android.widget.Toast
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
fun ManagedUserSelectionScreen(
    navController: NavController,
    caregiverId: String
) {
    val context = LocalContext.current
    val caregiverViewModel: CaregiverViewModel = viewModel(factory = CaregiverViewModelFactory(caregiverId))
    val managedUserIds by caregiverViewModel.managedUserIds.collectAsState()

    var searchInput by remember { mutableStateOf("") }
    var searchResultUserId by remember { mutableStateOf<String?>(null) }
    var selectedUserId by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {

        Text("현재 연결된 사용자 목록", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (managedUserIds.isEmpty()) {
            Text("연결된 사용자가 없습니다.")
        } else {
            managedUserIds.forEach {
                Text("• 사용자 ID: $it")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider()
        Spacer(modifier = Modifier.height(24.dp))

        Text("새 사용자 검색", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchInput,
            onValueChange = { searchInput = it },
            label = { Text("사용자 ID 입력") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = {
            caregiverViewModel.checkUserExists(searchInput) { exists ->
                if (exists) {
                    searchResultUserId = searchInput
                    selectedUserId = searchInput
                    Toast.makeText(context, "사용자 검색 성공", Toast.LENGTH_SHORT).show()
                } else {
                    searchResultUserId = null
                    Toast.makeText(context, "존재하지 않는 사용자입니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }) {
            Text("사용자 검색")
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (searchResultUserId != null) {
            Text("🔍 검색된 사용자 목록", style = MaterialTheme.typography.titleMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                RadioButton(
                    selected = selectedUserId == searchResultUserId,
                    onClick = { selectedUserId = searchResultUserId }
                )
                Text("사용자 ID: $searchResultUserId")
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    selectedUserId?.let { uid ->
                        caregiverViewModel.addManagedUser(uid) { success ->
                            if (success) {
                                Toast.makeText(context, "✅ 사용자 연결 완료", Toast.LENGTH_SHORT).show()
                                searchResultUserId = null
                                selectedUserId = null
                                searchInput = ""
                            } else {
                                Toast.makeText(context, "❌ 사용자 연결 실패", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                enabled = selectedUserId != null,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("사용자 연결")
            }
        }
    }
}

