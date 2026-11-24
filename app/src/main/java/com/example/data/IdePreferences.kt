package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.IdeThemes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ideDataStore: DataStore<Preferences> by preferencesDataStore(name = "zenncode_prefs")

data class IdeSettings(
    val themeId: String = IdeThemes.zenncodeDarkPlus.id,
    val editorFontSizeSp: Float = 13.5f,
    val wordWrap: Boolean = false,
    val showLineNumbers: Boolean = true,
    val tabSize: Int = 2,
    val terminalFontSizeSp: Float = 12.5f,
    val terminalCursorStyle: TerminalCursorStyle = TerminalCursorStyle.BLOCK,
    val terminalPrompt: String = "zenn@studio",
    val browserRunTarget: BrowserRunTarget = BrowserRunTarget.BUILTIN_BROWSER,
    val orientationMode: OrientationMode = OrientationMode.AUTO,
    val autoSave: Boolean = true
)

class IdePreferences(private val context: Context) {
    private object Keys {
        val THEME_ID = stringPreferencesKey("theme_id")
        val EDITOR_FONT_SIZE = floatPreferencesKey("editor_font_size")
        val WORD_WRAP = booleanPreferencesKey("word_wrap")
        val SHOW_LINE_NUMBERS = booleanPreferencesKey("show_line_numbers")
        val TAB_SIZE = intPreferencesKey("tab_size")
        val TERMINAL_FONT_SIZE = floatPreferencesKey("terminal_font_size")
        val TERMINAL_CURSOR = stringPreferencesKey("terminal_cursor")
        val TERMINAL_PROMPT = stringPreferencesKey("terminal_prompt")
        val BROWSER_TARGET = stringPreferencesKey("browser_target")
        val ORIENTATION_MODE = stringPreferencesKey("orientation_mode")
        val AUTO_SAVE = booleanPreferencesKey("auto_save")
    }

    val settingsFlow: Flow<IdeSettings> = context.ideDataStore.data.map { prefs ->
        IdeSettings(
            themeId = prefs[Keys.THEME_ID] ?: IdeThemes.zenncodeDarkPlus.id,
            editorFontSizeSp = (prefs[Keys.EDITOR_FONT_SIZE] ?: 13.5f).coerceIn(8f, 28f),
            wordWrap = prefs[Keys.WORD_WRAP] ?: false,
            showLineNumbers = prefs[Keys.SHOW_LINE_NUMBERS] ?: true,
            tabSize = prefs[Keys.TAB_SIZE] ?: 2,
            terminalFontSizeSp = (prefs[Keys.TERMINAL_FONT_SIZE] ?: 12.5f).coerceIn(9f, 22f),
            terminalCursorStyle = prefs[Keys.TERMINAL_CURSOR]?.let { name ->
                TerminalCursorStyle.entries.firstOrNull { it.name == name }
            } ?: TerminalCursorStyle.BLOCK,
            terminalPrompt = prefs[Keys.TERMINAL_PROMPT] ?: "zenn@studio",
            browserRunTarget = prefs[Keys.BROWSER_TARGET]?.let { name ->
                BrowserRunTarget.entries.firstOrNull { it.name == name }
            } ?: BrowserRunTarget.BUILTIN_BROWSER,
            orientationMode = prefs[Keys.ORIENTATION_MODE]?.let { name ->
                OrientationMode.entries.firstOrNull { it.name == name }
            } ?: OrientationMode.AUTO,
            autoSave = prefs[Keys.AUTO_SAVE] ?: true
        )
    }

    suspend fun setThemeId(themeId: String) {
        context.ideDataStore.edit { it[Keys.THEME_ID] = themeId }
    }

    suspend fun setEditorFontSize(sizeSp: Float) {
        context.ideDataStore.edit { it[Keys.EDITOR_FONT_SIZE] = sizeSp.coerceIn(8f, 28f) }
    }

    suspend fun setWordWrap(enabled: Boolean) {
        context.ideDataStore.edit { it[Keys.WORD_WRAP] = enabled }
    }

    suspend fun setShowLineNumbers(enabled: Boolean) {
        context.ideDataStore.edit { it[Keys.SHOW_LINE_NUMBERS] = enabled }
    }

    suspend fun setTabSize(size: Int) {
        context.ideDataStore.edit { it[Keys.TAB_SIZE] = size.coerceIn(2, 8) }
    }

    suspend fun setTerminalFontSize(sizeSp: Float) {
        context.ideDataStore.edit { it[Keys.TERMINAL_FONT_SIZE] = sizeSp.coerceIn(9f, 22f) }
    }

    suspend fun setTerminalCursorStyle(style: TerminalCursorStyle) {
        context.ideDataStore.edit { it[Keys.TERMINAL_CURSOR] = style.name }
    }

    suspend fun setTerminalPrompt(prompt: String) {
        context.ideDataStore.edit { it[Keys.TERMINAL_PROMPT] = prompt.ifBlank { "zenn@studio" } }
    }

    suspend fun setBrowserRunTarget(target: BrowserRunTarget) {
        context.ideDataStore.edit { it[Keys.BROWSER_TARGET] = target.name }
    }

    suspend fun setOrientationMode(mode: OrientationMode) {
        context.ideDataStore.edit { it[Keys.ORIENTATION_MODE] = mode.name }
    }

    suspend fun setAutoSave(enabled: Boolean) {
        context.ideDataStore.edit { it[Keys.AUTO_SAVE] = enabled }
    }
}
