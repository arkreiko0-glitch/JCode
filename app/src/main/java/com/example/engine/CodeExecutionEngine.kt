package com.example.engine

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.WorkspaceFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class ExecutionResult(
    val success: Boolean,
    val output: String,
    val durationMs: Long
)

class CodeExecutionEngine(
    private val context: Context,
    private val fileManager: WorkspaceFileManager
) {
    private val pythonInterpreter = MiniPythonInterpreter()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun runCode(relativePath: String, content: String, languageId: String): ExecutionResult {
        val start = System.currentTimeMillis()
        return when (languageId) {
            "javascript", "typescript" -> {
                val jsCode = if (languageId == "typescript") stripBasicTypeScript(content) else content
                val out = runJavascriptInWebView(jsCode)
                val dur = System.currentTimeMillis() - start
                ExecutionResult(!out.startsWith("[JS Error]"), out, dur)
            }
            "python" -> withContext(Dispatchers.Default) {
                val res = pythonInterpreter.execute(content)
                val dur = System.currentTimeMillis() - start
                res.fold(
                    onSuccess = { ExecutionResult(true, it.ifEmpty { "(Program exited with code 0)" }, dur) },
                    onFailure = { ExecutionResult(false, it.message ?: "Python error", dur) }
                )
            }
            "sql" -> withContext(Dispatchers.IO) {
                runSqlScript(content, start)
            }
            "json" -> withContext(Dispatchers.Default) {
                val dur = System.currentTimeMillis() - start
                runCatching {
                    val t = content.trim()
                    if (t.startsWith("[")) JSONArray(t).toString(2) else JSONObject(t).toString(2)
                }.fold(
                    onSuccess = { ExecutionResult(true, "Valid JSON ($relativePath):\n$it", dur) },
                    onFailure = { ExecutionResult(false, "JSON Error: ${it.message}", dur) }
                )
            }
            else -> ExecutionResult(
                true,
                "Preview/lint active for '$relativePath' ($languageId). Use Built-in Browser for web files or Terminal for shell commands.",
                System.currentTimeMillis() - start
            )
        }
    }

    private fun stripBasicTypeScript(ts: String): String {
        return ts
            .replace(Regex(":\\s*(string|number|boolean|any|void|unknown|never)\\b"), "")
            .replace(Regex("interface\\s+\\w+\\s*\\{[^}]*\\}"), "")
            .replace(Regex("type\\s+\\w+\\s*=[^;]+;"), "")
    }

    private suspend fun runJavascriptInWebView(jsCode: String): String = withContext(Dispatchers.Main) {
        withTimeoutOrNull(5000L) {
            suspendCancellableCoroutine { cont ->
                val logs = mutableListOf<String>()
                val webView = WebView(context).apply {
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        settings.safeBrowsingEnabled = false
                    }
                    settings.javaScriptEnabled = true
                }

                fun cleanupWebView() {
                    Handler(Looper.getMainLooper()).post {
                        runCatching {
                            webView.removeJavascriptInterface("ZennBridge")
                            webView.stopLoading()
                            webView.loadUrl("about:blank")
                            webView.destroy()
                        }
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: RenderProcessGoneDetail?
                    ): Boolean {
                        cleanupWebView()
                        if (cont.isActive) {
                            cont.resume("[JS Error] WebView renderer process terminated unexpectedly; please re-run.")
                        }
                        return true
                    }
                }

                val bridge = object {
                    @JavascriptInterface
                    fun log(level: String, msg: String) {
                        val prefix = if (level == "LOG") "" else "[$level] "
                        logs.add("$prefix$msg")
                    }
                }
                webView.addJavascriptInterface(bridge, "ZennBridge")

                cont.invokeOnCancellation { cleanupWebView() }

                val wrappedScript = """
                    (function() {
                      const _fmt = (a) => (typeof a === 'object' ? JSON.stringify(a) : String(a));
                      console.log = (...args) => ZennBridge.log('LOG', args.map(_fmt).join(' '));
                      console.warn = (...args) => ZennBridge.log('WARN', args.map(_fmt).join(' '));
                      console.error = (...args) => ZennBridge.log('ERROR', args.map(_fmt).join(' '));
                      try {
                        const __result = (function() {
                          $jsCode
                        })();
                        if (__result !== undefined) {
                          return "=> " + _fmt(__result);
                        }
                        return "OK";
                      } catch (err) {
                        return "ERR:" + (err && err.stack ? err.stack : String(err));
                      }
                    })();
                """.trimIndent()

                webView.evaluateJavascript(wrappedScript) { rawResult ->
                    val cleaned = rawResult
                        ?.trim()
                        ?.removeSurrounding("\"")
                        ?.replace("\\n", "\n")
                        ?.replace("\\\"", "\"")
                        ?: "OK"
                    cleanupWebView()

                    val outputText = buildString {
                        if (logs.isNotEmpty()) {
                            append(logs.joinToString("\n"))
                        }
                        if (cleaned.startsWith("ERR:")) {
                            if (isNotEmpty()) append('\n')
                            append("[JS Error] ").append(cleaned.removePrefix("ERR:"))
                        } else if (cleaned != "OK" && cleaned != "null") {
                            if (isNotEmpty()) append('\n')
                            append(cleaned)
                        }
                        if (isEmpty()) append("(Executed cleanly with no console output)")
                    }
                    if (cont.isActive) cont.resume(outputText)
                }
            }
        } ?: "[JS Error] Execution timed out after 5s"
    }

    private fun runSqlScript(sql: String, start: Long): ExecutionResult {
        val db = SQLiteDatabase.create(null)
        val out = StringBuilder()
        return try {
            val statements = sql
                .lines()
                .filterNot { it.trim().startsWith("--") }
                .joinToString("\n")
                .split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            for (stmt in statements) {
                val upper = stmt.uppercase()
                if (upper.startsWith("SELECT") || upper.startsWith("PRAGMA") || upper.startsWith("WITH")) {
                    db.rawQuery(stmt, null).use { cursor ->
                        val cols = cursor.columnNames
                        val rows = mutableListOf<List<String>>()
                        while (cursor.moveToNext()) {
                            rows.add((0 until cols.size).map { idx -> cursor.getString(idx) ?: "NULL" })
                        }
                        out.append(formatAsciiTable(cols.toList(), rows)).append('\n')
                    }
                } else {
                    db.execSQL(stmt)
                    out.append("OK: ${stmt.lines().first().take(60)}").append('\n')
                }
            }
            ExecutionResult(true, out.toString().trimEnd().ifEmpty { "SQL script executed." }, System.currentTimeMillis() - start)
        } catch (e: Exception) {
            ExecutionResult(false, "SQLite Error: ${e.message}", System.currentTimeMillis() - start)
        } finally {
            db.close()
        }
    }

    private fun formatAsciiTable(cols: List<String>, rows: List<List<String>>): String {
        if (cols.isEmpty()) return "(0 columns)"
        val widths = cols.mapIndexed { idx, col ->
            val maxRow = rows.maxOfOrNull { it.getOrElse(idx) { "" }.length } ?: 0
            maxOf(col.length, maxRow).coerceAtMost(28)
        }
        val sep = "+" + widths.joinToString("+") { "-".repeat(it + 2) } + "+"
        val sb = StringBuilder()
        sb.append(sep).append('\n')
        sb.append("|").append(cols.mapIndexed { i, c -> " ${c.take(widths[i]).padEnd(widths[i])} " }.joinToString("|")).append("|\n")
        sb.append(sep).append('\n')
        for (row in rows) {
            sb.append("|").append(row.mapIndexed { i, v -> " ${v.take(widths[i]).padEnd(widths[i])} " }.joinToString("|")).append("|\n")
        }
        sb.append(sep).append(" (${rows.size} rows)")
        return sb.toString()
    }

    suspend fun curlGet(url: String): ExecutionResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        runCatching {
            val req = Request.Builder().url(normalized).get().build()
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty().take(3000)
                ExecutionResult(
                    resp.isSuccessful,
                    "HTTP ${resp.code} ${resp.message}\n$body",
                    System.currentTimeMillis() - start
                )
            }
        }.getOrElse {
            ExecutionResult(false, "curl failed: ${it.message}", System.currentTimeMillis() - start)
        }
    }

    suspend fun runSystemShell(command: String, workingDir: File): ExecutionResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        runCatching {
            val pb = ProcessBuilder("/system/bin/sh", "-c", command)
                .directory(workingDir)
                .redirectErrorStream(true)
            val proc = pb.start()
            val completed = proc.waitFor(5, TimeUnit.SECONDS)
            if (!completed) {
                proc.destroyForcibly()
                return@runCatching ExecutionResult(false, "Process timed out after 5s", System.currentTimeMillis() - start)
            }
            val text = proc.inputStream.bufferedReader().readText().trimEnd()
            ExecutionResult(proc.exitValue() == 0, text.ifEmpty { "(exit ${proc.exitValue()})" }, System.currentTimeMillis() - start)
        }.getOrElse {
            ExecutionResult(false, "Shell error: ${it.message}", System.currentTimeMillis() - start)
        }
    }
}
