package com.tanu.personal.domain

import com.tanu.personal.data.*
import org.json.JSONArray
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MomEngine @Inject constructor(){
    data class Result(val mom:MomEntity,val actions:List<ActionItemEntity>)
    private val stop=setOf("the","and","that","this","with","from","have","will","would","there","their","about","into","your","you","for","are","was","were","they","then","also")
    fun build(meeting:MeetingEntity,segments:List<TranscriptSegmentEntity>):Result{
        val transcript=segments.joinToString(" "){it.text}.replace(Regex("\\s+")," ").trim()
        val sentences=transcript.split(Regex("(?<=[.!?])\\s+")).map{it.trim()}.filter{it.length>5}
        val participants=meeting.participantsCsv.split(',').map{it.trim()}.filter{it.isNotBlank()}
        val freq=mutableMapOf<String,Int>()
        sentences.forEach{ s-> s.lowercase(Locale.ENGLISH).replace(Regex("[^a-z0-9₹ ]")," ").split(Regex("\\s+")).filter{it.length>3&&it !in stop}.forEach{freq[it]=(freq[it]?:0)+1} }
        val ranked=sentences.mapIndexed{idx,s-> idx to s to s.lowercase().split(' ').sumOf{freq[it]?:0}}.sortedByDescending{it.second}.take(5).sortedBy{it.first.first}.map{it.first.second}
        val decisions=sentences.filter{ val l=it.lowercase(); listOf("decided","agreed","approved","confirmed","finalized","finalised","decision").any(l::contains)}.distinct().take(12)
        val actionSentences=sentences.filter{ val l=" ${it.lowercase()} "; listOf(" will "," must "," need to "," should "," please "," action "," send "," follow up "," follow-up ").any(l::contains)}.distinct().take(20)
        val dueRe=Regex("(?i)(?:by|before|due|on)\\s+((?:\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?)|(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)|(?:\\d{1,2}\\s+[A-Za-z]{3,9}(?:\\s+\\d{4})?))")
        val actions=actionSentences.map{ s->
            val owner=participants.firstOrNull{s.contains(it,true)} ?: if(s.contains("I will",true)||s.contains("I'll",true)) "You" else "Unassigned"
            val due=dueRe.find(s)?.groupValues?.getOrNull(1)?:""
            ActionItemEntity(UUID.randomUUID().toString(),meeting.id,s,owner,due,if(s.contains("urgent",true)||s.contains("today",true))"high" else "normal")
        }
        val follow=sentences.filter{ val l=it.lowercase(); listOf("pending","follow up","follow-up","awaiting","not confirmed","next step").any(l::contains)}.distinct().take(12)
        val numbers=Regex("(?:₹\\s?[0-9,.]+(?:\\s?(?:lakh|lakhs|crore|cr))?|\\bINR\\s?[0-9,.]+|\\b\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?\\b|\\b\\d+(?:\\.\\d+)?%\\b)",RegexOption.IGNORE_CASE).findAll(transcript).map{it.value}.distinct().take(20).toList()
        val clientCommit=actions.filter{it.owner!="You"&&it.owner!="Unassigned"}.map{it.title}
        val myCommit=actions.filter{it.owner=="You"}.map{it.title}
        val discussion=ranked.ifEmpty{sentences.take(5)}
        val summary=when{
            ranked.isNotEmpty()->ranked.joinToString(" ")
            transcript.isNotBlank()->transcript.take(900)
            else->"No speech content was detected."
        }
        fun json(list:List<String>)=JSONArray(list).toString()
        val mom=MomEntity(meeting.id,summary,json(discussion),json(decisions),json(clientCommit),json(myCommit),json(follow),json(numbers),"")
        return Result(mom,actions)
    }
}
