package com.tanu.personal.util
import org.json.JSONArray
fun jsonList(s:String):List<String>{val a=runCatching{JSONArray(s)}.getOrNull()?:return emptyList(); return (0 until a.length()).map{a.optString(it)}.filter{it.isNotBlank()}}
