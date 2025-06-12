package com.example.careband.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.careband.data.model.Alert
import com.example.careband.viewmodel.AlertViewModel
import com.example.careband.viewmodel.AlertViewModelFactory
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertScreen(
    navController: NavController,
    userId: String,
    viewModel: AlertViewModel,
    focusedAlertId: String? = null
) {
    //val viewModel: AlertViewModel = viewModel(factory = AlertViewModelFactory(userId))

    val alertList by viewModel.alertList.collectAsState()
    val listState = rememberLazyListState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    // 처음 진입 시 알림 불러오기
    LaunchedEffect(userId) {
        viewModel.startAlertListener()
        //viewModel.loadUserAlerts(userId)
    }

    LaunchedEffect(alertList) {
        if (!focusedAlertId.isNullOrEmpty()) {
            val index = alertList.indexOfFirst { it.alertId == focusedAlertId }
            if (index >= 0) {
                listState.scrollToItem(index)
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("이상 알림 로그") }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            when {
                loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                error != null -> {
                    Text(
                        text = error ?: "오류 발생",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                alertList.isEmpty() -> {
                    Text(
                        text = "알림 내역이 없습니다.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyColumn(state = listState) {
                        items(alertList) { alert ->
                            AlertItem(
                                alert = alert,
                                onRespond = {
                                    viewModel.markAlertAsResponded(alert.alertId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlertItem(
    alert: Alert,
    onRespond: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    val formattedTime = dateFormat.format(alert.timestamp.toDate())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "알림 종류: ${alert.alertType}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "발생 시각: $formattedTime",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = if (alert.responseReceived) "✅ 응답 완료" else "⏳ 응답 대기",
                style = MaterialTheme.typography.bodyMedium,
                color = if (alert.responseReceived)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )

            if (!alert.responseReceived) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRespond,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("응답 처리")
                }
            }
        }
    }
}
