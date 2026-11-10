package com.notegraph.app.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

data class LanHostState(
    val isRunning: Boolean = false,
    val port: Int = 8765,
    val pin: String = "4829",
    val addresses: List<String> = emptyList(),
    val statusMessage: String = "LAN server stopped"
)

/**
 * Pure Kotlin / stdlib Direct Wi-Fi LAN Sync Engine (zero external HTTP library deps).
 * Works bidirectionally with NoteGraph Desktop (Windows & macOS) and other Android devices
 * over local Wi-Fi without internet or cloud accounts.
 */
@Singleton
class LanSyncManager @Inject constructor(
    private val vaultSyncManager: VaultSyncManager,
    @ApplicationContext private val appContext: Context
) {
    companion object {
        const val DEFAULT_PORT = 8765
        private const val MAX_BODY_BYTES = 32 * 1024 * 1024
        private const val PREFS_NAME = "notegraph_sync_prefs"
        private const val KEY_LAN_PIN = "lan_sync_pin"
        private const val KEY_LAST_PEER = "lan_last_peer"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    private val _hostState = MutableStateFlow(
        LanHostState(
            isRunning = false,
            port = DEFAULT_PORT,
            pin = getOrCreatePin(),
            addresses = findLocalIpv4Addresses(),
            statusMessage = "LAN server stopped"
        )
    )
    val hostState: StateFlow<LanHostState> = _hostState.asStateFlow()

    fun getLastPeerAddress(): String = prefs.getString(KEY_LAST_PEER, "").orEmpty()

    fun regeneratePin(): String {
        val nextPin = (1000 + SecureRandom().nextInt(9000)).toString()
        prefs.edit().putString(KEY_LAN_PIN, nextPin).apply()
        _hostState.value = _hostState.value.copy(pin = nextPin)
        return nextPin
    }

    fun startHostServer(preferredPort: Int = DEFAULT_PORT) {
        if (_hostState.value.isRunning && serverSocket != null && serverSocket?.isClosed == false) {
            return
        }
        stopHostServer()
        val pin = getOrCreatePin()
        val addrs = findLocalIpv4Addresses()

        acceptJob = scope.launch {
            var boundSocket: ServerSocket? = null
            var actualPort = preferredPort
            for (candidatePort in listOf(preferredPort, 8766, 8767, 0)) {
                try {
                    boundSocket = ServerSocket(candidatePort).apply {
                        reuseAddress = true
                    }
                    actualPort = boundSocket.localPort
                    break
                } catch (_: Exception) {
                    boundSocket = null
                }
            }

            if (boundSocket == null) {
                _hostState.value = LanHostState(
                    isRunning = false,
                    port = preferredPort,
                    pin = pin,
                    addresses = addrs,
                    statusMessage = "Failed to bind Wi-Fi sync port"
                )
                return@launch
            }

            serverSocket = boundSocket
            val freshAddrs = findLocalIpv4Addresses()
            _hostState.value = LanHostState(
                isRunning = true,
                port = actualPort,
                pin = pin,
                addresses = freshAddrs,
                statusMessage = if (freshAddrs.isNotEmpty()) {
                    "Listening on ${freshAddrs.first()}:$actualPort"
                } else {
                    "Listening on port $actualPort"
                }
            )

            while (isActive && !boundSocket.isClosed) {
                val client = try {
                    boundSocket.accept()
                } catch (_: Exception) {
                    break
                }
                launch {
                    handleClientSocket(client)
                }
            }
        }
    }

    fun stopHostServer() {
        acceptJob?.cancel()
        acceptJob = null
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        _hostState.value = _hostState.value.copy(
            isRunning = false,
            addresses = findLocalIpv4Addresses(),
            statusMessage = "LAN server stopped"
        )
    }

    suspend fun syncWithPeer(rawHostPort: String, pin: String): Result<SyncSummary> =
        withContext(Dispatchers.IO) {
            val cleaned = rawHostPort.trim()
            if (cleaned.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Enter peer IP:Port (e.g. 192.168.1.42:8765)"))
            }
            val (host, port) = parseHostAndPort(cleaned)
            prefs.edit().putString(KEY_LAST_PEER, "$host:$port").apply()

            var conn: HttpURLConnection? = null
            try {
                val localPkg = vaultSyncManager.buildLocalPackage()
                val reqBytes = SyncEngine.codec.encodeToString(localPkg).toByteArray(Charsets.UTF_8)

                val url = URL("http://$host:$port/api/sync")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 5000
                    readTimeout = 25000
                    doInput = true
                    doOutput = true
                    useCaches = false
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("x-notegraph-pin", pin.trim())
                    setFixedLengthStreamingMode(reqBytes.size)
                }

                conn.outputStream.use { out ->
                    out.write(reqBytes)
                    out.flush()
                }

                val code = conn.responseCode
                val respBody = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

                if (code == 401 || code == 403) {
                    return@withContext Result.failure(IllegalStateException("PIN rejected by $host:$port"))
                }
                if (code !in 200..299) {
                    return@withContext Result.failure(
                        IllegalStateException("Peer returned HTTP $code${if (respBody.isNotBlank()) ": ${respBody.take(120)}" else ""}")
                    )
                }

                val remotePkg = SyncEngine.parsePackageOrLegacy(respBody)
                val (summary, _) = vaultSyncManager.applyRemotePackage(remotePkg)
                Result.success(summary)
            } catch (e: Exception) {
                Result.failure(IllegalStateException(e.message ?: "Unable to connect to $host:$port"))
            } finally {
                try {
                    conn?.disconnect()
                } catch (_: Exception) {
                }
            }
        }

    private suspend fun handleClientSocket(socket: Socket) {
        try {
            socket.soTimeout = 20000
            val inp = BufferedInputStream(socket.getInputStream())
            val req = parseHttpRequest(inp)
            if (req == null) {
                writeHttpJson(socket, 400, """{"error":"Malformed HTTP request"}""")
                return
            }

            if (req.method == "OPTIONS") {
                writeCorsPreflight(socket)
                return
            }

            val pathOnly = req.path.substringBefore('?')
            if (req.method == "GET" && pathOnly == "/api/ping") {
                val snap = vaultSyncManager.prefsState.value
                val pingJson = """{"ok":true,"app":"NoteGraph","platform":"android","deviceName":"${escapeJsonString(snap.deviceName)}","port":${_hostState.value.port}}"""
                writeHttpJson(socket, 200, pingJson)
                return
            }

            if (req.method == "POST" && pathOnly == "/api/sync") {
                val expectedPin = _hostState.value.pin.trim()
                val suppliedPin = req.headers["x-notegraph-pin"]?.trim().orEmpty()
                if (expectedPin.isNotEmpty() && suppliedPin != expectedPin) {
                    writeHttpJson(socket, 401, """{"error":"Invalid sync PIN"}""")
                    return
                }

                val bodyStr = req.body.toString(Charsets.UTF_8)
                val remotePkg = SyncEngine.parsePackageOrLegacy(bodyStr)
                val (summary, mergedPkg) = vaultSyncManager.applyRemotePackage(remotePkg)
                val mergedJson = SyncEngine.codec.encodeToString(mergedPkg)
                writeHttpJson(socket, 200, mergedJson)
                _hostState.value = _hostState.value.copy(
                    statusMessage = "Synced ${summary.notesMerged + summary.newNotesAdded} notes with ${summary.remoteDevice}"
                )
                return
            }

            writeHttpJson(socket, 404, """{"error":"Not found"}""")
        } catch (e: Exception) {
            try {
                writeHttpJson(socket, 500, """{"error":"${escapeJsonString(e.message ?: "Sync server error")}"}""")
            } catch (_: Exception) {
            }
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private data class HttpReq(
        val method: String,
        val path: String,
        val headers: Map<String, String>,
        val body: ByteArray
    )

    private fun parseHttpRequest(inp: BufferedInputStream): HttpReq? {
        val headerBuf = ByteArrayOutputStream(1024)
        var state = 0 // 1=\r, 2=\r\n, 3=\r\n\r, 4=\r\n\r\n
        while (headerBuf.size() < 32768) {
            val b = inp.read()
            if (b == -1) return null
            headerBuf.write(b)
            state = when {
                state == 0 && b == '\r'.code -> 1
                state == 1 && b == '\n'.code -> 2
                state == 2 && b == '\r'.code -> 3
                state == 3 && b == '\n'.code -> 4
                b == '\r'.code -> 1
                else -> 0
            }
            if (state == 4) break
        }
        if (state != 4) return null

        val headerText = headerBuf.toByteArray().toString(Charsets.ISO_8859_1)
        val lines = headerText.split("\r\n").filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val requestParts = lines[0].split(' ')
        if (requestParts.size < 2) return null
        val method = requestParts[0].uppercase()
        val path = requestParts[1]

        val headers = HashMap<String, String>()
        for (i in 1 until lines.size) {
            val colon = lines[i].indexOf(':')
            if (colon > 0) {
                val k = lines[i].substring(0, colon).trim().lowercase()
                val v = lines[i].substring(colon + 1).trim()
                headers[k] = v
            }
        }

        val transferEnc = headers["transfer-encoding"].orEmpty().lowercase()
        val bodyBytes = when {
            transferEnc.contains("chunked") -> readChunkedBody(inp)
            else -> {
                val contentLen = headers["content-length"]?.toIntOrNull()?.coerceIn(0, MAX_BODY_BYTES) ?: 0
                if (contentLen > 0) readExactBytes(inp, contentLen) else ByteArray(0)
            }
        }
        return HttpReq(method, path, headers, bodyBytes)
    }

    private fun readExactBytes(inp: InputStream, count: Int): ByteArray {
        val out = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val read = inp.read(out, offset, count - offset)
            if (read <= 0) break
            offset += read
        }
        return if (offset == count) out else out.copyOf(offset)
    }

    private fun readChunkedBody(inp: BufferedInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (out.size() < MAX_BODY_BYTES) {
            val line = readAsciiLine(inp) ?: break
            val sizeHex = line.substringBefore(';').trim()
            val chunkSize = sizeHex.toIntOrNull(16) ?: break
            if (chunkSize <= 0) {
                // Consume trailing headers/CRLF if present
                readAsciiLine(inp)
                break
            }
            val capped = minOf(chunkSize, MAX_BODY_BYTES - out.size())
            val chunk = readExactBytes(inp, capped)
            out.write(chunk)
            // Consume CRLF after chunk
            readAsciiLine(inp)
        }
        return out.toByteArray()
    }

    private fun readAsciiLine(inp: BufferedInputStream): String? {
        val buf = ByteArrayOutputStream(64)
        var prevCr = false
        while (buf.size() < 4096) {
            val b = inp.read()
            if (b == -1) return if (buf.size() == 0) null else buf.toByteArray().toString(Charsets.ISO_8859_1)
            if (prevCr && b == '\n'.code) {
                val bytes = buf.toByteArray()
                return String(bytes, 0, bytes.size - 1, Charsets.ISO_8859_1)
            }
            if (b == '\n'.code) {
                return buf.toByteArray().toString(Charsets.ISO_8859_1)
            }
            buf.write(b)
            prevCr = (b == '\r'.code)
        }
        return buf.toByteArray().toString(Charsets.ISO_8859_1)
    }

    private fun writeHttpJson(socket: Socket, status: Int, jsonBody: String) {
        val reason = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            404 -> "Not Found"
            else -> "Internal Server Error"
        }
        val bodyBytes = jsonBody.toByteArray(Charsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ").append(bodyBytes.size).append("\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(Charsets.ISO_8859_1)

        val out = socket.getOutputStream()
        out.write(headers)
        out.write(bodyBytes)
        out.flush()
    }

    private fun writeCorsPreflight(socket: Socket) {
        val headers = (
            "HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Headers: Content-Type, x-notegraph-pin\r\n" +
                "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
                "Connection: close\r\n\r\n"
            ).toByteArray(Charsets.ISO_8859_1)
        val out = socket.getOutputStream()
        out.write(headers)
        out.flush()
    }

    private fun parseHostAndPort(input: String): Pair<String, Int> {
        var s = input.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
        if (s.startsWith("[")) {
            val closeIdx = s.indexOf(']')
            if (closeIdx > 0) {
                val host = s.substring(0, closeIdx + 1)
                val portStr = s.substring(closeIdx + 1).removePrefix(":")
                return host to (portStr.toIntOrNull() ?: DEFAULT_PORT)
            }
        }
        val colonIdx = s.lastIndexOf(':')
        return if (colonIdx > 0) {
            val host = s.substring(0, colonIdx).trim()
            val port = s.substring(colonIdx + 1).trim().toIntOrNull() ?: DEFAULT_PORT
            host to port
        } else {
            s to DEFAULT_PORT
        }
    }

    private fun getOrCreatePin(): String {
        val existing = prefs.getString(KEY_LAN_PIN, null)
        if (!existing.isNullOrBlank()) return existing
        val created = (1000 + SecureRandom().nextInt(9000)).toString()
        prefs.edit().putString(KEY_LAN_PIN, created).apply()
        return created
    }

    private fun findLocalIpv4Addresses(): List<String> {
        val preferred = mutableListOf<String>()
        val fallback = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback || iface.isVirtual) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        val ip = addr.hostAddress ?: continue
                        val name = iface.name.lowercase()
                        if (name.contains("wlan") || name.contains("wifi") || name.contains("ap")) {
                            preferred.add(ip)
                        } else {
                            fallback.add(ip)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return (preferred + fallback).distinct()
    }

    private fun escapeJsonString(raw: String): String = raw
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "")
}
