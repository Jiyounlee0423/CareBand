// BleManager.kt
package com.example.careband.ble

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.example.careband.R
import com.example.careband.viewmodel.SensorDataViewModel
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class BleManager(
    private val context: Context,
    private val viewModel: SensorDataViewModel,
    private val userId: String,
    private val notifiedTo: String = "보호자",
    private val expectResponse: Boolean = false
) {
    private val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothGatt: BluetoothGatt? = null

    var onDeviceDiscovered: ((BluetoothDevice) -> Unit)? = null
    var onConnected: ((BluetoothDevice) -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    private val scanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val name = device.name
                val displayName = if (name.isNullOrBlank()) "이름 없음" else name
                val address = device.address ?: "주소 없음"
                Log.d("BLE", "🔍 발견된 기기: $displayName, $address")
                onDeviceDiscovered?.invoke(device)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        try {
            val scanner = bluetoothAdapter.bluetoothLeScanner ?: return
            Log.d("BLE", "startScan() 호출됨")

            val filters = listOf(ScanFilter.Builder().build())
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            Log.d("BLE", "🔵 BLE 스캔 중...")
            scanner.startScan(filters, settings, scanCallback)
        } catch (e: SecurityException) {
            Log.e("BLE", "❌ startScan 권한 오류: ${e.message}")
        }
    }

    fun stopScan() {
        try {
            val scanner = bluetoothAdapter.bluetoothLeScanner ?: return
            scanner.stopScan(scanCallback)
        } catch (e: SecurityException) {
            Log.e("BLE", "❌ stopScan 권한 오류: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        try {
            bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.d("BLE", "✅ BLE 연결됨: ${device.address}")
                        gatt.discoverServices()
                        onConnected?.invoke(device)
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.d("BLE", "⚠ BLE 연결 해제됨")
                        onDisconnected?.invoke()
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    val services = gatt.services
                    services.forEach { service ->
                        service.characteristics.forEach { characteristic ->
                            gatt.setCharacteristicNotification(characteristic, true)
                            val descriptor = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                            descriptor?.let {
                                it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(it)
                            }
                        }
                    }
                }

                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    val value = characteristic.value?.toString(Charsets.UTF_8) ?: return
                    Log.d("BLE", "📥 수신된 데이터: $value")

                    viewModel.updateFallStatus(value)

                    when {
                        value == "FALL" -> {
                            sendFallNotification()
                            saveToVitalSigns(fallDetected = true)
                            saveToAlerts("fall")
                        }
                        value.startsWith("BPM:") -> {
                            val bpm = value.removePrefix("BPM:").toFloatOrNull() ?: return
                            saveToVitalSigns(bpm = bpm)
                            if (bpm < 80 || bpm > 120) saveToAlerts("hr_high")
                        }
                        value.startsWith("SpO2:") -> {
                            val spo2 = value.removePrefix("SpO2:").toFloatOrNull() ?: return
                            saveToVitalSigns(spo2 = spo2)
                            if (spo2 < 96) saveToAlerts("spo2_low")
                        }
                        value == "MPU_ERROR" -> {
                            Log.e("BLE", "❗ MPU 센서 오류 수신됨")
                            saveToAlerts("mpu_error")
                        }
                        value == "MAX_ERROR" -> {
                            Log.e("BLE", "❗ MAX30102 센서 오류 수신됨")
                            saveToAlerts("max_error")
                        }
                        value == "SLEEP_MODE" -> {
                            Log.i("BLE", "💤 ESP32 슬립 모드 진입")
                        }
                    }
                }
            })
        } catch (e: SecurityException) {
            Log.e("BLE", "❌ connectGatt 권한 오류: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            onDisconnected?.invoke()
        } catch (e: SecurityException) {
            Log.e("BLE", "❌ disconnect 권한 오류: ${e.message}")
        }
    }

    private fun sendFallNotification() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "fall_alert"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "낙상 경고", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle("낙상 감지")
            .setContentText("사용자에게 낙상이 감지되었습니다.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(1, notification)
    }

    private fun saveToVitalSigns(bpm: Float? = null, spo2: Float? = null, fallDetected: Boolean? = null) {
        val db = FirebaseFirestore.getInstance()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val data = mutableMapOf<String, Any>(
            "user_id" to userId,
            "timestamp" to timestamp
        )
        bpm?.let { data["heart_rate"] = it }
        spo2?.let { data["spo2"] = it }
        fallDetected?.let { data["fall_detected"] = it }

        db.collection("vital_signs").add(data)
            .addOnSuccessListener { Log.d("BLE", "✅ vital_signs 저장 성공") }
            .addOnFailureListener { e -> Log.w("BLE", "❌ vital_signs 저장 실패", e) }
    }

    private fun saveToAlerts(type: String) {
        val db = FirebaseFirestore.getInstance()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val data = mapOf(
            "user_id" to userId,
            "alert_type" to type,
            "is_false_alarm" to false,
            "notified_to" to notifiedTo,
            "response_received" to expectResponse,
            "timestamp" to timestamp
        )
        db.collection("alerts").add(data)
            .addOnSuccessListener { Log.d("BLE", "✅ alerts 저장 성공") }
            .addOnFailureListener { e -> Log.w("BLE", "❌ alerts 저장 실패", e) }
    }
}
