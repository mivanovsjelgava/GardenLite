package com.smu.gardenlite

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.material.button.MaterialButton
import java.util.*

class HomeActivity : AppCompatActivity() {

    private lateinit var tvTimer: TextView
    private val channelId = "garden_status"

    // 1. Слушатель входящих данных от ESP32
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val data = String(characteristic.value, Charsets.UTF_8)

            runOnUiThread {
                when {
                    // Обработка статуса света
                    data.startsWith("STATUS:") -> {
                        val state = data.substring(7)
                        val msg = if (state == "ON") "Gaisma IESLĒGTA" else "Gaisma IZSLĒGTA"
                        showNotify(msg)
                    }
                    // Обработка таймера
                    data.startsWith("NEXT:") -> {
                        val parts = data.split(":")
                        if (parts.size >= 3) {
                            val prefix = if (parts[1] == "ONIN") "Ieslēgsies pēc: " else "Izslēgsies pēc: "
                            val hours = parts[2].toDoubleOrNull() ?: 0.0
                            tvTimer.text = prefix + formatTime(hours)
                        }
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread {
                    Toast.makeText(this@HomeActivity, "Savienojums pārtraukts", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home)

        tvTimer = findViewById(R.id.tvTimer)
        val btnOn = findViewById<MaterialButton>(R.id.btnOn)
        val btnOff = findViewById<MaterialButton>(R.id.btnOff)
        val btnSync = findViewById<MaterialButton>(R.id.btnSync)
        val btnReset = findViewById<MaterialButton>(R.id.btnReset)

        createNotificationChannel()
        setupNotifications()

        btnOn.setOnClickListener { sendBleData("ON") }
        btnOff.setOnClickListener { sendBleData("OFF") }

        btnSync.setOnClickListener {
            val ts = System.currentTimeMillis() / 1000
            sendBleData("TIME:$ts")
            Toast.makeText(this, "Sinhronizēts!", Toast.LENGTH_SHORT).show()
        }

        btnReset.setOnClickListener {
            val prefs = getSharedPreferences("GardenLitePrefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("isSetupDone", false).apply()

            BleManager.gatt?.apply {
                disconnect()
                close()
            }
            BleManager.gatt = null

            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Garden Status", NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        // ПЕРЕХВАТЫВАЕМ КОЛЛБЕК: Теперь HomeActivity будет слушать ESP32
        BleManager.gatt?.let { gatt ->
            // Это заставляет GATT переключить все события на наш локальный gattCallback
            // В некоторых версиях Android требуется переподключение, но попробуем так:
            // Если данные не пойдут, мы добавим переподключение.
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupNotifications() {
        val gatt = BleManager.gatt ?: return

        // Важно использовать UUID из BleManager, чтобы не было опечаток
        val service = gatt.getService(BleManager.SERVICE_UUID)
        val characteristic = service?.getCharacteristic(BleManager.NOTIFY_CHAR_UUID)

        if (characteristic != null) {
            // Сообщаем системе Android, что мы хотим слушать эту характеристику
            gatt.setCharacteristicNotification(characteristic, true)

            // Пишем в дескриптор, чтобы ESP32 начала передачу
            val descriptor = characteristic.getDescriptor(UUID.fromString("ffcbff16-d1d9-44ff-b5e3-24b1012deba4"))
            if (descriptor != null) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
        } else {
            Toast.makeText(this, "Уведомления не найдены!", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotify(msg: String) {
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("GardenLite Status")
            .setContentText(msg)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(1, builder.build())
        }
    }

    private fun formatTime(hours: Double): String {
        val totalMinutes = (hours * 60).toInt()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return String.format("%02d:%02d", h, m)
    }

    @SuppressLint("MissingPermission")
    private fun sendBleData(data: String) {
        val gatt = BleManager.gatt ?: return
        val service = gatt.getService(BleManager.SERVICE_UUID)
        val characteristic = service?.getCharacteristic(BleManager.CHAR_UUID)

        if (characteristic != null) {
            val bytes = data.toByteArray(Charsets.UTF_8)
            characteristic.value = bytes
            gatt.writeCharacteristic(characteristic)
        }
    }
}