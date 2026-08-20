package uz.allsms.sender

import android.content.Context
import android.content.SharedPreferences

/**
 * Barcha lokal holat shu yerda saqlanadi: server manzili, ulangan qurilma tokeni va nomi.
 * Ilova o'chib-yonsa ham (yoki telefon qayta yuklansa) shu qiymatlar orqali darhol xizmatga qaytadi.
 */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("allsms_sender_prefs", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString(KEY_SERVER_URL, "") ?: ""
        set(value) = sp.edit().putString(KEY_SERVER_URL, value.trim().trimEnd('/')).apply()

    var deviceId: Long
        get() = sp.getLong(KEY_DEVICE_ID, -1L)
        set(value) = sp.edit().putLong(KEY_DEVICE_ID, value).apply()

    var deviceToken: String?
        get() = sp.getString(KEY_DEVICE_TOKEN, null)
        set(value) = sp.edit().putString(KEY_DEVICE_TOKEN, value).apply()

    var deviceName: String
        get() = sp.getString(KEY_DEVICE_NAME, "") ?: ""
        set(value) = sp.edit().putString(KEY_DEVICE_NAME, value).apply()

    var serviceRunning: Boolean
        get() = sp.getBoolean(KEY_SERVICE_RUNNING, false)
        set(value) = sp.edit().putBoolean(KEY_SERVICE_RUNNING, value).apply()

    val isPaired: Boolean
        get() = !deviceToken.isNullOrBlank() && deviceId > 0 && serverUrl.isNotBlank()

    fun clearPairing() {
        sp.edit()
            .remove(KEY_DEVICE_ID)
            .remove(KEY_DEVICE_TOKEN)
            .remove(KEY_DEVICE_NAME)
            .putBoolean(KEY_SERVICE_RUNNING, false)
            .apply()
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_SERVICE_RUNNING = "service_running"
    }
}
