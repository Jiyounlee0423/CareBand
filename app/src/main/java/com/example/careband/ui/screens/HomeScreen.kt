package com.example.careband.ui.screens

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.careband.R
import com.example.careband.data.model.UserType
import com.example.careband.navigation.Route
import com.example.careband.viewmodel.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import java.time.LocalDate
import com.example.careband.ble.BleManager
import com.example.careband.data.model.VitalSignsRecord

@Composable
fun HomeScreen(navController: NavController, bleManager: BleManager) {
    val authViewModel: AuthViewModel = viewModel()
    val medicationCheckViewModel: MedicationCheckViewModel = viewModel()
    val vitalViewModel: VitalSignsViewModel = viewModel(factory = VitalSignsViewModelFactory(authViewModel.userId.value ?: ""))

    val userType by authViewModel.userType.collectAsState()
    val userId = authViewModel.userId.collectAsState().value ?: ""
    val today = remember { SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date()) }
    val todayMedications by medicationCheckViewModel.todayMedications.collectAsState()
    val records by vitalViewModel.records.collectAsStateWithLifecycle()

    val latestRecordState = vitalViewModel.latestRecord.collectAsState()
    //val latestRecordState by vitalViewModel.latestRecord.collectAsStateWithLifecycle()
    val latestRecord by vitalViewModel.latestRecord.collectAsState()







    val latest = records.lastOrNull()

    LaunchedEffect(latestRecordState.value) {
        Log.d("UI", "📲 최신 레코드 업데이트됨: ${latestRecordState.value}")
    }
    LaunchedEffect(latestRecord) {
        Log.d("UI", "📲 최신 레코드 UI에서 감지됨: $latestRecord")
    }

    val isConnectedState by bleManager.isConnected.collectAsState()


    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 로그인 상태 확인
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                authViewModel.checkLoginStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 백핸들러
    BackHandler {
        authViewModel.logout()
        navController.navigate(Route.LOGIN) {
            popUpTo(Route.HOME) { inclusive = true }
        }
    }

    // 복약 불러오기
    LaunchedEffect(userId) {
        if (userId.isNotBlank()) {
            medicationCheckViewModel.loadTodayMedications(userId)
        }
    }

    LaunchedEffect(userId) {
        if (userId.isNotBlank()) {
            Log.d("UI", "🔥 userId = $userId")
            medicationCheckViewModel.loadTodayMedications(userId)
        }
    }

//    LaunchedEffect(Unit) {
//        if (userId.isNotBlank()) {
//            vitalViewModel.updateLatestVitalSigns(null, null, null) // 초기화용
//        }
//    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = today,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(8.dp)
                .background(Color(0xFFFADADD), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            !isConnectedState -> {
                Text("기기 연결이 필요합니다.", color = Color.Red)
            }
            latestRecord == null -> {
                Text("등록된 생체 정보가 없습니다.", color = Color.Gray)
            }
            else -> {
                val record = latestRecord!!
                val items = mutableListOf<Triple<String, String, Int>>()

                if (record.heartRate > 0) items.add(Triple("심박수", "${record.heartRate} BPM", R.drawable.heart_icon))
                if (record.spo2 > 0) items.add(Triple("산소포화도", "${record.spo2} %", R.drawable.spo2_icon))
                if (record.bodyTemp > 0f) items.add(Triple("체온", "${record.bodyTemp} °C", R.drawable.thermometer))

                if (items.isEmpty()) {
                    Text("등록된 생체 정보가 없습니다.", color = Color.Gray)
                } else {
                    items.forEach { (label, value, iconRes) ->
                        VitalRow(label = label, value = value, icon = painterResource(id = iconRes))
                    }
                }
            }
        }


        LaunchedEffect(records) {
            Log.d("UI", "📈 최신 데이터 변경됨: ${records.lastOrNull()}")
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (todayMedications.isNotEmpty()) {
            todayMedications.forEach { record ->
                MedicationItem(
                    name = record.medicineName,
                    startDate = record.startDate,
                    endDate = record.endDate,
                    checked = record.takenDates.contains(today),
                    onChecked = {
                        medicationCheckViewModel.updateMedicationCheckState(
                            userId = userId, record = record, isChecked = it
                        )
                    }
                )
            }
        } else {
            Text("등록된 약 정보가 없습니다.", color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(24.dp))

        userType?.let { type ->
            when (type) {
                UserType.USER -> {
                    HomeButtonRow("의료 리포트", Route.MEDICAL_REPORT, "알림 기록", Route.ALERT_LOG, navController)
                    HomeButtonRow("건강 기록", Route.HEALTH_RECORD, "의료 이력", Route.MEDICAL_HISTORY, navController)
                }
                UserType.CAREGIVER -> {
                    HomeButtonRow("의료 리포트", Route.MEDICAL_REPORT, "알림 기록", Route.ALERT_LOG, navController)
                    HomeButtonRow("사용자 관리", Route.USER_MANAGEMENT, "의료 이력", Route.MEDICAL_HISTORY, navController)
                }
            }
        }
    }
}

@Composable
fun VitalRow(label: String, value: String, icon: Painter) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painter = icon, contentDescription = label, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "$label: $value", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun HomeButtonRow(
    leftLabel: String,
    leftRoute: String,
    rightLabel: String,
    rightRoute: String,
    navController: NavController
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HomeButton(label = leftLabel, onClick = { navController.navigate(leftRoute) }, modifier = Modifier.weight(1f))
        HomeButton(label = rightLabel, onClick = { navController.navigate(rightRoute) }, modifier = Modifier.weight(1f))
    }
}

@Composable
fun MedicationItem(
    name: String,
    startDate: String,
    endDate: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    var isChecked by remember { mutableStateOf(checked) }
    val period = if (endDate.isNotBlank()) "$startDate ~ $endDate" else "$startDate ~"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Color(0xFFF3F3F3), shape = RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(text = period, fontSize = 14.sp, color = Color.Gray)
        }

        Checkbox(
            checked = isChecked,
            onCheckedChange = {
                isChecked = it
                onChecked(it)
            }
        )
    }
}

@Composable
fun HomeButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(48.dp)
    ) {
        Text(text = label, textAlign = TextAlign.Center)
    }
}
