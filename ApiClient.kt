package uz.allsms.sender

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

/**
 * allSMS backend API bilan ishlaydi.
 */
class ApiClient(private val baseUrl: String) {

    class ApiException(
        message: String,
        val httpCode: Int = -1
    ) : IOException(message)

    private fun request(
        path: String,
        method: String,
        token: String? = null,
        body: JSONObject? = null
    ): JSONObject {

        val url = URL(baseUrl.trimEnd('/') + path)

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000

            setRequestProperty(
                "Content-Type",
                "application/json; charset=utf-8"
            )

            setRequestProperty(
                "Accept",
                "application/json"
            )

            if (token != null) {
                setRequestProperty(
                    "Authorization",
                    "Bearer $token"
                )
            }

            if (this is HttpsURLConnection) {
                // Standart tizim TLS sozlamalari ishlatiladi.
            }
        }

        try {
            if (body != null) {
                conn.doOutput = true

                OutputStreamWriter(
                    conn.outputStream,
                    StandardCharsets.UTF_8
                ).use {
                    it.write(body.toString())
                }
            }

            val code = conn.responseCode

            val stream =
                if (code in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream
                }

            val text =
                stream
                    ?.bufferedReader(StandardCharsets.UTF_8)
                    ?.use { it.readText() }
                    ?: "{}"

            val json =
                try {
                    JSONObject(text)
                } catch (e: Exception) {
                    JSONObject()
                }

            if (code !in 200..299) {
                val msg = json.optString(
                    "error",
                    "Server xatosi (HTTP $code)"
                )

                throw ApiException(msg, code)
            }

            return json

        } finally {
            conn.disconnect()
        }
    }

    fun confirmPairing(
        pairingCode: String,
        deviceName: String
    ): JSONObject {

        val body = JSONObject()
            .put("pairing_code", pairingCode)
            .put("device_name", deviceName)

        return request(
            "/api/devices/confirm",
            "POST",
            null,
            body
        )
    }

    fun heartbeat(
        token: String,
        batteryPercent: Int?
    ): JSONObject {

        val body = JSONObject()

        if (batteryPercent != null) {
            body.put("battery", batteryPercent)
        }

        return request(
            "/api/device/heartbeat",
            "POST",
            token,
            body
        )
    }

    fun fetchPending(
        token: String
    ): List<PendingMessage> {

        val json = request(
            "/api/device/pending",
            "GET",
            token,
            null
        )

        val arr: JSONArray =
            json.optJSONArray("messages") ?: JSONArray()

        val out = ArrayList<PendingMessage>(arr.length())

        for (i in 0 until arr.length()) {

            val o = arr.getJSONObject(i)

            out.add(
                PendingMessage(
                    jobId = o.getLong("job_id"),
                    idx = o.getInt("idx"),
                    phone = o.getString("phone"),
                    name = o.optString("name", null),
                    message = o.getString("message")
                )
            )
        }

        return out
    }

    fun report(
        token: String,
        jobId: Long,
        idx: Int,
        success: Boolean,
        error: String? = null
    ) {

        val body = JSONObject()
            .put("job_id", jobId)
            .put("idx", idx)
            .put(
                "status",
                if (success) {
                    "yuborildi"
                } else {
                    "xatolik"
                }
            )

        if (!success && error != null) {
            body.put("error", error)
        }

        request(
            "/api/device/report",
            "POST",
            token,
            body
        )
    }

    fun unpair(token: String) {
        request(
            "/api/device/unpair",
            "POST",
            token,
            JSONObject()
        )
    }

    data class PendingMessage(
        val jobId: Long,
        val idx: Int,
        val phone: String,
        val name: String?,
        val message: String
    )
}
