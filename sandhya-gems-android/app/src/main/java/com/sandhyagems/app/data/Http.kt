package com.sandhyagems.app.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HttpException(val code: Int) : IOException("HTTP $code")

object Http {
    /** Downloads [url] as text. Call from a background thread. */
    @Throws(IOException::class)
    fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            if (code !in 200..299) throw HttpException(code)
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
