package uz.allsms.sender

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import kotlin.concurrent.thread

/**
 * SmsManager har bir segmentni jo'natgach shu receiverga natija (resultCode) qaytaradi.
 * Ko'p segmentli SMS uchun PendingReports segmentlarni sanab, hammasi qaytgach
 * BITTA yakuniy natijani /api/device/report ga yuboradi.
 */
class SmsResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getLongExtra(EXTRA_JOB_ID, -1L)
        val idx = intent.getIntExtra(EXTRA_IDX, -1)
        if (jobId <= 0 || idx < 0) return

        val (success, error) = when (resultCode) {
            android.app.Activity.RESULT_OK -> true to null
            SmsManager.RESULT_ERROR_NO_SERVICE -> false to "Tarmoq yo'q (RESULT_ERROR_NO_SERVICE)"
            SmsManager.RESULT_ERROR_RADIO_OFF -> false to "Radio o'chiq (RESULT_ERROR_RADIO_OFF)"
            SmsManager.RESULT_ERROR_NULL_PDU -> false to "PDU xatosi"
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> false to "Umumiy xatolik"
            else -> false to "Noma'lum xato (kod $resultCode)"
        }

        val finished = PendingReports.recordPartResult(jobId, idx, success, error)
        if (!finished) return
        val tracker = PendingReports.finish(jobId, idx) ?: return

        val pendingResult = goAsync()
        thread {
            try {
                val prefs = Prefs(context)
                val token = prefs.deviceToken
                if (token != null) {
                    val api = ApiClient(prefs.serverUrl)
                    val ok = !tracker.anyFailed
                    api.report(token, jobId, idx, ok, tracker.lastError)
                    PendingReports.addLog("Job #$jobId (#$idx): ${if (ok) "yuborildi" else "xatolik — ${tracker.lastError}"}", ok)
                }
            } catch (e: Exception) {
                PendingReports.addLog("Job #$jobId (#$idx): report yuborilmadi (${e.message})", false)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_JOB_ID = "job_id"
        const val EXTRA_IDX = "idx"
    }
}
