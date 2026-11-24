package com.example.data

enum class SidebarTab(val label: String) {
    EXPLORER("Explorer"),
    SEARCH("Search"),
    SOURCE_CONTROL("Source Control"),
    EXTENSIONS("Extensions"),
    BROWSER("Browser"),
    AI_AGENT("AI Studio"),
    SETTINGS("Themes & Settings")
}

enum class BottomPanelTab(val label: String) {
    TERMINAL("Terminal"),
    PROBLEMS("Problems"),
    OUTPUT("Output")
}

enum class BrowserRunTarget(val label: String) {
    BUILTIN_BROWSER("Built-in Browser"),
    GOOGLE_CHROME("Google Chrome")
}

enum class OrientationMode(val label: String) {
    AUTO("Auto"),
    LANDSCAPE("Landscape"),
    PORTRAIT("Portrait")
}

enum class TerminalCursorStyle(val label: String) {
    BLOCK("Block █"),
    UNDERLINE("Underline _"),
    BAR("Bar |")
}

enum class AiProviderType(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String
) {
    GEMINI(
        displayName = "Google Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
        defaultModel = "gemini-2.5-flash"
    ),
    OPENAI(
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini"
    ),
    ANTHROPIC(
        displayName = "Anthropic Claude",
        defaultBaseUrl = "https://api.anthropic.com/v1",
        defaultModel = "claude-3-5-haiku-latest"
    ),
    GROQ(
        displayName = "Groq",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "llama-3.3-70b-versatile"
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "openai/gpt-4o-mini"
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-chat"
    ),
    CUSTOM(
        displayName = "Custom OpenAI-Compatible",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "custom-model"
    )
}

enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO
}

data class CodeDiagnostic(
    val filePath: String,
    val line: Int,
    val column: Int,
    val severity: DiagnosticSeverity,
    val message: String,
    val source: String = "ZennLint"
)

data class WorkspaceNode(
    val relativePath: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val depth: Int = 0
)

data class OpenEditorTab(
    val relativePath: String,
    val fileName: String,
    val content: String,
    val savedContent: String,
    val languageId: String,
    val cursorLine: Int = 1,
    val cursorColumn: Int = 1,
    val undoStack: List<String> = emptyList(),
    val redoStack: List<String> = emptyList()
) {
    val isDirty: Boolean get() = content != savedContent
}

enum class TerminalEntryType {
    COMMAND,
    OUTPUT,
    SUCCESS,
    ERROR,
    WARNING,
    INFO
}

data class TerminalEntry(
    val id: Long,
    val text: String,
    val type: TerminalEntryType
)

data class SearchHit(
    val relativePath: String,
    val line: Int,
    val preview: String
)

data class GitFileChange(
    val relativePath: String,
    val status: String, // "M", "A", "D"
    val addedLines: Int,
    val removedLines: Int,
    val diffPreview: String
)
