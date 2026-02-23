package quoi.utils

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

object WebUtils {
    fun setupConnection(url: String, timeout: Int = 5000, useCaches: Boolean = true): InputStream {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.setRequestMethod("GET")
        connection.setUseCaches(useCaches)
        connection.setReadTimeout(timeout)
        connection.setConnectTimeout(timeout)
        connection.setDoOutput(true)
        return connection.inputStream
    }
}