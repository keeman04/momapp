package com.tanu.personal.ai

import com.tanu.personal.data.ActionItemEntity
import com.tanu.personal.data.MeetingEntity
import com.tanu.personal.data.MomEntity
import com.tanu.personal.data.TranscriptSegmentEntity
import com.tanu.personal.domain.MomEngine
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object AiMomCodec {
    fun systemPrompt(): String = """
        You are TANU, an AI conversation assistant running for a meeting-notes app.
        Produce a precise professional Minutes of Meeting in ENGLISH even when the transcript mixes Tamil, English, Hindi, Telugu, Malayalam, Kannada or slang.
        Preserve names, company terms, numbers, currency, dates and commitments. Never invent facts.
        Resolve obvious code-switching into natural English. If an owner or due date is not actually stated, use an empty string rather than guessing.
        Return JSON only. Do not add markdown, explanations or reasoning. /no_think
    """.trimIndent()

    fun prompt(
        meeting: MeetingEntity,
        segments: List<TranscriptSegmentEntity>,
        baseline: MomEngine.Result,
        maxTranscriptChars: Int
    ): String {
        val transcript = compactTranscript(segments, maxTranscriptChars)
        val baselineActions = JSONArray().apply {
            baseline.actions.take(30).forEach { a ->
                put(JSONObject().apply {
                    put("task", a.title)
                    put("owner", a.owner)
                    put("due_date", a.dueDate)
                    put("priority", a.priority)
                })
            }
        }
        return """
            Meeting title: ${meeting.title}
            Participants: ${meeting.participantsCsv.ifBlank { "Not provided" }}

            A deterministic extractor already produced this draft. Use it only as a hint and correct it against the transcript:
            Draft summary: ${baseline.mom.summary}
            Draft decisions: ${baseline.mom.decisionsJson}
            Draft actions: $baselineActions

            Transcript:
            $transcript

            Return exactly one JSON object with these keys:
            {
              "summary": "short accurate executive summary",
              "discussion_points": ["..."],
              "decisions": ["..."],
              "actions": [
                {"task":"...","owner":"","due_date":"","priority":"high|normal|low"}
              ],
              "follow_ups": ["..."],
              "important_numbers": ["..."],
              "next_meeting": ""
            }
            Keep the summary concise. Keep action tasks executable. Do not invent owners, dates, decisions or numbers.
        """.trimIndent()
    }

    fun parse(
        meeting: MeetingEntity,
        raw: String,
        baseline: MomEngine.Result,
        userName: String
    ): MomEngine.Result {
        val json = JSONObject(cleanJson(raw))
        val summary = json.optString("summary").trim().ifBlank { baseline.mom.summary }
        val discussion = json.stringList("discussion_points")
        val decisions = json.stringList("decisions")
        val followUps = json.stringList("follow_ups")
        val importantNumbers = json.stringList("important_numbers")
        val nextMeeting = json.optString("next_meeting").trim()

        val actionsJson = json.optJSONArray("actions") ?: JSONArray()
        val actions = buildList {
            for (i in 0 until actionsJson.length()) {
                val item = actionsJson.optJSONObject(i) ?: continue
                val task = item.optString("task").trim()
                if (task.isBlank()) continue
                val owner = item.optString("owner").trim().ifBlank { "Unassigned" }
                val due = item.optString("due_date").trim()
                val priority = when (item.optString("priority").lowercase()) {
                    "high" -> "high"
                    "low" -> "low"
                    else -> "normal"
                }
                add(ActionItemEntity(UUID.randomUUID().toString(), meeting.id, task, owner, due, priority))
            }
        }.ifEmpty { baseline.actions }

        val mine = actions.filter { isMine(it.owner, userName) }.map { it.title }
        val others = actions.filterNot { isMine(it.owner, userName) || it.owner.equals("Unassigned", true) }.map { it.title }

        fun jsonArray(values: List<String>, fallback: String): String =
            if (values.isEmpty()) fallback else JSONArray(values.distinct()).toString()

        val mom = MomEntity(
            meetingId = meeting.id,
            summary = summary,
            discussionPointsJson = jsonArray(discussion, baseline.mom.discussionPointsJson),
            decisionsJson = jsonArray(decisions, baseline.mom.decisionsJson),
            clientCommitmentsJson = if (others.isEmpty()) baseline.mom.clientCommitmentsJson else JSONArray(others.distinct()).toString(),
            myCommitmentsJson = if (mine.isEmpty()) baseline.mom.myCommitmentsJson else JSONArray(mine.distinct()).toString(),
            followUpsJson = jsonArray(followUps, baseline.mom.followUpsJson),
            importantNumbersJson = jsonArray(importantNumbers, baseline.mom.importantNumbersJson),
            nextMeeting = nextMeeting.ifBlank { baseline.mom.nextMeeting },
            updatedAt = System.currentTimeMillis()
        )
        return MomEngine.Result(mom, actions)
    }

    fun jsonSchema(): JSONObject {
        val stringArray = JSONObject().apply {
            put("type", "array")
            put("items", JSONObject().put("type", "string"))
        }
        val action = JSONObject().apply {
            put("type", "object")
            put("additionalProperties", false)
            put("properties", JSONObject().apply {
                put("task", JSONObject().put("type", "string"))
                put("owner", JSONObject().put("type", "string"))
                put("due_date", JSONObject().put("type", "string"))
                put("priority", JSONObject().apply {
                    put("type", "string")
                    put("enum", JSONArray(listOf("high", "normal", "low")))
                })
            })
            put("required", JSONArray(listOf("task", "owner", "due_date", "priority")))
        }
        return JSONObject().apply {
            put("type", "object")
            put("additionalProperties", false)
            put("properties", JSONObject().apply {
                put("summary", JSONObject().put("type", "string"))
                put("discussion_points", stringArray)
                put("decisions", JSONObject(stringArray.toString()))
                put("actions", JSONObject().apply {
                    put("type", "array")
                    put("items", action)
                })
                put("follow_ups", JSONObject(stringArray.toString()))
                put("important_numbers", JSONObject(stringArray.toString()))
                put("next_meeting", JSONObject().put("type", "string"))
            })
            put("required", JSONArray(listOf("summary", "discussion_points", "decisions", "actions", "follow_ups", "important_numbers", "next_meeting")))
        }
    }

    private fun JSONObject.stringList(key: String): List<String> {
        val a = optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until a.length()) {
                val value = a.optString(i).trim()
                if (value.isNotBlank()) add(value)
            }
        }.distinct()
    }

    private fun isMine(owner: String, userName: String): Boolean {
        if (owner.equals("You", true) || owner.equals("Me", true) || owner.equals("I", true)) return true
        return userName.isNotBlank() && !userName.equals("You", true) && owner.contains(userName, true)
    }

    private fun compactTranscript(segments: List<TranscriptSegmentEntity>, maxChars: Int): String {
        val lines = segments.map {
            val seconds = it.startMs / 1000
            val stamp = "%02d:%02d".format(seconds / 60, seconds % 60)
            "[$stamp] ${it.speaker}: ${it.text.trim()}"
        }.filter { it.length > 10 }
        val full = lines.joinToString("\n")
        if (full.length <= maxChars) return full

        val hotWords = listOf(
            "decid", "agree", "approv", "confirm", "will", "must", "need to", "should", "please",
            "send", "follow", "pending", "due", "before", "tomorrow", "today", "monday", "tuesday",
            "wednesday", "thursday", "friday", "saturday", "sunday", "₹", "inr", "%", "lakh", "crore"
        )
        val selected = LinkedHashSet<String>()
        lines.take(35).forEach(selected::add)
        lines.filter { line -> hotWords.any { line.contains(it, true) } }.take(120).forEach(selected::add)
        lines.takeLast(35).forEach(selected::add)
        return selected.joinToString("\n").take(maxChars)
    }

    private fun cleanJson(raw: String): String {
        var s = raw.trim()
        s = s.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```json").removePrefix("```").trim()
            if (s.endsWith("```")) s = s.dropLast(3).trim()
        }
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        require(start >= 0 && end > start) { "AI did not return a JSON object" }
        return s.substring(start, end + 1)
    }
}
