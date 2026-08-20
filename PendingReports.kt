package uz.allsms.sender

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bitta SMS bir necha "segment"ga bo'linib yuborilishi mumkin (uzun matn yoki kirill harflar).
 * Har bir segment alohida natija qaytaradi (Android radiosidan) — shu tracker hammasi
 * qaytgandan keyingina serverga BITTA yakuniy natija (report) yuboradi.
 */
object PendingReports {
    data class Tracker(val totalParts: Int, var remaining: Int, var anyFailed: Boolean, var lastError: String?)

    private val trackers = ConcurrentHashMap<String, Tracker>()
    private val requestCodeSeq = AtomicInteger(1000)

    fun key(jobId: Long, idx: Int) = "$jobId:$idx"

    fun register(jobId: Long, idx: Int, totalParts: Int) {
        trackers[key(jobId, idx)] = Tracker(totalParts, totalParts, false, null)
    }

    /** @return true bo'lsa — shu SMSning barcha qismlari yakunlandi (report vaqti keldi) */
    fun recordPartResult(jobId: Long, idx: Int, success: Boolean, error: String?): Boolean {
        val t = trackers[key(jobId, idx)] ?: return false
        synchronized(t) {
            t.remaining -= 1
            if (!success) { t.anyFailed = true; t.lastError = error }
            return t.remaining <= 0
        }
    }

    fun finish(jobId: Long, idx: Int): Tracker? = trackers.remove(key(jobId, idx))

    fun nextRequestCode(): Int = requestCodeSeq.incrementAndGet()

    // ---- oddiy statistikalar/jurnal, MainActivity ko'rsatib turadi ----
    data class LogEntry(val time: Long, val text: String, val ok: Boolean)

    private val log = Collections.synchronizedList(ArrayList<LogEntry>())
    private const val LOG_LIMIT = 80

    fun addLog(text: String, ok: Boolean) {
        log.add(0, LogEntry(System.currentTimeMillis(), text, ok))
        while (log.size > LOG_LIMIT) log.removeAt(log.size - 1)
    }

    fun snapshotLog(): List<LogEntry> = synchronized(log) { ArrayList(log) }

    @Volatile var lastPollAt: Long = 0L
    @Volatile var lastHeartbeatAt: Long = 0L
    @Volatile var lastError: String? = null
}
