package com.example.engine

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

class LocalWebServer(private val rootDir: File) {
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()

    @Volatile
    var port: Int = 0
        private set

    @Volatile
    var isRunning: Boolean = false
        private set

    fun start(preferredPort: Int = 8765): Int {
        if (isRunning && port > 0) return port
        val socket = runCatching {
            ServerSocket(preferredPort, 50, InetAddress.getByName("127.0.0.1"))
        }.getOrElse {
            ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        }
        serverSocket = socket
        port = socket.localPort
        isRunning = true

        executor.execute {
            while (isRunning && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    executor.execute { handleClient(client) }
                } catch (e: Exception) {
                    if (isRunning) {
                        Log.w("LocalWebServer", "Accept loop ended: ${e.message}")
                    }
                    break
                }
            }
        }
        return port
    }

    fun baseUrl(): String = "http://127.0.0.1:${if (port > 0) port else start()}"

    fun urlFor(relativePath: String): String {
        val cleaned = relativePath.trimStart('/')
        return "${baseUrl()}/$cleaned"
    }

    fun stop() {
        isRunning = false
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun handleClient(client: Socket) {
        client.use { sock ->
            val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
            val requestLine = reader.readLine() ?: return
            while (true) {
                val headerLine = reader.readLine()
                if (headerLine.isNullOrEmpty()) break
            }
            val parts = requestLine.split(" ")
            val rawPath = parts.getOrNull(1) ?: "/"
            val pathNoQuery = rawPath.substringBefore('?')
            val decoded = URLDecoder.decode(pathNoQuery, "UTF-8").trimStart('/')
            val safePath = decoded.replace("..", "")
            var targetFile = if (safePath.isEmpty()) File(rootDir, "index.html") else File(rootDir, safePath)
            if (targetFile.isDirectory) {
                targetFile = File(targetFile, "index.html")
            }

            val out = sock.getOutputStream()
            val rootCanonical = rootDir.canonicalPath
            if (!targetFile.canonicalPath.startsWith(rootCanonical) || !targetFile.exists() || !targetFile.isFile) {
                val body = "<h1>404 Not Found</h1><p>Zenncode Local Server could not find /$safePath</p>".toByteArray()
                val header = buildString {
                    append("HTTP/1.1 404 Not Found\r\n")
                    append("Content-Type: text/html; charset=utf-8\r\n")
                    append("Content-Length: ${body.size}\r\n")
                    append("Connection: close\r\n\r\n")
                }.toByteArray()
                out.write(header)
                out.write(body)
                out.flush()
                return
            }

            val bytes = targetFile.readBytes()
            val mime = guessMimeType(targetFile.name)
            val header = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: $mime\r\n")
                append("Content-Length: ${bytes.size}\r\n")
                append("Cache-Control: no-store\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray()
            out.write(header)
            out.write(bytes)
            out.flush()
        }
    }

    private fun guessMimeType(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> "text/html; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "js", "mjs" -> "application/javascript; charset=utf-8"
            "json" -> "application/json; charset=utf-8"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "md" -> "text/plain; charset=utf-8"
            else -> "text/plain; charset=utf-8"
        }
    }
}
