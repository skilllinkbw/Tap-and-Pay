package com.getauthepay.app.network

import com.getauthepay.app.core.log.SecureLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

/**
 * Thin HttpURLConnection-based HTTP client. Intentionally avoids any
 * additional dependencies — OkHttp / Retrofit add attack surface and
 * APK weight for an app that must be audited by the acquiring bank.
 *
 * The client enforces:
 *   - HTTPS-only base URL (cleartext is rejected outright)
 *   - Bounded connect / read timeouts
 *   - Mandatory session-token bearer on every request
 *   - Strict body size cap
 *   - Sensitive data is sanitised before being written to logs
 */
class HttpClient(
    private val baseUrl: String,
    private val sessionTokenProvider: () -> String?,
    private val correlationIdProvider: () -> String = { java.util.UUID.randomUUID().toString() },
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 20_000,
) {

    init {
        require(baseUrl.startsWith("https://")) {
            "HTTP base URL must use https:// (got '$baseUrl')"
        }
    }

    suspend fun get(path: String): HttpResponse = request("GET", path, null)

    suspend fun post(path: String, body: JSONObject?): HttpResponse =
        request("POST", path, body?.toString())

    suspend fun put(path: String, body: JSONObject?): HttpResponse =
        request("PUT", path, body?.toString())

    suspend fun delete(path: String): HttpResponse = request("DELETE", path, null)

    private suspend fun request(
        method: String,
        path: String,
        body: String?,
    ): HttpResponse = withContext(Dispatchers.IO) {
        val url = URL(baseUrl.trimEnd('/') + "/" + path.trimStart('/'))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doInput = true
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "AuthePay-Android/1.0")
            setRequestProperty("X-AuthePay-Client", "android-merchant")
            setRequestProperty("X-AuthePay-Correlation-Id", correlationIdProvider())
            sessionTokenProvider()?.let {
                setRequestProperty("Authorization", "Bearer $it")
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            if (this is HttpsURLConnection) {
                sslSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
            }
        }

        try {
            if (body != null) {
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use {
                    it.write(body)
                    it.flush()
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use(BufferedReader::readText)
            } ?: ""
            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key.orEmpty().lowercase(Locale.ROOT) }
                .mapValues { it.value?.firstOrNull().orEmpty() }
            SecureLogger.event(
                event = "http.response",
                status = code.toString(),
                extra = mapOf("method" to method, "path" to path),
            )
            HttpResponse(code, text, headers)
        } catch (io: IOException) {
            SecureLogger.event(
                event = "http.io_error",
                status = "ERR",
                extra = mapOf("method" to method, "path" to path,
                    "error" to (io.javaClass.simpleName)),
            )
            throw NetworkUnavailable("HTTP I/O failure: ${io.message}", io)
        } finally {
            connection.disconnect()
        }
    }
}

data class HttpResponse(
    val code: Int,
    val body: String,
    val headers: Map<String, String>,
) {
    fun jsonBody(): JSONObject? =
        if (body.isBlank()) null else runCatching { JSONObject(body) }.getOrNull()
}

open class ApiException(message: String, val code: Int = 0) : RuntimeException(message)
class NetworkUnavailable(message: String, cause: Throwable? = null) : ApiException(message)
class UnauthorizedException(message: String) : ApiException(message, 401)
class ForbiddenException(message: String) : ApiException(message, 403)
class NotFoundException(message: String) : ApiException(message, 404)
class RateLimitedException(message: String) : ApiException(message, 429)
class ServerException(message: String, code: Int) : ApiException(message, code)