package uz.allsms.sender

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Ilova "ishga tushirilgandan" keyin shu servis fonda doim ishlab turadi:
 *  - har ~25 soniyada heartbeat (backend qurilmani "online" deb bilib turishi uchun)
 *  - har ~6 soniyada navbatdagi SMS'larni so'raydi va SmsManager orqali haqiqiy yuboradi
 * Bildirishnoma doim ko'rinib turadi (Android talabi — fon servis "ko'rinmas" ishlay olmaydi).
 */
class SenderService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var prefs: Prefs
    private lateinit var api: ApiClient

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!prefs.isPaired) { stopSelf(); return START_NOT_STICKY }
        api = ApiClient(prefs.serverUrl)
        startForeground(NOTIF_ID, buildNotification("Ishga tushmoqda…"))
        prefs.serviceRunning = true
        scope.launch { heartbeatLoop() }
        scope.launch { pollLoop() }
        return START_STICKY
    }

    override fun onDestroy() {
        prefs.serviceRunning = false
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun heartbeatLoop() {
        while (scope.isActive) {
            val token = prefs.deviceToken
            if (token != null) {
                try {
                    api.heartbeat(token, batteryPercent())
                    PendingReports.lastHeartbeatAt = System.currentTimeMillis()
                    PendingReports.lastError = null
                } catch (e: Exception) {
                    PendingReports.lastError = e.message
                }
            }
            delay(25_000)
        }
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            val token = prefs.deviceToken
            if (token != null) {
                try {
                    val pending = api.fetchPending(token)
                    PendingReports.lastPollAt = System.currentTimeMillis()
                    PendingReports.lastError = null
                    if (pending.isNotEmpty()) {
                        updateNotification("${pending.size} ta SMS yuborilmoqda…")
                        pending.forEach { sendOne(token, it) }
                        updateNotification("Kutmoqda — ulangan")
                    }
                } catch (e: Exception) {
                    PendingReports.lastError = e.message
                    updateNotification("Ulanish xatosi, qayta urinilmoqda…")
                }
            }
            delay(6_000)
        }
    }

    /** Bitta xabarni haqiqiy SIM orqali yuboradi (kerak bo'lsa bir necha segmentga bo'lib). */
    private fun sendOne(token: String, msg: ApiClient.PendingMessage) {
        try {
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(msg.message)

            if (parts.size <= 1) {
                val pi = resultPendingIntent(msg.jobId, msg.idx, 0)
                PendingReports.register(msg.jobId, msg.idx, 1)
                smsManager.sendTextMessage(msg.phone, null, msg.message, pi, null)
            } else {
                val sentIntents = ArrayList<PendingIntent>(parts.size)
                for (i in parts.indices) sentIntents.add(resultPendingIntent(msg.jobId, msg.idx, i))
                PendingReports.register(msg.jobId, msg.idx, parts.size)
                smsManager.sendMultipartTextMessage(msg.phone, null, parts, sentIntents, null)
            }
        } catch (e: Exception) {
            // SmsManager o'zi chaqirilmasdanoq xato bersa (masalan ruxsat yo'q) — darhol xato deb xabar beramiz
            try { api.report(token, msg.jobId, msg.idx, false, e.message ?: "send xatosi") } catch (_: Exception) {}
            PendingReports.addLog("${msg.phone}: xatolik (${e.message})", false)
        }
    }

    private fun resultPendingIntent(jobId: Long, idx: Int, partIndex: Int): PendingIntent {
        val intent = Intent(this, SmsResultReceiver::class.java).apply {
            action = ACTION_SMS_RESULT
            putExtra(SmsResultReceiver.EXTRA_JOB_ID, jobId)
            putExtra(SmsResultReceiver.EXTRA_IDX, idx)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(this, PendingReports.nextRequestCode(), intent, flags)
    }

    private fun batteryPercent(): Int? {
        return try {
            val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) { null }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "allSMS Sender xizmati", NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Qurilma ulanganini va SMS yuborilayotganini bildiradi" }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val contentPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("allSMS Sender — ${prefs.deviceName}")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setContentIntent(contentPi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        const val CHANNEL_ID = "allsms_sender_service"
        const val NOTIF_ID = 42
        const val ACTION_SMS_RESULT = "uz.allsms.sender.ACTION_SMS_RESULT"
    }
}
