package com.tanu.personal.ai

import android.app.ActivityManager
import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.tanu.personal.data.MeetingEntity
import com.tanu.personal.data.TranscriptSegmentEntity
import com.tanu.personal.domain.MomEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalLlmProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mutex = Mutex()
    private val assetName = "models/Qwen3-0.6B-Q4_K_M.gguf"

    suspend fun enhance(
        meeting: MeetingEntity,
        segments: List<TranscriptSegmentEntity>,
        baseline: MomEngine.Result,
        userName: String
    ): MomEngine.Result = mutex.withLock {
        require(hasSafeMemoryHeadroom()) { "On-device AI memory is low; using TANU fallback notes." }
        val engine = AiChat.getInferenceEngine(context)
        try {
            awaitInitialized(engine)
            val model = ensureModelFile()
            engine.loadModel(model.absolutePath)
            engine.setSystemPrompt(AiMomCodec.systemPrompt())
            val prompt = AiMomCodec.prompt(meeting, segments, baseline, maxTranscriptChars = 18_000)
            val text = withTimeout(180_000) {
                val out = StringBuilder()
                engine.sendUserPrompt(prompt, predictLength = 1100).collect { token -> out.append(token) }
                out.toString()
            }
            AiMomCodec.parse(meeting, text, baseline, userName)
        } finally {
            runCatching { engine.cleanUp() }
        }
    }

    private suspend fun awaitInitialized(engine: InferenceEngine) {
        val state = withTimeout(30_000) {
            engine.state.first {
                it is InferenceEngine.State.Initialized ||
                    it is InferenceEngine.State.ModelReady ||
                    it is InferenceEngine.State.Error
            }
        }
        when (state) {
            is InferenceEngine.State.Error -> {
                engine.cleanUp()
                withTimeout(10_000) { engine.state.first { it is InferenceEngine.State.Initialized } }
            }
            is InferenceEngine.State.ModelReady -> engine.cleanUp()
            else -> Unit
        }
    }

    private fun hasSafeMemoryHeadroom(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val minimumFree = 900L * 1024L * 1024L
        return !info.lowMemory && info.availMem >= minimumFree && am.largeMemoryClass >= 384
    }

    private fun ensureModelFile(): File {
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        val final = File(dir, "Qwen3-0.6B-Q4_K_M.gguf")
        if (final.exists() && final.length() > 250_000_000L) return final

        val temp = File(dir, "${final.name}.part")
        if (temp.exists()) temp.delete()
        try {
            context.assets.open(assetName).use { input ->
                temp.outputStream().buffered().use { output -> input.copyTo(output, 1024 * 1024) }
            }
            require(temp.length() > 250_000_000L) { "Bundled TANU AI model is incomplete" }
            if (final.exists()) final.delete()
            require(temp.renameTo(final)) { "Could not prepare the TANU AI model" }
            return final
        } catch (e: Exception) {
            runCatching { temp.delete() }
            throw e
        }
    }
}
