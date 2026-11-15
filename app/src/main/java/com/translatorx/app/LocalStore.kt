package com.translatorx.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("translator_x", Context.MODE_PRIVATE)
    var endpoint: String
        get() = prefs.getString("endpoint", "") ?: ""
        set(value) { prefs.edit().putString("endpoint", value).apply() }
    var apiKey: String
        get() = prefs.getString("apiKey", "") ?: ""
        set(value) { prefs.edit().putString("apiKey", value).apply() }

    fun history(): List<Pair<String, String>> {
        val arr = runCatching { JSONArray(prefs.getString("history", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val obj = arr.getJSONObject(i)
                obj.getString("source") to obj.getString("translation")
            }.getOrNull()
        }
    }

    fun addHistory(source: String, translation: String, from: String, to: String) {
        val arr = runCatching { JSONArray(prefs.getString("history", "[]")) }.getOrDefault(JSONArray())
        val next = JSONArray()
        next.put(JSONObject().put("source", source).put("translation", translation).put("from", from).put("to", to))
        for (i in 0 until minOf(arr.length(), 99)) next.put(arr.get(i))
        prefs.edit().putString("history", next.toString()).apply()
    }

    fun clearHistory() { prefs.edit().remove("history").apply() }
}
