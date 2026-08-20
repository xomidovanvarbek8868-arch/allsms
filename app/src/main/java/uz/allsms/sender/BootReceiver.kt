package uz.allsms.sender

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Telefon qayta yuklangach, avval ulangan va yoqilgan bo'lsa, xizmatni avtomatik qayta ishga tushiradi. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = Prefs(context)
        if (prefs.isPaired && prefs.serviceRunning) {
            val svc = Intent(context, SenderService::class.java)
            ContextCompat.startForegroundService(context, svc)
        }
    }
}
