package com.tanu.personal.ai

import com.tanu.personal.data.MeetingEntity
import com.tanu.personal.data.SettingsStore
import com.tanu.personal.data.TranscriptSegmentEntity
import com.tanu.personal.domain.MomEngine
import com.tanu.personal.security.OpenAiKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAiMomProvider @Inject constructor(
    private val keyStore: OpenAiKeyStore,
    private val settings: SettingsStore
) {
    fun available(): Boolean = keyStore.hasKey()

    suspend fun enhance(
        meeting: MeetingEntity,
        segments: List<TranscriptSegmentEntity>,
        baseline: MomEngine.Result,
        userName: String
    ): MomEngine.Result = withContext(Dispatchers.IO) {
        val apiKey = keyStore.load()
        require(apiKey.isNotBlank()) { "OpenAI is not connected" }

        val format = JSONObject().apply {
            put("type", "json_schema")
            put("name", "tanu_mom")
            put("strict", true)
            put("schema", AiMomCodec.jsonSchema())
        }
        val request = JSONObject().apply {
            put("model", settings.openAiModel.ifBlank { "gpt-5.4-nano" })
            put("store", false)
            put("instructions", AiMomCodec.systemPrompt())
            put("input", AiMomCodec.prompt(meeting, segments, baseline, maxTranscriptChars = 90_000))
            put("text", JSONObject().put("format", format))
        }

        val connection = (URL("https://api.openai.com/v1/responses").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(request.toString()) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            require(code in 200..299) { "OpenAI request failed ($code): ${body.take(300)}" }
            val rawText = extractOutputText(JSONObject(body))
            require(rawText.isNotBlank()) { "OpenAI returned no meeting notes" }
            AiMomCodec.parse(meeting, rawText, baseline, userName)
        } finally {
            connection.disconnect()
        }
    }

    private fun extractOutputText(response: JSONObject): String {
        val direct = response.optString("output_text").trim()
        if (direct.isNotBlank()) return direct
        val output = response.optJSONArray("output") ?: return ""
        val result = StringBuilder()
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                val text = part.optString("text").trim()
                if (text.isNotBlank()) result.append(text)
            }
        }
        return result.toString()
    }
}
