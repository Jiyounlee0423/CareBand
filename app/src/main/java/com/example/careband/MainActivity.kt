package com.example.careband

import android.Manifest
import android.content.pm.PackageManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.*
import com.example.careband.ble.BleManager
import com.example.careband.navigation.Route
import com.example.careband.ui.components.CareBandTopBar
import com.example.careband.ui.screens.*
import com.example.careband.ui.theme.CareBandTheme
import com.example.careband.viewmodel.*
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Notification 클릭 → 전달된 딥링크 처리
        val navigateToAlert = intent.getBooleanExtra("navigateToAlertScreen", false)
        val alertIdFromNotification = intent.getStringExtra("alertId")


        // ✅ [1] Notification 채널 생성은 Android 8.0 이상에서 한 번만 실행
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "careband_channel",              // 채널 ID
                "CareBand 알림",                  // 채널 이름 (설정창에 표시됨)
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "이상 징후 감지 시 알림"
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        setContent {
            CareBandTheme {
                val context = LocalContext.current
                val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"

                // ✅ 알림에서 넘어온 값 remember로 wrapping
                val alertIdFromNotificationRemembered = remember { alertIdFromNotification }
                val navigateToAlertRemembered = remember { navigateToAlert }

                val vitalViewModel: VitalSignsViewModel = viewModel(factory = VitalSignsViewModelFactory(userId))
                val sensorDataViewModel: SensorDataViewModel = viewModel(factory = SensorDataViewModelFactory(userId))
                val bleViewModel = remember { BleViewModel() }

                val bleManager = remember {
                    BleManager(
                        context = context,
                        bleViewModel = bleViewModel,
                        sensorDataViewModel = sensorDataViewModel,
                        userId = userId,
                        vitalViewModel = vitalViewModel // ✅ 실시간 반영용 ViewModel 전달
                    )
                }

                // ✅ [2] 블루투스 권한 요청은 Android 12 이상
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val permissions = arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                    if (!permissions.all {
                            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
                        }) {
                        ActivityCompat.requestPermissions(this, permissions, 1001)
                    }
                }

                val navController = rememberNavController()
                val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
                val userType by authViewModel.userType.collectAsState()
                val userName by authViewModel.userName.collectAsState()
                val currentBackStack by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStack?.destination?.route

                var startDestination by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(isLoggedIn) {
                    startDestination = if (isLoggedIn) {
                        if (navigateToAlertRemembered) Route.ALERT_LOG else Route.HOME
                    } else {
                        Route.LOGIN
                    }

                    if (isLoggedIn) {
                        navController.popBackStack(Route.LOGIN, inclusive = true)
                    }
                }

                BackHandler(enabled = isLoggedIn && currentRoute == Route.LOGIN) {}

                val caregiverId = authViewModel.userId.collectAsState().value ?: ""

                if (startDestination != null) {
                    Scaffold(
                        topBar = {
                            CareBandTopBar(
                                isLoggedIn = isLoggedIn,
                                userType = userType,
                                userName = userName ?: "",
                                onMenuClick = {
                                    if (currentRoute == Route.NAV_MENU) navController.popBackStack()
                                    else navController.navigate(Route.NAV_MENU)
                                },
                                onProfileClick = {
                                    if (currentRoute == Route.PROFILE_MENU) navController.popBackStack()
                                    else navController.navigate(Route.PROFILE_MENU)
                                },
                                onLogoClick = {
                                    navController.navigate("home") {
                                        popUpTo("home") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                    ) { paddingValues ->
                        NavHost(
                            navController = navController,
                            startDestination = startDestination!!,
                            modifier = Modifier.padding(paddingValues)
                        ) {
                            composable(Route.LOGIN) {
                                LoginScreen(
                                    onLoginSuccess = {
                                        navController.navigate(Route.HOME) {
                                            popUpTo(Route.LOGIN) { inclusive = true }
                                        }
                                    },
                                    onRegisterClick = {
                                        navController.navigate(Route.REGISTER)
                                    },
                                    drawerState = null,
                                    scope = rememberCoroutineScope(),
                                    authViewModel = authViewModel
                                )
                            }
                            composable(Route.REGISTER) {
                                RegisterScreen(
                                    onRegisterSuccess = {
                                        navController.navigate(Route.HOME) {
                                            popUpTo(Route.REGISTER) { inclusive = true }
                                        }
                                    },
                                    onLoginClick = {
                                        navController.navigate(Route.LOGIN)
                                    },
                                    drawerState = null,
                                    scope = rememberCoroutineScope(),
                                    authViewModel = authViewModel
                                )
                            }
                            composable(Route.HOME) {
                                HomeScreen(
                                    navController = navController,
                                    bleManager = bleManager
                                )
                            }
                            composable(Route.PROFILE_MENU) {
                                ProfileMenuScreen(navController)
                            }
                            composable(Route.HEALTH_RECORD) {
                                HealthRecordScreen(navController)
                            }
                            composable(Route.MEDICAL_HISTORY) {
                                MedicalHistoryScreen(navController)
                            }
                            composable(Route.DISEASE_RECORD) {
                                DiseaseRecordScreen(userId = userId)
                            }
                            composable(Route.MEDICATION_RECORD) {
                                MedicationRecordScreen(userId = userId)
                            }
                            composable(Route.VACCINATION_RECORD) {
                                VaccinationRecordScreen(userId = userId)
                            }
                            composable(Route.MEDICAL_REPORT) {
                                userType?.let {
                                    MedicalReportScreen(
                                        navController = navController,
                                        userId = userId,
                                        userType = it
                                    )
                                }
                            }
                            composable(Route.VITALSIGNS_VIEW) {
                                VitalSignsChartScreen(userId = userId)
                            }
                            composable(Route.ALERT_LOG) {
                                val alertViewModelFactory = remember { AlertViewModelFactory(userId) }
                                val alertViewModel: AlertViewModel = viewModel(factory = alertViewModelFactory)

                                AlertScreen(
                                    navController = navController,
                                    userId = userId,
                                    viewModel = alertViewModel,
                                    focusedAlertId = alertIdFromNotificationRemembered
                                )
                            }

                            composable(Route.USER_MANAGEMENT) {
                                val caregiverIdNullable by authViewModel.userId.collectAsState(initial = null)

                                // 여기부터는 Composable context 안이기 때문에 사용 가능!
                                if (!caregiverIdNullable.isNullOrBlank()) {
                                    val caregiverId = caregiverIdNullable!!

                                    CaregiverManagedUserScreen(
                                        caregiverId = caregiverId,
                                        navController = navController
                                    )
                                } else {
                                    // ✅ Composable 안이기 때문에 CircularProgressIndicator 사용 가능
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }

                            composable(Route.DEVICE_CONNECTION) {
                                BleManagerScreen(
                                    bleViewModel = bleViewModel,
                                    sensorDataViewModel = sensorDataViewModel,
                                    bleManager = bleManager
                                )
                            }
                            composable(Route.NAV_MENU) {
                                NavigationMenuScreen(
                                    navController = navController,
                                    isLoggedIn = isLoggedIn,
                                    userName = userName ?: "",
                                    userType = userType,
                                    onMenuClick = { menu ->
                                        when (menu) {
                                            "건강 기록" -> navController.navigate(Route.HEALTH_RECORD)
                                            "의료 이력" -> navController.navigate(Route.MEDICAL_HISTORY)
                                            "의료 리포트" -> navController.navigate(Route.MEDICAL_REPORT)
                                            "데이터 시각화" -> navController.navigate(Route.VITALSIGNS_VIEW)
                                            "알림 기록" -> navController.navigate(Route.ALERT_LOG)
                                            "사용자 관리" -> navController.navigate(Route.USER_MANAGEMENT)
                                            "설정" -> {}
                                            "기기 연결" -> navController.navigate(Route.DEVICE_CONNECTION)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d("BLE", "✅ 권한 허용됨")
            } else {
                Log.w("BLE", "❌ 권한 거부됨")
            }
        }
    }
}
