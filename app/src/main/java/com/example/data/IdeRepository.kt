package com.example.data

import kotlinx.coroutines.flow.Flow

class IdeRepository(private val db: IdeDatabase) {
    private val extensionDao = db.extensionDao()
    private val aiKeyDao = db.aiKeyProfileDao()
    private val memoryDao = db.aiMemoryNoteDao()
    private val skillDao = db.aiSkillDao()
    private val chatDao = db.aiChatMessageDao()
    private val snapshotDao = db.workspaceSnapshotDao()

    val extensions: Flow<List<ExtensionEntity>> = extensionDao.observeAll()
    val aiKeyProfiles: Flow<List<AiKeyProfileEntity>> = aiKeyDao.observeAll()
    val memoryNotes: Flow<List<AiMemoryNoteEntity>> = memoryDao.observeAll()
    val skills: Flow<List<AiSkillEntity>> = skillDao.observeAll()
    val chatMessages: Flow<List<AiChatMessageEntity>> = chatDao.observeAll()
    val snapshots: Flow<List<WorkspaceSnapshotEntity>> = snapshotDao.observeAll()

    suspend fun seedDefaultsIfEmpty() {
        if (extensionDao.getAllOnce().isEmpty()) {
            extensionDao.insertAll(defaultExtensions())
        }
        if (skillDao.getAllOnce().isEmpty()) {
            skillDao.insertAll(defaultSkills())
        }
        if (memoryDao.getAllOnce().isEmpty()) {
            memoryDao.insert(
                AiMemoryNoteEntity(
                    title = "Workspace Stack",
                    content = "Project uses clean modular files in Zenncode workspace (HTML/CSS/JS web app, Python scripts, and SQLite queries). Prefer concise, bug-free code with clear explanations.",
                    isPinned = true
                )
            )
        }
    }

    suspend fun updateExtension(ext: ExtensionEntity) = extensionDao.update(ext)

    suspend fun getAiKeyProfilesOnce(): List<AiKeyProfileEntity> = aiKeyDao.getAllOnce()
    suspend fun saveAiKeyProfile(profile: AiKeyProfileEntity): Long = aiKeyDao.insert(profile)
    suspend fun updateAiKeyProfile(profile: AiKeyProfileEntity) = aiKeyDao.update(profile)
    suspend fun deleteAiKeyProfile(id: Long) = aiKeyDao.deleteById(id)

    suspend fun getMemoryNotesOnce(): List<AiMemoryNoteEntity> = memoryDao.getAllOnce()
    suspend fun saveMemoryNote(note: AiMemoryNoteEntity): Long = memoryDao.insert(note)
    suspend fun updateMemoryNote(note: AiMemoryNoteEntity) = memoryDao.update(note)
    suspend fun deleteMemoryNote(id: Long) = memoryDao.deleteById(id)

    suspend fun getSkillsOnce(): List<AiSkillEntity> = skillDao.getAllOnce()
    suspend fun saveSkill(skill: AiSkillEntity): Long = skillDao.insert(skill)
    suspend fun updateSkill(skill: AiSkillEntity) = skillDao.update(skill)
    suspend fun deleteSkill(id: Long) = skillDao.deleteById(id)

    suspend fun getChatMessagesOnce(): List<AiChatMessageEntity> = chatDao.getAllOnce()
    suspend fun addChatMessage(message: AiChatMessageEntity): Long = chatDao.insert(message)
    suspend fun clearChat() = chatDao.clearAll()

    suspend fun getLatestSnapshot(): WorkspaceSnapshotEntity? = snapshotDao.getLatest()
    suspend fun saveSnapshot(snapshot: WorkspaceSnapshotEntity): Long = snapshotDao.insert(snapshot)

