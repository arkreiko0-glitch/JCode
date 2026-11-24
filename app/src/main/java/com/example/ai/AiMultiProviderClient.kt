package com.example.ai

import com.example.data.AiChatMessageEntity
import com.example.data.AiKeyProfileEntity
import com.example.data.AiMemoryNoteEntity
import com.example.data.AiProviderType
import com.example.data.AiSkillEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AiRollbackEvent(
    val failedKeyLabel: String,
    val failedModel: String,
    val errorReason: String,
    val nextKeyLabel: String?,
    val nextModel: String?
)

data class AiCompletionOutcome(
    val success: Boolean,
    val responseText: String,
    val usedProfile: AiKeyProfileEntity?,
    val rollbackEvents: List<AiRollbackEvent>,
    val errorSummary: String? = null
)

class AiMultiProviderClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun sendWithAutoRollback(
        profiles: List<AiKeyProfileEntity>,
        chatHistory: List<AiChatMessageEntity>,
        memoryNotes: List<AiMemoryNoteEntity>,
        activeSkills: List<AiSkillEntity>,
        workspaceContext: String,
        userPrompt: String,
        onProfileFailure: suspend (AiKeyProfileEntity, String) -> Unit,
        onProfileSuccess: suspend (AiKeyProfileEntity) -> Unit
    ): AiCompletionOutcome = withContext(Dispatchers.IO) {
        val enabled = profiles.filter { it.isEnabled && it.apiKey.isNotBlank() }
            .sortedWith(compareBy<AiKeyProfileEntity> { it.priorityOrder }.thenBy { it.id })

        if (enabled.isEmpty()) {
            return@withContext AiCompletionOutcome(
                success = false,
                responseText = "",
                usedProfile = null,
                rollbackEvents = emptyList(),
                errorSummary = "No active API keys configured. Add at least one API key in AI Studio -> Keys."
            )
        }

        val systemPrompt = buildUnifiedSystemPrompt(memoryNotes, activeSkills, workspaceContext)
        val rollbacks = mutableListOf<AiRollbackEvent>()

        for (index in enabled.indices) {
            val profile = enabled[index]
            val result = callSingleProvider(profile, systemPrompt, chatHistory, userPrompt)
            if (result.isSuccess) {
                val reply = result.getOrThrow().trim()
                if (reply.isNotEmpty()) {
                    onProfileSuccess(profile)
                    return@withContext AiCompletionOutcome(
                        success = true,
                        responseText = reply,
                        usedProfile = profile,
                        rollbackEvents = rollbacks
                    )
                }
            }

            val errMsg = result.exceptionOrNull()?.message ?: "Empty response"
            onProfileFailure(profile, errMsg)
            val next = enabled.getOrNull(index + 1)
            rollbacks.add(
                AiRollbackEvent(
                    failedKeyLabel = profile.label,
                    failedModel = profile.modelId,
                    errorReason = errMsg,
                    nextKeyLabel = next?.label,
                    nextModel = next?.modelId
                )
            )
        }

        AiCompletionOutcome(
            success = false,
            responseText = "",
            usedProfile = null,
            rollbackEvents = rollbacks,
            errorSummary = "All ${enabled.size} configured API key(s) failed. Last error: ${rollbacks.lastOrNull()?.errorReason}"
        )
    }

    suspend fun testKey(profile: AiKeyProfileEntity): Result<String> = withContext(Dispatchers.IO) {
        callSingleProvider(
            profile = profile,
            systemPrompt = "You are a connectivity test. Reply with only: OK",
            history = emptyList(),
            userPrompt = "Reply OK"
        )
    }

    private fun buildUnifiedSystemPrompt(
        memoryNotes: List<AiMemoryNoteEntity>,
        activeSkills: List<AiSkillEntity>,
        workspaceContext: String
    ): String = buildString {
        appendLine("You are Zenncode AI, an expert coding assistant inside the Zenncode mobile IDE.")
        appendLine("Keep answers concise, actionable, and formatted with fenced code blocks when providing code.")
        if (activeSkills.isNotEmpty()) {
            appendLine("\n### Active Skills")
            activeSkills.forEach { skill ->
                appendLine("- [${skill.name}]: ${skill.systemPrompt}")
            }
        }
        if (memoryNotes.isNotEmpty()) {
            appendLine("\n### Shared Persistent Memory")
            memoryNotes.take(12).forEach { note ->
                appendLine("- ${note.title}: ${note.content}")
            }
        }
        if (workspaceContext.isNotBlank()) {
            appendLine("\n### Active Workspace Context")
            appendLine(workspaceContext)
        }
    }

    private fun callSingleProvider(
        profile: AiKeyProfileEntity,
        systemPrompt: String,
        history: List<AiChatMessageEntity>,
        userPrompt: String
    ): Result<String> {
        val provider = AiProviderType.entries.firstOrNull { it.name == profile.provider } ?: AiProviderType.CUSTOM
        return try {
            when (provider) {
                AiProviderType.GEMINI -> callGemini(profile, systemPrompt, history, userPrompt)
                AiProviderType.ANTHROPIC -> callAnthropic(profile, systemPrompt, history, userPrompt)
                else -> callOpenAiCompatible(profile, systemPrompt, history, userPrompt)
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Request exception"))
        }
    }

    private fun callGemini(
        profile: AiKeyProfileEntity,
        systemPrompt: String,
        history: List<AiChatMessageEntity>,
        userPrompt: String
    ): Result<String> {
        val base = profile.baseUrl.trimEnd('/').ifEmpty { AiProviderType.GEMINI.defaultBaseUrl }
        val url = "$base/models/${profile.modelId.trim()}:generateContent?key=${profile.apiKey.trim()}"

        val contents = JSONArray()
        history.filter { it.role == "USER" || it.role == "ASSISTANT" }.takeLast(14).forEach { msg ->
            contents.put(
                JSONObject().apply {
                    put("role", if (msg.role == "USER") "user" else "model")
                    put("parts", JSONArray().put(JSONObject().put("text", msg.content)))
                }
            )
        }
        contents.put(
            JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))
            }
        )

        val payload = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            put("contents", contents)
        }

        val req = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return Result.failure(Exception("HTTP ${resp.code}: ${extractErrorMessage(raw, resp.message)}"))
            }
            val json = JSONObject(raw)
            val candidates = json.optJSONArray("candidates")
            val text = candidates?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text", "")
                .orEmpty()
            return if (text.isNotBlank()) Result.success(text)
            else Result.failure(Exception("Empty Gemini candidate"))
        }
    }

    private fun callAnthropic(
        profile: AiKeyProfileEntity,
        systemPrompt: String,
        history: List<AiChatMessageEntity>,
        userPrompt: String
    ): Result<String> {
        val base = profile.baseUrl.trimEnd('/').ifEmpty { AiProviderType.ANTHROPIC.defaultBaseUrl }
        val url = "$base/messages"

        val messages = JSONArray()
        history.filter { it.role == "USER" || it.role == "ASSISTANT" }.takeLast(14).forEach { msg ->
            messages.put(
                JSONObject().apply {
                    put("role", if (msg.role == "USER") "user" else "assistant")
                    put("content", msg.content)
                }
            )
        }
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", userPrompt)
        })

        val payload = JSONObject().apply {
            put("model", profile.modelId.trim())
            put("max_tokens", 2048)
            put("system", systemPrompt)
            put("messages", messages)
        }

        val req = Request.Builder()
            .url(url)
            .addHeader("x-api-key", profile.apiKey.trim())
            .addHeader("anthropic-version", "2023-06-01")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return Result.failure(Exception("HTTP ${resp.code}: ${extractErrorMessage(raw, resp.message)}"))
            }
            val json = JSONObject(raw)
            val contentArr = json.optJSONArray("content")
            val text = contentArr?.optJSONObject(0)?.optString("text", "").orEmpty()
            return if (text.isNotBlank()) Result.success(text)
            else Result.failure(Exception("Empty Anthropic content"))
        }
    }

    private fun callOpenAiCompatible(
        profile: AiKeyProfileEntity,
        systemPrompt: String,
        history: List<AiChatMessageEntity>,
        userPrompt: String
    ): Result<String> {
        val base = profile.baseUrl.trimEnd('/').ifEmpty { AiProviderType.OPENAI.defaultBaseUrl }
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"

        val messages = JSONArray()
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })
        history.filter { it.role == "USER" || it.role == "ASSISTANT" }.takeLast(14).forEach { msg ->
            messages.put(
                JSONObject().apply {
                    put("role", if (msg.role == "USER") "user" else "assistant")
                    put("content", msg.content)
                }
            )
        }
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", userPrompt)
        })

        val payload = JSONObject().apply {
            put("model", profile.modelId.trim())
            put("messages", messages)
            put("temperature", 0.3)
        }

        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${profile.apiKey.trim()}")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return Result.failure(Exception("HTTP ${resp.code}: ${extractErrorMessage(raw, resp.message)}"))
            }
            val json = JSONObject(raw)
            val text = json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                .orEmpty()
            return if (text.isNotBlank()) Result.success(text)
            else Result.failure(Exception("Empty model choice"))
        }
    }

    private fun extractErrorMessage(raw: String, fallback: String): String {
        return runCatching {
            val obj = JSONObject(raw)
            val err = obj.optJSONObject("error")
            err?.optString("message")?.takeIf { it.isNotBlank() }
                ?: obj.optString("message").takeIf { it.isNotBlank() }
                ?: fallback
        }.getOrDefault(fallback.ifBlank { "Request failed" }).take(140)
    }
}
