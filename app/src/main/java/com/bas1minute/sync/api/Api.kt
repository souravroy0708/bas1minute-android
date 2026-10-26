package com.bas1minute.sync.api

import android.content.Context
import com.bas1minute.sync.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** code == null means the website couldn't be reached (offline or down). */
data class ApiReply(val code: Int?, val json: JSONObject?) {
    val ok: Boolean get() = code != null && code in 200..299
    val error: String? get() = json?.optString("error")?.takeIf { it.isNotBlank() }
    /** First message per field, e.g. {"mobile": "Enter a 10-digit Indian mobile number."} */
    val fields: Map<String, String>
        get() {
            val f = json?.optJSONObject("fields") ?: return emptyMap()
            val out = HashMap<String, String>()
            for (k in f.keys()) f.optJSONArray(k)?.optString(0)?.let { out[k] = it }
            return out
        }
}

class Api(context: Context) {
    private val prefs = Prefs(context)

    suspend fun get(path: String): ApiReply = request("GET", path, null)
    suspend fun post(path: String, body: JSONObject = JSONObject()): ApiReply = request("POST", path, body)

    private suspend fun request(method: String, path: String, body: JSONObject?): ApiReply = withContext(Dispatchers.IO) {
        try {
            val conn = URL("${prefs.serverUrl}/api/v1/$path").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 12_000
                conn.readTimeout = 20_000
                conn.setRequestProperty("Accept", "application/json")
                if (prefs.token.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer ${prefs.token}")
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                val json = try { if (text.isBlank()) null else JSONObject(text) } catch (e: Exception) { null }
                ApiReply(code, json)
            } finally {
                conn.disconnect()
            }
        } catch (e: IOException) {
            ApiReply(null, null)
        }
    }
}
