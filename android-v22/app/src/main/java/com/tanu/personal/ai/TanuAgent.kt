package com.tanu.personal.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.tanu.personal.data.AiMode
import com.tanu.personal.data.MeetingEntity
import com.tanu.personal.data.SettingsStore
import com.tanu.personal.data.TranscriptSegmentEntity
import com.tanu.personal.domain.MomEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TanuAgent @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deterministic: MomEngine,
    private val local: LocalLlmProvider,
    private val openAi: OpenAiMomProvider,
    private val settings: SettingsStore
) {
    suspend fun build(meeting: MeetingEntity, segments: List<TranscriptSegmentEntity>): MomEngine.Result {
        val baseline = deterministic.build(meeting, segments)
        val userName = settings.userName

        return when (settings.aiMode) {
            AiMode.DEVICE -> localOrBaseline(meeting, segments, baseline, userName)
            AiMode.OPENAI -> {
                if (openAi.available() && online()) {
                    runCatching { openAi.enhance(meeting, segments, baseline, userName) }
                        .getOrElse { localOrBaseline(meeting, segments, baseline, userName) }
                } else {
                    localOrBaseline(meeting, segments, baseline, userName)
                }
            }
            else -> {
                if (openAi.available() && online()) {
                    runCatching { openAi.enhance(meeting, segments, baseline, userName) }
                        .getOrElse { localOrBaseline(meeting, segments, baseline, userName) }
                } else {
                    localOrBaseline(meeting, segments, baseline, userName)
                }
            }
        }
    }

    private suspend fun localOrBaseline(
        meeting: MeetingEntity,
        segments: List<TranscriptSegmentEntity>,
        baseline: MomEngine.Result,
        userName: String
    ): MomEngine.Result = runCatching {
        local.enhance(meeting, segments, baseline, userName)
    }.getOrElse { baseline }

    private fun online(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