    private fun defaultExtensions(): List<ExtensionEntity> = listOf(
        ExtensionEntity(
            id = "zenn.web-pack",
            name = "HTML / CSS / JS Web Essentials",
            publisher = "Zenncode Core",
            category = "LANGUAGE",
            languageId = "web",
            version = "2.4.0",
            description = "Syntax highlighting, live error diagnostics, WebView JS runtime, and localhost preview for HTML, CSS, and JavaScript.",
            fileExtensions = ".html,.htm,.css,.js,.mjs",
            sizeLabel = "420 KB",
            sampleTemplate = "<!DOCTYPE html>\n<html>\n<head>\n  <meta charset=\"UTF-8\">\n  <title>Zenncode App</title>\n</head>\n<body>\n  <h1>Hello from Zenncode!</h1>\n</body>\n</html>\n",
            isInstalled = true,
            isEnabled = true,
            downloadsLabel = "18.4M"
        ),
        ExtensionEntity(
            id = "zenn.python",
            name = "Python Language Pack",
            publisher = "Zenncode Labs",
            category = "LANGUAGE",
            languageId = "python",
            version = "3.12.1",
            description = "Python syntax grammar, indentation & colon error detector, snippets, and built-in fast Python interpreter.",
            fileExtensions = ".py,.pyw",
            sizeLabel = "680 KB",
            sampleTemplate = "def greet(name):\n    return f\"Hello, {name}! Welcome to Zenncode.\"\n\nfor i in range(1, 4):\n    print(f\"[{i}]\", greet(\"Developer\"))\n",
            isInstalled = false,
            isEnabled = false,
            downloadsLabel = "24.1M"
        ),
        ExtensionEntity(
            id = "zenn.typescript",
            name = "TypeScript & TSX Support",
            publisher = "Zenncode Labs",
            category = "LANGUAGE",
            languageId = "typescript",
            version = "5.7.2",
            description = "TypeScript syntax highlighting, interface/type tokens, error diagnostics, and transpiled JS runner.",
            fileExtensions = ".ts,.tsx",
            sizeLabel = "540 KB",
            sampleTemplate = "interface User {\n  name: string;\n  role: string;\n}\n\nconst dev: User = { name: \"Reiko\", role: \"Engineer\" };\nconsole.log(`\${dev.name} - \${dev.role}`);\n",
            isInstalled = false,
            isEnabled = false,
            downloadsLabel = "14.2M"
        ),
        ExtensionEntity(
            id = "zenn.kotlin",
            name = "Kotlin Modern Language",
            publisher = "Zenncode Mobile",
            category = "LANGUAGE",
            languageId = "kotlin",
            version = "2.1.0",
            description = "Kotlin syntax colors, data class & coroutine snippets, and bracket/type diagnostics.",
            fileExtensions = ".kt,.kts",
            sizeLabel = "510 KB",
            sampleTemplate = "data class Task(val title: String, val done: Boolean = false)\n\nfun main() {\n    val items = listOf(Task(\"Build Zenncode\", true), Task(\"Ship APK\"))\n    items.forEach { println(it) }\n}\n",
            isInstalled = false,
            isEnabled = false,
            downloadsLabel = "6.8M"
        ),
        ExtensionEntity(
            id = "zenn.java",
            name = "Java Language Support",
            publisher = "Zenncode Core",
            category = "LANGUAGE",
            languageId = "java",
            version = "21.0.4",
            description = "Java syntax highlighting, semicolon & brace linter, and OOP class templates.",
            fileExtensions = ".java",
            sizeLabel = "490 KB",
            sampleTemplate = "public class Main {\n    public static void main(String[] args) {\n        System.out.println(\"Hello Java in Zenncode!\");\n    }\n}\n",
            isInstalled = false,
            isEnabled = false,
            downloadsLabel = "11.5M"
        ),
        ExtensionEntity(
            id = "zenn.cpp",
            name = "C / C++ IntelliSense Pack",
            publisher = "Zenncode Systems",
            category = "LANGUAGE",
            languageId = "cpp",
            version = "1.22.0",
            description = "C and C++ syntax highlighting, preprocessor directives, and pointer/brace diagnostics.",
            fileExtensions = ".c,.cpp,.h,.hpp",
            sizeLabel = "610 KB",
            sampleTemplate = "#include <iostream>\n\nint main() {\n    std::cout << \"Fast C++ editing in Zenncode!\" << std::endl;\n    return 0;\n}\n",
            isInstalled = false,
            isEnabled = false,
            downloadsLabel = "9.9M"
        ),
        ExtensionEntity(
            id = "zenn.rust",
            name = "Rust Analyzer Lite",
            publisher = "Zenncode Systems",
            category = "LANGUAGE",
            languageId = "rust",
            version = "0.4.8",
            description = "Rust syntax tokens, macro highlighting, lifetime annotations, and borrow-style lint hints.",
            fileExtensions = ".rs",
            sizeLabel = "470 KB",
            sampleTemplate = "fn main() {\n    let stack = vec![\"Rust\", \"Zenncode\", \"Fast\"];\n    println!(\"{:?}\", stack);\n}\n",
            isInstalled = false,
            isEnabled = false,
            downloadsLabel = "4.7M"
        ),
        ExtensionEntity(
            id = "zenn.go",
            name = "Go (Golang) Pack",
            publisher = "Zenncode Cloud",
            category = "LANGUAGE",
            languageId = "go",
            version = "1.23.1",
            description = "Go syntax highlighting, goroutine/channel snippets, and package/import diagnostics.",
            fileExtensions = ".go",
            sizeLabel = "390 KB",
            sampleTemplate = "package main\n\nimport \"fmt\"\n\nfunc main() {\n    fmt.Println(\"Hello from Go!\")\n}\n",
            isInstalled = false,
            isEnabled = false,
            downloadsLabel = "5.3M"
        ),
        ExtensionEntity(
            id = "zenn.sqlite",
            name = "SQL & SQLite Runner",
            publisher = "Zenncode Data",
            category = "LANGUAGE",
            languageId = "sql",
            version = "3.46.0",
            description = "SQL syntax highlighting plus real built-in Android SQLite query execution with ASCII table output.",
            fileExtensions = ".sql",
            sizeLabel = "310 KB",
            sampleTemplate = "CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, role TEXT);\nINSERT INTO users VALUES (1, 'Alex', 'Admin'), (2, 'Sam', 'Dev');\nSELECT * FROM users;\n",
            isInstalled = true,
            isEnabled = true,
            downloadsLabel = "8.2M"
        ),
        ExtensionEntity(
            id = "zenn.json-md",
            name = "JSON, YAML & Markdown Pro",
            publisher = "Zenncode Core",
            category = "TOOL",
            languageId = "json",
            version = "1.9.0",
            description = "Strict JSON parser error-line indicator, JSON pretty formatter, and live Markdown HTML preview.",
            fileExtensions = ".json,.yaml,.yml,.md",
            sizeLabel = "250 KB",
            sampleTemplate = "{\n  \"app\": \"Zenncode\",\n  \"fast\": true,\n  \"features\": [\"Terminal\", \"Browser\", \"AI Rollback\"]\n}\n",
            isInstalled = true,
            isEnabled = true,
            downloadsLabel = "15.0M"
        ),
        ExtensionEntity(
            id = "zenn.lint-pro",
            name = "ZennLint Error Lens",
            publisher = "Zenncode Quality",
            category = "LINTER",
            languageId = "all",
            version = "3.1.2",
            description = "Inline gutter markers, exact line/column error indicators, and quick-fix diagnostics across all open tabs.",
            fileExtensions = "*",
            sizeLabel = "180 KB",
            sampleTemplate = "",
            isInstalled = true,
            isEnabled = true,
            downloadsLabel = "21.6M"
        )
    )

