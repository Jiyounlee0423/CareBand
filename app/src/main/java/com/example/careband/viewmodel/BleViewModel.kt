package com.example.careband.viewmodel

import android.bluetooth.BluetoothDevice
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class BleViewModel : ViewModel() {
    var connectedDevice = mutableStateOf<BluetoothDevice?>(null)
        private set

    fun updateConnectedDevice(device: BluetoothDevice?) {
        connectedDevice.value = device
    }
}