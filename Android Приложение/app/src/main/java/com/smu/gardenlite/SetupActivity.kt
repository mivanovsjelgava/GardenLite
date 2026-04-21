package com.smu.gardenlite

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.util.*
import android.widget.Toast

class SetupActivity : AppCompatActivity() {

    private val SERVICE_UUID = BleManager.SERVICE_UUID
    private val CHAR_UUID = BleManager.CHAR_UUID

    private var bluetoothGatt: BluetoothGatt? = null
    private var scanner: BluetoothLeScanner? = null

    private lateinit var tvStatus: TextView
    private lateinit var pbScanning: ProgressBar
    private lateinit var btnTalak: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.setup)

        tvStatus = findViewById(R.id.tvStatus)
        pbScanning = findViewById(R.id.pbScanning)
        btnTalak = findViewById(R.id.btnTalak)

        btnTalak.setOnClickListener {
            val prefs = getSharedPreferences("GardenLitePrefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("isSetupDone", true).apply()

            val intent = Intent(this, HomeActivity::class.java)
            startActivity(intent)
            finish()
        }

        startBle()
    }

    @SuppressLint("MissingPermission")
    private fun startBle() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            tvStatus.text = "Ieslēdziet BT!"
            pbScanning.visibility = View.GONE
            return
        }

        scanner = adapter.bluetoothLeScanner
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        tvStatus.text = "Meklē ierīci..."
        pbScanning.visibility = View.VISIBLE
        scanner?.startScan(null, settings, scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = result.scanRecord?.deviceName ?: device.name ?: "Unknown"

            if (name == "ESP32-Greenhouse" || name == "ESP32-C3-BLE") {
                scanner?.stopScan(this)
                runOnUiThread {
                    tvStatus.text = "Savienojas..."
                }
                bluetoothGatt = device.connectGatt(this@SetupActivity, false, gattCallback)
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                BleManager.gatt = null
                runOnUiThread {
                    tvStatus.text = "Atvienots"
                    pbScanning.visibility = View.GONE
                    btnTalak.isEnabled = false
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                val characteristic = service?.getCharacteristic(CHAR_UUID)

                if (characteristic != null) {
                    BleManager.gatt = gatt

                    val currentTimestamp = System.currentTimeMillis() / 1000
                    writeBle(gatt, characteristic, "TIME:$currentTimestamp".toByteArray())

                    sendLocationData(gatt, characteristic)

                    runOnUiThread {
                        tvStatus.text = "Gatavs! Laiks sinhronizēts."
                        tvStatus.setTextColor(Color.parseColor("#149a5c"))
                        pbScanning.visibility = View.GONE
                        btnTalak.isEnabled = true
                        btnTalak.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#149a5c"))
                    }
                } else {
                    runOnUiThread {
                        tvStatus.text = "UUID kļūda"
                        pbScanning.visibility = View.GONE
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendCurrentTime(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val currentTimestamp = System.currentTimeMillis() / 1000
        val command = "TIME:$currentTimestamp"

        val bytes = command.toByteArray(Charsets.UTF_8)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            characteristic.value = bytes
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            gatt.writeCharacteristic(characteristic)
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendLocationData(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager

        val lastKnownLocation = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            ?: locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)

        if (lastKnownLocation != null) {
            val lat = lastKnownLocation.latitude
            val lon = lastKnownLocation.longitude

            val command = String.format(java.util.Locale.US, "GPS:%.4f,%.4f", lat, lon)

            Thread.sleep(300)

            val bytes = command.toByteArray(Charsets.UTF_8)
            writeBle(gatt, characteristic, bytes)

            runOnUiThread {
                Toast.makeText(this, "GPS nosūtīts: $lat, $lon", Toast.LENGTH_SHORT).show()
            }
        } else {
            runOnUiThread {
                Toast.makeText(this, "Nevar noteikt lokāciju", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeBle(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            characteristic.value = bytes
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            gatt.writeCharacteristic(characteristic)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}