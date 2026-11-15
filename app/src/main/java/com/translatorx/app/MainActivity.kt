package com.translatorx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.SwapHoriz
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TranslatorXApp() }
    }
}

private val languages = linkedMapOf(
    "Русский" to "ru", "English" to "en", "Deutsch" to "de",
    "Français" to "fr", "Español" to "es", "Türkçe" to "tr",
    "العربية" to "ar", "Italiano" to "it"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorXApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { LocalStore(context) }
    var source by remember { mutableStateOf("Русский") }
    var target by remember { mutableStateOf("English") }
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Готов к переводу") }
    var mode by remember { mutableStateOf(Mode.AUTO) }
    var busy by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf("Перевод") }
    var endpoint by remember { mutableStateOf(store.endpoint) }
    var key by remember { mutableStateOf(store.apiKey) }
    var models by remember { mutableStateOf(setOf<String>()) }
    val manager = remember { ModelManager() }

    fun refreshModels() {
        scope.launch {
            try { models = manager.installed() }
            catch (e: Exception) { status = e.message ?: "Ошибка моделей" }
        }
    }
    LaunchedEffect(page) { if (page == "Языки") refreshModels() }

    MaterialTheme {
        Scaffold(topBar = {
            TopAppBar(title = { Text("Translator X", fontWeight = FontWeight.Bold) },
                actions = { Icon(Icons.Default.Translate, contentDescription = null) })
        }, bottomBar = {
            NavigationBar {
                listOf("Перевод", "Языки", "История", "Настройки").forEach { item ->
                    NavigationBarItem(selected = page == item, onClick = { page = item },
                        icon = { Text(when(item) {"Перевод" -> "🌐"; "Языки" -> "⬇"; "История" -> "◷"; else -> "⚙"}) },
                        label = { Text(item) })
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (page) {
                    "Перевод" -> {
                        Text("Переводите свободно", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Онлайн и без интернета", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LanguageMenu(source, Modifier.weight(1f)) { source = it }
                            IconButton(onClick = { val old = source; source = target; target = old }) {
                                Icon(Icons.AutoMirrored.Filled.SwapHoriz, "Поменять языки")
                            }
                            LanguageMenu(target, Modifier.weight(1f)) { target = it }
                        }
                        OutlinedTextField(value = input, onValueChange = { input = it },
                            label = { Text("Введите текст") }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                            minLines = 5)
                        Text("Режим", style = MaterialTheme.typography.titleMedium)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            Mode.entries.forEachIndexed { index, m ->
                                SegmentedButton(shape = SegmentedButtonDefaults.itemShape(index, Mode.entries.size),
                                    selected = mode == m, onClick = { mode = m },
                                    label = { Text(when(m) { Mode.AUTO -> "Авто"; Mode.OFFLINE -> "Офлайн"; Mode.ONLINE -> "Онлайн" }) })
                            }
                        }
                        Button(enabled = !busy && input.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    val router = TranslationRouter(OfflineTranslationEngine(),
                                        endpoint.takeIf { it.isNotBlank() }?.let { OnlineTranslationEngine(it, key) })
                                    val result = router.translate(input, languages.getValue(source), languages.getValue(target), mode)
                                    output = result.text
                                    status = result.engine
                                    store.addHistory(input, output, source, target)
                                } catch (e: Exception) { status = e.message ?: "Ошибка перевода" }
                                finally { busy = false }
                            }
                        }) { Text(if (busy) "Переводим…" else "Перевести") }
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Результат", style = MaterialTheme.typography.titleMedium)
                                Text(output.ifBlank { "Здесь появится перевод" })
                                if (output.isNotBlank()) {
                                    TextButton(onClick = {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Translation", output))
                                    }) { Icon(Icons.Default.ContentCopy, null); Spacer(Modifier.width(8.dp)); Text("Копировать") }
                                }
                            }
                        }
                        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    "Языки" -> {
                        Text("Офлайн-языки", style = MaterialTheme.typography.headlineMedium)
                        Text("Скачивайте модели заранее. Загрузка требует интернета.")
                        languages.forEach { (name, tag) ->
                            ListItem(headlineContent = { Text(name) },
                                supportingContent = { Text(if (tag in models) "Загружен" else "Не загружен") },
                                trailingContent = {
                                    TextButton(onClick = {
                                        scope.launch {
                                            try {
                                                status = "Обработка: $name"
                                                if (tag in models) manager.delete(tag) else manager.download(tag)
                                                refreshModels()
                                                status = "Готово: $name"
                                            } catch(e: Exception) { status = e.message ?: "Ошибка загрузки" }
                                        }
                                    }) { Text(if (tag in models) "Удалить" else "Скачать") }
                                })
                            HorizontalDivider()
                        }
                        Text(status)
                    }
                    "История" -> {
                        Text("История переводов", style = MaterialTheme.typography.headlineMedium)
                        store.history().forEach { item ->
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(item.first, fontWeight = FontWeight.SemiBold)
                                    Text(item.second)
                                }
                            }
                        }
                        TextButton(onClick = { store.clearHistory(); page = "Перевод" }) { Text("Очистить историю") }
                    }
                    "Настройки" -> {
                        Text("Онлайн-движок", style = MaterialTheme.typography.headlineMedium)
                        Text("Укажите собственный HTTPS-сервер с совместимым LibreTranslate API. Без сервера доступен локальный перевод.")
                        OutlinedTextField(endpoint, { endpoint = it }, label = { Text("HTTPS URL сервера") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(key, { key = it }, label = { Text("API ключ (необязательно)") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { store.endpoint = endpoint; store.apiKey = key; status = "Сохранено" }) { Text("Сохранить") }
                        Text(status)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageMenu(selected: String, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selected) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            languages.keys.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(name); expanded = false })
            }
        }
    }
}
