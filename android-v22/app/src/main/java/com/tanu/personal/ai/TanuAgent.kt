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
    private val openAi: OpenAiMomProvider,
    private val settings: SettingsStore
) {
    suspend fun build(meeting: MeetingEntity, segments: List<TranscriptSegmentEntity>): MomEngine.Result {
        val baseline = deterministic.build(meeting, segments)
        val userName = settings.userName

        return when (settings.aiMode) {
            AiMode.DEVICE -> baseline
            AiMode.OPENAI -> {
                if (openAi.available() && online()) {
                    runCatching { openAi.enhance(meeting, segments, baseline, userName) }
                        .getOrElse { baseline }
                } else {
                    baseline
                }
            }
            else -> {
                if (openAi.available() && online()) {
                    runCatching { openAi.enhance(meeting, segments, baseline, userName) }
                        .getOrElse { baseline }
                } else {
                    baseline
                }
            }
        }
    }

    private fun online(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
