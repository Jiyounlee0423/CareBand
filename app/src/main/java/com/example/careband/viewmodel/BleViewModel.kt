package com.example.careband.viewmodel

import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BleViewModel : ViewModel() {
    var connectedDevice = mutableStateOf<BluetoothDevice?>(null)
        private set

    fun updateConnectedDevice(device: BluetoothDevice?) {
        connectedDevice.value = device
    }

    private val _connectionStatus = MutableStateFlow(false)
    val connectionStatus: StateFlow<Boolean> = _connectionStatus

    fun setConnectionStatus(isConnected: Boolean) {
        _connectionStatus.value = isConnected
    }
}