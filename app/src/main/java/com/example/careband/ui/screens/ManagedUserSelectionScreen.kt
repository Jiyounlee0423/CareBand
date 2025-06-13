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
fun ManagedUserSelectionScreen(
    connectedUsers: List<String>, // ex) ["1111", "3333", "5555"]
    selectedUserId: String?,
    onSelectUser: (String) -> Unit,
    onBindSelectedUser: () -> Unit,
    onSearchUser: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("현재 연결된 사용자 목록", style = MaterialTheme.typography.titleMedium)

        Spacer(modifier = Modifier.height(8.dp))

        connectedUsers.forEach { userId ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectUser(userId) }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = (userId == selectedUserId),
                    onCheckedChange = { onSelectUser(userId) }
                )
                Text("사용자 ID: $userId")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onBindSelectedUser,
            enabled = selectedUserId != null
        ) {
            Text("연동하기")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        Text("새 사용자 검색", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("사용자 ID 입력") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { onSearchUser(searchQuery) }) {
            Text("사용자 목록에 추가")
        }
    }
}
