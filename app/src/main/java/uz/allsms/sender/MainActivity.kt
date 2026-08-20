package uz.allsms.sender

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private val uiHandler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    // SEND_SMS (majburiy) + POST_NOTIFICATIONS (Android 13+, ixtiyoriy lekin tavsiya etiladi)
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val smsGranted = results[Manifest.permission.SEND_SMS] == true
        if (smsGranted) doConnect() else showPairError("SMS yuborish ruxsati kerak — aks holda ilova SMS jo'nata olmaydi.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        findViewById<TextInputEditText>(R.id.inputServerUrl).setText(
            prefs.serverUrl.ifBlank { "https://" }
        )

        findViewById<android.view.View>(R.id.btnConnect).setOnClickListener { onConnectClicked() }
        findViewById<android.view.View>(R.id.btnDisconnect).setOnClickListener { onDisconnectClicked() }
        findViewById<android.view.View>(R.id.btnBatteryOpt).setOnClickListener { requestIgnoreBatteryOptimizations() }

        findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchService)
            .setOnCheckedChangeListener { button, isChecked ->
                if (!button.isPressed) return@setOnCheckedChangeListener // dasturiy o'zgarishda qayta ishga tushmasin
                if (isChecked) startSenderService() else stopSenderService()
            }

        renderState()
    }

    override fun onResume() {
        super.onResume()
        renderState()
        refreshRunnable = object : Runnable {
            override fun run() {
                updateLiveStatus()
                uiHandler.postDelayed(this, 3000)
            }
        }
        uiHandler.post(refreshRunnable!!)
    }

    override fun onPause() {
        super.onPause()
        refreshRunnable?.let { uiHandler.removeCallbacks(it) }
    }

    // ---------------- ULASH ----------------

    private fun onConnectClicked() {
        val missing = requiredPermissionsMissing()
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            doConnect()
        }
    }

    private fun requiredPermissionsMissing(): List<String> {
        val need = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.SEND_SMS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return need
    }

    private fun doConnect() {
        val url = findViewById<TextInputEditText>(R.id.inputServerUrl).text.toString().trim()
        val code = findViewById<TextInputEditText>(R.id.inputPairingCode).text.toString().trim()
        val name = findViewById<TextInputEditText>(R.id.inputDeviceName).text.toString().trim()
            .ifBlank { Build.MODEL ?: "Android qurilma" }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            showPairError("Server manzili http:// yoki https:// bilan boshlanishi kerak.")
            return
        }
        if (code.length != 6) {
            showPairError("6 xonali kodni to'liq kiriting.")
            return
        }

        setConnectLoading(true)
        thread {
            try {
                val api = ApiClient(url)
                val resp = api.confirmPairing(code, name)
                prefs.serverUrl = url
                prefs.deviceId = resp.getLong("device_id")
                prefs.deviceToken = resp.getString("device_token")
                prefs.deviceName = name
                runOnUiThread {
                    setConnectLoading(false)
                    Toast.makeText(this, "Ulandi! Xizmatni yoqamiz…", Toast.LENGTH_SHORT).show()
                    renderState()
                    startSenderService()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setConnectLoading(false)
                    showPairError(e.message ?: "Ulanishda xatolik yuz berdi.")
                }
            }
        }
    }

    private fun onDisconnectClicked() {
        val token = prefs.deviceToken
        stopSenderService()
        thread {
            try { if (token != null) ApiClient(prefs.serverUrl).unpair(token) } catch (_: Exception) { /* baribir lokal tozalaymiz */ }
            runOnUiThread {
                prefs.clearPairing()
                renderState()
            }
        }
    }

    private fun setConnectLoading(loading: Boolean) {
        findViewById<android.view.View>(R.id.btnConnect).isEnabled = !loading
        (findViewById<android.view.View>(R.id.btnConnect) as com.google.android.material.button.MaterialButton).text =
            if (loading) "Ulanmoqda…" else "Ulash"
    }

    private fun showPairError(msg: String) {
        val v = findViewById<android.widget.TextView>(R.id.pairError)
        v.text = msg
        v.visibility = android.view.View.VISIBLE
    }

    // ---------------- XIZMATNI BOSHQARISH ----------------

    private fun startSenderService() {
        prefs.serviceRunning = true
        val intent = Intent(this, SenderService::class.java)
        ContextCompat.startForegroundService(this, intent)
        renderState()
    }

    private fun stopSenderService() {
        prefs.serviceRunning = false
        stopService(Intent(this, SenderService::class.java))
        renderState()
    }

    private fun requestIgnoreBatteryOptimizations() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                })
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        } else {
            Toast.makeText(this, "Batareya optimallashtirish allaqachon o'chirilgan.", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------- UI HOLATI ----------------

    private fun renderState() {
        val paired = prefs.isPaired
        findViewById<android.view.View>(R.id.pairCard).visibility = if (paired) android.view.View.GONE else android.view.View.VISIBLE
        findViewById<android.view.View>(R.id.statusCard).visibility = if (paired) android.view.View.VISIBLE else android.view.View.GONE
        if (paired) {
            findViewById<android.widget.TextView>(R.id.deviceNameText).text = prefs.deviceName
            findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchService)
                .isChecked = prefs.serviceRunning
            updateLiveStatus()
        }
    }

    private fun updateLiveStatus() {
        if (!prefs.isPaired) return
        val running = prefs.serviceRunning
        val dot = findViewById<android.view.View>(R.id.statusDot)
        val sub = findViewById<android.widget.TextView>(R.id.statusSubText)
        val now = System.currentTimeMillis()
        val recentHeartbeat = running && (now - PendingReports.lastHeartbeatAt) < 60_000

        when {
            !running -> {
                dot.setBackgroundResource(R.drawable.dot_gray)
                sub.text = "Xizmat o'chiq — yoqish uchun tugmani bosing"
            }
            recentHeartbeat -> {
                dot.setBackgroundResource(R.drawable.dot_green)
                sub.text = "Ulangan — server bilan aloqa bor"
            }
            else -> {
                dot.setBackgroundResource(R.drawable.dot_orange)
                sub.text = "Ishga tushmoqda…"
            }
        }

        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val stats = StringBuilder()
        if (PendingReports.lastHeartbeatAt > 0) stats.append("So'nggi signal: ${fmt.format(Date(PendingReports.lastHeartbeatAt))}\n")
        if (PendingReports.lastPollAt > 0) stats.append("So'nggi tekshiruv: ${fmt.format(Date(PendingReports.lastPollAt))}\n")
        PendingReports.lastError?.let { stats.append("Xato: $it") }
        findViewById<android.widget.TextView>(R.id.statsText).text = stats.toString().trim()

        val log = PendingReports.snapshotLog()
        val logView = findViewById<android.widget.TextView>(R.id.logText)
        logView.text = if (log.isEmpty()) "Hali faoliyat yo'q." else log.joinToString("\n") {
            "${fmt.format(Date(it.time))}  ${if (it.ok) "✓" else "✗"}  ${it.text}"
        }
    }
}
