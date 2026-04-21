package com.smu.gardenlite

import android.bluetooth.BluetoothGatt
import java.util.UUID

object BleManager {
    var gatt: BluetoothGatt? = null

    val SERVICE_UUID: UUID = UUID.fromString("0de8b116-cd29-4e19-aa41-8c5cfc610091")
    val CHAR_UUID: UUID = UUID.fromString("e602eb6f-121f-415d-9a87-c48ba0cdbd93")
    val NOTIFY_CHAR_UUID: UUID = UUID.fromString("ffcbff16-d1d9-44ff-b5e3-24b1012deba4")
}