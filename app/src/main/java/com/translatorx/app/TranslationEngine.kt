package com.translatorx.app

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class Mode { AUTO, OFFLINE, ONLINE }
data class TranslationResult(val text: String, val engine: String)

interface TranslationEngine {
    suspend fun translate(text: String, source: String, target: String): TranslationResult
}

class OfflineTranslationEngine : TranslationEngine {
    override suspend fun translate(text: String, source: String, target: String): TranslationResult {
        val sourceLang = requireNotNull(TranslateLanguage.fromLanguageTag(source)) { "Исходный язык не поддерживается" }
        val targetLang = requireNotNull(TranslateLanguage.fromLanguageTag(target)) { "Целевой язык не поддерживается" }
        val installed = ModelManager().installed()
        require(sourceLang in installed && targetLang in installed) { "Сначала скачайте оба языка в разделе «Языки»" }
        val client = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(sourceLang).setTargetLanguage(targetLang).build()
        )
        try {
            return TranslationResult(client.translate(text).await(), "ML Kit · офлайн")
        } finally {
            client.close()
        }
    }
}

class OnlineTranslationEngine(private val endpoint: String, private val apiKey: String) : TranslationEngine {
    override suspend fun translate(text: String, source: String, target: String): TranslationResult =
        withContext(Dispatchers.IO) {
            require(endpoint.startsWith("https://")) { "Нужен HTTPS-адрес сервера" }
            val url = URL(endpoint.trimEnd('/') + "/translate")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 12000
                conn.readTimeout = 20000
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val body = JSONObject().put("q", text).put("source", source).put("target", target).put("format", "text")
                if (apiKey.isNotBlank()) body.put("api_key", apiKey)
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode !in 200..299) error("Сервер вернул HTTP ${conn.responseCode}")
                TranslationResult(JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getString("translatedText"), "Онлайн")
            } finally { conn.disconnect() }
        }
}

class TranslationRouter(private val offline: TranslationEngine, private val online: TranslationEngine?) {
    suspend fun translate(text: String, source: String, target: String, mode: Mode): TranslationResult {
        if (source == target) return TranslationResult(text, "Без перевода")
        return when (mode) {
            Mode.OFFLINE -> offline.translate(text, source, target)
            Mode.ONLINE -> (online ?: error("Сначала настройте онлайн-сервер")).translate(text, source, target)
            Mode.AUTO -> if (online == null) offline.translate(text, source, target) else {
                try { online.translate(text, source, target) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { offline.translate(text, source, target) }
            }
        }
    }
}
