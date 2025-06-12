package com.example.careband.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import com.example.careband.data.model.Alert
import com.example.careband.data.model.VitalSignsRecord
import com.example.careband.data.repository.AlertRepository
import com.example.careband.viewmodel.BleViewModel
import com.example.careband.viewmodel.SensorDataViewModel
import com.example.careband.viewmodel.VitalSignsViewModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

class BleManager(
    private val context: Context,
    private val bleViewModel: BleViewModel,
    private val sensorDataViewModel: SensorDataViewModel,
    private val userId: String,
    private val vitalViewModel: VitalSignsViewModel
) {
    private val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectedDevice: BluetoothDevice? = null
    private val _isConnected = MutableStateFlow(false)
    private val descriptorQueue = LinkedBlockingQueue<BluetoothGattDescriptor>()

    var onDeviceDiscovered: ((BluetoothDevice) -> Unit)? = null
    var onConnected: ((BluetoothDevice) -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val lastSavedTimestamps = mutableMapOf<String, Long>()
    private val saveIntervalMillis = 60_000L

    val latestBPM = MutableStateFlow<Float?>(null)
    val latestSpO2 = MutableStateFlow<Float?>(null)
    val latestTemp = MutableStateFlow<Float?>(null)

//    private fun handleBLEData(type: String, value: Float) {
//        val now = System.currentTimeMillis()
//        val timeStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date(now))
//        val todayStr = timeStr.substring(0, 10)
//
//        val current = _records.value.lastOrNull() ?: VitalSignsRecord(
//            timestamp = timeStr,
//            userId = userId,
//            date = todayStr
//        )
//
//        val updated = when (type) {
//            "BPM" -> current.copy(heartRate = value.toInt())
//            "SpO2" -> current.copy(spo2 = value.toInt())
//            "TEMP" -> current.copy(bodyTemp = value)
//            else -> current
//        }
//
//        _records.value = listOf(updated)
//    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val hasPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                Log.e("BLE", "❌ BLUETOOTH_CONNECT 권한 없음")
                return
            }
            result?.device?.let { device ->
                onDeviceDiscovered?.invoke(device)
            }
        }
    }

    private val descriptorWriteQueue = LinkedBlockingQueue<BluetoothGattDescriptor>()

    fun startScan() {
        val hasPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            Log.e("BLE", "❌ BLUETOOTH_SCAN 권한 없음")
            return
        }
        if (connectedDevice != null) return
        bluetoothAdapter.bluetoothLeScanner?.apply {
            val filters = listOf(ScanFilter.Builder().build())
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            startScan(filters, settings, scanCallback)
        }
    }

    fun stopScan() {
        val hasPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            Log.e("BLE", "❌ BLUETOOTH_SCAN 권한 없음")
            return
        }
        bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
    }

    fun connectToDevice(device: BluetoothDevice) {
        val hasPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            Log.e("BLE", "❌ BLUETOOTH_CONNECT 권한 없음")
            return
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e("BLE", "❌ BLUETOOTH_CONNECT 권한 없음")
            return
        }
        try {
            stopScan()
            connectedDevice = device
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        } catch (e: SecurityException) {
            Log.e("BLE", "❌ connectGatt 실패: 권한 오류", e)
        }
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
                onConnected?.invoke(gatt.device)
                bleViewModel.updateConnectedDevice(gatt.device)
                Log.d("BLE", "✅ onConnectionStateChange: 연결됨")
                _isConnected.value = true
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectedDevice = null
                onDisconnected?.invoke()
                bleViewModel.updateConnectedDevice(null)
                Log.d("BLE", "❌ onConnectionStateChange: 연결 끊김")
                _isConnected.value = false
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val hasPermission = ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                Log.e("BLE", "❌ 알림 설정 중 권한 없음")
                return
            }
            try {
                val service = gatt.getService(UUID.fromString("12345678-0000-1000-8000-00805f9b34fb")) ?: return
                val characteristicUUIDs = listOf(
                    "0000ABCD-0000-1000-8000-00805f9b34fb",
                    "00002A1C-0000-1000-8000-00805f9b34fb",
                    "00002A37-0000-1000-8000-00805f9b34fb",
                    "00002A38-0000-1000-8000-00805f9b34fb"
                ).map { UUID.fromString(it) }

                characteristicUUIDs.forEach { uuid ->
                    val characteristic = service.getCharacteristic(uuid)
                    gatt.setCharacteristicNotification(characteristic, true)
                    val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                    descriptor?.let {
                        it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        descriptorWriteQueue.add(it)
                    }
                }
                writeNextDescriptor()
            } catch (e: SecurityException) {
                Log.e("BLE", "❌ 알림 설정 실패: 권한 오류", e)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            writeNextDescriptor()
        }

        // 지속 시간 추적 변수
        private var spo2AlertStart: Long? = null
        private var bpmAlertStart: Long? = null
        private var tempAlertStart: Long? = null
        private var fallAlertLastSent: Long? = null

        // 기준값
        private val spo2Threshold = 90.0f
        private val bpmLow = 50.0f
        private val bpmHigh = 120.0f
        private val tempHigh = 37.5f

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            val value = characteristic?.getStringValue(0) ?: return
            val now = System.currentTimeMillis()

            when {
                value.startsWith("BPM:") -> {
                    val bpm = value.substringAfter(":").toFloatOrNull() ?: return
                    vitalViewModel.updateLiveVitalSign("BPM", bpm)
                    latestBPM.value = bpm

                    if (bpm in 30.0..180.0) {
                        if (bpm < 50.0 || bpm > 120.0) {
                            if (bpmAlertStart == null) bpmAlertStart = now
                            else if (now - bpmAlertStart!! >= 5000) {
                                saveToAlerts("hr")
                                bpmAlertStart = null
                            }
                        } else {
                            bpmAlertStart = null
                        }
                    } else {
                        Log.w("BLE", "🚫 유효하지 않은 BPM 값: $bpm")
                        bpmAlertStart = null
                    }
                }

                value.startsWith("TEMP:") -> {
                    val temp = value.substringAfter(":").toFloatOrNull() ?: return
                    vitalViewModel.updateLiveVitalSign("TEMP", temp)
                    latestTemp.value = temp

                    if (temp in 30.0..45.0) {
                        if (temp > 37.5) {
                            if (tempAlertStart == null) tempAlertStart = now
                            else if (now - tempAlertStart!! >= 5000) {
                                saveToAlerts("TEMP")
                                tempAlertStart = null
                            }
                        } else {
                            tempAlertStart = null
                        }
                    } else {
                        Log.w("BLE", "🚫 유효하지 않은 체온 값: $temp")
                        tempAlertStart = null
                    }
                }

                value.startsWith("SpO2:") -> {
                    val spo2 = value.substringAfter(":").toFloatOrNull() ?: return
                    vitalViewModel.updateLiveVitalSign("SpO2", spo2)
                    latestSpO2.value = spo2

                    // ✅ 센서 정상 감지 범위: 80~100%
                    if (spo2 in 80.0..100.0) {
                        if (spo2 < 90.0) {
                            if (spo2AlertStart == null) spo2AlertStart = now
                            else if (now - spo2AlertStart!! >= 10000) {
                                saveToAlerts("spo2")
                                spo2AlertStart = null
                            }
                        } else {
                            spo2AlertStart = null
                        }
                    } else {
                        // ⚠️ 유효하지 않은 측정값 → 경고만 띄우고 알림 X
                        Log.w("BLE", "🚫 유효하지 않은 SpO2 값: $spo2")
                        spo2AlertStart = null
                    }
                }

                value == "FALL" -> {
                    val last = fallAlertLastSent ?: 0L
                    if (now - last >= 5000) {
                        saveToAlerts("fall")
                        fallAlertLastSent = now
                    }
                }
            }
        }
    }

    private fun shouldSave(type: String, currentTime: Long): Boolean {
        val lastTime = lastSavedTimestamps[type] ?: 0
        return if (currentTime - lastTime >= saveIntervalMillis) {
            lastSavedTimestamps[type] = currentTime
            true
        } else {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeNextDescriptor() {
        if (descriptorWriteQueue.isNotEmpty()) {
            bluetoothGatt?.writeDescriptor(descriptorWriteQueue.poll())
        }
    }

    private fun saveToVitalSignsMap(type: String, value: Any) {
        val db = FirebaseFirestore.getInstance()
        val now = Timestamp.now()
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val record = mapOf("user_id" to userId, "timestamp" to now, "value" to value)
        db.collection("vital_signs").document(dateKey).collection(type)
            .add(record)
            .addOnSuccessListener { Log.d("BLE", "✅ $type 저장 성공") }
            .addOnFailureListener { e -> Log.e("BLE", "❌ $type 저장 실패", e) }
    }

    private fun saveToAlerts(type: String) {
        val alert = Alert(
            alertId = UUID.randomUUID().toString(),
            alertType = type
        )

        AlertRepository().saveAlert(
            context = context,       // ✅ context 먼저
            userId = userId,         // ✅ 그 다음 userId
            alert = alert,           // ✅ alert 객체 전달
            onSuccess = {
                Log.d("BLE", "✅ alerts 저장 성공")
            },
            onFailure = { e ->
                Log.e("BLE", "❌ alerts 저장 실패: $e")
            }
        )
    }


    fun getConnectedDevice(): BluetoothDevice? {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e("BLE", "❌ getConnectedDevice 권한 없음")
            return null
        }
        return connectedDevice
    }

    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            connectedDevice = null
            onDisconnected?.invoke()
            bleViewModel.updateConnectedDevice(null)
            Log.d("BLE", "🔌 연결 해제됨")
        } catch (e: SecurityException) {
            Log.e("BLE", "❌ disconnect 실패: 권한 오류", e)
        }
    }
}

class BleViewModel : ViewModel() {
    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun updateConnectedDevice(device: BluetoothDevice?) {
        _connectedDevice.value = device
        _isConnected.value = device != null
    }
}
