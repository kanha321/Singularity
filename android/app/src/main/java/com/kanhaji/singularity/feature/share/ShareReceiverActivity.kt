package com.kanhaji.singularity.feature.share

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.kanhaji.singularity.service.AppBridgeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class ShareReceiverActivity : Activity() {

    companion object {
        private const val TAG = "ShareReceiver"
        private val URL_REGEX = Regex("""(?i)\b(?:https?|ftp)://[^\s<>]+|\b[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}(?:/[^\s<>]*)?""")

        fun extractUrl(text: String): String? {
            val match = URL_REGEX.find(text)
            if (match != null) {
                val found = match.value
                return if (found.startsWith("http://", ignoreCase = true) ||
                    found.startsWith("https://", ignoreCase = true) ||
                    found.startsWith("ftp://", ignoreCase = true)
                ) {
                    found
                } else {
                    "https://$found"
                }
            }
            return if (text.startsWith("http://") || text.startsWith("https://")) text.trim() else null
        }

        fun dispatchUrlToDesktop(url: String) {
            val serverBase = AppBridgeService.fileServerUrl.value
            if (serverBase.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val endpoint = "${serverBase.trimEnd('/')}/api/desktop/open"
                        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 2000
                            readTimeout = 2000
                            doOutput = true
                            setRequestProperty("Content-Type", "application/json")
                        }
                        val payload = JSONObject().put("url", url).toString()
                        OutputStreamWriter(conn.outputStream).use { it.write(payload) }
                        val code = conn.responseCode
                        Log.i(TAG, "Direct HTTP dispatch to $endpoint returned $code")
                    } catch (e: Exception) {
                        Log.d(TAG, "Direct HTTP failed, falling back to local bridge: ${e.message}")
                        AppBridgeService.requestOpenUrlOnDesktop(url)
                    }
                }
            } else {
                AppBridgeService.requestOpenUrlOnDesktop(url)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intentAction = intent?.action
        val intentType = intent?.type

        if (Intent.ACTION_SEND == intentAction && intentType != null && intentType.startsWith("text/")) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val targetUrl = extractUrl(sharedText) ?: sharedText.trim()

            if (targetUrl.isNotEmpty()) {
                Toast.makeText(this, "Opening on Desktop 💻", Toast.LENGTH_SHORT).show()
                dispatchUrlToDesktop(targetUrl)
            } else {
                Toast.makeText(this, "No valid link found to share", Toast.LENGTH_SHORT).show()
            }
        }

        finish()
    }
}