    private fun defaultSkills(): List<AiSkillEntity> = listOf(
        AiSkillEntity(
            name = "Bug Hunter & Error Fixer",
            description = "Finds exact line bugs, syntax errors, and edge cases; proposes clean drop-in fixes.",
            systemPrompt = "Always inspect the active file diagnostics and line numbers first. Pinpoint exact line causes and provide clean, minimal, runnable fixes.",
            isEnabled = true,
            isBuiltIn = true
        ),
        AiSkillEntity(
            name = "Bilingual Tagalog/English Dev",
            description = "Explains technical concepts clearly in natural Taglish/English when helpful.",
            systemPrompt = "If the user writes in Tagalog or Taglish, respond clearly in natural Tagalog/English with accurate coding terminology; otherwise match the user's language.",
            isEnabled = true,
            isBuiltIn = true
        ),
        AiSkillEntity(
            name = "Lightweight & Fast Code",
            description = "Optimizes generated code for low memory, O(n) simplicity, and mobile performance.",
            systemPrompt = "Prioritize lightweight, zero-bloat implementations with clean structure and fast runtime performance.",
            isEnabled = false,
            isBuiltIn = true
        ),
        AiSkillEntity(
            name = "Unit Test Architect",
            description = "Generates edge-case tests for JS, Python, Kotlin, or SQL queries.",
            systemPrompt = "When asked to test or verify code, produce concise assertions covering normal, boundary, and error inputs.",
            isEnabled = false,
            isBuiltIn = true
        )
    )
}
