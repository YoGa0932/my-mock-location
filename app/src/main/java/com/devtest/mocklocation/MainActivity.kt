package com.devtest.mocklocation

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var etLatitude: EditText
    private lateinit var etLongitude: EditText
    private lateinit var etAltitude: EditText
    private lateinit var etAccuracy: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvStatus: TextView
    private lateinit var prefs: SharedPreferences
    private var mockThread: Thread? = null
    @Volatile private var isRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("mock_prefs", Context.MODE_PRIVATE)
        etLatitude  = findViewById(R.id.etLatitude)
        etLongitude = findViewById(R.id.etLongitude)
        etAltitude  = findViewById(R.id.etAltitude)
        etAccuracy  = findViewById(R.id.etAccuracy)
        btnStart    = findViewById(R.id.btnStart)
        btnStop     = findViewById(R.id.btnStop)
        tvStatus    = findViewById(R.id.tvStatus)
        etLatitude.setText(prefs.getString("lat", ""))
        etLongitude.setText(prefs.getString("lon", ""))
        etAltitude.setText(prefs.getString("alt", "0.0"))
        etAccuracy.setText(prefs.getString("acc", "3.0"))
        btnStart.setOnClickListener { startMock() }
        btnStop.setOnClickListener  { stopMock()  }
        updateUI(false)
    }

    private fun startMock() {
        if (!checkMockPermission()) { showMockSettingDialog(); return }
        val latStr = etLatitude.text.toString().trim()
        val lonStr = etLongitude.text.toString().trim()
        val altStr = etAltitude.text.toString().trim()
        val accStr = etAccuracy.text.toString().trim()
        if (latStr.isEmpty() || lonStr.isEmpty()) { toast("请输入经纬度"); return }
        val lat = latStr.toDoubleOrNull()
        val lon = lonStr.toDoubleOrNull()
        val alt = altStr.toDoubleOrNull() ?: 0.0
        val acc = accStr.toFloatOrNull() ?: 3.0f
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) { toast("经纬度格式不正确"); return }
        prefs.edit().putString("lat", latStr).putString("lon", lonStr).putString("alt", altStr).putString("acc", accStr).apply()
        isRunning = true
        updateUI(true)
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        mockThread = Thread {
            try {
                setupProvider(locationManager, LocationManager.GPS_PROVIDER)
                setupProvider(locationManager, LocationManager.NETWORK_PROVIDER)
                while (isRunning) {
                    pushLocation(locationManager, LocationManager.GPS_PROVIDER, lat, lon, alt, acc)
                    pushLocation(locationManager, LocationManager.NETWORK_PROVIDER, lat, lon, alt, acc)
                    Thread.sleep(500)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "错误：${e.message}\n请确认已在开发者选项中选择本应用"
                    updateUI(false)
                }
            } finally {
                try { locationManager.removeTestProvider(LocationManager.GPS_PROVIDER) } catch (_: Exception) {}
                try { locationManager.removeTestProvider(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) {}
            }
        }.also { it.start() }
    }

    private fun setupProvider(lm: LocationManager, provider: String) {
        try { lm.removeTestProvider(provider) } catch (_: Exception) {}
        lm.addTestProvider(provider, false, false, false, false, true, true, true,
            android.location.Criteria.POWER_LOW, android.location.Criteria.ACCURACY_FINE)
        lm.setTestProviderEnabled(provider, true)
    }

    private fun pushLocation(lm: LocationManager, provider: String, lat: Double, lon: Double, alt: Double, acc: Float) {
        val loc = Location(provider).apply {
            latitude  = lat; longitude = lon; altitude  = alt; accuracy  = acc
            time      = System.currentTimeMillis()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                verticalAccuracyMeters = acc
        }
        try { lm.setTestProviderLocation(provider, loc) } catch (_: Exception) {}
    }

    private fun stopMock() {
        isRunning = false; mockThread?.interrupt(); mockThread = null
        updateUI(false); toast("已停止模拟定位")
    }

    private fun checkMockPermission(): Boolean {
        return try {
            val opsManager = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                opsManager.unsafeCheckOp(AppOpsManager.OPSTR_MOCK_LOCATION, android.os.Process.myUid(), packageName)
            else @Suppress("DEPRECATION") opsManager.checkOp(AppOpsManager.OPSTR_MOCK_LOCATION, android.os.Process.myUid(), packageName)
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { false }
    }

    private fun showMockSettingDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要授权")
            .setMessage("请前往【开发者选项】→【选择模拟位置信息应用】→ 选择本应用（MockLocation）")
            .setPositiveButton("去设置") { _, _ -> startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
            .setNegativeButton("取消", null).show()
    }

    private fun updateUI(running: Boolean) {
        runOnUiThread {
            btnStart.isEnabled = !running; btnStop.isEnabled = running
            tvStatus.text = if (running)
                "模拟运行中\n纬度：${etLatitude.text}\n经度：${etLongitude.text}\n海拔：${etAltitude.text}m"
            else "未运行"
        }
    }

    override fun onDestroy() { super.onDestroy(); stopMock() }
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}