package com.example.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import kotlin.math.max

class WorkspaceFileManager(context: Context) {
    val rootDir: File = File(context.filesDir, "zenncode_workspace")

    init {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
            seedStarterProject()
        }
    }

    private fun seedStarterProject() {
        writeFile(
            "index.html",
            """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>Zenncode Web App</title>
              <link rel="stylesheet" href="styles.css" />
            </head>
            <body>
              <main class="card">
                <span class="badge">Zenncode Live Preview</span>
                <h1>Hello, Developer!</h1>
                <p>Edit <code>index.html</code>, <code>styles.css</code>, or <code>app.js</code> and run in the Built-in Browser or Google Chrome.</p>
                <button id="counterBtn">Clicks: 0</button>
                <pre id="output"></pre>
              </main>
              <script src="app.js"></script>
            </body>
            </html>
            """.trimIndent() + "\n"
        )
        writeFile(
            "styles.css",
            """
            :root {
              --bg: #0b1120;
              --card: #141c2f;
              --accent: #38bdf8;
              --text: #e2e8f0;
            }
            * { box-sizing: border-box; }
            body {
              margin: 0;
              min-height: 100vh;
              display: grid;
              place-items: center;
              background: radial-gradient(circle at top, #172554, var(--bg));
              color: var(--text);
              font-family: system-ui, -apple-system, sans-serif;
              padding: 20px;
            }
            .card {
              max-width: 460px;
              width: 100%;
              background: var(--card);
              border: 1px solid #26344e;
              border-radius: 16px;
              padding: 24px;
              box-shadow: 0 20px 50px rgba(0, 0, 0, 0.45);
            }
            .badge {
              display: inline-block;
              padding: 4px 10px;
              border-radius: 999px;
              background: rgba(56, 189, 248, 0.16);
              color: var(--accent);
              font-size: 12px;
              font-weight: 600;
            }
            button {
              margin-top: 12px;
              background: var(--accent);
              color: #08121e;
              border: none;
              padding: 10px 18px;
              border-radius: 10px;
              font-weight: 700;
              cursor: pointer;
            }
            """.trimIndent() + "\n"
        )
        writeFile(
            "app.js",
            """
            const btn = document.getElementById("counterBtn");
            const output = document.getElementById("output");
            let count = 0;

            if (btn) {
              btn.addEventListener("click", () => {
                count += 1;
                btn.textContent = "Clicks: " + count;
                if (output) {
                  output.textContent = "Last tap at " + new Date().toLocaleTimeString();
                }
                console.log("Button clicked:", count);
              });
            }

            console.log("app.js loaded cleanly in Zenncode!");
            """.trimIndent() + "\n"
        )
        writeFile(
            "main.py",
            """
            def fibonacci(n):
                seq = []
                a = 0
                b = 1
                for i in range(n):
                    seq.append(a)
                    temp = a + b
                    a = b
                    b = temp
                return seq

            result = fibonacci(8)
            print("Zenncode Python Runner ->", result)
            print("Sum:", sum(result))
            """.trimIndent() + "\n"
        )
        writeFile(
            "queries.sql",
            """
            CREATE TABLE projects (
              id INTEGER PRIMARY KEY,
              name TEXT NOT NULL,
              language TEXT NOT NULL,
              stars INTEGER
            );

            INSERT INTO projects VALUES (1, 'zenncode-web', 'JavaScript', 42);
            INSERT INTO projects VALUES (2, 'fast-parser', 'Python', 87);
            INSERT INTO projects VALUES (3, 'edge-api', 'SQL', 64);

            SELECT name, language, stars FROM projects ORDER BY stars DESC;
            """.trimIndent() + "\n"
        )
        writeFile(
            "config.json",
            """
            {
              "name": "zenncode-starter",
              "version": "1.0.0",
              "entry": "index.html",
              "liveServerPort": 8765
            }
            """.trimIndent() + "\n"
        )
        writeFile(
            "README.md",
            """
            # Zenncode Workspace

            Welcome to **Zenncode**, your lightweight mobile code studio.

            - Open `index.html` and press **Run** to preview in Built-in Browser or Google Chrome.
            - Install language packs in **Extensions** (Python, TypeScript, Kotlin, Rust, Go, C++, Java).
            - Use the **Terminal** below (`run main.py`, `run app.js`, `run queries.sql`, `ls`, `help`).
            - Configure multiple **AI API Keys** with automatic rollback while sharing memory & skills.
            """.trimIndent() + "\n"
        )
    }

    fun listTree(): List<WorkspaceNode> {
        val result = mutableListOf<WorkspaceNode>()
        fun walk(dir: File, depth: Int) {
            val children = dir.listFiles()?.sortedWith(
                compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
            ) ?: return
            for (child in children) {
                val rel = child.relativeTo(rootDir).path.replace(File.separatorChar, '/')
                result.add(
                    WorkspaceNode(
                        relativePath = rel,
                        name = child.name,
                        isDirectory = child.isDirectory,
                        sizeBytes = if (child.isDirectory) 0L else child.length(),
                        depth = depth
                    )
                )
                if (child.isDirectory) {
                    walk(child, depth + 1)
                }
            }
        }
        walk(rootDir, 0)
        return result
    }

    fun resolveSafe(relativePath: String): File {
        val cleaned = relativePath.trim().trimStart('/').replace("..", "")
        val target = File(rootDir, cleaned)
        val rootCanonical = rootDir.canonicalPath
        val targetCanonical = target.canonicalPath
        require(targetCanonical == rootCanonical || targetCanonical.startsWith("$rootCanonical${File.separator}")) {
            "Invalid path outside workspace"
        }
        return target
    }

    fun readFile(relativePath: String): String {
        val file = resolveSafe(relativePath)
        return if (file.exists() && file.isFile) file.readText() else ""
    }

    fun writeFile(relativePath: String, content: String) {
        val file = resolveSafe(relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    fun createFile(relativePath: String, initialContent: String = ""): Boolean {
        val file = resolveSafe(relativePath)
        if (file.exists()) return false
        file.parentFile?.mkdirs()
        file.writeText(initialContent)
        return true
    }

    fun createDirectory(relativePath: String): Boolean {
        val dir = resolveSafe(relativePath)
        return if (dir.exists()) false else dir.mkdirs()
    }

    fun deletePath(relativePath: String): Boolean {
        val target = resolveSafe(relativePath)
        if (!target.exists() || target == rootDir) return false
        return target.deleteRecursively()
    }

    fun renamePath(oldRelativePath: String, newName: String): String? {
        val oldFile = resolveSafe(oldRelativePath)
        if (!oldFile.exists()) return null
        val sanitized = newName.trim().replace("/", "").replace("..", "")
        if (sanitized.isEmpty()) return null
        val newFile = File(oldFile.parentFile ?: rootDir, sanitized)
        if (newFile.exists()) return null
        return if (oldFile.renameTo(newFile)) {
            newFile.relativeTo(rootDir).path.replace(File.separatorChar, '/')
        } else null
    }

    fun searchInWorkspace(query: String): List<SearchHit> {
        if (query.isBlank()) return emptyList()
        val hits = mutableListOf<SearchHit>()
        for (node in listTree()) {
            if (node.isDirectory) continue
            val lines = readFile(node.relativePath).lines()
            lines.forEachIndexed { idx, lineText ->
                if (lineText.contains(query, ignoreCase = true)) {
                    hits.add(
                        SearchHit(
                            relativePath = node.relativePath,
                            line = idx + 1,
                            preview = lineText.trim().take(120)
                        )
                    )
                    if (hits.size >= 150) return hits
                }
            }
        }
        return hits
    }

    fun exportAllFilesMap(): Map<String, String> {
        val map = linkedMapOf<String, String>()
        for (node in listTree()) {
            if (!node.isDirectory) {
                map[node.relativePath] = readFile(node.relativePath)
            }
        }
        return map
    }

    fun serializeFilesMap(files: Map<String, String>): String {
        val obj = JSONObject()
        for ((k, v) in files) {
            obj.put(k, v)
        }
        return obj.toString()
    }

    fun parseFilesMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            val result = linkedMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = obj.optString(key, "")
            }
            result
        }.getOrDefault(emptyMap())
    }

    fun computeGitDiff(latestSnapshotJson: String?): List<GitFileChange> {
        val current = exportAllFilesMap()
        val previous = parseFilesMap(latestSnapshotJson)
        val allPaths = (current.keys + previous.keys).toSortedSet()
        val changes = mutableListOf<GitFileChange>()

        for (path in allPaths) {
            val curText = current[path]
            val prevText = previous[path]
            when {
                prevText == null && curText != null -> {
                    val lines = curText.lines()
                    changes.add(
                        GitFileChange(
                            relativePath = path,
                            status = "A",
                            addedLines = lines.size,
                            removedLines = 0,
                            diffPreview = lines.take(6).joinToString("\n") { "+ $it" }
                        )
                    )
                }
                prevText != null && curText == null -> {
                    val lines = prevText.lines()
                    changes.add(
                        GitFileChange(
                            relativePath = path,
                            status = "D",
                            addedLines = 0,
                            removedLines = lines.size,
                            diffPreview = lines.take(6).joinToString("\n") { "- $it" }
                        )
                    )
                }
                prevText != null && curText != null && prevText != curText -> {
                    val prevLines = prevText.lines()
                    val curLines = curText.lines()
                    val diffLines = mutableListOf<String>()
                    var added = 0
                    var removed = 0
                    val maxLen = max(prevLines.size, curLines.size)
                    for (i in 0 until maxLen) {
                        val p = prevLines.getOrNull(i)
                        val c = curLines.getOrNull(i)
                        if (p != c) {
                            if (p != null) {
                                removed++
                                if (diffLines.size < 8) diffLines.add("- ${i + 1}: $p")
                            }
                            if (c != null) {
                                added++
                                if (diffLines.size < 8) diffLines.add("+ ${i + 1}: $c")
                            }
                        }
                    }
                    changes.add(
                        GitFileChange(
                            relativePath = path,
                            status = "M",
                            addedLines = added,
                            removedLines = removed,
                            diffPreview = diffLines.joinToString("\n")
                        )
                    )
                }
            }
        }
        return changes
    }

    companion object {
        fun detectLanguageId(fileName: String): String {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "html", "htm", "xml", "svg" -> "html"
                "css", "scss" -> "css"
                "js", "mjs", "cjs" -> "javascript"
                "ts", "tsx" -> "typescript"
                "py", "pyw" -> "python"
                "kt", "kts" -> "kotlin"
                "java" -> "java"
                "c", "h" -> "c"
                "cpp", "hpp", "cc" -> "cpp"
                "rs" -> "rust"
                "go" -> "go"
                "sql" -> "sql"
                "json" -> "json"
                "md", "markdown" -> "markdown"
                "sh", "bash" -> "shell"
                "yaml", "yml" -> "yaml"
                else -> "plaintext"
            }
        }
    }
}
